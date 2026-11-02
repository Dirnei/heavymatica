using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Nodes;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Admin;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;

namespace Heavymatica.Cloud.Tests.Features.Admin;

/// <summary>A service with both listeners on Kestrel and browsers that use its admin site.</summary>
internal sealed class AdminSite : IDisposable
{
    private readonly List<IDisposable> disposables = [];
    private int nextAddress = 10;

    private AdminSite(CloudFactory factory, FakeTimeProvider clock)
    {
        Factory = factory;
        Clock = clock;
    }

    public CloudFactory Factory { get; }

    public FakeTimeProvider Clock { get; }

    public Uri Admin => Factory.AdminAddress!;

    public Uri Public => Factory.PublicAddress!;

    public string Origin => Admin.GetLeftPart(UriPartial.Authority);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public static AdminSite Start(CloudInfrastructure infrastructure, Func<CloudFactory, CloudFactory>? configure = null)
    {
        var clock = new FakeTimeProvider(DateTimeOffset.UtcNow);
        var factory = new CloudFactory(infrastructure) { Clock = clock }.WithAdmin();
        factory = configure?.Invoke(factory) ?? factory;
        factory.StartServer();
        return new AdminSite(factory, clock);
    }

    /// <summary>A browser profile of its own: cookies, a passkey authenticator and a client address.</summary>
    public AdminBrowser Browser(string? address = null)
    {
        var browser = new AdminBrowser(this, address ?? $"198.51.100.{nextAddress++}");
        disposables.Add(browser);
        return browser;
    }

    public HttpClient PublicClient()
    {
        var client = new HttpClient { BaseAddress = Public };
        disposables.Add(client);
        return client;
    }

    public async Task<string> SetupCodeAsync(string? adminId = null) =>
        (await Factory.Services.GetRequiredService<SetupCodes>().IssueAsync(adminId, Cancel))!.Code;

    /// <summary>A new admin from a setup code, with the given number of passkeys, signed in.</summary>
    public async Task<(AdminBrowser Browser, AdminSession Session)> AdminAsync(string displayName = "Alice", int passkeys = 1)
    {
        var browser = Browser();
        using var registered = await browser.RegisterAsync(new { setupCode = await SetupCodeAsync(), displayName });
        Assert.Equal(HttpStatusCode.OK, registered.StatusCode);
        for (var i = 1; i < passkeys; i++)
        {
            using var added = await browser.AddPasskeyAsync();
            Assert.Equal(HttpStatusCode.OK, added.StatusCode);
        }

        return (browser, await browser.SessionAsync());
    }

    public void Dispose()
    {
        disposables.ForEach(disposable => disposable.Dispose());
        Factory.Dispose();
    }
}

/// <summary>Calls the admin API the way the admin site does: same origin, request marker, cookies kept.</summary>
internal sealed class AdminBrowser : IDisposable
{
    private readonly AdminSite site;

    public AdminBrowser(AdminSite site, string address)
    {
        this.site = site;
        Address = address;
        Cookies = new CookieContainer();
        Client = new HttpClient(new HttpClientHandler { CookieContainer = Cookies, AllowAutoRedirect = false }) { BaseAddress = site.Admin };
        Client.DefaultRequestHeaders.Add(CloudFactory.ClientAddressHeader, address);
    }

    public string Address { get; }

    public CookieContainer Cookies { get; }

    public HttpClient Client { get; }

    public SoftwareAuthenticator Authenticator { get; } = new();

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public HttpRequestMessage Request(HttpMethod method, string path, object? body = null, bool marker = true, string? origin = null)
    {
        var request = new HttpRequestMessage(method, new Uri(path, UriKind.Relative));
        if (method != HttpMethod.Get)
        {
            if (marker)
            {
                request.Headers.Add("X-Heavymatica-Request", "1");
            }

            request.Headers.Add("Origin", origin ?? site.Origin);
        }

        if (body is not null)
        {
            request.Content = JsonContent.Create(body, options: SignIn.Json);
        }

        return request;
    }

    public Task<HttpResponseMessage> SendAsync(HttpMethod method, string path, object? body = null) => Client.SendAsync(Request(method, path, body), Cancel);

    public Task<HttpResponseMessage> GetAsync(string path) => SendAsync(HttpMethod.Get, path);

    public Task<HttpResponseMessage> PostAsync(string path, object? body = null) => SendAsync(HttpMethod.Post, path, body ?? new { });

    public Task<HttpResponseMessage> DeleteAsync(string path) => SendAsync(HttpMethod.Delete, path);

    public static async Task<T> ReadAsync<T>(HttpResponseMessage response)
    {
        ArgumentNullException.ThrowIfNull(response);
        Assert.True(response.IsSuccessStatusCode, $"{(int)response.StatusCode}: {await response.Content.ReadAsStringAsync(Cancel)}");
        return (await response.Content.ReadFromJsonAsync<T>(SignIn.Json, Cancel))!;
    }

    public async Task<AdminSession> SessionAsync()
    {
        using var response = await GetAsync("/admin/v1/session");
        return await ReadAsync<AdminSession>(response);
    }

    /// <summary>Registration from a setup code or invitation: options, the authenticator, then the attestation. Answers the first failure.</summary>
    public Task<HttpResponseMessage> RegisterAsync(object request, string? passkeyName = null) =>
        CreateAsync("/admin/v1/registration/options", request, "/admin/v1/registration", passkeyName);

    public Task<HttpResponseMessage> AddPasskeyAsync(string? passkeyName = null) =>
        CreateAsync("/admin/v1/passkeys/options", new { }, "/admin/v1/passkeys", passkeyName);

    public async Task<HttpResponseMessage> SignInAsync(SoftwareAuthenticator.StoredCredential? credential = null, string? rpIdOverride = null)
    {
        var options = await PostAsync("/admin/v1/sign-in/options");
        if (!options.IsSuccessStatusCode)
        {
            return options;
        }

        var json = await OptionsAsync(options);
        return await PostAsync("/admin/v1/sign-in", new { credential = Authenticator.Get(json, site.Origin, credential, rpIdOverride) });
    }

    public void Dispose()
    {
        Client.Dispose();
        Authenticator.Dispose();
    }

    private async Task<HttpResponseMessage> CreateAsync(string optionsPath, object request, string finishPath, string? passkeyName)
    {
        var options = await PostAsync(optionsPath, request);
        if (!options.IsSuccessStatusCode)
        {
            return options;
        }

        var json = await OptionsAsync(options);
        JsonNode credential = Authenticator.Create(json, site.Origin);
        return await PostAsync(finishPath, new { credential, name = passkeyName });
    }

    private static async Task<JsonElement> OptionsAsync(HttpResponseMessage response)
    {
        using (response)
        {
            var body = await response.Content.ReadFromJsonAsync<JsonElement>(SignIn.Json, Cancel);
            return body.GetProperty("options").Clone();
        }
    }
}

internal static class AdminResponses
{
    public static async Task<Problem> AdminProblemAsync(this HttpResponseMessage response)
    {
        ArgumentNullException.ThrowIfNull(response);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        return (await response.Content.ReadFromJsonAsync<Problem>(SignIn.Json, TestContext.Current.CancellationToken))!;
    }
}
