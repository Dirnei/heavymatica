using System.Security.Claims;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authentication.JwtBearer;

namespace Heavymatica.Cloud.Features.Identity;

/// <summary>
/// The website session: an HttpOnly cookie holding an encrypted ticket with the same claims as a game access token,
/// backed by a <c>web</c> session document that is checked on every request.
/// </summary>
internal static class WebSessions
{
    public const string Scheme = "WebSession";

    /// <summary>Picks the bearer scheme when the request carries an access token, otherwise the website session.</summary>
    public const string PlayerScheme = "Player";

    /// <summary>Holds the GitHub identity for a few minutes between the OAuth callback and the website sign-in.</summary>
    public const string ExternalScheme = "WebExternal";

    public const string DevelopmentCookieName = "heavymatica";
    public const string CookieName = "__Host-heavymatica";

    public static readonly TimeSpan IdleLifetime = IdentityIndexes.SessionIdleLifetime;
    public static readonly TimeSpan TouchInterval = TimeSpan.FromHours(1);

    public static AuthenticationBuilder AddWebSessions(this AuthenticationBuilder authentication, IHostEnvironment environment)
    {
        ArgumentNullException.ThrowIfNull(authentication);
        ArgumentNullException.ThrowIfNull(environment);
        var development = environment.IsDevelopment();

        authentication.AddPolicyScheme(PlayerScheme, "Access token or website session", policy =>
            policy.ForwardDefaultSelector = context =>
                context.Request.Headers.Authorization.ToString().StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)
                    ? JwtBearerDefaults.AuthenticationScheme
                    : Scheme);

        authentication.AddCookie(Scheme, cookie =>
        {
            cookie.Cookie.Name = development ? DevelopmentCookieName : CookieName;
            cookie.Cookie.HttpOnly = true;
            // Lax, not Strict: the sign-in ends with a top-level navigation back from github.com.
            cookie.Cookie.SameSite = SameSiteMode.Lax;
            cookie.Cookie.Path = "/";
            cookie.Cookie.SecurePolicy = development ? CookieSecurePolicy.SameAsRequest : CookieSecurePolicy.Always;
            cookie.ExpireTimeSpan = IdleLifetime;
            cookie.SlidingExpiration = false;
            cookie.Events = new CookieAuthenticationEvents
            {
                OnValidatePrincipal = ValidateAsync,
                OnRedirectToLogin = context => Problems.WriteAsync(context.Response, StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated,
                    "Sign in on the website first."),
                OnRedirectToAccessDenied = context => Problems.WriteAsync(context.Response, StatusCodes.Status403Forbidden, ProblemCode.Request_forbidden,
                    "This request is not allowed."),
            };
        });

        authentication.AddCookie(ExternalScheme, cookie =>
        {
            cookie.Cookie.Name = (development ? DevelopmentCookieName : CookieName) + "-external";
            cookie.Cookie.HttpOnly = true;
            cookie.Cookie.SameSite = SameSiteMode.Lax;
            cookie.Cookie.Path = "/v1/web/sign-in";
            cookie.Cookie.SecurePolicy = development ? CookieSecurePolicy.SameAsRequest : CookieSecurePolicy.Always;
            cookie.ExpireTimeSpan = TimeSpan.FromMinutes(5);
        });
        return authentication;
    }

    /// <summary>The website principal of a signed-in identity and its <c>web</c> session.</summary>
    public static ClaimsPrincipal Principal(SignedInIdentity signedIn, string sessionId) =>
        new(new ClaimsIdentity(AccessTokens.Claims(signedIn, sessionId), Scheme, AccessTokens.NameClaim, null));

    public static AuthenticationProperties Properties(TimeProvider time)
    {
        ArgumentNullException.ThrowIfNull(time);
        return new AuthenticationProperties { IsPersistent = true, IssuedUtc = time.GetUtcNow(), ExpiresUtc = time.GetUtcNow() + IdleLifetime };
    }

    public static bool IsWebSession(this ClaimsPrincipal user)
    {
        ArgumentNullException.ThrowIfNull(user);
        return user.Identities.Any(identity => identity.IsAuthenticated && identity.AuthenticationType == Scheme);
    }

    private static async Task ValidateAsync(CookieValidatePrincipalContext context)
    {
        var services = context.HttpContext.RequestServices;
        var sessions = services.GetRequiredService<Sessions>();
        var accounts = services.GetRequiredService<Accounts>();
        var time = services.GetRequiredService<TimeProvider>();
        var cancellationToken = context.HttpContext.RequestAborted;
        var principal = context.Principal;

        var sessionId = principal?.FindFirstValue(AccessTokens.SessionClaim);
        var identityId = principal?.FindFirstValue(AccessTokens.IdentityClaim);
        var session = sessionId is null ? null : await sessions.FindAsync(sessionId, cancellationToken);
        var valid = session is { Kind: SessionKinds.Web }
            && sessions.IsActive(session)
            && session.IdentityId == identityId
            && session.AccountId == principal?.FindFirstValue(AccessTokens.AccountClaim)
            && identityId is not null
            && await accounts.FindIdentityAsync(identityId, cancellationToken) is { } identity
            && identity.AccountId == session.AccountId;
        if (!valid)
        {
            context.RejectPrincipal();
            await context.HttpContext.SignOutAsync(Scheme);
            return;
        }

        if (time.GetUtcNow().UtcDateTime - session!.LastUsedAt >= TouchInterval)
        {
            await sessions.TouchAsync(session.Id, cancellationToken);
            context.Properties.IssuedUtc = time.GetUtcNow();
            context.Properties.ExpiresUtc = time.GetUtcNow() + IdleLifetime;
            context.ShouldRenew = true;
        }
    }
}
