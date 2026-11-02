using System.Text.Json;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Admin;

internal static class CeremonyKinds
{
    public const string SignIn = "sign-in";
    public const string Registration = "registration";
    public const string AddPasskey = "add-passkey";
}

/// <summary>
/// What a passkey ceremony needs between its options call and its second call: Identity's attestation or assertion
/// state, and for a registration the code or invitation it was started with and the admin it is for.
/// </summary>
internal sealed record Ceremony
{
    public required string Kind { get; init; }

    public required string State { get; init; }

    public DateTimeOffset ExpiresAt { get; init; }

    /// <summary>The admin the passkey is for; a new one when <see cref="NewAdmin"/> is set.</summary>
    public string? AdminId { get; init; }

    public bool NewAdmin { get; init; }

    public string? DisplayName { get; init; }

    public string? SetupCodeHash { get; init; }

    public string? InvitationId { get; init; }
}

/// <summary>
/// Keeps a ceremony in a short-lived, encrypted, HttpOnly cookie on the admin paths, so the browser that asked for the
/// options is the one that finishes. Starting a ceremony replaces the previous one.
/// </summary>
internal sealed class Ceremonies(IDataProtectionProvider protection, TimeProvider time, IOptions<AdminOptions> options, IHostEnvironment environment)
{
    private const string DevelopmentCookieName = "heavymatica-admin-ceremony";
    private const string CookieName = "__Secure-heavymatica-admin-ceremony";

    private readonly IDataProtector protector = protection.CreateProtector("Heavymatica.Admin.Ceremony");

    private string Name => environment.IsDevelopment() ? DevelopmentCookieName : CookieName;

    public void Start(HttpContext context, Ceremony ceremony)
    {
        ArgumentNullException.ThrowIfNull(context);
        ArgumentNullException.ThrowIfNull(ceremony);
        var stored = ceremony with { ExpiresAt = time.GetUtcNow() + options.Value.CeremonyLifetime };
        context.Response.Cookies.Append(Name, protector.Protect(JsonSerializer.Serialize(stored)), new CookieOptions
        {
            HttpOnly = true,
            SameSite = SameSiteMode.Strict,
            Secure = !environment.IsDevelopment() || context.Request.IsHttps,
            Path = "/admin",
            MaxAge = options.Value.CeremonyLifetime,
        });
    }

    /// <summary>The ceremony of the given kind this browser started, once; null when there is none or it expired.</summary>
    public Ceremony? Take(HttpContext context, string kind)
    {
        ArgumentNullException.ThrowIfNull(context);
        if (!context.Request.Cookies.TryGetValue(Name, out var value))
        {
            return null;
        }

        context.Response.Cookies.Delete(Name, new CookieOptions { Path = "/admin", Secure = !environment.IsDevelopment() || context.Request.IsHttps });
        Ceremony? ceremony;
        try
        {
            ceremony = JsonSerializer.Deserialize<Ceremony>(protector.Unprotect(value));
        }
        catch (Exception e) when (e is System.Security.Cryptography.CryptographicException or JsonException)
        {
            return null;
        }

        return ceremony is not null && ceremony.Kind == kind && ceremony.ExpiresAt > time.GetUtcNow() ? ceremony : null;
    }
}
