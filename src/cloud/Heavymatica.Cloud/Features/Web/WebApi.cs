using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Web;

internal sealed class WebApi(IHttpContextAccessor http, Sessions sessions) : IWebController
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
}
