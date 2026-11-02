using System.Net;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Admin;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminManagementTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public void Dispose() => site?.Dispose();

    private async Task<(AdminBrowser Browser, AdminSession Session)> InvitedAsync(AdminBrowser inviter, string displayName)
    {
        using var created = await inviter.PostAsync("/admin/v1/invitations", new { kind = "admin" });
        var invitation = await AdminBrowser.ReadAsync<Invitation>(created);
        var browser = Site.Browser();
        using var registered = await browser.RegisterAsync(new { invitationToken = invitation.Url!.Fragment.TrimStart('#'), displayName });
        Assert.Equal(HttpStatusCode.OK, registered.StatusCode);
        return (browser, await browser.SessionAsync());
    }

    [Fact]
    public async Task Admins_are_listed_oldest_first_with_their_passkeys_and_the_caller_marked()
    {
        var (alice, aliceSession) = await Site.AdminAsync("Alice");
        Site.Clock.Advance(TimeSpan.FromSeconds(1));
        var (_, bobSession) = await InvitedAsync(alice, "Bob");

        using var response = await alice.GetAsync("/admin/v1/admins");
        var list = await AdminBrowser.ReadAsync<AdminList>(response);

        Assert.Equal([(aliceSession.AdminId, "Alice", 1, true), (bobSession.AdminId, "Bob", 1, false)],
            list.Admins.Select(admin => (admin.Id, admin.DisplayName, admin.Passkeys.Count, admin.IsYou)));
        Assert.Equal(alice.Authenticator.Credentials.Select(credential => credential.IdText).Order(), list.Admins[0].Passkeys.Select(passkey => passkey.Id).Order());
    }

    [Fact]
    public async Task Removing_an_admin_ends_their_sessions_and_their_passkeys_no_longer_sign_in()
    {
        var (alice, _) = await Site.AdminAsync("Alice");
        var (bob, bobSession) = await InvitedAsync(alice, "Bob");

        using var removed = await alice.DeleteAsync("/admin/v1/admins/" + bobSession.AdminId);
        using var bobsNextRequest = await bob.GetAsync("/admin/v1/session");
        using var bobSignsIn = await bob.SignInAsync();

        Assert.Equal(HttpStatusCode.NoContent, removed.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, bobsNextRequest.StatusCode);
        Assert.Equal(ProblemCode.Passkey_failed, (await bobSignsIn.AdminProblemAsync()).Code);
        Assert.Equal(0, await AdminPasskeyDocument.In(Site.Factory.Database).CountDocumentsAsync(passkey => passkey.AdminId == bobSession.AdminId,
            cancellationToken: Cancel));
    }

    [Fact]
    public async Task An_admin_cannot_remove_themselves()
    {
        var (alice, aliceSession) = await Site.AdminAsync("Alice");
        _ = await InvitedAsync(alice, "Bob");

        using var response = await alice.DeleteAsync("/admin/v1/admins/" + aliceSession.AdminId);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(ProblemCode.Cannot_remove_self, (await response.AdminProblemAsync()).Code);
    }

    [Fact]
    public async Task The_last_admin_cannot_be_removed()
    {
        var (alice, aliceSession) = await Site.AdminAsync("Alice");

        using var response = await alice.DeleteAsync("/admin/v1/admins/" + aliceSession.AdminId);
        var (outcome, _) = await ((Admins)Site.Factory.Services.GetService(typeof(Admins))!).RemoveAsync(AdminUser.NewId(), aliceSession.AdminId, Cancel);

        Assert.Equal(ProblemCode.Last_admin, (await response.AdminProblemAsync()).Code);
        Assert.Equal(RemoveAdminOutcome.LastAdmin, outcome);
        Assert.Equal(1, await AdminUser.In(Site.Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task Removing_an_unknown_admin_is_not_found()
    {
        var (alice, _) = await Site.AdminAsync("Alice");

        using var response = await alice.DeleteAsync("/admin/v1/admins/" + AdminUser.NewId());

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task An_admins_last_passkey_is_not_removed()
    {
        var (alice, session) = await Site.AdminAsync("Alice");
        var passkey = alice.Authenticator.Credentials[0].IdText;

        using var response = await alice.DeleteAsync($"/admin/v1/admins/{session.AdminId}/passkeys/{passkey}");

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(ProblemCode.Passkey_required, (await response.AdminProblemAsync()).Code);
        Assert.Equal(1, (await alice.SessionAsync()).PasskeyCount);
    }

    [Fact]
    public async Task A_second_passkey_can_be_removed_by_any_admin()
    {
        var (alice, _) = await Site.AdminAsync("Alice");
        var (bob, bobSession) = await InvitedAsync(alice, "Bob");
        using var second = await bob.AddPasskeyAsync();
        var passkey = bob.Authenticator.Credentials[0];

        using var response = await alice.DeleteAsync($"/admin/v1/admins/{bobSession.AdminId}/passkeys/{passkey.IdText}");
        using var unknown = await alice.DeleteAsync($"/admin/v1/admins/{bobSession.AdminId}/passkeys/{passkey.IdText}");
        using (await bob.PostAsync("/admin/v1/sign-out"))
        {
        }

        using var signIn = await bob.SignInAsync(passkey);

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, unknown.StatusCode);
        Assert.Equal(1, await AdminPasskeyDocument.In(Site.Factory.Database).CountDocumentsAsync(candidate => candidate.AdminId == bobSession.AdminId,
            cancellationToken: Cancel));
        Assert.Equal(ProblemCode.Passkey_failed, (await signIn.AdminProblemAsync()).Code);
    }
}
