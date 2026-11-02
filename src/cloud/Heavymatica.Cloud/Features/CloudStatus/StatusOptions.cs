using Heavymatica.Cloud.Contract;

namespace Heavymatica.Cloud.Features.CloudStatus;

internal sealed class StatusOptions
{
    public const string Section = "Status";

    /// <summary>Whether the game offers sign-in to players without a session. Closed until configured.</summary>
    public SignInState SignIn { get; set; } = SignInState.Closed;

    /// <summary>The oldest mod version that may use the service, such as <c>0.3.0</c>; none when empty.</summary>
    public string? MinClient { get; set; }
}
