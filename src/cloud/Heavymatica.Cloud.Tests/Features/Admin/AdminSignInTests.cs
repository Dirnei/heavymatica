using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Admin;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminSignInTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public void Dispose() => site?.Dispose();

    [Fact]
    public async Task An_admin_signs_in_with_a_passkey_reads_the_session_and_signs_out()
    {
        var (registered, created) = await Site.AdminAsync("Alice");
        var browser = Site.Browser();
        using (await registered.PostAsync("/admin/v1/sign-out"))
        {
        }

        using var signIn = await SignInWithAsync(browser, registered);
        var session = await AdminBrowser.ReadAsync<AdminSession>(signIn);
        var read = await browser.SessionAsync();
        using var signOut = await browser.PostAsync("/admin/v1/sign-out");
        using var after = await browser.GetAsync("/admin/v1/session");

        Assert.Equal((created.AdminId, "Alice", 1), (session.AdminId, session.DisplayName, session.PasskeyCount));
        Assert.Equal(Site.Clock.GetUtcNow().AddHours(8), session.ExpiresAt, TimeSpan.FromSeconds(1));
        Assert.Equal(session.AdminId, read.AdminId);
        Assert.Equal(HttpStatusCode.NoContent, signOut.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, after.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await after.AdminProblemAsync()).Code);
        var stored = await AdminPasskeyDocument.In(Site.Factory.Database).Find(passkey => passkey.AdminId == created.AdminId)
            .SortBy(passkey => passkey.CreatedAt).ToListAsync(Cancel);
        Assert.Equal(1, stored[0].SignCount);
        Assert.NotNull(stored[0].LastUsedAt);
    }

    [Fact]
    public async Task A_signed_out_cookie_no_longer_works_when_sent_again()
    {
        var (browser, _) = await Site.AdminAsync();
        var cookies = browser.Cookies.GetCookieHeader(Site.Admin);

        using (await browser.PostAsync("/admin/v1/sign-out"))
        {
        }

        using var replay = new HttpRequestMessage(HttpMethod.Get, new Uri(Site.Admin, "/admin/v1/session"));
        replay.Headers.Add("Cookie", cookies);
        using var client = new HttpClient();
        using var response = await client.SendAsync(replay, Cancel);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task A_passkey_signing_for_another_relying_party_fails_with_passkey_failed()
    {
        var (registered, _) = await Site.AdminAsync();
        using (await registered.PostAsync("/admin/v1/sign-out"))
        {
        }

        using var response = await registered.SignInAsync(rpIdOverride: "admin.evil.example");
        using var session = await registered.GetAsync("/admin/v1/session");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(ProblemCode.Passkey_failed, (await response.AdminProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.Unauthorized, session.StatusCode);
    }

    [Fact]
    public async Task An_unknown_passkey_fails_with_passkey_failed()
    {
        _ = await Site.AdminAsync();
        var stranger = Site.Browser();

        using var response = await SignInWithAsync(stranger, null);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(ProblemCode.Passkey_failed, (await response.AdminProblemAsync()).Code);
    }

    [Fact]
    public async Task Signing_in_without_options_from_the_same_browser_fails()
    {
        var (registered, _) = await Site.AdminAsync();
        var other = Site.Browser();
        using var options = await registered.PostAsync("/admin/v1/sign-in/options");
        var json = (await options.Content.ReadFromJsonAsync<System.Text.Json.JsonElement>(SignIn.Json, Cancel)).GetProperty("options");

        using var response = await other.PostAsync("/admin/v1/sign-in", new { credential = registered.Authenticator.Get(json, Site.Origin) });

        Assert.Equal(ProblemCode.Passkey_failed, (await response.AdminProblemAsync()).Code);
    }

    /// <summary>Signs in in <paramref name="browser"/> with a passkey of <paramref name="owner"/>, or with a passkey no admin has.</summary>
    private async Task<HttpResponseMessage> SignInWithAsync(AdminBrowser browser, AdminBrowser? owner)
    {
        using var options = await browser.PostAsync("/admin/v1/sign-in/options");
        var json = (await options.Content.ReadFromJsonAsync<System.Text.Json.JsonElement>(SignIn.Json, Cancel)).GetProperty("options");
        if (owner is null)
        {
            using var unknown = new SoftwareAuthenticator();
            unknown.Create(System.Text.Json.JsonDocument.Parse(
                "{\"challenge\":\"AAAA\",\"rp\":{\"id\":\"localhost\"},\"user\":{\"id\":\"AAAA\"}}").RootElement, Site.Origin);
            return await browser.PostAsync("/admin/v1/sign-in", new { credential = unknown.Get(json, Site.Origin) });
        }

        return await browser.PostAsync("/admin/v1/sign-in", new { credential = owner.Authenticator.Get(json, Site.Origin) });
    }
}
