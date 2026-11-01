using System.Net;
using System.Text;
using System.Web;
using AspNet.Security.OAuth.GitHub;
using Heavymatica.Cloud.Features.Web;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;

namespace Heavymatica.Cloud.Tests.Features.Web;

/// <summary>
/// Stands in for GitHub's token and user endpoints behind the OAuth handler's backchannel, and drives a browser's
/// sign-in: the start, the authorize redirect, the callback with state and correlation cookie, and <c>complete</c>.
/// </summary>
internal sealed class FakeGitHub : HttpMessageHandler
{
    public const string AccessToken = "gho_FakeGitHubAccessToken0123456789";

    public long UserId { get; set; } = 583231;

    public string Login { get; set; } = "octocat";

    /// <summary>When set, the token endpoint answers 500.</summary>
    public bool Failing { get; set; }

    /// <summary>A service with GitHub credentials whose OAuth handler talks to this fake.</summary>
    public CloudFactory Cloud(CloudInfrastructure infrastructure, string? databaseName = null, FakeTimeProvider? clock = null) =>
        new CloudFactory(infrastructure, databaseName ?? CloudFactory.NewDatabaseName())
        {
            Clock = clock,
            TestServices = services => services.Configure<GitHubAuthenticationOptions>(GitHubSignIn.Scheme, options => options.BackchannelHttpHandler = this),
        }.With("GitHub:ClientId", "test-client-id").With("GitHub:ClientSecret", "test-client-secret");

    public static HttpClient Browser(CloudFactory factory)
    {
        ArgumentNullException.ThrowIfNull(factory);
        return factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false, HandleCookies = true });
    }

    /// <summary>Runs a whole sign-in and returns every response on the way, the last one being the final redirect.</summary>
    public static async Task<Flow> SignInAsync(HttpClient browser, string intent, string returnTo = "/library/", bool deny = false)
    {
        ArgumentNullException.ThrowIfNull(browser);
        var flow = new Flow();
        var start = await flow.GetAsync(browser, $"/v1/web/sign-in/github?intent={intent}&returnTo={Uri.EscapeDataString(returnTo)}");
        Assert.Equal(HttpStatusCode.Redirect, start.Status);
        var authorize = new Uri(start.Location!);
        Assert.Equal("github.com", authorize.Host);
        var query = HttpUtility.ParseQueryString(authorize.Query);
        Assert.Equal("http://localhost/v1/web/sign-in/github/callback", query["redirect_uri"]);
        var state = query["state"]!;

        var callback = await flow.GetAsync(browser, deny
            ? $"/v1/web/sign-in/github/callback?error=access_denied&state={Uri.EscapeDataString(state)}"
            : $"/v1/web/sign-in/github/callback?code=fake-code&state={Uri.EscapeDataString(state)}");
        Assert.Equal(HttpStatusCode.Redirect, callback.Status);
        if (callback.Location == GitHubSignIn.CompletePath)
        {
            var complete = await flow.GetAsync(browser, GitHubSignIn.CompletePath);
            Assert.Equal(HttpStatusCode.Redirect, complete.Status);
        }

        return flow;
    }

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        if (request.RequestUri!.AbsoluteUri == GitHubAuthenticationDefaults.TokenEndpoint && request.Method == HttpMethod.Post)
        {
            if (Failing)
            {
                return new HttpResponseMessage(HttpStatusCode.InternalServerError);
            }

            var form = await request.Content!.ReadAsStringAsync(cancellationToken);
            Assert.Contains("code=fake-code", form, StringComparison.Ordinal);
            return Json($$"""{"access_token":"{{AccessToken}}","token_type":"bearer","scope":""}""");
        }

        if (request.RequestUri.AbsoluteUri == GitHubAuthenticationDefaults.UserInformationEndpoint)
        {
            Assert.Equal("Bearer " + AccessToken, request.Headers.Authorization?.ToString());
            return Json($$"""{"id":{{UserId}},"login":"{{Login}}","name":"The Octocat"}""");
        }

        return new HttpResponseMessage(HttpStatusCode.NotFound);
    }

    private static HttpResponseMessage Json(string json) =>
        new(HttpStatusCode.OK) { Content = new StringContent(json, Encoding.UTF8, "application/json") };

    internal sealed record Step(HttpStatusCode Status, string? Location, string Headers, string Body);

    internal sealed class Flow
    {
        public List<Step> Steps { get; } = [];

        public string? FinalLocation => Steps[^1].Location;

        public async Task<Step> GetAsync(HttpClient browser, string path)
        {
            using var response = await browser.GetAsync(new Uri(path, UriKind.Relative), TestContext.Current.CancellationToken);
            var step = new Step(
                response.StatusCode,
                response.Headers.Location?.OriginalString,
                response.Headers.ToString() + response.Content.Headers,
                await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken));
            Steps.Add(step);
            return step;
        }
    }
}
