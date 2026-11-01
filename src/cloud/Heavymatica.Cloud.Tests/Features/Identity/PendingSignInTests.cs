using System.Net;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Tests.Features.Links;

namespace Heavymatica.Cloud.Tests.Features.Identity;

[Trait("Category", "Integration")]
public sealed class PendingSignInTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly CloudFactory factory = new CloudFactory(infrastructure).On();

    public void Dispose() => factory.Dispose();

    [Theory]
    [InlineData("/v1/schematics")]
    [InlineData("/v1/me")]
    public async Task A_pending_token_reaches_no_data(string path)
    {
        using var client = factory.CreateClient();
        var pending = await Linking.PendingAsync(client, Guid.NewGuid(), "Outsider");

        using var response = await Linking.SendAsync(client, Caller.Bearer(pending.PendingToken), HttpMethod.Get, path);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await response.ProblemAsync()).Code);
    }

    [Theory]
    [InlineData("/v1/schematics")]
    [InlineData("/v1/me")]
    public async Task A_pending_cookie_reaches_no_data(string path)
    {
        using var client = factory.CreateClient();
        var cookie = Linking.PendingCookie(factory, "9001", "visitor");

        using var response = await Linking.SendAsync(client, Caller.WebPending(cookie), HttpMethod.Get, path);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task A_pending_token_may_finish_the_sign_in()
    {
        using var client = factory.CreateClient();
        var pending = await Linking.PendingAsync(client, Guid.NewGuid(), "Finisher");

        using var response = await Linking.CompleteSignInAsync(client, Caller.Bearer(pending.PendingToken), createAccount: true);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task A_pending_cookie_may_read_the_pending_sign_in()
    {
        using var client = factory.CreateClient();
        var cookie = Linking.PendingCookie(factory, "9002", "visitor");

        using var response = await Linking.SendAsync(client, Caller.WebPending(cookie), HttpMethod.Get, "/v1/web/pending");
        var pending = await response.ReadAsync<WebPending>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(("github", "visitor", true), (pending.Identity.Provider, pending.Identity.Name, pending.JoinAvailable));
    }

    [Fact]
    public async Task An_account_token_cannot_finish_a_pending_sign_in()
    {
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Member");

        using var response = await Linking.CompleteSignInAsync(client, Caller.Bearer(session.AccessToken), createAccount: true);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task A_pending_cookie_cannot_finish_a_game_sign_in_and_a_pending_token_cannot_read_the_website_one()
    {
        using var client = factory.CreateClient();
        var cookie = Linking.PendingCookie(factory, "9003", "visitor");
        var pending = await Linking.PendingAsync(client, Guid.NewGuid(), "Gamer");

        using var cookieOnGame = await Linking.CompleteSignInAsync(client, Caller.WebPending(cookie), createAccount: true);
        using var tokenOnWeb = await Linking.SendAsync(client, Caller.Bearer(pending.PendingToken), HttpMethod.Get, "/v1/web/pending");

        Assert.Equal(HttpStatusCode.Unauthorized, cookieOnGame.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, tokenOnWeb.StatusCode);
    }

    [Fact]
    public async Task A_pending_cookie_request_from_another_site_is_forbidden()
    {
        using var client = factory.CreateClient();
        var cookie = Linking.PendingCookie(factory, "9004", "visitor");
        using var forged = new HttpRequestMessage(HttpMethod.Post, new Uri("/v1/web/pending/account", UriKind.Relative));
        forged.Headers.Add("Cookie", $"{Cloud.Features.Identity.PendingSignIns.DevelopmentCookieName}={cookie}");
        forged.Headers.Add("Origin", "http://localhost:9999");

        using var response = await client.SendAsync(forged, TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal(ProblemCode.Request_forbidden, (await response.ProblemAsync()).Code);
    }
}
