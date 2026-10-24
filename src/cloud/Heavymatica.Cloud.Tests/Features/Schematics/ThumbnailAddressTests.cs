using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Tests.Features.Identity;
using Microsoft.AspNetCore.Mvc.Testing;

namespace Heavymatica.Cloud.Tests.Features.Schematics;

[Trait("Category", "Integration")]
public sealed class ThumbnailAddressTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task A_thumbnail_redirects_to_a_cached_link_that_returns_the_png()
    {
        var factory = Factory();
        var library = await SignedInWithoutRedirectsAsync(factory, "Owner");
        var created = await library.NewAsync("Pictured", Library.Fixture("house.nbt"));
        var png = Library.Png(64, 64, 500);
        using (var attached = await library.ThumbnailAsync(created.SchematicId, 1, png))
        {
            Assert.Equal(HttpStatusCode.NoContent, attached.StatusCode);
        }

        using var response = await library.Client.GetAsync(Path(created.SchematicId), Cancel);
        using var anonymous = new HttpClient();
        var image = await anonymous.GetByteArrayAsync(response.Headers.Location!, Cancel);

        Assert.Equal(HttpStatusCode.Redirect, response.StatusCode);
        Assert.Equal(png, image);
        Assert.True(response.Headers.CacheControl!.Private);
        Assert.Equal(TimeSpan.FromSeconds(240), response.Headers.CacheControl.MaxAge);
    }

    [Fact]
    public async Task A_version_without_thumbnail_and_another_accounts_version_are_both_not_found()
    {
        var factory = Factory();
        var owner = await SignedInWithoutRedirectsAsync(factory, "Owner");
        var intruder = await SignedInWithoutRedirectsAsync(factory, "Intruder");
        var bare = await owner.NewAsync("Bare", Library.Fixture("house.nbt"));
        var pictured = await owner.NewAsync("Pictured", Library.Fixture("house.v2.schem"));
        using (await owner.ThumbnailAsync(pictured.SchematicId, 1, Library.Png(64, 64)))
        {
        }

        using var missing = await owner.Client.GetAsync(Path(bare.SchematicId), Cancel);
        using var foreign = await intruder.Client.GetAsync(Path(pictured.SchematicId), Cancel);
        using var unknown = await intruder.Client.GetAsync(Path("000000000000000000000000"), Cancel);

        Assert.Equal(HttpStatusCode.NotFound, missing.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, foreign.StatusCode);
        Assert.Equal(await unknown.Content.ReadAsStringAsync(Cancel), await foreign.Content.ReadAsStringAsync(Cancel));
    }

    [Fact]
    public async Task The_website_session_cookie_reaches_the_thumbnail()
    {
        var factory = Factory();
        using var client = factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });
        var (_, _, cookie) = await WebCookies.SessionAsync(factory);
        using var upload = WebCookies.Request(HttpMethod.Post, "/v1/schematics?name=castle", cookie, marker: true, origin: "http://localhost");
        upload.Content = new ByteArrayContent(Library.Fixture("house.nbt"));
        upload.Content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        using var uploaded = await client.SendAsync(upload, Cancel);
        var id = (await uploaded.Content.ReadFromJsonAsync<UploadResult>(SignIn.Json, Cancel))!.SchematicId;
        using var attach = WebCookies.Request(HttpMethod.Put, $"/v1/schematics/{id}/versions/1/thumbnail", cookie, marker: true, origin: "http://localhost");
        attach.Content = new ByteArrayContent(Library.Png(64, 64));
        attach.Content.Headers.ContentType = new MediaTypeHeaderValue("image/png");
        using (await client.SendAsync(attach, Cancel))
        {
        }

        Assert.Equal(HttpStatusCode.Redirect, await WebCookies.StatusAsync(client, WebCookies.Request(HttpMethod.Get, Path(id), cookie)));
    }

    private static string Path(string schematicId) => $"/v1/schematics/{schematicId}/versions/1/thumbnail";

    private static async Task<Library> SignedInWithoutRedirectsAsync(CloudFactory factory, string name)
    {
        var client = factory.CreateClient(new WebApplicationFactoryClientOptions { AllowAutoRedirect = false });
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), name);
        client.Authorize(session.AccessToken);
        return new Library(client);
    }

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure);
        disposables.Add(factory);
        return factory;
    }
}
