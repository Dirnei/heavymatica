using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Account;

internal sealed class AccountApi(Accounts accounts, IHttpContextAccessor http) : IAccountController
{
    public async Task<ActionResult<Contract.Account>> GetMeAsync(CancellationToken cancellationToken = default)
    {
        var user = http.HttpContext!.User;
        var account = await accounts.FindAsync(user.AccountId(), cancellationToken);
        var identity = await accounts.FindIdentityAsync(user.IdentityId(), cancellationToken);
        return account is null || identity is null || identity.AccountId != account.Id
            ? Problems.Result(StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated, "The account no longer exists.")
            : Accounts.ToContract(account, identity);
    }
}
