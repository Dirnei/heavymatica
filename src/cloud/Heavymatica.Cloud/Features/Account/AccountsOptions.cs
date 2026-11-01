namespace Heavymatica.Cloud.Features.Account;

internal sealed class AccountsOptions
{
    public const string Section = "Accounts";

    /// <summary>
    /// The website origin that redeems link codes, for example <c>https://heavymatica.dev</c>. Linking (link codes and
    /// pending sign-ins) is available only while it is set; without it, unknown identities get an account at once.
    /// </summary>
    public Uri? LinkBaseUrl { get; set; }

    public bool LinkingAvailable => LinkBaseUrl is not null;

    public TimeSpan LinkCodeLifetime { get; set; } = TimeSpan.FromMinutes(5);

    /// <summary>How long the issuer has to confirm once a code is redeemed.</summary>
    public TimeSpan LinkConfirmLifetime { get; set; } = TimeSpan.FromMinutes(5);

    /// <summary>Codes one issuer may issue per hour, counted from the audit log.</summary>
    public int LinkIssuesPerHour { get; set; } = 10;

    public int LinkRedeemsPerRedeemer { get; set; } = 10;

    public int LinkRedeemsPerAddress { get; set; } = 30;

    public TimeSpan LinkRedeemWindow { get; set; } = TimeSpan.FromMinutes(10);

    public TimeSpan LinkAuditRetention { get; set; } = TimeSpan.FromDays(90);
}
