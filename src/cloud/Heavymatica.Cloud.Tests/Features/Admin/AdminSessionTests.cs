using System.Net;
using System.Net.Http.Headers;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Admin;
using Heavymatica.Cloud.Tests.Features.Identity;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminSessionTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public void Dispose() => site?.Dispose();

    [Fact]
    public async Task The_session_ends_after_30_minutes_without_requests()
    {
        var (browser, _) = await Site.AdminAsync();

        Site.Clock.Advance(TimeSpan.FromMinutes(29));
        using var active = await browser.GetAsync("/admin/v1/session");
        Site.Clock.Advance(TimeSpan.FromMinutes(31));
        using var idle = await browser.GetAsync("/admin/v1/session");

        Assert.Equal(HttpStatusCode.OK, active.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, idle.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await idle.AdminProblemAsync()).Code);
    }

    [Fact]
    public async Task The_session_ends_8_hours_after_sign_in_however_active()
    {
        var (browser, _) = await Site.AdminAsync();

        for (var elapsed = TimeSpan.Zero; elapsed < TimeSpan.FromHours(8) - TimeSpan.FromMinutes(25); elapsed += TimeSpan.FromMinutes(25))
        {
            Site.Clock.Advance(TimeSpan.FromMinutes(25));
            using var active = await browser.GetAsync("/admin/v1/session");
            Assert.Equal(HttpStatusCode.OK, active.StatusCode);
        }

        Site.Clock.Advance(TimeSpan.FromMinutes(25));
        using var expired = await browser.GetAsync("/admin/v1/session");

        Assert.Equal(HttpStatusCode.Unauthorized, expired.StatusCode);
    }

    [Fact]
    public async Task A_player_website_session_is_unauthenticated_on_the_admin_api()
    {
        _ = await Site.AdminAsync();
        var (_, _, cookie) = await WebCookies.SessionAsync(Site.Factory);
        using var client = new HttpClient { BaseAddress = Site.Admin };
        using var request = new HttpRequestMessage(HttpMethod.Get, new Uri("/admin/v1/session", UriKind.Relative));
        request.Headers.Add("Cookie", $"{WebCookies.Name}={cookie}");

        using var response = await client.SendAsync(request, Cancel);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await response.AdminProblemAsync()).Code);
    }

    [Fact]
    public async Task A_player_access_token_is_unauthenticated_on_the_admin_api()
    {
        _ = await Site.AdminAsync();
        var session = await SignIn.AsAsync(Site.PublicClient(), Guid.NewGuid(), "Steve");
        using var client = new HttpClient { BaseAddress = Site.Admin };
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", session.AccessToken);

        using var response = await client.GetAsync(new Uri("/admin/v1/session", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task An_admin_cookie_is_unauthenticated_on_the_player_api()
    {
        var (browser, _) = await Site.AdminAsync();
        var cookie = browser.Cookies.GetCookies(Site.Admin)[AdminSessions.DevelopmentCookieName]!;
        using var client = Site.PublicClient();
        using var request = new HttpRequestMessage(HttpMethod.Get, new Uri("/v1/me", UriKind.Relative));
        request.Headers.Add("Cookie", $"{cookie.Name}={cookie.Value}");

        using var response = await client.SendAsync(request, Cancel);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task The_admin_cookie_is_http_only_strict_and_bound_to_the_host()
    {
        var browser = Site.Browser();
        using var response = await browser.RegisterAsync(new { setupCode = await Site.SetupCodeAsync(), displayName = "Alice" });

        var header = Assert.Single(response.Headers.GetValues("Set-Cookie"), value => value.StartsWith(AdminSessions.DevelopmentCookieName + "=", StringComparison.Ordinal));
        Assert.Contains("httponly", header, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("samesite=strict", header, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("path=/", header, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("domain=", header, StringComparison.OrdinalIgnoreCase);
    }

    [Theory]
    [InlineData(false, null)]
    [InlineData(true, "http://evil.example")]
    public async Task A_data_changing_request_from_another_site_or_without_the_marker_is_forbidden(bool marker, string? origin)
    {
        var (browser, _) = await Site.AdminAsync();

        using var request = browser.Request(HttpMethod.Post, "/admin/v1/sign-out", new { }, marker, origin);
        using var response = await browser.Client.SendAsync(request, Cancel);
        using var still = await browser.GetAsync("/admin/v1/session");

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal(ProblemCode.Request_forbidden, (await response.AdminProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.OK, still.StatusCode);
    }
}
