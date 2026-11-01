using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Web;

internal sealed class WebApi(IHttpContextAccessor http, Sessions sessions, Accounts accounts, TimeProvider time,
    IOptions<AccountsOptions> accountsOptions) : IWebController
{
    public Task<ActionResult<Problem>> StartGitHubSignInAsync(Intent intent, string? returnTo, CancellationToken cancellationToken = default)
    {
        var context = http.HttpContext!;
        if (!GitHubSignIn.IsAvailable(context.RequestServices))
        {
            return Task.FromResult<ActionResult<Problem>>(Problems.Result(StatusCodes.Status503ServiceUnavailable, ProblemCode.Web_sign_in_unavailable,
                "Website sign-in is not available right now."));
        }

        var properties = GitHubSignIn.ChallengeProperties(intent == Intent.Register ? GitHubSignIn.Register : GitHubSignIn.Login, returnTo);
        return Task.FromResult<ActionResult<Problem>>(new ChallengeResult(GitHubSignIn.Scheme, properties));
    }

    public async Task<IActionResult> SignOutWebAsync(CancellationToken cancellationToken = default)
    {
        var context = http.HttpContext!;
        if (context.User.IsWebSession())
        {
            await sessions.EndAsync(context.User.SessionId(), cancellationToken);
            await context.SignOutAsync(WebSessions.Scheme);
        }

        return new NoContentResult();
    }

    public async Task<ActionResult<WebPending>> GetWebPendingAsync(CancellationToken cancellationToken = default)
    {
        var context = http.HttpContext!;
        var pending = context.User.PendingIdentity();
        var ticket = await context.AuthenticateAsync(PendingSignIns.WebScheme);
        return new WebPending
        {
            Identity = new PendingIdentity { Provider = pending.Provider, Name = pending.Name },
            ExpiresAt = ticket.Properties?.ExpiresUtc ?? time.GetUtcNow(),
            JoinAvailable = accountsOptions.Value.LinkingAvailable,
        };
    }

    public async Task<ActionResult<Contract.Account>> CreateWebAccountAsync(CancellationToken cancellationToken = default)
    {
        var context = http.HttpContext!;
        var pending = context.User.PendingIdentity();
        var (signedIn, _) = await accounts.CreateWithIdentityAsync(pending.Provider, pending.Subject, pending.Name, cancellationToken);
        await GitHubSignIn.StartWebSessionAsync(context, sessions, time, signedIn);
        return Accounts.ToContract(signedIn.Account, signedIn.Identity);
    }

    public async Task<ActionResult<Contract.Account>> CompleteWebSignInAsync(CancellationToken cancellationToken = default)
    {
        var context = http.HttpContext!;
        var pending = context.User.PendingIdentity();
        if (await accounts.SignInAsync(pending.Provider, pending.Subject, pending.Name, cancellationToken) is not { } signedIn)
        {
            return Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Account_choice_required,
                "This sign-in has no account yet. Create one, or join one with a link code first.");
        }

        await GitHubSignIn.StartWebSessionAsync(context, sessions, time, signedIn);
        return Accounts.ToContract(signedIn.Account, signedIn.Identity);
    }
}
