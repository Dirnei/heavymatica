using Heavymatica.Cloud.Contract.Admin;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>The admins, their passkeys and invitations.</summary>
internal sealed class AdminsApi(Admins admins, Invitations invitations, AdminAudit audit, IHttpContextAccessor http) : IAdminAdminsController
{
    private HttpContext Context => http.HttpContext!;

    private AdminActor Actor => AdminActor.Of(AdminSessions.Caller(Context).Admin, AdminContract.Ip(Context));

    public async Task<ActionResult<AdminList>> ListAdminsAsync(CancellationToken cancellationToken = default)
    {
        var caller = AdminSessions.Caller(Context).Admin;
        var all = await admins.ListAsync(cancellationToken);
        var passkeys = (await admins.PasskeysAsync(all.Select(admin => admin.Id), cancellationToken)).ToLookup(passkey => passkey.AdminId);
        return new AdminList
        {
            Admins = all.Select(admin => new Contract.Admin.Admin
            {
                Id = admin.Id,
                DisplayName = admin.DisplayName,
                CreatedAt = AdminContract.Utc(admin.CreatedAt),
                Passkeys = passkeys[admin.Id].Select(AdminContract.Passkey).ToList(),
                IsYou = admin.Id == caller.Id,
            }).ToList(),
        };
    }

    public async Task<IActionResult> RemoveAdminAsync(string adminId, CancellationToken cancellationToken = default)
    {
        var (outcome, removed) = await admins.RemoveAsync(AdminSessions.Caller(Context).Admin.Id, adminId, cancellationToken);
        switch (outcome)
        {
            case RemoveAdminOutcome.NotFound:
                return AdminProblems.NotFound("There is no such admin.");
            case RemoveAdminOutcome.LastAdmin:
                return AdminProblems.Result(StatusCodes.Status409Conflict, ProblemCode.Last_admin, "This is the last admin. It cannot be removed.");
            case RemoveAdminOutcome.Self:
                return AdminProblems.Result(StatusCodes.Status409Conflict, ProblemCode.Cannot_remove_self, "Admins cannot remove themselves.");
        }

        await audit.RecordAsync(Actor, AdminAuditKinds.AdminRemoved, adminId, new() { ["displayName"] = removed!.DisplayName }, cancellationToken);
        return new NoContentResult();
    }

    public async Task<IActionResult> RemovePasskeyAsync(string adminId, string passkeyId, CancellationToken cancellationToken = default)
    {
        switch (await admins.RemovePasskeyAsync(adminId, passkeyId, cancellationToken))
        {
            case RemovePasskeyOutcome.NotFound:
                return AdminProblems.NotFound("The admin has no such passkey.");
            case RemovePasskeyOutcome.PasskeyRequired:
                return AdminProblems.Result(StatusCodes.Status409Conflict, ProblemCode.Passkey_required,
                    $"Every admin keeps at least {Admins.RequiredPasskeys} passkeys. Add another one before removing this one.");
        }

        await audit.RecordAsync(Actor, AdminAuditKinds.PasskeyRemoved, adminId, new() { ["passkeyId"] = passkeyId }, cancellationToken);
        return new NoContentResult();
    }

    public async Task<ActionResult<InvitationList>> ListInvitationsAsync(CancellationToken cancellationToken = default)
    {
        var open = await invitations.ListOpenAsync(cancellationToken);
        var names = (await admins.ListAsync(cancellationToken)).ToDictionary(admin => admin.Id, admin => admin.DisplayName);
        return new InvitationList { Invitations = open.Select(invitation => ToContract(invitation, names)).ToList() };
    }

    public async Task<ActionResult<Invitation>> CreateInvitationAsync(InvitationRequest body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        var caller = AdminSessions.Caller(Context).Admin;
        string? adminId = null;
        if (body.Kind == InvitationKind.Recovery)
        {
            if (string.IsNullOrWhiteSpace(body.AdminId))
            {
                return AdminProblems.Invalid("A recovery invitation names the admin it is for.");
            }

            if (await admins.FindAsync(body.AdminId, cancellationToken) is null)
            {
                return AdminProblems.NotFound("There is no such admin.");
            }

            adminId = body.AdminId;
        }

        var kind = body.Kind == InvitationKind.Recovery ? InvitationKinds.Recovery : InvitationKinds.Admin;
        var (invitation, token) = await invitations.CreateAsync(kind, adminId, caller.Id, cancellationToken);
        var details = new Dictionary<string, string> { ["kind"] = kind };
        if (adminId is not null)
        {
            details["adminId"] = adminId;
        }

        await audit.RecordAsync(Actor, AdminAuditKinds.InvitationCreated, invitation.Id, details, cancellationToken);
        var names = (await admins.ListAsync(cancellationToken)).ToDictionary(admin => admin.Id, admin => admin.DisplayName);
        var contract = ToContract(invitation, names);
        contract.Url = invitations.LinkFor(token);
        return new ObjectResult(contract) { StatusCode = StatusCodes.Status201Created };
    }

    public async Task<IActionResult> RevokeInvitationAsync(string invitationId, CancellationToken cancellationToken = default)
    {
        if (!await invitations.RevokeAsync(invitationId, cancellationToken))
        {
            return AdminProblems.NotFound("There is no such open invitation.");
        }

        await audit.RecordAsync(Actor, AdminAuditKinds.InvitationRevoked, invitationId, null, cancellationToken);
        return new NoContentResult();
    }

    private static Invitation ToContract(InvitationDocument invitation, Dictionary<string, string> names) => new()
    {
        Id = invitation.Id,
        Kind = invitation.Kind == InvitationKinds.Recovery ? InvitationKind.Recovery : InvitationKind.Admin,
        AdminId = invitation.AdminId,
        AdminName = invitation.AdminId is { } adminId ? names.GetValueOrDefault(adminId) : null,
        CreatedBy = names.GetValueOrDefault(invitation.CreatedBy, invitation.CreatedBy),
        CreatedAt = AdminContract.Utc(invitation.CreatedAt),
        ExpiresAt = AdminContract.Utc(invitation.ExpiresAt),
    };
}
