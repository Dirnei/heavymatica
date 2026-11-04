using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;

namespace Heavymatica.Cloud.Features.Web;

/// <summary>
/// A website sign-in without GitHub, mapped only in Development: <c>/dev/web/sign-in?minecraft=&lt;profile id&gt;</c>
/// starts a website session for the account of that Minecraft identity, so scripts and local checks can use the
/// website as a game player. It skips every authentication, so it must never be reachable in production.
/// </summary>
internal static class DevWebSignIn
{
    public const string Path = "/dev/web/sign-in";

    public static WebApplication MapDevWebSignIn(this WebApplication app)
    {
        if (!app.Environment.IsDevelopment())
        {
            return app;
        }

        app.MapGet(Path, SignInAsync).AllowAnonymous().ExcludeFromDescription();
        return app;
    }

    private static async Task<IResult> SignInAsync(HttpContext context, Accounts accounts, Sessions sessions, TimeProvider time,
        string? minecraft, string? returnTo)
    {
        if (!Guid.TryParse(minecraft, out var profile))
        {
            return Results.BadRequest();
        }

        var signedIn = await accounts.FindByIdentityAsync(Providers.Minecraft, AuthApi.MinecraftSubject(profile), context.RequestAborted);
        if (signedIn is null)
        {
            return Results.NotFound();
        }

        await GitHubSignIn.StartWebSessionAsync(context, sessions, time, signedIn);
        return Results.Redirect(GitHubSignIn.SafeReturnTo(returnTo));
    }
}
