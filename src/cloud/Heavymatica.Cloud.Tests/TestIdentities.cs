using Heavymatica.Cloud.Features.Account;

namespace Heavymatica.Cloud.Tests;

internal static class TestIdentities
{
    /// <summary>An account with a Minecraft identity that exists only in memory, for issuing tokens directly.</summary>
    public static SignedInIdentity Minecraft(string accountId, string name) =>
        new(new AccountDocument { Id = accountId }, Accounts.NewIdentity(accountId, Providers.Minecraft, Guid.NewGuid().ToString(), name, DateTime.UtcNow));
}
