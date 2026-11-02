using System.Security.Claims;
using System.Security.Cryptography;
using Heavymatica.Cloud.Contract.Admin;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>The signed-in admin of the current request, as the session check found them.</summary>
internal sealed record AdminCaller(AdminUser Admin, AdminSessionDocument Session);

/// <summary>
/// The admin session: an HttpOnly, SameSite=Strict cookie bound to the admin host, backed by an <c>admin_sessions</c>
/// document that is checked on every request. A session ends after a stretch without requests, a fixed time after
/// sign-in, on sign-out, or when its admin is removed.
/// </summary>
internal sealed class AdminSessions(IMongoDatabase database, TimeProvider time, IOptions<AdminOptions> options)
{
    public const string Scheme = "AdminSession";
    public const string Policy = "admin";

    public const string DevelopmentCookieName = "heavymatica-admin";
    public const string CookieName = "__Host-heavymatica-admin";

    public const string AdminClaim = "admin";
    public const string SessionClaim = "sid";

    private static readonly object CallerKey = new();
    private static readonly TimeSpan TouchInterval = TimeSpan.FromMinutes(1);

    private readonly IMongoCollection<AdminSessionDocument> sessions = AdminSessionDocument.In(database);
    private readonly IMongoCollection<AdminUser> admins = AdminUser.In(database);

    private AdminOptions Options => options.Value;

    public static void AddScheme(AuthenticationBuilder authentication, IHostEnvironment environment)
    {
        ArgumentNullException.ThrowIfNull(authentication);
        ArgumentNullException.ThrowIfNull(environment);
        var development = environment.IsDevelopment();
        authentication.AddCookie(Scheme, cookie =>
        {
            cookie.Cookie.Name = development ? DevelopmentCookieName : CookieName;
            cookie.Cookie.HttpOnly = true;
            cookie.Cookie.SameSite = SameSiteMode.Strict;
            cookie.Cookie.Path = "/";
            cookie.Cookie.SecurePolicy = development ? CookieSecurePolicy.SameAsRequest : CookieSecurePolicy.Always;
            cookie.SlidingExpiration = false;
            cookie.Events = new CookieAuthenticationEvents
            {
                OnValidatePrincipal = context => context.HttpContext.RequestServices.GetRequiredService<AdminSessions>().ValidateAsync(context),
                OnRedirectToLogin = context => AdminProblems.WriteAsync(context.Response, StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated,
                    "Sign in with a passkey first."),
                OnRedirectToAccessDenied = context => AdminProblems.WriteAsync(context.Response, StatusCodes.Status403Forbidden, ProblemCode.Request_forbidden,
                    "This request is not allowed."),
            };
        });
    }

    /// <summary>The caller of an admin endpoint that requires a session.</summary>
    public static AdminCaller Caller(HttpContext context)
    {
        ArgumentNullException.ThrowIfNull(context);
        return context.Items[CallerKey] as AdminCaller ?? throw new InvalidOperationException("The request has no admin session.");
    }

    public static AdminCaller? TryCaller(HttpContext context)
    {
        ArgumentNullException.ThrowIfNull(context);
        return context.Items[CallerKey] as AdminCaller;
    }

    public DateTime ExpiresAt(AdminSessionDocument session)
    {
        ArgumentNullException.ThrowIfNull(session);
        return session.CreatedAt + Options.SessionLifetime;
    }

    /// <summary>Starts a session for the admin and sets its cookie.</summary>
    public async Task<AdminSessionDocument> SignInAsync(HttpContext context, AdminUser admin, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(context);
        ArgumentNullException.ThrowIfNull(admin);
        var now = time.GetUtcNow().UtcDateTime;
        var session = new AdminSessionDocument
        {
            Id = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(16)),
            AdminId = admin.Id,
            CreatedAt = now,
            LastUsedAt = now,
        };
        await sessions.InsertOneAsync(session, cancellationToken: cancellationToken);

        var principal = new ClaimsPrincipal(new ClaimsIdentity([new Claim(AdminClaim, admin.Id), new Claim(SessionClaim, session.Id)], Scheme, AdminClaim, null));
        await context.SignInAsync(Scheme, principal, new AuthenticationProperties
        {
            IsPersistent = false,
            IssuedUtc = time.GetUtcNow(),
            ExpiresUtc = new DateTimeOffset(ExpiresAt(session), TimeSpan.Zero),
        });
        context.Items[CallerKey] = new AdminCaller(admin, session);
        return session;
    }

    public async Task SignOutAsync(HttpContext context, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(context);
        if (TryCaller(context) is { } caller)
        {
            await EndAsync(caller.Session.Id, cancellationToken);
        }

        await context.SignOutAsync(Scheme);
    }

    public Task EndAsync(string sessionId, CancellationToken cancellationToken) =>
        sessions.UpdateOneAsync(session => session.Id == sessionId && session.EndedAt == null,
            Builders<AdminSessionDocument>.Update.Set(session => session.EndedAt, time.GetUtcNow().UtcDateTime), cancellationToken: cancellationToken);

    public Task EndAllAsync(string adminId, CancellationToken cancellationToken) =>
        sessions.UpdateManyAsync(session => session.AdminId == adminId && session.EndedAt == null,
            Builders<AdminSessionDocument>.Update.Set(session => session.EndedAt, time.GetUtcNow().UtcDateTime), cancellationToken: cancellationToken);

    public bool IsActive(AdminSessionDocument session)
    {
        ArgumentNullException.ThrowIfNull(session);
        var now = time.GetUtcNow().UtcDateTime;
        return session.EndedAt is null && now < session.LastUsedAt + Options.SessionIdleLifetime && now < ExpiresAt(session);
    }

    private async Task ValidateAsync(CookieValidatePrincipalContext context)
    {
        var cancellationToken = context.HttpContext.RequestAborted;
        var adminId = context.Principal?.FindFirstValue(AdminClaim);
        var sessionId = context.Principal?.FindFirstValue(SessionClaim);
        var session = sessionId is null ? null : await sessions.Find(candidate => candidate.Id == sessionId).FirstOrDefaultAsync(cancellationToken);
        var admin = session is not null && session.AdminId == adminId && IsActive(session)
            ? await admins.Find(candidate => candidate.Id == session.AdminId).FirstOrDefaultAsync(cancellationToken)
            : null;
        if (admin is null)
        {
            context.RejectPrincipal();
            await context.HttpContext.SignOutAsync(Scheme);
            return;
        }

        var now = time.GetUtcNow().UtcDateTime;
        if (now - session!.LastUsedAt >= TouchInterval)
        {
            await sessions.UpdateOneAsync(candidate => candidate.Id == session.Id,
                Builders<AdminSessionDocument>.Update.Set(candidate => candidate.LastUsedAt, now), cancellationToken: cancellationToken);
        }

        context.HttpContext.Items[CallerKey] = new AdminCaller(admin, session);
    }
}
