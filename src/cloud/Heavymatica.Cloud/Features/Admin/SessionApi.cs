using Heavymatica.Cloud.Contract.Admin;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>Passkey sign-in, the current session and sign-out.</summary>
internal sealed class SessionApi(
    IPasskeyHandler<AdminUser> passkeys,
    Ceremonies ceremonies,
    Admins admins,
    AdminSessions sessions,
    AdminAudit audit,
    IHttpContextAccessor http) : IAdminSessionController
{
    private HttpContext Context => http.HttpContext!;

    public async Task<ActionResult<AdminSession>> GetSessionAsync(CancellationToken cancellationToken = default)
    {
        var caller = AdminSessions.Caller(Context);
        return await SessionOfAsync(admins, sessions, caller.Admin, caller.Session, cancellationToken);
    }

    public async Task<ActionResult<PasskeyOptions>> CreateSignInOptionsAsync(CancellationToken cancellationToken = default)
    {
        var options = await passkeys.MakeRequestOptionsAsync(null, Context);
        ceremonies.Start(Context, new Ceremony { Kind = CeremonyKinds.SignIn, State = options.AssertionState ?? string.Empty });
        return AdminContract.Options(options.RequestOptionsJson);
    }

    public async Task<ActionResult<AdminSession>> SignInAsync(PasskeyCredential body, CancellationToken cancellationToken = default)
    {
        var ip = AdminContract.Ip(Context);
        var ceremony = ceremonies.Take(Context, CeremonyKinds.SignIn);
        var credential = AdminContract.CredentialJson(body);
        if (ceremony is null || credential is null)
        {
            await audit.RecordAsync(AdminActor.Anonymous(ip), AdminAuditKinds.SignInFailed, null,
                new() { ["reason"] = ceremony is null ? "no sign-in started" : "no credential" }, cancellationToken);
            return Failed("Start the sign-in again.");
        }

        var result = await passkeys.PerformAssertionAsync(new PasskeyAssertionContext
        {
            CredentialJson = credential,
            AssertionState = ceremony.State,
            HttpContext = Context,
        });
        if (!result.Succeeded)
        {
            await audit.RecordAsync(AdminActor.Anonymous(ip), AdminAuditKinds.SignInFailed, null,
                new() { ["reason"] = result.Failure?.Message ?? "assertion failed" }, cancellationToken);
            return Failed("The passkey was not accepted.");
        }

        var admin = result.User!;
        var passkeyId = AdminUserStore.CredentialKey(result.Passkey!.CredentialId);
        await admins.RecordUseAsync(passkeyId, result.Passkey.SignCount, result.Passkey.IsBackedUp, cancellationToken);
        var session = await sessions.SignInAsync(Context, admin, cancellationToken);
        await audit.RecordAsync(AdminActor.Of(admin, ip), AdminAuditKinds.SignedIn, admin.Id, new() { ["passkeyId"] = passkeyId }, cancellationToken);
        return await SessionOfAsync(admins, sessions, admin, session, cancellationToken);
    }

    public async Task<IActionResult> SignOutAsync(CancellationToken cancellationToken = default)
    {
        await sessions.SignOutAsync(Context, cancellationToken);
        return new NoContentResult();
    }

    public static async Task<AdminSession> SessionOfAsync(Admins admins, AdminSessions sessions, AdminUser admin, AdminSessionDocument session,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(admins);
        ArgumentNullException.ThrowIfNull(sessions);
        ArgumentNullException.ThrowIfNull(admin);
        return AdminContract.Session(admin, await admins.PasskeyCountAsync(admin.Id, cancellationToken), sessions.ExpiresAt(session));
    }

    private static ObjectResult Failed(string detail) => AdminProblems.Result(StatusCodes.Status400BadRequest, ProblemCode.Passkey_failed, detail);
}
