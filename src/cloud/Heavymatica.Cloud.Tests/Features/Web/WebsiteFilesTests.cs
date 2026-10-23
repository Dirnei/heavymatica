using System.Net;
using Heavymatica.Cloud.Contract;

namespace Heavymatica.Cloud.Tests.Features.Web;

[Trait("Category", "Integration")]
public sealed class WebsiteFilesTests : IDisposable
{
    private readonly string root = Directory.CreateTempSubdirectory("heavymatica-web-").FullName;
    private readonly CloudFactory factory;

    public WebsiteFilesTests(CloudInfrastructure infrastructure)
    {
        Write("index.html", "<h1>landing</h1>");
        Write("library/index.html", "<h1>library</h1>");
        Write("404.html", "<h1>lost</h1>");
        Write("_astro/app.Bx12.js", "console.log('app')");
        Write("v1/me", "a file that must never shadow the API");
        Write("health/index.html", "a file that must never shadow the health check");
        factory = new CloudFactory(infrastructure).With("Web:Root", root);
    }

    public void Dispose()
    {
        factory.Dispose();
        Directory.Delete(root, recursive: true);
    }

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Theory]
    [InlineData("/", "<h1>landing</h1>")]
    [InlineData("/library/", "<h1>library</h1>")]
    public async Task Pages_are_served_without_a_session_and_cached_briefly(string path, string content)
    {
        using var client = factory.CreateClient();

        using var response = await client.GetAsync(new Uri(path, UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("text/html", response.Content.Headers.ContentType?.MediaType);
        Assert.Equal(content, await response.Content.ReadAsStringAsync(Cancel));
        Assert.Equal(TimeSpan.FromSeconds(60), response.Headers.CacheControl?.MaxAge);
    }

    [Fact]
    public async Task Hashed_assets_are_immutable()
    {
        using var client = factory.CreateClient();

        using var response = await client.GetAsync(new Uri("/_astro/app.Bx12.js", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Contains("immutable", response.Headers.CacheControl?.ToString(), StringComparison.Ordinal);
        Assert.Equal(TimeSpan.FromDays(365), response.Headers.CacheControl?.MaxAge);
    }

    [Fact]
    public async Task Unknown_pages_answer_the_404_page()
    {
        using var client = factory.CreateClient();

        using var response = await client.GetAsync(new Uri("/no/such/page", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal("<h1>lost</h1>", await response.Content.ReadAsStringAsync(Cancel));
    }

    [Fact]
    public async Task API_paths_are_never_served_from_files()
    {
        using var client = factory.CreateClient();

        using var me = await client.GetAsync(new Uri("/v1/me", UriKind.Relative), Cancel);
        using var unknown = await client.GetAsync(new Uri("/v1/no-such-endpoint", UriKind.Relative), Cancel);
        using var health = await client.GetAsync(new Uri("/health/", UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.Unauthorized, me.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await me.ProblemAsync()).Code);
        Assert.Equal(ProblemCode.Not_found, (await unknown.ProblemAsync()).Code);
        Assert.DoesNotContain("never shadow", await health.Content.ReadAsStringAsync(Cancel), StringComparison.Ordinal);
    }

    private void Write(string path, string content)
    {
        var file = Path.Combine(root, path);
        Directory.CreateDirectory(Path.GetDirectoryName(file)!);
        File.WriteAllText(file, content);
    }
}
