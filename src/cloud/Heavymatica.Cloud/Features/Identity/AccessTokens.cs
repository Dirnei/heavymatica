using System.Security.Claims;
using System.Security.Cryptography;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Tokens;

namespace Heavymatica.Cloud.Features.Identity;

/// <summary>Issues the service's ES256 access tokens and describes how to validate them (D3).</summary>
internal sealed class AccessTokens
{
    public const string AccountClaim = "sub";
    public const string SessionClaim = "sid";

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

    public (string Token, DateTimeOffset ExpiresAt) Issue(string accountId, string name, string sessionId)
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
            Subject = new ClaimsIdentity([new Claim(AccountClaim, accountId), new Claim("name", name), new Claim(SessionClaim, sessionId)]),
            SigningCredentials = credentials,
        });
        return (token, expires);
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
/// The ES256 key pair. Outside Development the private key must come from configuration, so every instance signs
/// with the same key and later game-server tokens can be checked with the public key alone.
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
