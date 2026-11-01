using System.Security.Claims;
using System.Security.Cryptography;
using Heavymatica.Cloud.Features.Account;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Tokens;

namespace Heavymatica.Cloud.Features.Identity;

internal sealed class AccessTokens
{
    public const string AccountClaim = "sub";
    public const string IdentityClaim = "iid";
    public const string ProviderClaim = "idp";
    public const string NameClaim = "name";
    public const string SessionClaim = "sid";

    /// <summary>The provider subject of a pending sign-in, which has no identity document yet.</summary>
    public const string PendingSubjectClaim = "isub";

    private readonly IdentityOptions options;
    private readonly TimeProvider time;
    private readonly SigningCredentials credentials;
    private readonly JsonWebTokenHandler handler = new();

    public AccessTokens(IOptions<IdentityOptions> options, TimeProvider time, SigningKey key)
    {
        this.options = options.Value;
        this.time = time;
        credentials = new SigningCredentials(key.Key, SecurityAlgorithms.EcdsaSha256);
    }

    public (string Token, DateTimeOffset ExpiresAt) Issue(SignedInIdentity signedIn, string sessionId)
    {
        ArgumentNullException.ThrowIfNull(signedIn);
        return Issue(Claims(signedIn, sessionId));
    }

    /// <summary>The claims of a game access token and of a website session.</summary>
    public static IEnumerable<Claim> Claims(SignedInIdentity signedIn, string sessionId)
    {
        ArgumentNullException.ThrowIfNull(signedIn);
        return
        [
            new Claim(AccountClaim, signedIn.Account.Id),
            new Claim(IdentityClaim, signedIn.Identity.Id),
            new Claim(ProviderClaim, signedIn.Identity.Provider),
            new Claim(NameClaim, signedIn.Identity.Name ?? signedIn.Identity.Subject),
            new Claim(SessionClaim, sessionId),
        ];
    }

    internal (string Token, DateTimeOffset ExpiresAt) Issue(IEnumerable<Claim> claims)
    {
        var now = time.GetUtcNow();
        var expires = now + options.AccessTokenLifetime;
        var token = handler.CreateToken(new SecurityTokenDescriptor
        {
            Issuer = options.Issuer,
            Audience = options.Audience,
            IssuedAt = now.UtcDateTime,
            NotBefore = now.UtcDateTime,
            Expires = expires.UtcDateTime,
            Subject = new ClaimsIdentity(claims),
            SigningCredentials = credentials,
        });
        return (token, expires);
    }

    /// <summary>The claims of a pending sign-in, in a pending token and in the website's pending cookie.</summary>
    public static IEnumerable<Claim> PendingClaims(string provider, string subject, string? name) =>
    [
        new Claim(ProviderClaim, provider),
        new Claim(PendingSubjectClaim, subject),
        new Claim(NameClaim, name ?? subject),
    ];

    public (string Token, DateTimeOffset ExpiresAt) IssuePending(string provider, string subject, string? name)
    {
        var now = time.GetUtcNow();
        var expires = now + options.PendingLifetime;
        var token = handler.CreateToken(new SecurityTokenDescriptor
        {
            Issuer = options.Issuer,
            Audience = options.PendingAudience,
            IssuedAt = now.UtcDateTime,
            NotBefore = now.UtcDateTime,
            Expires = expires.UtcDateTime,
            Subject = new ClaimsIdentity(PendingClaims(provider, subject, name)),
            SigningCredentials = credentials,
        });
        return (token, expires);
    }

    /// <summary>The audiences a token names, read without validating it; only for choosing the scheme that validates it.</summary>
    public static bool NamesAudience(string token, string audience)
    {
        var reader = new JsonWebTokenHandler();
        if (!reader.CanReadToken(token))
        {
            return false;
        }
        // CanReadToken only checks the shape; a payload that is not valid Base64url JSON throws when read.
        try
        {
            return reader.ReadJsonWebToken(token).Audiences.Contains(audience, StringComparer.Ordinal);
        }
        catch (SecurityTokenMalformedException)
        {
            return false;
        }
        catch (ArgumentException)
        {
            return false;
        }
    }

    public static TokenValidationParameters PendingValidation(IdentityOptions options, SigningKey key)
    {
        ArgumentNullException.ThrowIfNull(options);
        var validation = Validation(options, key);
        validation.ValidAudience = options.PendingAudience;
        validation.NameClaimType = NameClaim;
        return validation;
    }

    public static TokenValidationParameters Validation(IdentityOptions options, SigningKey key) => new()
    {
        ValidIssuer = options.Issuer,
        ValidAudience = options.Audience,
        IssuerSigningKey = key.Key,
        ValidAlgorithms = [SecurityAlgorithms.EcdsaSha256],
        ClockSkew = TimeSpan.FromSeconds(30),
        NameClaimType = AccountClaim,
    };
}

/// <summary>
/// Outside Development the private key must come from configuration, so every instance signs with the same key.
/// </summary>
internal sealed class SigningKey : IDisposable
{
    private readonly ECDsa ecdsa;

    private SigningKey(ECDsa ecdsa)
    {
        this.ecdsa = ecdsa;
        Key = new ECDsaSecurityKey(ecdsa) { KeyId = Convert.ToHexStringLower(SHA256.HashData(ecdsa.ExportSubjectPublicKeyInfo()))[..16] };
    }

    public ECDsaSecurityKey Key { get; }

    public static SigningKey From(IdentityOptions options, IHostEnvironment environment, ILogger logger)
    {
        var ecdsa = ECDsa.Create();
        if (!string.IsNullOrWhiteSpace(options.SigningKey))
        {
            ecdsa.ImportFromPem(options.SigningKey);
            return new SigningKey(ecdsa);
        }

        if (!environment.IsDevelopment())
        {
            ecdsa.Dispose();
            throw new InvalidOperationException("Identity:SigningKey must hold an ES256 private key (PEM) outside Development.");
        }

        ecdsa.GenerateKey(ECCurve.NamedCurves.nistP256);
        IdentityLog.TemporarySigningKey(logger);
        return new SigningKey(ecdsa);
    }

    public void Dispose() => ecdsa.Dispose();
}
