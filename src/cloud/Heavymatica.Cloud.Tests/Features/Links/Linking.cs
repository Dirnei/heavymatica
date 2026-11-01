using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Tests.Features.Identity;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Tests.Features.Links;

/// <summary>
/// Who sends a request: an access or pending token, or a website cookie (session or pending). Cookie requests carry the
/// website's request marker and origin, as the website sends them.
/// </summary>
internal sealed record Caller(string? Token = null, string? CookieName = null, string? Cookie = null, string? Address = null)
{
    public static Caller Bearer(string token) => new(Token: token);

    public static Caller WebSession(string cookie) => new(CookieName: WebCookies.Name, Cookie: cookie);

    public static Caller WebPending(string cookie) => new(CookieName: PendingSignIns.DevelopmentCookieName, Cookie: cookie);

    public Caller From(string address) => this with { Address = address };

    public HttpRequestMessage Request(HttpMethod method, string path, object? body = null)
    {
        var request = new HttpRequestMessage(method, new Uri(path, UriKind.Relative));
        if (Token is not null)
        {
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", Token);
        }

        if (Cookie is not null)
        {
            request.Headers.Add("Cookie", $"{CookieName}={Cookie}");
            request.Headers.Add("X-Heavymatica-Request", "1");
            request.Headers.Add("Origin", "http://localhost");
        }

        if (Address is not null)
        {
            request.Headers.Add(CloudFactory.ClientAddressHeader, Address);
        }

        if (body is not null)
        {
            request.Content = JsonContent.Create(body, options: SignIn.Json);
        }

        return request;
    }
}

/// <summary>Pending sign-ins and the Links operations, called the way the mod and the website call them.</summary>
internal static class Linking
{
    public const string LinkBaseUrl = "http://localhost:4321";

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    /// <summary>A service with linking available.</summary>
    public static CloudFactory On(this CloudFactory factory)
    {
        ArgumentNullException.ThrowIfNull(factory);
        return factory.With("Accounts:LinkBaseUrl", LinkBaseUrl);
    }

    /// <summary>The handshake through the sign-in call: challenge, join, sign-in.</summary>
    public static async Task<HttpResponseMessage> SignInCallAsync(HttpClient client, Guid profile, string name)
    {
        var challenge = await SignIn.ChallengeAsync(client);
        await SignIn.JoinAsync(client, profile, challenge.ServerId);
        return await client.PostAsJsonAsync(new Uri("/v1/auth/sign-in", UriKind.Relative), new { challengeId = challenge.ChallengeId, name }, Cancel);
    }

    public static async Task<Contract.SignInResult> SignInAsync(HttpClient client, Guid profile, string name)
    {
        using var response = await SignInCallAsync(client, profile, name);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<Contract.SignInResult>(SignIn.Json, Cancel))!;
    }

    public static async Task<PendingSignIn> PendingAsync(HttpClient client, Guid profile, string name)
    {
        var result = await SignInAsync(client, profile, name);
        Assert.Null(result.Session);
        return result.Pending!;
    }

    public static Task<HttpResponseMessage> CompleteSignInAsync(HttpClient client, Caller caller, bool createAccount) =>
        SendAsync(client, caller, HttpMethod.Post, "/v1/auth/pending/session", new { createAccount });

    /// <summary>A website pending cookie made directly, as a GitHub sign-in without an account leaves it.</summary>
    public static string PendingCookie(CloudFactory factory, string subject, string login)
    {
        ArgumentNullException.ThrowIfNull(factory);
        var options = factory.Services.GetRequiredService<IOptionsMonitor<CookieAuthenticationOptions>>().Get(PendingSignIns.WebScheme);
        var properties = PendingSignIns.WebProperties(factory.Services.GetRequiredService<TimeProvider>(),
            factory.Services.GetRequiredService<IOptions<IdentityOptions>>().Value);
        var ticket = new AuthenticationTicket(PendingSignIns.WebPrincipal("github", subject, login), properties, PendingSignIns.WebScheme);
        return options.TicketDataFormat.Protect(ticket);
    }

    public static async Task<HttpResponseMessage> SendAsync(HttpClient client, Caller caller, HttpMethod method, string path, object? body = null)
    {
        ArgumentNullException.ThrowIfNull(client);
        ArgumentNullException.ThrowIfNull(caller);
        using var request = caller.Request(method, path, body);
        return await client.SendAsync(request, Cancel);
    }

    public static Task<HttpResponseMessage> CreateAsync(HttpClient client, Caller caller) => SendAsync(client, caller, HttpMethod.Post, "/v1/link-codes");

    public static async Task<LinkCode> IssueAsync(HttpClient client, Caller caller)
    {
        using var response = await CreateAsync(client, caller);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<LinkCode>(SignIn.Json, Cancel))!;
    }

    public static Task<HttpResponseMessage> RedeemCallAsync(HttpClient client, Caller caller, string code) =>
        SendAsync(client, caller, HttpMethod.Post, "/v1/link-codes/redeem", new { code });

    public static async Task<LinkCode> RedeemAsync(HttpClient client, Caller caller, string code)
    {
        using var response = await RedeemCallAsync(client, caller, code);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<LinkCode>(SignIn.Json, Cancel))!;
    }

    public static Task<HttpResponseMessage> GetCallAsync(HttpClient client, Caller caller, string codeId) =>
        SendAsync(client, caller, HttpMethod.Get, "/v1/link-codes/" + codeId);

    public static async Task<LinkCode> GetAsync(HttpClient client, Caller caller, string codeId)
    {
        using var response = await GetCallAsync(client, caller, codeId);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<LinkCode>(SignIn.Json, Cancel))!;
    }

    public static Task<HttpResponseMessage> ConfirmCallAsync(HttpClient client, Caller caller, string codeId) =>
        SendAsync(client, caller, HttpMethod.Post, $"/v1/link-codes/{codeId}/confirm");

    public static async Task<LinkCode> ConfirmAsync(HttpClient client, Caller caller, string codeId)
    {
        using var response = await ConfirmCallAsync(client, caller, codeId);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<LinkCode>(SignIn.Json, Cancel))!;
    }

    public static Task<HttpResponseMessage> CancelCallAsync(HttpClient client, Caller caller, string codeId) =>
        SendAsync(client, caller, HttpMethod.Delete, "/v1/link-codes/" + codeId);

    public static async Task<Contract.Account> MeAsync(HttpClient client, Caller caller)
    {
        using var response = await SendAsync(client, caller, HttpMethod.Get, "/v1/me");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<Contract.Account>(SignIn.Json, Cancel))!;
    }

    public static async Task<T> ReadAsync<T>(this HttpResponseMessage response)
    {
        ArgumentNullException.ThrowIfNull(response);
        return (await response.Content.ReadFromJsonAsync<T>(SignIn.Json, Cancel))!;
    }
}
