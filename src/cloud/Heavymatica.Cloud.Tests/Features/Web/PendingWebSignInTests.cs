using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Tests.Features.Identity;
using Heavymatica.Cloud.Tests.Features.Links;
using Microsoft.Extensions.DependencyInjection;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Web;

[Trait("Category", "Integration")]
public sealed class PendingWebSignInTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly FakeGitHub github = new();
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task Sign_in_without_an_account_while_linking_is_on_holds_a_pending_sign_in_and_creates_nothing()
    {
        var factory = Factory();
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "login");
        using var me = await browser.GetAsync(new Uri("/v1/me", UriKind.Relative), Cancel);
        using var schematics = await browser.GetAsync(new Uri("/v1/schematics", UriKind.Relative), Cancel);
        var pending = await browser.GetFromJsonAsync<WebPending>(new Uri("/v1/web/pending", UriKind.Relative), SignIn.Json, Cancel);

        Assert.Equal("/?choice=1&returnTo=%2Flibrary%2F", flow.FinalLocation);
        Assert.Contains(PendingSignIns.DevelopmentCookieName + "=", flow.Steps[^1].Headers, StringComparison.Ordinal);
        Assert.Contains("httponly", flow.Steps[^1].Headers, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain(WebCookies.Name + "=", flow.Steps[^1].Headers, StringComparison.Ordinal);
        Assert.Equal(HttpStatusCode.Unauthorized, me.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, schematics.StatusCode);
        Assert.Equal(("github", "octocat", true), (pending!.Identity.Provider, pending.Identity.Name, pending.JoinAvailable));
        Assert.Equal(0, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_pending_sign_in_ends_the_website_session_the_browser_held()
    {
        var factory = Factory();
        using var browser = FakeGitHub.Browser(factory);
        await FakeGitHub.SignInAsync(browser, "register");
        var held = await SessionDocument.In(factory.Database).Find(session => session.Kind == SessionKinds.Web).SingleAsync(Cancel);
        github.UserId = 777;
        github.Login = "newcomer";

        var flow = await FakeGitHub.SignInAsync(browser, "login");
        using var me = await browser.GetAsync(new Uri("/v1/me", UriKind.Relative), Cancel);
        var pending = await browser.GetFromJsonAsync<WebPending>(new Uri("/v1/web/pending", UriKind.Relative), SignIn.Json, Cancel);

        Assert.Equal("/?choice=1&returnTo=%2Flibrary%2F", flow.FinalLocation);
        Assert.Equal(HttpStatusCode.Unauthorized, me.StatusCode);
        Assert.Equal("newcomer", pending!.Identity.Name);
        var ended = await SessionDocument.In(factory.Database).Find(session => session.Id == held.Id).SingleAsync(Cancel);
        Assert.False(factory.Services.GetRequiredService<Sessions>().IsActive(ended));
    }

    [Fact]
    public async Task The_choice_keeps_where_the_player_wanted_to_go()
    {
        var factory = Factory();
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "login", "/link/?code=K7QM-3XRP");

        Assert.Equal("/?choice=1&returnTo=" + Uri.EscapeDataString("/link/?code=K7QM-3XRP"), flow.FinalLocation);
    }

    [Fact]
    public async Task Without_linking_sign_in_without_an_account_still_says_so()
    {
        var factory = Track(github.Cloud(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "login");
        using var pending = await browser.GetAsync(new Uri("/v1/web/pending", UriKind.Relative), Cancel);

        Assert.Equal("/?error=no_account", flow.FinalLocation);
        Assert.Equal(HttpStatusCode.Unauthorized, pending.StatusCode);
    }

    [Fact]
    public async Task Register_still_creates_the_account_at_once_while_linking_is_on()
    {
        var factory = Factory();
        using var browser = FakeGitHub.Browser(factory);

        var flow = await FakeGitHub.SignInAsync(browser, "register");

        Assert.Equal("/library/", flow.FinalLocation);
        Assert.Equal(1, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task Creating_the_account_swaps_the_pending_sign_in_for_a_website_session_of_a_new_account()
    {
        var factory = Factory();
        using var browser = FakeGitHub.Browser(factory);
        await FakeGitHub.SignInAsync(browser, "login");

        using var created = await PostAsync(browser, "/v1/web/pending/account");
        var account = await created.ReadAsync<Contract.Account>();
        var me = await browser.GetFromJsonAsync<Contract.Account>(new Uri("/v1/me", UriKind.Relative), SignIn.Json, Cancel);
        using var pendingAfter = await browser.GetAsync(new Uri("/v1/web/pending", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.OK, created.StatusCode);
        Assert.Equal((account.Id, "github", "octocat"), (me!.Id, me.Provider, me.Name));
        Assert.Equal(HttpStatusCode.Unauthorized, pendingAfter.StatusCode);
        var identity = await IdentityDocument.In(factory.Database).Find(_ => true).SingleAsync(Cancel);
        Assert.Equal((account.Id, "583231"), (identity.AccountId, identity.Subject));
        Assert.Equal(1, await SessionDocument.In(factory.Database).CountDocumentsAsync(session => session.AccountId == account.Id && session.Kind == SessionKinds.Web,
            cancellationToken: Cancel));
    }

    [Fact]
    public async Task Creating_the_account_twice_with_one_pending_sign_in_makes_one_account()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var pending = Caller.WebPending(Linking.PendingCookie(factory, "583231", "octocat"));

        using var first = await Linking.SendAsync(client, pending, HttpMethod.Post, "/v1/web/pending/account");
        using var replay = await Linking.SendAsync(client, pending, HttpMethod.Post, "/v1/web/pending/account");

        Assert.Equal(HttpStatusCode.OK, replay.StatusCode);
        Assert.Equal((await first.ReadAsync<Contract.Account>()).Id, (await replay.ReadAsync<Contract.Account>()).Id);
        Assert.Equal(1, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task Completing_before_a_join_asks_for_the_choice()
    {
        var factory = Factory();
        using var browser = FakeGitHub.Browser(factory);
        await FakeGitHub.SignInAsync(browser, "login");

        using var response = await PostAsync(browser, "/v1/web/pending/session");

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(ProblemCode.Account_choice_required, (await response.ProblemAsync()).Code);
        Assert.Equal(0, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_pending_sign_in_ends_after_10_minutes()
    {
        var clock = new Microsoft.Extensions.Time.Testing.FakeTimeProvider(DateTimeOffset.UtcNow);
        var factory = Track(new CloudFactory(infrastructure) { Clock = clock }.On());
        using var client = factory.CreateClient();
        var pending = Caller.WebPending(Linking.PendingCookie(factory, "77", "late"));

        clock.Advance(TimeSpan.FromMinutes(11));
        using var response = await Linking.SendAsync(client, pending, HttpMethod.Get, "/v1/web/pending");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    internal static async Task<HttpResponseMessage> PostAsync(HttpClient browser, string path)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri(path, UriKind.Relative));
        request.Headers.Add("X-Heavymatica-Request", "1");
        request.Headers.Add("Origin", "http://localhost");
        return await browser.SendAsync(request, Cancel);
    }

    private CloudFactory Factory() => Track(github.Cloud(infrastructure).On());

    private T Track<T>(T disposable)
        where T : IDisposable
    {
        disposables.Add(disposable);
        return disposable;
    }
}
