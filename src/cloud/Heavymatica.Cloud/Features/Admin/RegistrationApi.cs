using Heavymatica.Cloud.Contract.Admin;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>
/// Registering passkeys: from a setup code or an invitation, which creates an admin or adds a passkey to an existing
/// one, and for the signed-in admin. A code or invitation is checked when the options are made and again, then used up,
/// only once the passkey is verified; it is spent only when a passkey was really registered.
/// </summary>
internal sealed class RegistrationApi(
    IPasskeyHandler<AdminUser> passkeys,
    Ceremonies ceremonies,
    Admins admins,
    AdminSessions sessions,
    SetupCodes setupCodes,
    Invitations invitations,
    AdminAudit audit,
    TimeProvider time,
    IHttpContextAccessor http) : IAdminRegistrationController
{
    private const int MaxNameLength = 60;

    private HttpContext Context => http.HttpContext!;

    private string Ip => AdminContract.Ip(Context);

    public async Task<ActionResult<PasskeyOptions>> CreateRegistrationOptionsAsync(RegistrationRequest body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        var setupCode = string.IsNullOrWhiteSpace(body.SetupCode) ? null : body.SetupCode;
        var token = string.IsNullOrWhiteSpace(body.InvitationToken) ? null : body.InvitationToken;
        if ((setupCode is null) == (token is null))
        {
            return AdminProblems.Invalid("Send either a setup code or an invitation token.");
        }

        string? existingAdminId;
        Ceremony ceremony;
        if (setupCode is not null)
        {
            var code = SetupCodes.IsWellFormed(setupCode) ? await setupCodes.FindOpenAsync(SetupCodes.Hash(setupCode), cancellationToken) : null;
            if (code is null || (code.AdminId is not null && await admins.FindAsync(code.AdminId, cancellationToken) is null))
            {
                return await SetupCodeFailedAsync(cancellationToken);
            }

            existingAdminId = code.AdminId;
            ceremony = new Ceremony { Kind = CeremonyKinds.Registration, State = string.Empty, SetupCodeHash = code.CodeHash };
        }
        else
        {
            var invitation = await invitations.FindOpenByTokenAsync(token!, cancellationToken);
            if (invitation is null
                || (invitation.Kind == InvitationKinds.Recovery && (invitation.AdminId is null || await admins.FindAsync(invitation.AdminId, cancellationToken) is null)))
            {
                return await InvitationFailedAsync(invitation?.Id, cancellationToken);
            }

            existingAdminId = invitation.Kind == InvitationKinds.Recovery ? invitation.AdminId : null;
            ceremony = new Ceremony { Kind = CeremonyKinds.Registration, State = string.Empty, InvitationId = invitation.Id };
        }

        PasskeyUserEntity user;
        if (existingAdminId is not null)
        {
            var admin = (await admins.FindAsync(existingAdminId, cancellationToken))!;
            user = new PasskeyUserEntity { Id = admin.Id, Name = admin.DisplayName, DisplayName = admin.DisplayName };
            ceremony = ceremony with { AdminId = admin.Id };
        }
        else
        {
            if (AdminContract.Name(body.DisplayName, MaxNameLength) is not { } displayName)
            {
                return AdminProblems.Invalid("A display name of 1 to 60 characters is needed for a new admin.");
            }

            var adminId = AdminUser.NewId();
            user = new PasskeyUserEntity { Id = adminId, Name = displayName, DisplayName = displayName };
            ceremony = ceremony with { AdminId = adminId, NewAdmin = true, DisplayName = displayName };
        }

        var options = await passkeys.MakeCreationOptionsAsync(user, Context);
        ceremonies.Start(Context, ceremony with { State = options.AttestationState ?? string.Empty });
        return AdminContract.Options(options.CreationOptionsJson);
    }

    public async Task<ActionResult<AdminSession>> RegisterAsync(PasskeyCredential body, CancellationToken cancellationToken = default)
    {
        var ceremony = ceremonies.Take(Context, CeremonyKinds.Registration);
        var credential = AdminContract.CredentialJson(body);
        if (ceremony?.AdminId is null || credential is null)
        {
            return PasskeyFailed("Start the registration again.");
        }

        if (!await IsStillOpenAsync(ceremony, cancellationToken))
        {
            return await FailedCodeAsync(ceremony, cancellationToken);
        }

        var existing = ceremony.NewAdmin ? null : await admins.FindAsync(ceremony.AdminId, cancellationToken);
        if (!ceremony.NewAdmin && existing is null)
        {
            return await FailedCodeAsync(ceremony, cancellationToken);
        }

        var result = await passkeys.PerformAttestationAsync(new PasskeyAttestationContext
        {
            CredentialJson = credential,
            AttestationState = ceremony.State,
            HttpContext = Context,
        });
        if (!result.Succeeded || result.UserEntity?.Id != ceremony.AdminId)
        {
            return PasskeyFailed("The passkey could not be registered.");
        }

        if (!await ConsumeAsync(ceremony, cancellationToken))
        {
            return await FailedCodeAsync(ceremony, cancellationToken);
        }

        var passkey = AdminUserStore.ToDocument(ceremony.AdminId, result.Passkey!, time.GetUtcNow().UtcDateTime);
        passkey.Name = AdminContract.Name(body.Name, MaxNameLength);
        var admin = existing ?? new AdminUser { Id = ceremony.AdminId, DisplayName = ceremony.DisplayName!, CreatedAt = time.GetUtcNow().UtcDateTime };
        var stored = existing is null ? await admins.CreateAsync(admin, passkey, cancellationToken) : await admins.AddPasskeyAsync(passkey, cancellationToken);
        if (!stored)
        {
            await ReleaseAsync(ceremony, cancellationToken);
            return PasskeyFailed("This passkey is registered already.");
        }

        var actor = AdminActor.Of(admin, Ip);
        var via = Via(ceremony);
        if (existing is null)
        {
            await audit.RecordAsync(actor, AdminAuditKinds.AdminCreated, admin.Id, new() { ["displayName"] = admin.DisplayName, ["via"] = via }, cancellationToken);
        }

        await RecordPasskeyAddedAsync(audit, actor, admin.Id, passkey, via, cancellationToken);
        var session = await sessions.SignInAsync(Context, admin, cancellationToken);
        return await SessionApi.SessionOfAsync(admins, sessions, admin, session, cancellationToken);
    }

    public async Task<ActionResult<PasskeyOptions>> CreatePasskeyOptionsAsync(CancellationToken cancellationToken = default)
    {
        var admin = AdminSessions.Caller(Context).Admin;
        var options = await passkeys.MakeCreationOptionsAsync(
            new PasskeyUserEntity { Id = admin.Id, Name = admin.DisplayName, DisplayName = admin.DisplayName }, Context);
        ceremonies.Start(Context, new Ceremony { Kind = CeremonyKinds.AddPasskey, State = options.AttestationState ?? string.Empty, AdminId = admin.Id });
        return AdminContract.Options(options.CreationOptionsJson);
    }

    public async Task<ActionResult<AdminSession>> AddPasskeyAsync(PasskeyCredential body, CancellationToken cancellationToken = default)
    {
        var caller = AdminSessions.Caller(Context);
        var ceremony = ceremonies.Take(Context, CeremonyKinds.AddPasskey);
        var credential = AdminContract.CredentialJson(body);
        if (ceremony?.AdminId != caller.Admin.Id || credential is null)
        {
            return PasskeyFailed("Start adding the passkey again.");
        }

        var result = await passkeys.PerformAttestationAsync(new PasskeyAttestationContext
        {
            CredentialJson = credential,
            AttestationState = ceremony.State,
            HttpContext = Context,
        });
        if (!result.Succeeded || result.UserEntity?.Id != caller.Admin.Id)
        {
            return PasskeyFailed("The passkey could not be registered.");
        }

        var passkey = AdminUserStore.ToDocument(caller.Admin.Id, result.Passkey!, time.GetUtcNow().UtcDateTime);
        passkey.Name = AdminContract.Name(body.Name, MaxNameLength);
        if (!await admins.AddPasskeyAsync(passkey, cancellationToken))
        {
            return PasskeyFailed("This passkey is registered already.");
        }

        await RecordPasskeyAddedAsync(audit, AdminActor.Of(caller.Admin, Ip), caller.Admin.Id, passkey, "session", cancellationToken);
        return await SessionApi.SessionOfAsync(admins, sessions, caller.Admin, caller.Session, cancellationToken);
    }

    private static Task RecordPasskeyAddedAsync(AdminAudit audit, AdminActor actor, string adminId, AdminPasskeyDocument passkey, string via,
        CancellationToken cancellationToken)
    {
        var details = new Dictionary<string, string> { ["passkeyId"] = passkey.Id, ["via"] = via };
        if (passkey.Name is not null)
        {
            details["name"] = passkey.Name;
        }

        return audit.RecordAsync(actor, AdminAuditKinds.PasskeyAdded, adminId, details, cancellationToken);
    }

    private static string Via(Ceremony ceremony) => ceremony.SetupCodeHash is not null ? "setup_code" : "invitation";

    private async Task<bool> IsStillOpenAsync(Ceremony ceremony, CancellationToken cancellationToken) =>
        ceremony.SetupCodeHash is { } hash
            ? await setupCodes.FindOpenAsync(hash, cancellationToken) is not null
            : ceremony.InvitationId is { } id && await invitations.FindOpenAsync(id, cancellationToken) is not null;

    private Task<bool> ConsumeAsync(Ceremony ceremony, CancellationToken cancellationToken) =>
        ceremony.SetupCodeHash is { } hash ? setupCodes.ConsumeAsync(hash, cancellationToken) : invitations.ConsumeAsync(ceremony.InvitationId!, cancellationToken);

    private Task ReleaseAsync(Ceremony ceremony, CancellationToken cancellationToken) =>
        ceremony.SetupCodeHash is { } hash ? setupCodes.ReleaseAsync(hash, cancellationToken) : invitations.ReleaseAsync(ceremony.InvitationId!, cancellationToken);

    private Task<ObjectResult> FailedCodeAsync(Ceremony ceremony, CancellationToken cancellationToken) =>
        ceremony.SetupCodeHash is not null ? SetupCodeFailedAsync(cancellationToken) : InvitationFailedAsync(ceremony.InvitationId, cancellationToken);

    private async Task<ObjectResult> SetupCodeFailedAsync(CancellationToken cancellationToken)
    {
        await audit.RecordAsync(AdminActor.Anonymous(Ip), AdminAuditKinds.SetupCodeFailed, null, null, cancellationToken);
        return AdminProblems.Result(StatusCodes.Status400BadRequest, ProblemCode.Setup_code_invalid,
            "This setup code is not valid. It may have expired, been used or been replaced by a newer one.");
    }

    private async Task<ObjectResult> InvitationFailedAsync(string? invitationId, CancellationToken cancellationToken)
    {
        await audit.RecordAsync(AdminActor.Anonymous(Ip), AdminAuditKinds.InvitationFailed, invitationId, null, cancellationToken);
        return AdminProblems.Result(StatusCodes.Status400BadRequest, ProblemCode.Invitation_invalid,
            "This invitation is not valid. It may have expired, been used or been revoked.");
    }

    private static ObjectResult PasskeyFailed(string detail) => AdminProblems.Result(StatusCodes.Status400BadRequest, ProblemCode.Passkey_failed, detail);
}
