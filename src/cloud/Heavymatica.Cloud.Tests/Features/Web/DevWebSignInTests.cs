using System.Net;
using System.Net.Http.Json;
using System.Security.Cryptography;
using Heavymatica.Cloud.Features.Web;

namespace Heavymatica.Cloud.Tests.Features.Web;

[Trait("Category", "Integration")]
public sealed class DevWebSignInTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task Signs_the_browser_in_to_the_account_of_a_minecraft_identity()
    {
        var factory = Track(new CloudFactory(infrastructure));
        using var game = factory.CreateClient();
        var profile = Guid.NewGuid();
        await SignIn.AsAsync(game, profile, "HeavyDocs");
        using var browser = FakeGitHub.Browser(factory);

        using var response = await browser.GetAsync(new Uri($"{DevWebSignIn.Path}?minecraft={profile}&returnTo=/schematic/", UriKind.Relative), Cancel);
        var me = (await browser.GetFromJsonAsync<Contract.Account>(new Uri("/v1/me", UriKind.Relative), SignIn.Json, Cancel))!;

        Assert.Equal(HttpStatusCode.Redirect, response.StatusCode);
        Assert.Equal("/schematic/", response.Headers.Location?.OriginalString);
        Assert.Equal(("minecraft", "HeavyDocs", (Guid?)profile), (me.Provider, me.Name, me.Uuid));
    }

    [Fact]
    public async Task An_unknown_identity_starts_no_session()
    {
        var factory = Track(new CloudFactory(infrastructure));
        using var browser = FakeGitHub.Browser(factory);

        using var response = await browser.GetAsync(new Uri($"{DevWebSignIn.Path}?minecraft={Guid.NewGuid()}", UriKind.Relative), Cancel);
        using var me = await browser.GetAsync(new Uri("/v1/me", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, me.StatusCode);
    }

    [Fact]
    public async Task Does_not_exist_in_production()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var factory = Track(new CloudFactory(infrastructure) { Environment = "Production" }.With("Identity:SigningKey", key.ExportECPrivateKeyPem()));
        using var browser = FakeGitHub.Browser(factory);

        using var response = await browser.GetAsync(new Uri($"{DevWebSignIn.Path}?minecraft={Guid.NewGuid()}", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    private T Track<T>(T disposable)
        where T : IDisposable
    {
        disposables.Add(disposable);
        return disposable;
    }
}
