using System.Net;
using Heavymatica.Cloud.Contract.Admin;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdditionalPasskeyTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    public void Dispose() => site?.Dispose();

    [Fact]
    public async Task An_admin_with_one_passkey_can_use_every_call_and_add_more_passkeys()
    {
        var (browser, session) = await Site.AdminAsync(passkeys: 1);

        using var accounts = await browser.GetAsync("/admin/v1/accounts?q=steve");
        using var admins = await browser.GetAsync("/admin/v1/admins");
        using var invite = await browser.PostAsync("/admin/v1/invitations", new { kind = "admin" });
        using var added = await browser.AddPasskeyAsync("Phone");
        var after = await AdminBrowser.ReadAsync<AdminSession>(added);

        Assert.Equal(1, session.PasskeyCount);
        Assert.Equal(HttpStatusCode.OK, accounts.StatusCode);
        Assert.Equal(HttpStatusCode.OK, admins.StatusCode);
        Assert.True(invite.IsSuccessStatusCode, invite.StatusCode.ToString());
        Assert.Equal(2, after.PasskeyCount);
    }

    [Fact]
    public async Task Removing_the_last_passkey_is_refused_and_any_other_can_go()
    {
        var (browser, session) = await Site.AdminAsync(passkeys: 2);
        var first = browser.Authenticator.Credentials[0].IdText;
        var second = browser.Authenticator.Credentials[1].IdText;

        using var removed = await browser.DeleteAsync($"/admin/v1/admins/{session.AdminId}/passkeys/{first}");
        using var refused = await browser.DeleteAsync($"/admin/v1/admins/{session.AdminId}/passkeys/{second}");

        Assert.Equal(HttpStatusCode.NoContent, removed.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, refused.StatusCode);
        Assert.Equal(ProblemCode.Passkey_required, (await refused.AdminProblemAsync()).Code);
        Assert.Equal(1, (await browser.SessionAsync()).PasskeyCount);
    }

    [Fact]
    public async Task Adding_a_passkey_excludes_the_passkeys_the_admin_has()
    {
        var (browser, _) = await Site.AdminAsync(passkeys: 1);
        using var options = await browser.PostAsync("/admin/v1/passkeys/options");
        var json = (await System.Net.Http.Json.HttpContentJsonExtensions.ReadFromJsonAsync<System.Text.Json.JsonElement>(options.Content, SignIn.Json,
            TestContext.Current.CancellationToken)).GetProperty("options");

        var excluded = json.GetProperty("excludeCredentials").EnumerateArray().Select(entry => entry.GetProperty("id").GetString()).ToList();

        Assert.Equal([browser.Authenticator.Credentials[0].IdText], excluded);
    }
}
