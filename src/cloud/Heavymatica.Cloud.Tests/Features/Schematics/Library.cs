using System.Net.Http.Headers;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;

namespace Heavymatica.Cloud.Tests.Features.Schematics;

/// <summary>Calls the Schematics operations the way the mod does, for storage tests.</summary>
internal sealed class Library(HttpClient client)
{
    public HttpClient Client { get; } = client;

    public static byte[] Fixture(string name) => File.ReadAllBytes(Path.Combine(RepositoryFiles.Conformance, "files", name));

    public static async Task<Library> SignedInAsync(CloudFactory factory, string name = "Builder")
    {
        ArgumentNullException.ThrowIfNull(factory);
        var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), name);
        client.Authorize(session.AccessToken);
        return new Library(client);
    }

    public Task<HttpResponseMessage> UploadNewAsync(string name, byte[] file, string? folder = null) =>
        Client.PostAsync(new Uri("/v1/schematics?name=" + Uri.EscapeDataString(name) + (folder is null ? string.Empty : "&folder=" + Uri.EscapeDataString(folder)), UriKind.Relative),
            Binary(file), TestContext.Current.CancellationToken);

    public Task<HttpResponseMessage> UploadVersionAsync(string schematicId, byte[] file) =>
        Client.PostAsync(new Uri($"/v1/schematics/{schematicId}/versions", UriKind.Relative), Binary(file), TestContext.Current.CancellationToken);

    public async Task<UploadResult> NewAsync(string name, byte[] file, string? folder = null)
    {
        using var response = await UploadNewAsync(name, file, folder);
        Assert.Equal(System.Net.HttpStatusCode.Created, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<UploadResult>(SignIn.Json, TestContext.Current.CancellationToken))!;
    }

    public async Task<UploadResult> VersionAsync(string schematicId, byte[] file)
    {
        using var response = await UploadVersionAsync(schematicId, file);
        Assert.Equal(System.Net.HttpStatusCode.Created, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<UploadResult>(SignIn.Json, TestContext.Current.CancellationToken))!;
    }

    public async Task<SchematicDetails> DetailsAsync(string schematicId) =>
        (await Client.GetFromJsonAsync<SchematicDetails>(new Uri($"/v1/schematics/{schematicId}", UriKind.Relative), SignIn.Json, TestContext.Current.CancellationToken))!;

    public async Task<SchematicPage> ListAsync(string query = "") =>
        (await Client.GetFromJsonAsync<SchematicPage>(new Uri("/v1/schematics" + query, UriKind.Relative), SignIn.Json, TestContext.Current.CancellationToken))!;

    public async Task<Contract.Account> MeAsync() =>
        (await Client.GetFromJsonAsync<Contract.Account>(new Uri("/v1/me", UriKind.Relative), SignIn.Json, TestContext.Current.CancellationToken))!;

    public async Task<FolderList> FoldersAsync() =>
        (await Client.GetFromJsonAsync<FolderList>(new Uri("/v1/folders", UriKind.Relative), SignIn.Json, TestContext.Current.CancellationToken))!;

    public Task<HttpResponseMessage> MoveAsync(IEnumerable<string> schematicIds, string folder) =>
        Client.PostAsJsonAsync(new Uri("/v1/schematics/move", UriKind.Relative), new { schematicIds, folder }, TestContext.Current.CancellationToken);

    public Task<HttpResponseMessage> RenameFolderAsync(string from, string to) =>
        Client.PostAsJsonAsync(new Uri("/v1/folders/rename", UriKind.Relative), new { from, to }, TestContext.Current.CancellationToken);

    public Task<HttpResponseMessage> OrganizeAsync(string schematicId, object request) =>
        Client.PatchAsJsonAsync(new Uri($"/v1/schematics/{schematicId}", UriKind.Relative), request, TestContext.Current.CancellationToken);

    public Task<HttpResponseMessage> LinksAsync(string schematicId, int version) =>
        Client.PostAsync(new Uri($"/v1/schematics/{schematicId}/versions/{version}/links", UriKind.Relative), null, TestContext.Current.CancellationToken);

    public Task<HttpResponseMessage> ThumbnailAsync(string schematicId, int version, byte[] png)
    {
        var content = new ByteArrayContent(png);
        content.Headers.ContentType = new MediaTypeHeaderValue("image/png");
        return Client.PutAsync(new Uri($"/v1/schematics/{schematicId}/versions/{version}/thumbnail", UriKind.Relative), content, TestContext.Current.CancellationToken);
    }

    public Task<HttpResponseMessage> DeleteAsync(string path) =>
        Client.DeleteAsync(new Uri(path, UriKind.Relative), TestContext.Current.CancellationToken);

    /// <summary>A PNG header with the given size, padded to <paramref name="totalBytes"/>. Only the header is checked.</summary>
    public static byte[] Png(int width, int height, int totalBytes = 64)
    {
        var png = new byte[Math.Max(totalBytes, 33)];
        new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, (byte)'I', (byte)'H', (byte)'D', (byte)'R' }.CopyTo(png, 0);
        System.Buffers.Binary.BinaryPrimitives.WriteInt32BigEndian(png.AsSpan(16), width);
        System.Buffers.Binary.BinaryPrimitives.WriteInt32BigEndian(png.AsSpan(20), height);
        return png;
    }

    private static ByteArrayContent Binary(byte[] file)
    {
        var content = new ByteArrayContent(file);
        content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        return content;
    }
}
