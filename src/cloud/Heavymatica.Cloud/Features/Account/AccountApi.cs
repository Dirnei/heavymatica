using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Account;

/// <summary>Implements the contract's Account operations.</summary>
internal sealed class AccountApi(Accounts accounts, IHttpContextAccessor http) : IAccountController
{
    public async Task<ActionResult<Contract.Account>> GetMeAsync(CancellationToken cancellationToken = default)
    {
        var account = await accounts.FindAsync(http.HttpContext!.User.AccountId(), cancellationToken);
        return account is null
            ? Problems.Result(StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated, "The account no longer exists.")
            : Accounts.ToContract(account);
    }
}
