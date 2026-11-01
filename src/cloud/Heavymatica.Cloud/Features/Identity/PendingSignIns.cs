using System.Security.Claims;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Identity;

internal enum PrincipalKind
{
    None,
    Account,
    Pending,
}

/// <summary>A verified identity without an account, as a pending sign-in names it.</summary>
internal sealed record PendingIdentityClaims(string Provider, string Subject, string Name);

/// <summary>
/// Pending sign-ins: a verified identity without an account, for a few minutes. The game holds a pending token (a JWT
/// with its own audience, so the account bearer scheme never accepts it); the website holds the <c>WebPending</c>
/// cookie. Both are stateless and grant only the calls that create an account, finish the sign-in or handle link
/// codes.
/// </summary>
internal static class PendingSignIns
{
    public const string BearerScheme = "pending";
    public const string WebScheme = "WebPending";

    /// <summary>Picks the scheme of whatever the request carries: an access or pending token, a website session or a pending cookie.</summary>
    public const string AnyScheme = "AccountOrPending";

    public const string AnyPolicy = "account-or-pending";
    public const string GamePolicy = "pending-game";
    public const string WebPolicy = "pending-web";

    public const string DevelopmentCookieName = "heavymatica-pending";
    public const string CookieName = "__Host-heavymatica-pending";

    public static AuthenticationBuilder AddPendingSignIns(this AuthenticationBuilder authentication, IHostEnvironment environment)
    {
        ArgumentNullException.ThrowIfNull(authentication);
        ArgumentNullException.ThrowIfNull(environment);
        var development = environment.IsDevelopment();
        var cookieName = development ? DevelopmentCookieName : CookieName;
        var sessionCookieName = development ? WebSessions.DevelopmentCookieName : WebSessions.CookieName;

        authentication.AddJwtBearer(BearerScheme);
        authentication.AddCookie(WebScheme, cookie =>
        {
            cookie.Cookie.Name = cookieName;
            cookie.Cookie.HttpOnly = true;
            cookie.Cookie.SameSite = SameSiteMode.Lax;
            cookie.Cookie.Path = "/";
            cookie.Cookie.SecurePolicy = development ? CookieSecurePolicy.SameAsRequest : CookieSecurePolicy.Always;
            cookie.SlidingExpiration = false;
            cookie.Events = new CookieAuthenticationEvents
            {
                OnRedirectToLogin = context => Problems.WriteAsync(context.Response, StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated,
                    "The pending sign-in has ended. Sign in again."),
                OnRedirectToAccessDenied = context => Problems.WriteAsync(context.Response, StatusCodes.Status403Forbidden, ProblemCode.Request_forbidden,
                    "This request is not allowed."),
            };
        });

        authentication.AddPolicyScheme(AnyScheme, "Account or pending sign-in, any transport", policy =>
            policy.ForwardDefaultSelector = context =>
            {
                var authorization = context.Request.Headers.Authorization.ToString();
                if (authorization.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
                {
                    var audience = context.RequestServices.GetRequiredService<IOptions<IdentityOptions>>().Value.PendingAudience;
                    return AccessTokens.NamesAudience(authorization["Bearer ".Length..].Trim(), audience) ? BearerScheme : JwtBearerDefaults.AuthenticationScheme;
                }

                return !context.Request.Cookies.ContainsKey(sessionCookieName) && context.Request.Cookies.ContainsKey(cookieName)
                    ? WebScheme
                    : WebSessions.Scheme;
            });
        return authentication;
    }

    public static ClaimsPrincipal WebPrincipal(string provider, string subject, string? name) =>
        new(new ClaimsIdentity(AccessTokens.PendingClaims(provider, subject, name), WebScheme, AccessTokens.NameClaim, null));

    public static AuthenticationProperties WebProperties(TimeProvider time, IdentityOptions options)
    {
        ArgumentNullException.ThrowIfNull(time);
        ArgumentNullException.ThrowIfNull(options);
        return new AuthenticationProperties { IsPersistent = true, IssuedUtc = time.GetUtcNow(), ExpiresUtc = time.GetUtcNow() + options.PendingLifetime };
    }

    public static PrincipalKind Kind(this ClaimsPrincipal user)
    {
        ArgumentNullException.ThrowIfNull(user);
        if (user.Identity?.IsAuthenticated != true)
        {
            return PrincipalKind.None;
        }

        if (user.FindFirstValue(AccessTokens.IdentityClaim) is not null && user.FindFirstValue(AccessTokens.AccountClaim) is not null)
        {
            return PrincipalKind.Account;
        }

        return user.FindFirstValue(AccessTokens.PendingSubjectClaim) is not null && user.FindFirstValue(AccessTokens.ProviderClaim) is not null
            ? PrincipalKind.Pending
            : PrincipalKind.None;
    }

    /// <summary>The identity of a pending sign-in.</summary>
    public static PendingIdentityClaims PendingIdentity(this ClaimsPrincipal user)
    {
        ArgumentNullException.ThrowIfNull(user);
        var provider = user.FindFirstValue(AccessTokens.ProviderClaim);
        var subject = user.FindFirstValue(AccessTokens.PendingSubjectClaim);
        return provider is null || subject is null
            ? throw new InvalidOperationException("The request is not a pending sign-in.")
            : new PendingIdentityClaims(provider, subject, user.FindFirstValue(AccessTokens.NameClaim) ?? subject);
    }

    public static bool IsWebPending(this ClaimsPrincipal user)
    {
        ArgumentNullException.ThrowIfNull(user);
        return user.Identities.Any(identity => identity.IsAuthenticated && identity.AuthenticationType == WebScheme);
    }
}
