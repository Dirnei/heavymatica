using System.Text.Json;
using Heavymatica.Cloud.Contract.Admin;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>Conversions between the admin contract and the stored documents.</summary>
internal static class AdminContract
{
    public static string Ip(HttpContext context)
    {
        ArgumentNullException.ThrowIfNull(context);
        return context.Connection.RemoteIpAddress?.ToString() ?? "unknown";
    }

    /// <summary>WebAuthn JSON from the API, as an object the contract passes through unchanged.</summary>
    public static PasskeyOptions Options(string json)
    {
        using var document = JsonDocument.Parse(json);
        return new PasskeyOptions { Options = document.RootElement.Clone() };
    }

    /// <summary>The browser's credential JSON, as the client sent it.</summary>
    public static string? CredentialJson(PasskeyCredential? body) => body?.Credential switch
    {
        JsonElement { ValueKind: JsonValueKind.Object } element => element.GetRawText(),
        _ => null,
    };

    public static AdminSession Session(AdminUser admin, int passkeyCount, DateTime expiresAt)
    {
        ArgumentNullException.ThrowIfNull(admin);
        return new AdminSession
        {
            AdminId = admin.Id,
            DisplayName = admin.DisplayName,
            PasskeyCount = passkeyCount,
            ExpiresAt = Utc(expiresAt),
        };
    }

    public static Passkey Passkey(AdminPasskeyDocument passkey)
    {
        ArgumentNullException.ThrowIfNull(passkey);
        return new Passkey
        {
            Id = passkey.Id,
            Name = passkey.Name,
            CreatedAt = Utc(passkey.CreatedAt),
            LastUsedAt = passkey.LastUsedAt is { } used ? Utc(used) : null,
        };
    }

    public static DateTimeOffset Utc(DateTime value) => new(DateTime.SpecifyKind(value, DateTimeKind.Utc));

    /// <summary>A trimmed display or passkey name, or null when it is empty or too long.</summary>
    public static string? Name(string? value, int maxLength)
    {
        var trimmed = value?.Trim();
        return string.IsNullOrEmpty(trimmed) || trimmed.Length > maxLength ? null : trimmed;
    }
}
