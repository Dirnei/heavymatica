using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Tests.Features.Identity;
using Heavymatica.Cloud.Tests.Features.Schematics;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Web;

[Trait("Category", "Integration")]
public sealed class GitHubSignInTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task Without_credentials_web_sign_in_is_unavailable_and_the_game_still_signs_in()
    {
        var factory = Track(new CloudFactory(infrastructure));
        using var client = factory.CreateClient();

        using var response = await client.GetAsync(new Uri("/v1/web/sign-in/github?intent=register", UriKind.Relative), Cancel);
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Gamer");

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal(ProblemCode.Web_sign_in_unavailable, (await response.ProblemAsync()).Code);
        Assert.False(string.IsNullOrEmpty(session.AccessToken));
    }

    [Fact]
    public async Task Register_creates_an_account_for_the_github_user_and_signs_in()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "register");
        var me = await MeAsync(browser);

        Assert.Equal("/library/", flow.FinalLocation);
        Assert.Contains(WebCookies.Name + "=", flow.Steps[^1].Headers, StringComparison.Ordinal);
        Assert.Contains("httponly", flow.Steps[^1].Headers, StringComparison.OrdinalIgnoreCase);
        var identity = await IdentityDocument.In(factory.Database).Find(_ => true).SingleAsync(Cancel);
        Assert.Equal(("github", "583231", "octocat"), (identity.Provider, identity.Subject, identity.Name));
        Assert.Equal((identity.AccountId, "github", "octocat", (Guid?)null), (me.Id, me.Provider, me.Name, me.Uuid));
        Assert.NotEqual("583231", me.Id);
        var audit = await AuditEntryDocument.In(factory.Database).Find(_ => true).SingleAsync(Cancel);
        Assert.Equal((AuditKinds.AccountCreated, identity.AccountId, new AuditActor("github", identity.Id)), (audit.Kind, audit.AccountId, audit.Actor));
        Assert.Empty((await new Library(browser).ListAsync()).Items);
    }

    [Fact]
    public async Task Sign_in_reaches_the_registered_account()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var registering = FakeGitHub.Browser(factory);
        await FakeGitHub.SignInAsync(registering, "register");
        var registered = await MeAsync(registering);
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "login", "/schematic/?id=abc");

        Assert.Equal("/schematic/?id=abc", flow.FinalLocation);
        Assert.Equal(registered.Id, (await MeAsync(browser)).Id);
    }

    [Fact]
    public async Task Sign_in_without_an_account_creates_nothing_and_says_so()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "login");
        using var me = await browser.GetAsync(new Uri("/v1/me", UriKind.Relative), Cancel);

        Assert.Equal("/?error=no_account", flow.FinalLocation);
        Assert.Equal(HttpStatusCode.Unauthorized, me.StatusCode);
        Assert.Equal(0, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
        Assert.Equal(0, await IdentityDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task Registering_twice_signs_in_to_the_same_account_and_a_new_login_updates_the_name()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var first = FakeGitHub.Browser(factory);
        await FakeGitHub.SignInAsync(first, "register");
        var before = await MeAsync(first);
        github.Login = "octocat-renamed";
        using var second = FakeGitHub.Browser(factory);

        await FakeGitHub.SignInAsync(second, "register");
        var after = await MeAsync(second);

        Assert.Equal(before.Id, after.Id);
        Assert.Equal("octocat-renamed", after.Name);
        Assert.Equal(1, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
        Assert.Equal("octocat-renamed", (await IdentityDocument.In(factory.Database).Find(_ => true).SingleAsync(Cancel)).Name);
    }

    [Fact]
    public async Task Cancelling_on_github_says_so_and_creates_nothing()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "register", deny: true);

        Assert.Equal("/?error=cancelled", flow.FinalLocation);
        Assert.Equal(0, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_failing_github_backchannel_says_github_is_unavailable()
    {
        var github = new FakeGitHub { Failing = true };
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "register");

        Assert.Equal("/?error=github_unavailable", flow.FinalLocation);
        Assert.Equal(0, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_callback_with_a_forged_state_fails_the_sign_in()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        using var response = await browser.GetAsync(new Uri("/v1/web/sign-in/github/callback?code=fake-code&state=forged", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.Redirect, response.StatusCode);
        Assert.Equal("/?error=sign_in_failed", response.Headers.Location?.OriginalString);
    }

    [Fact]
    public async Task No_response_carries_an_access_token_or_the_github_token()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "register");
        await flow.GetAsync(browser, "/v1/me");
        await flow.GetAsync(browser, "/v1/schematics");

        foreach (var step in flow.Steps)
        {
            Assert.DoesNotContain(FakeGitHub.AccessToken, step.Headers + step.Body, StringComparison.Ordinal);
            Assert.DoesNotContain("eyJ", step.Headers + step.Body, StringComparison.Ordinal);
            Assert.DoesNotContain("refreshToken", step.Body, StringComparison.Ordinal);
        }
    }

    [Theory]
    [InlineData("//evil.example")]
    [InlineData("https://evil.example/")]
    [InlineData("/\\evil.example")]
    public async Task A_foreign_return_address_lands_on_the_library(string returnTo)
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "register", returnTo);

        Assert.Equal("/library/", flow.FinalLocation);
    }

    [Fact]
    public async Task Sign_out_revokes_the_session()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);
        await FakeGitHub.SignInAsync(browser, "register");
        var account = (await MeAsync(browser)).Id;

        using var signOut = new HttpRequestMessage(HttpMethod.Post, new Uri("/v1/web/sign-out", UriKind.Relative));
        signOut.Headers.Add("X-Heavymatica-Request", "1");
        signOut.Headers.Add("Origin", "http://localhost");
        using var signedOut = await browser.SendAsync(signOut, Cancel);
        using var me = await browser.GetAsync(new Uri("/v1/me", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.NoContent, signedOut.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, me.StatusCode);
        var session = await SessionDocument.In(factory.Database).Find(candidate => candidate.AccountId == account).SingleAsync(Cancel);
        Assert.True(session.Revoked);
    }

    [Fact]
    public async Task A_cross_site_sign_out_is_forbidden()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);
        await FakeGitHub.SignInAsync(browser, "register");

        using var forged = new HttpRequestMessage(HttpMethod.Post, new Uri("/v1/web/sign-out", UriKind.Relative));
        forged.Headers.Add("Origin", "http://localhost:9999");
        using var response = await browser.SendAsync(forged, Cancel);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await browser.GetAsync(new Uri("/v1/me", UriKind.Relative), Cancel)).StatusCode);
    }

    [Fact]
    public async Task GitHub_and_Minecraft_accounts_of_one_player_are_separate()
    {
        var github = new FakeGitHub();
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);
        await FakeGitHub.SignInAsync(browser, "register");
        var web = new Library(browser);
        web.Client.DefaultRequestHeaders.Add("X-Heavymatica-Request", "1");
        web.Client.DefaultRequestHeaders.Add("Origin", "http://localhost");
        var game = await Library.SignedInAsync(factory, "octocat");
        var fromWeb = await web.NewAsync("From the web", Library.Fixture("house.nbt"));
        var fromGame = await game.NewAsync("From the game", Library.Fixture("house.v2.schem"));

        var webList = await web.ListAsync();
        var gameList = await game.ListAsync();
        using var webAsksGame = await web.Client.GetAsync(new Uri("/v1/schematics/" + fromGame.SchematicId, UriKind.Relative), Cancel);
        using var webAsksMissing = await web.Client.GetAsync(new Uri("/v1/schematics/000000000000000000000000", UriKind.Relative), Cancel);
        using var gameAsksWeb = await game.Client.GetAsync(new Uri("/v1/schematics/" + fromWeb.SchematicId, UriKind.Relative), Cancel);

        Assert.NotEqual((await web.MeAsync()).Id, (await game.MeAsync()).Id);
        Assert.Equal([fromWeb.SchematicId], webList.Items.Select(item => item.Id));
        Assert.Equal([fromGame.SchematicId], gameList.Items.Select(item => item.Id));
        Assert.Equal(HttpStatusCode.NotFound, webAsksGame.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, gameAsksWeb.StatusCode);
        Assert.Equal(await webAsksMissing.Content.ReadAsStringAsync(Cancel), await webAsksGame.Content.ReadAsStringAsync(Cancel));
    }

    private static async Task<Contract.Account> MeAsync(HttpClient browser) =>
        (await browser.GetFromJsonAsync<Contract.Account>(new Uri("/v1/me", UriKind.Relative), SignIn.Json, Cancel))!;

    private T Track<T>(T disposable)
        where T : IDisposable
    {
        disposables.Add(disposable);
        return disposable;
    }
}
