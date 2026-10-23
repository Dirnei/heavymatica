using System.Security.Claims;
using AspNet.Security.OAuth.GitHub;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.OAuth;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Web;

internal sealed class GitHubOptions
{
    public const string Section = "GitHub";

    public string? ClientId { get; set; }

    public string? ClientSecret { get; set; }

    public bool Configured => !string.IsNullOrWhiteSpace(ClientId) && !string.IsNullOrWhiteSpace(ClientSecret);
}

/// <summary>
/// Website sign-in through GitHub's OAuth flow, run by the service. The OAuth handler leaves the GitHub identity in a
/// short-lived external cookie; <c>complete</c> maps it to an account and starts the website session.
/// </summary>
internal static class GitHubSignIn
{
    public const string Scheme = GitHubAuthenticationDefaults.AuthenticationScheme;
    public const string CallbackPath = "/v1/web/sign-in/github/callback";
    public const string CompletePath = "/v1/web/sign-in/complete";
    public const string DefaultReturnTo = "/library/";

    public const string IntentItem = "intent";
    public const string ReturnToItem = "returnTo";

    public const string Register = "register";
    public const string Login = "login";

    public static WebApplicationBuilder AddGitHubSignIn(this WebApplicationBuilder builder, AuthenticationBuilder authentication)
    {
        ArgumentNullException.ThrowIfNull(builder);
        ArgumentNullException.ThrowIfNull(authentication);
        builder.Services.AddOptions<GitHubOptions>().BindConfiguration(GitHubOptions.Section);
        var options = builder.Configuration.GetSection(GitHubOptions.Section).Get<GitHubOptions>() ?? new GitHubOptions();
        if (!options.Configured)
        {
            return builder;
        }

        var development = builder.Environment.IsDevelopment();
        authentication.AddGitHub(Scheme, github =>
        {
            github.ClientId = options.ClientId!;
            github.ClientSecret = options.ClientSecret!;
            github.CallbackPath = CallbackPath;
            github.SignInScheme = WebSessions.ExternalScheme;
            github.SaveTokens = false;
            github.Scope.Clear();
            github.CorrelationCookie.SameSite = SameSiteMode.Lax;
            github.CorrelationCookie.SecurePolicy = development ? CookieSecurePolicy.SameAsRequest : CookieSecurePolicy.Always;
            github.Events = new OAuthEvents
            {
                OnAccessDenied = context =>
                {
                    context.Response.Redirect(ErrorPath("cancelled"));
                    context.HandleResponse();
                    return Task.CompletedTask;
                },
                OnRemoteFailure = context =>
                {
                    // The handler reports a broken flow (state, correlation, code) by these messages; anything else
                    // went wrong while talking to GitHub.
                    var brokenFlow = context.Failure?.Message is "The oauth state was missing or invalid." or "Correlation failed." or "Code was not found.";
                    context.Response.Redirect(ErrorPath(brokenFlow ? "sign_in_failed" : "github_unavailable"));
                    context.HandleResponse();
                    return Task.CompletedTask;
                },
            };
        });
        return builder;
    }

    public static WebApplication MapGitHubSignIn(this WebApplication app)
    {
        ArgumentNullException.ThrowIfNull(app);
        app.MapGet(CompletePath, CompleteAsync).AllowAnonymous().ExcludeFromDescription();
        return app;
    }

    /// <summary>A local path starting with a single slash; anything else opens the library, so the flow is no open redirect.</summary>
    public static string SafeReturnTo(string? returnTo) =>
        returnTo is { Length: > 0 } && returnTo[0] == '/' && (returnTo.Length == 1 || returnTo[1] is not ('/' or '\\'))
            && !returnTo.Contains('\\', StringComparison.Ordinal) && Uri.IsWellFormedUriString(returnTo, UriKind.Relative)
            ? returnTo
            : DefaultReturnTo;

    public static string ErrorPath(string error) => "/?error=" + error;

    public static AuthenticationProperties ChallengeProperties(string intent, string? returnTo)
    {
        var properties = new AuthenticationProperties { RedirectUri = CompletePath };
        properties.Items[IntentItem] = intent;
        properties.Items[ReturnToItem] = SafeReturnTo(returnTo);
        return properties;
    }

    private static async Task<IResult> CompleteAsync(HttpContext context, Accounts accounts, Sessions sessions, TimeProvider time)
    {
        var cancellationToken = context.RequestAborted;
        var external = await context.AuthenticateAsync(WebSessions.ExternalScheme);
        await context.SignOutAsync(WebSessions.ExternalScheme);
        var userId = external.Principal?.FindFirstValue(ClaimTypes.NameIdentifier);
        if (!external.Succeeded || string.IsNullOrEmpty(userId))
        {
            return Results.Redirect(ErrorPath("sign_in_failed"));
        }

        var login = external.Principal!.FindFirstValue(ClaimTypes.Name) ?? userId;
        var intent = external.Properties?.GetString(IntentItem);
        var returnTo = SafeReturnTo(external.Properties?.GetString(ReturnToItem));

        var signedIn = intent == Register
            ? (await accounts.CreateWithIdentityAsync(Providers.GitHub, userId, login, cancellationToken)).SignedIn
            : await accounts.SignInAsync(Providers.GitHub, userId, login, cancellationToken);
        if (signedIn is null)
        {
            return Results.Redirect(ErrorPath("no_account"));
        }

        var sessionId = await sessions.StartWebAsync(signedIn.Account.Id, signedIn.Identity.Id, cancellationToken);
        await context.SignInAsync(WebSessions.Scheme, WebSessions.Principal(signedIn, sessionId), WebSessions.Properties(time));
        return Results.Redirect(returnTo);
    }

    public static bool IsAvailable(IServiceProvider services) =>
        services.GetRequiredService<IOptions<GitHubOptions>>().Value.Configured;
}
