using System.Net;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Admin;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class InvitationTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public void Dispose() => site?.Dispose();

    private static string TokenOf(Invitation invitation) => invitation.Url!.Fragment.TrimStart('#');

    private static async Task<Invitation> InviteAsync(AdminBrowser admin, object request)
    {
        using var response = await admin.PostAsync("/admin/v1/invitations", request);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        return await AdminBrowser.ReadAsync<Invitation>(response);
    }

    [Fact]
    public async Task An_invitation_link_carries_its_token_in_the_fragment_and_only_its_hash_is_stored()
    {
        var (alice, session) = await Site.AdminAsync("Alice");

        var invitation = await InviteAsync(alice, new { kind = "admin" });

        Assert.Equal("https://localhost/invite/", invitation.Url!.GetLeftPart(UriPartial.Path));
        Assert.Equal((InvitationKind.Admin, "Alice"), (invitation.Kind, invitation.CreatedBy));
        Assert.Equal(Site.Clock.GetUtcNow().AddHours(24), invitation.ExpiresAt, TimeSpan.FromSeconds(1));
        var stored = await InvitationDocument.In(Site.Factory.Database).Find(candidate => candidate.Id == invitation.Id).SingleAsync(Cancel);
        Assert.Equal((Invitations.Hash(TokenOf(invitation)), session.AdminId), (stored.TokenHash, stored.CreatedBy));
    }

    [Fact]
    public async Task An_invitation_creates_a_new_admin_once()
    {
        var (alice, _) = await Site.AdminAsync("Alice");
        var invitation = await InviteAsync(alice, new { kind = "admin" });

        using var used = await Site.Browser().RegisterAsync(new { invitationToken = TokenOf(invitation), displayName = "Bob" });
        using var reused = await Site.Browser().RegisterAsync(new { invitationToken = TokenOf(invitation), displayName = "Mallory" });
        using var list = await alice.GetAsync("/admin/v1/invitations");

        var bob = await AdminBrowser.ReadAsync<AdminSession>(used);
        Assert.Equal(("Bob", 1), (bob.DisplayName, bob.PasskeyCount));
        Assert.Equal(ProblemCode.Invitation_invalid, (await reused.AdminProblemAsync()).Code);
        Assert.Equal(["Alice", "Bob"], (await AdminUser.In(Site.Factory.Database).Find(_ => true).SortBy(admin => admin.CreatedAt).ToListAsync(Cancel))
            .Select(admin => admin.DisplayName));
        Assert.Empty((await AdminBrowser.ReadAsync<InvitationList>(list)).Invitations);
    }

    [Fact]
    public async Task An_invitation_expires_after_24_hours()
    {
        var (alice, _) = await Site.AdminAsync("Alice");
        var invitation = await InviteAsync(alice, new { kind = "admin" });

        Site.Clock.Advance(TimeSpan.FromHours(24) + TimeSpan.FromSeconds(1));
        using var response = await Site.Browser().RegisterAsync(new { invitationToken = TokenOf(invitation), displayName = "Bob" });

        Assert.Equal(ProblemCode.Invitation_invalid, (await response.AdminProblemAsync()).Code);
        Assert.Equal(1, await AdminUser.In(Site.Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_revoked_invitation_is_rejected_and_no_admin_is_created()
    {
        var (alice, _) = await Site.AdminAsync("Alice");
        var invitation = await InviteAsync(alice, new { kind = "admin" });
        using var listed = await alice.GetAsync("/admin/v1/invitations");
        var open = await AdminBrowser.ReadAsync<InvitationList>(listed);

        using var revoked = await alice.DeleteAsync("/admin/v1/invitations/" + invitation.Id);
        using var again = await alice.DeleteAsync("/admin/v1/invitations/" + invitation.Id);
        using var response = await Site.Browser().RegisterAsync(new { invitationToken = TokenOf(invitation), displayName = "Bob" });

        Assert.Equal([(invitation.Id, (Uri?)null)], open.Invitations.Select(item => (item.Id, item.Url)));
        Assert.Equal(HttpStatusCode.NoContent, revoked.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, again.StatusCode);
        Assert.Equal(ProblemCode.Invitation_invalid, (await response.AdminProblemAsync()).Code);
        Assert.Equal(1, await AdminUser.In(Site.Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_recovery_invitation_adds_a_passkey_to_the_named_admin_who_can_sign_in_with_it()
    {
        var (alice, _) = await Site.AdminAsync("Alice");
        var (_, bob) = await Site.AdminAsync("Bob");
        var invitation = await InviteAsync(alice, new { kind = "recovery", adminId = bob.AdminId });
        var lostDevice = Site.Browser();

        using var registered = await lostDevice.RegisterAsync(new { invitationToken = TokenOf(invitation) });
        var session = await AdminBrowser.ReadAsync<AdminSession>(registered);
        using (await lostDevice.PostAsync("/admin/v1/sign-out"))
        {
        }

        using var signIn = await lostDevice.SignInAsync();

        Assert.Equal((InvitationKind.Recovery, bob.AdminId, "Bob"), (invitation.Kind, invitation.AdminId, invitation.AdminName));
        Assert.Equal((bob.AdminId, 3), (session.AdminId, session.PasskeyCount));
        Assert.Equal(bob.AdminId, (await AdminBrowser.ReadAsync<AdminSession>(signIn)).AdminId);
        Assert.Equal(2, await AdminUser.In(Site.Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_recovery_invitation_names_an_existing_admin()
    {
        var (alice, _) = await Site.AdminAsync("Alice");

        using var missing = await alice.PostAsync("/admin/v1/invitations", new { kind = "recovery" });
        using var unknown = await alice.PostAsync("/admin/v1/invitations", new { kind = "recovery", adminId = AdminUser.NewId() });
        using var badKind = await alice.PostAsync("/admin/v1/invitations", new { kind = "owner" });

        Assert.Equal(ProblemCode.Invalid_request, (await missing.AdminProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.NotFound, unknown.StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, badKind.StatusCode);
    }
}
