using System.Security.Claims;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Account;

internal sealed class AccountApi(Accounts accounts, Sessions sessions, IOptions<AccountsOptions> options, IHttpContextAccessor http) : IAccountController
{
    public async Task<ActionResult<Contract.Account>> GetMeAsync(CancellationToken cancellationToken = default)
    {
        var user = http.HttpContext!.User;
        var account = await accounts.FindAsync(user.AccountId(), cancellationToken);
        var identity = await accounts.FindIdentityAsync(user.IdentityId(), cancellationToken);
        if (account is null || identity is null || identity.AccountId != account.Id)
        {
            return Problems.Result(StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated, "The account no longer exists.");
        }

        var me = Accounts.ToContract(account, identity);
        me.Identities = (await accounts.ListIdentitiesAsync(account.Id, cancellationToken))
            .Select(linked => new Contract.Identity
            {
                Id = linked.Id,
                Provider = linked.Provider,
                Name = linked.Name,
                Current = linked.Id == identity.Id,
            })
            .ToList();
        me.LinkingAvailable = options.Value.LinkingAvailable;
        return me;
    }

    public async Task<IActionResult> UnlinkIdentityAsync(string identityId, CancellationToken cancellationToken = default)
    {
        var user = http.HttpContext!.User;
        var actor = new AuditActor(user.FindFirstValue(AccessTokens.ProviderClaim) ?? string.Empty, user.IdentityId());
        switch (await accounts.RemoveIdentityAsync(user.AccountId(), identityId, actor, cancellationToken))
        {
            case RemoveIdentityOutcome.NotFound:
                return Problems.Result(StatusCodes.Status404NotFound, ProblemCode.Not_found, "The account has no such identity.");
            case RemoveIdentityOutcome.LastIdentity:
                return Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Last_identity,
                    "This is the account's only sign-in. It cannot be unlinked.");
        }

        await sessions.EndAllForIdentityAsync(identityId, cancellationToken);
        return new NoContentResult();
    }
}
