namespace Heavymatica.Cloud.Features.Identity;

internal sealed class IdentityOptions
{
    public const string Section = "Identity";

    /// <summary>Base URL of Mojang's session server. Development points it at the DevMojang slice.</summary>
    public Uri MojangSessionUrl { get; set; } = new("https://sessionserver.mojang.com");

    /// <summary>
    /// PEM of the ES256 (P-256) private key that signs access tokens. Required outside Development; Development
    /// generates a temporary key when none is configured.
    /// </summary>
    public string? SigningKey { get; set; }

    public string Issuer { get; set; } = "heavymatica-cloud";

    public string Audience { get; set; } = "heavymatica";

    public TimeSpan AccessTokenLifetime { get; set; } = TimeSpan.FromMinutes(15);

    /// <summary>The audience of pending sign-in tokens; access tokens never carry it.</summary>
    public string PendingAudience { get; set; } = "heavymatica-pending";

    /// <summary>How long a pending sign-in (token or website cookie) lasts.</summary>
    public TimeSpan PendingLifetime { get; set; } = TimeSpan.FromMinutes(10);

    public TimeSpan ChallengeLifetime { get; set; } = TimeSpan.FromSeconds(60);

    /// <summary>Sign-in requests (challenge and verify) allowed per client IP and minute.</summary>
    public int SignInRequestsPerMinute { get; set; } = 20;
}
