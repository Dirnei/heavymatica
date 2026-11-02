using System.Net;
using Heavymatica.Cloud.Contract.Admin;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class SecondPasskeyTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    public void Dispose() => site?.Dispose();

    [Fact]
    public async Task An_admin_with_one_passkey_can_only_add_a_second_and_then_everything_works()
    {
        var (browser, session) = await Site.AdminAsync(passkeys: 1);

        using var blocked = await browser.GetAsync("/admin/v1/accounts?q=steve");
        using var admins = await browser.GetAsync("/admin/v1/admins");
        using var invite = await browser.PostAsync("/admin/v1/invitations", new { kind = "admin" });
        using var added = await browser.AddPasskeyAsync("Phone");
        var after = await AdminBrowser.ReadAsync<AdminSession>(added);
        using var allowed = await browser.GetAsync("/admin/v1/accounts?q=steve");

        Assert.Equal((1, true), (session.PasskeyCount, session.NeedsSecondPasskey));
        Assert.Equal(HttpStatusCode.Forbidden, blocked.StatusCode);
        Assert.Equal(ProblemCode.Second_passkey_required, (await blocked.AdminProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.Forbidden, admins.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, invite.StatusCode);
        Assert.Equal((2, false), (after.PasskeyCount, after.NeedsSecondPasskey));
        Assert.Equal(HttpStatusCode.OK, allowed.StatusCode);
    }

    [Fact]
    public async Task An_admin_with_one_passkey_still_reads_the_session_and_signs_out()
    {
        var (browser, _) = await Site.AdminAsync(passkeys: 1);

        using var session = await browser.GetAsync("/admin/v1/session");
        using var signOut = await browser.PostAsync("/admin/v1/sign-out");

        Assert.Equal(HttpStatusCode.OK, session.StatusCode);
        Assert.Equal(HttpStatusCode.NoContent, signOut.StatusCode);
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
