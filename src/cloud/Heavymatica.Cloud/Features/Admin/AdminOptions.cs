namespace Heavymatica.Cloud.Features.Admin;

internal sealed class AdminOptions
{
    public const string Section = "Admin";

    /// <summary>The admin listener, for example <c>http://localhost:5181</c>. Without it and <see cref="Host"/> there is no admin access.</summary>
    public Uri? Url { get; set; }

    /// <summary>The only host name the admin listener answers for, and the passkeys' relying party ID.</summary>
    public string? Host { get; set; }

    /// <summary>Where admins open the admin site, for invitation links and the setup command. Defaults to <c>https://{Host}/</c>.</summary>
    public Uri? SiteUrl { get; set; }

    /// <summary>The built admin site, served on the admin listener. Relative paths are resolved against the executable's directory.</summary>
    public string SiteRoot { get; set; } = "wwwroot-admin";

    public TimeSpan SetupCodeLifetime { get; set; } = TimeSpan.FromMinutes(15);

    public TimeSpan InvitationLifetime { get; set; } = TimeSpan.FromHours(24);

    public TimeSpan SessionIdleLifetime { get; set; } = TimeSpan.FromMinutes(30);

    public TimeSpan SessionLifetime { get; set; } = TimeSpan.FromHours(8);

    public TimeSpan CeremonyLifetime { get; set; } = TimeSpan.FromMinutes(5);

    public int SignInAttemptsPerMinute { get; set; } = 10;

    public int CodeAttempts { get; set; } = 5;

    public TimeSpan CodeAttemptWindow { get; set; } = TimeSpan.FromMinutes(10);

    public TimeSpan AuditRetention { get; set; } = TimeSpan.FromDays(400);

    public bool Configured => Url is not null && !string.IsNullOrWhiteSpace(Host);

    public int Port => Url?.Port ?? throw new InvalidOperationException("Admin:Url is not configured.");

    public Uri Site => SiteUrl ?? new Uri($"https://{Host}/");

    /// <summary>Throws when only one of <see cref="Url"/> and <see cref="Host"/> is set.</summary>
    public void Validate()
    {
        if ((Url is null) != string.IsNullOrWhiteSpace(Host))
        {
            throw new InvalidOperationException("Set both Admin:Url and Admin:Host to open the admin listener, or neither.");
        }
    }
}
