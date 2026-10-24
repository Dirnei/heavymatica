using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Features.Schematics;
using Heavymatica.Cloud.Tests.Features.Schematics;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Identity;

[Trait("Category", "Integration")]
public sealed class WebSessionTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task The_session_cookie_reaches_me_and_the_library()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var (signedIn, _, cookie) = await WebCookies.SessionAsync(factory, "octocat");

        using var meResponse = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/me", cookie), Cancel);
        using var list = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/schematics", cookie), Cancel);

        Assert.Equal(HttpStatusCode.OK, meResponse.StatusCode);
        var me = (await meResponse.Content.ReadFromJsonAsync<Contract.Account>(SignIn.Json, Cancel))!;
        Assert.Equal((signedIn.Account.Id, "github", "octocat", (Guid?)null), (me.Id, me.Provider, me.Name, me.Uuid));
        Assert.Equal(HttpStatusCode.OK, list.StatusCode);
        Assert.Empty((await list.Content.ReadFromJsonAsync<SchematicPage>(SignIn.Json, Cancel))!.Items);
    }

    [Fact]
    public async Task A_revoked_session_is_unauthenticated_and_its_cookie_is_cleared()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var (_, sessionId, cookie) = await WebCookies.SessionAsync(factory);
        await factory.Services.GetRequiredService<Sessions>().EndAsync(sessionId, Cancel);

        using var response = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/me", cookie), Cancel);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await response.ProblemAsync()).Code);
        Assert.True(WebCookies.ClearsCookie(response));
    }

    [Fact]
    public async Task A_session_unused_for_31_days_is_unauthenticated_and_its_cookie_is_cleared()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var (_, sessionId, cookie) = await WebCookies.SessionAsync(factory);
        await SessionDocument.In(factory.Database).UpdateOneAsync(session => session.Id == sessionId,
            Builders<SessionDocument>.Update.Set(session => session.LastUsedAt, DateTime.UtcNow.AddDays(-31)), cancellationToken: Cancel);

        using var response = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/me", cookie), Cancel);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.True(WebCookies.ClearsCookie(response));
    }

    [Fact]
    public async Task A_session_whose_identity_left_the_account_is_unauthenticated()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var (signedIn, _, cookie) = await WebCookies.SessionAsync(factory);
        await Cloud.Features.Account.IdentityDocument.In(factory.Database).DeleteOneAsync(identity => identity.Id == signedIn.Identity.Id, Cancel);

        using var response = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/me", cookie), Cancel);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.True(WebCookies.ClearsCookie(response));
    }

    [Fact]
    public async Task Use_after_an_hour_records_the_use_and_renews_the_cookie()
    {
        var clock = new FakeTimeProvider(DateTimeOffset.UtcNow);
        var factory = Factory(clock);
        using var client = factory.CreateClient();
        var (_, sessionId, cookie) = await WebCookies.SessionAsync(factory);

        using var soon = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/me", cookie), Cancel);
        clock.Advance(TimeSpan.FromHours(2));
        using var later = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/me", cookie), Cancel);

        Assert.False(soon.Headers.Contains("Set-Cookie"));
        Assert.Contains(later.Headers.GetValues("Set-Cookie"), value => value.StartsWith(WebCookies.Name + "=", StringComparison.Ordinal)
            && !value.StartsWith(WebCookies.Name + "=;", StringComparison.Ordinal));
        var session = await SessionDocument.In(factory.Database).Find(candidate => candidate.Id == sessionId).SingleAsync(Cancel);
        Assert.Equal(clock.GetUtcNow().UtcDateTime, session.LastUsedAt, TimeSpan.FromSeconds(1));
    }

    [Fact]
    public async Task The_bearer_path_is_unchanged()
    {
        var factory = Factory();
        var library = await Library.SignedInAsync(factory);

        var me = await library.MeAsync();

        Assert.Equal("minecraft", me.Provider);
    }

    [Fact]
    public async Task A_cookie_delete_needs_the_marker_and_the_own_origin()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var (signedIn, _, cookie) = await WebCookies.SessionAsync(factory);
        var schematicId = await UploadAsync(client, cookie);
        var path = "/v1/schematics/" + schematicId;

        using var noMarker = await client.SendAsync(WebCookies.Request(HttpMethod.Delete, path, cookie, origin: "http://localhost"), Cancel);
        using var foreign = await client.SendAsync(WebCookies.Request(HttpMethod.Delete, path, cookie, marker: true, origin: "http://evil.example"), Cancel);
        using var noOrigin = await client.SendAsync(WebCookies.Request(HttpMethod.Delete, path, cookie, marker: true), Cancel);

        Assert.Equal(HttpStatusCode.Forbidden, noMarker.StatusCode);
        Assert.Equal(ProblemCode.Request_forbidden, (await noMarker.ProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.Forbidden, foreign.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, noOrigin.StatusCode);
        Assert.Equal(1, await SchematicDocument.In(factory.Database).CountDocumentsAsync(schematic => schematic.Owner == signedIn.Account.Id,
            cancellationToken: Cancel));

        using var sameSite = WebCookies.Request(HttpMethod.Delete, path, cookie, marker: true);
        sameSite.Headers.Add("Sec-Fetch-Site", "same-origin");
        Assert.Equal(HttpStatusCode.NoContent, await WebCookies.StatusAsync(client, sameSite));
        Assert.Equal(0, await SchematicDocument.In(factory.Database).CountDocumentsAsync(schematic => schematic.Owner == signedIn.Account.Id,
            cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_cookie_delete_with_marker_and_origin_succeeds_and_a_cookie_get_needs_neither()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var (_, _, cookie) = await WebCookies.SessionAsync(factory);
        var schematicId = await UploadAsync(client, cookie);

        var get = await WebCookies.StatusAsync(client, WebCookies.Request(HttpMethod.Get, "/v1/schematics/" + schematicId, cookie));
        var delete = await WebCookies.StatusAsync(client,
            WebCookies.Request(HttpMethod.Delete, "/v1/schematics/" + schematicId, cookie, marker: true, origin: "http://localhost"));

        Assert.Equal(HttpStatusCode.OK, get);
        Assert.Equal(HttpStatusCode.NoContent, delete);
    }

    [Fact]
    public async Task Folder_operations_accept_the_cookie_and_its_moves_and_renames_need_the_marker()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var (_, _, cookie) = await WebCookies.SessionAsync(factory);
        var schematicId = await UploadAsync(client, cookie);

        var moveWithout = await WebCookies.StatusAsync(client, Json(HttpMethod.Post, "/v1/schematics/move", cookie, new { schematicIds = new[] { schematicId }, folder = "village" }, marker: false));
        var moveWith = await WebCookies.StatusAsync(client, Json(HttpMethod.Post, "/v1/schematics/move", cookie, new { schematicIds = new[] { schematicId }, folder = "village" }, marker: true));
        var renameWithout = await WebCookies.StatusAsync(client, Json(HttpMethod.Post, "/v1/folders/rename", cookie, new { from = "village", to = "town" }, marker: false));
        var renameWith = await WebCookies.StatusAsync(client, Json(HttpMethod.Post, "/v1/folders/rename", cookie, new { from = "village", to = "town" }, marker: true));
        using var folders = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/folders", cookie), Cancel);

        Assert.Equal(HttpStatusCode.Forbidden, moveWithout);
        Assert.Equal(HttpStatusCode.OK, moveWith);
        Assert.Equal(HttpStatusCode.Forbidden, renameWithout);
        Assert.Equal(HttpStatusCode.OK, renameWith);
        Assert.Equal(HttpStatusCode.OK, folders.StatusCode);
        Assert.Equal(["town"], (await folders.Content.ReadFromJsonAsync<FolderList>(SignIn.Json, Cancel))!.Folders.Select(folder => folder.Path));
    }

    private static HttpRequestMessage Json(HttpMethod method, string path, string cookie, object body, bool marker)
    {
        var request = WebCookies.Request(method, path, cookie, marker, origin: "http://localhost");
        request.Content = JsonContent.Create(body);
        return request;
    }

    [Fact]
    public async Task A_bearer_delete_needs_neither_marker_nor_origin()
    {
        var factory = Factory();
        var library = await Library.SignedInAsync(factory);
        var uploaded = await library.NewAsync("Mine", Library.Fixture("house.nbt"));
        library.Client.DefaultRequestHeaders.Add("Origin", "http://evil.example");

        using var response = await library.DeleteAsync("/v1/schematics/" + uploaded.SchematicId);

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
    }

    [Fact]
    public async Task A_cookie_from_one_instance_is_accepted_by_another_on_the_same_database()
    {
        var first = Factory();
        var (signedIn, _, cookie) = await WebCookies.SessionAsync(first);
        var second = new CloudFactory(infrastructure, first.DatabaseName);
        disposables.Add(second);
        using var client = second.CreateClient();

        using var response = await client.SendAsync(WebCookies.Request(HttpMethod.Get, "/v1/me", cookie), Cancel);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(signedIn.Account.Id, (await response.Content.ReadFromJsonAsync<Contract.Account>(SignIn.Json, Cancel))!.Id);
    }

    private static async Task<string> UploadAsync(HttpClient client, string cookie)
    {
        using var request = WebCookies.Request(HttpMethod.Post, "/v1/schematics?name=castle", cookie, marker: true, origin: "http://localhost");
        request.Content = new ByteArrayContent(Library.Fixture("house.nbt"));
        request.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
        using var response = await client.SendAsync(request, Cancel);
        Assert.Equal(HttpStatusCode.Created, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<UploadResult>(SignIn.Json, Cancel))!.SchematicId;
    }

    private CloudFactory Factory(FakeTimeProvider? clock = null)
    {
        var factory = new CloudFactory(infrastructure) { Clock = clock };
        disposables.Add(factory);
        return factory;
    }
}
