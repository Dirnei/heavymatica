using System.Net;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Tests.Features.Identity;

/// <summary>Website sessions created directly, without a GitHub sign-in, and requests sent with their cookie.</summary>
internal static class WebCookies
{
    public const string Name = WebSessions.DevelopmentCookieName;

    public static async Task<(SignedInIdentity SignedIn, string SessionId, string Cookie)> SessionAsync(CloudFactory factory, string login = "octocat")
    {
        ArgumentNullException.ThrowIfNull(factory);
        var accounts = factory.Services.GetRequiredService<Accounts>();
        var (signedIn, _) = await accounts.CreateWithIdentityAsync(Providers.GitHub, Random.Shared.Next(1, int.MaxValue).ToString(System.Globalization.CultureInfo.InvariantCulture),
            login, TestContext.Current.CancellationToken);
        var sessionId = await factory.Services.GetRequiredService<Sessions>().StartWebAsync(signedIn.Account.Id, signedIn.Identity.Id,
            TestContext.Current.CancellationToken);
        return (signedIn, sessionId, CookieFor(factory, signedIn, sessionId));
    }

    public static string CookieFor(CloudFactory factory, SignedInIdentity signedIn, string sessionId)
    {
        ArgumentNullException.ThrowIfNull(factory);
        var options = factory.Services.GetRequiredService<IOptionsMonitor<CookieAuthenticationOptions>>().Get(WebSessions.Scheme);
        var time = factory.Services.GetRequiredService<TimeProvider>();
        var ticket = new AuthenticationTicket(WebSessions.Principal(signedIn, sessionId), WebSessions.Properties(time), WebSessions.Scheme);
        return options.TicketDataFormat.Protect(ticket);
    }

    public static HttpRequestMessage Request(HttpMethod method, string path, string cookie, bool marker = false, string? origin = null)
    {
        var request = new HttpRequestMessage(method, new Uri(path, UriKind.Relative));
        request.Headers.Add("Cookie", $"{Name}={cookie}");
        if (marker)
        {
            request.Headers.Add("X-Heavymatica-Request", "1");
        }

        if (origin is not null)
        {
            request.Headers.Add("Origin", origin);
        }

        return request;
    }

    /// <summary>True when the response deletes the session cookie.</summary>
    public static bool ClearsCookie(HttpResponseMessage response)
    {
        ArgumentNullException.ThrowIfNull(response);
        return response.Headers.TryGetValues("Set-Cookie", out var cookies)
            && cookies.Any(cookie => cookie.StartsWith(Name + "=;", StringComparison.Ordinal) && cookie.Contains("expires=Thu, 01 Jan 1970", StringComparison.OrdinalIgnoreCase));
    }

    public static async Task<HttpStatusCode> StatusAsync(HttpClient client, HttpRequestMessage request)
    {
        ArgumentNullException.ThrowIfNull(client);
        using (request)
        {
            using var response = await client.SendAsync(request, TestContext.Current.CancellationToken);
            return response.StatusCode;
        }
    }
}
