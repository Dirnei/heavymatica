using System.Net;
using Heavymatica.Cloud.Features.Admin;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminListenerTests : IDisposable
{
    private readonly string root = Directory.CreateTempSubdirectory("heavymatica-web-").FullName;
    private readonly CloudInfrastructure infrastructure;
    private readonly AdminSite site;

    public AdminListenerTests(CloudInfrastructure infrastructure)
    {
        this.infrastructure = infrastructure;
        File.WriteAllText(Path.Combine(root, "index.html"), "<h1>landing</h1>");
        site = AdminSite.Start(infrastructure, factory => factory.With("Web:Root", root));
    }

    public void Dispose()
    {
        site.Dispose();
        Directory.Delete(root, recursive: true);
    }

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Theory]
    [InlineData("/admin/v1/session")]
    [InlineData("/admin/v1/admins")]
    public async Task An_admin_path_on_the_public_listener_is_not_found_like_any_unknown_path(string path)
    {
        using var client = site.PublicClient();

        using var admin = await client.GetAsync(new Uri(path, UriKind.Relative), Cancel);
        using var unknown = await client.GetAsync(new Uri("/v1/no-such-thing", UriKind.Relative), Cancel);
        using var signIn = await client.PostAsync(new Uri("/admin/v1/sign-in/options", UriKind.Relative), null, Cancel);

        Assert.Equal(HttpStatusCode.NotFound, admin.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, signIn.StatusCode);
        Assert.Equal(await unknown.Content.ReadAsStringAsync(Cancel), await admin.Content.ReadAsStringAsync(Cancel));
    }

    [Fact]
    public async Task An_admin_path_on_the_public_listener_is_not_found_even_with_the_admin_host_and_port_in_the_host_header()
    {
        using var client = site.PublicClient();
        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri("/admin/v1/sign-in/options", UriKind.Relative));
        request.Headers.Host = site.Admin.Authority;

        using var response = await client.SendAsync(request, Cancel);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Theory]
    [InlineData("/v1/schematics")]
    [InlineData("/v1/me")]
    [InlineData("/")]
    [InlineData("/index.html")]
    [InlineData("/health")]
    [InlineData("/alive")]
    [InlineData("/openapi/v1.yaml")]
    [InlineData("/dev/mojang/session/minecraft/hasJoined?username=x&serverId=y")]
    public async Task The_player_site_and_api_are_not_found_on_the_admin_listener(string path)
    {
        using var client = new HttpClient { BaseAddress = site.Admin };
        using var publicClient = site.PublicClient();

        using var response = await client.GetAsync(new Uri(path, UriKind.Relative), Cancel);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        if (path == "/")
        {
            using var landing = await publicClient.GetAsync(new Uri(path, UriKind.Relative), Cancel);
            Assert.Equal("<h1>landing</h1>", await landing.Content.ReadAsStringAsync(Cancel));
        }
    }

    [Fact]
    public async Task A_wrong_host_name_on_the_admin_listener_is_not_found()
    {
        using var client = new HttpClient { BaseAddress = site.Admin };
        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri("/admin/v1/sign-in/options", UriKind.Relative));
        request.Headers.Host = "admin.evil.example:" + site.Admin.Port;
        using var right = new HttpRequestMessage(HttpMethod.Post, new Uri("/admin/v1/sign-in/options", UriKind.Relative));

        using var response = await client.SendAsync(request, Cancel);
        using var answered = await client.SendAsync(right, Cancel);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(HttpStatusCode.OK, answered.StatusCode);
    }

    [Fact]
    public async Task The_admin_host_name_matches_on_any_port_so_a_dev_proxy_keeping_its_host_header_works()
    {
        using var client = new HttpClient { BaseAddress = site.Admin };
        using var request = new HttpRequestMessage(HttpMethod.Post, new Uri("/admin/v1/sign-in/options", UriKind.Relative));
        request.Headers.Host = "localhost:4322";

        using var response = await client.SendAsync(request, Cancel);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task Without_an_admin_listener_nothing_admin_is_mapped()
    {
        using var factory = new CloudFactory(infrastructure);
        using var client = factory.CreateClient();

        using var response = await client.GetAsync(new Uri("/admin/v1/session", UriKind.Relative), Cancel);
        var endpoints = factory.Services.GetRequiredService<EndpointDataSource>().Endpoints;

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.DoesNotContain(endpoints, endpoint => endpoint.Metadata.GetMetadata<AdminEndpoint>() is not null);
        Assert.DoesNotContain(endpoints, endpoint => (endpoint as RouteEndpoint)?.RoutePattern.RawText?.StartsWith("admin", StringComparison.Ordinal) == true);
    }

    [Fact]
    public void Every_admin_endpoint_requires_the_admin_host_and_no_other_endpoint_names_a_host()
    {
        var endpoints = site.Factory.Services.GetRequiredService<EndpointDataSource>().Endpoints.OfType<RouteEndpoint>().ToList();
        var admin = endpoints.Where(endpoint => endpoint.RoutePattern.RawText!.TrimStart('/').StartsWith("admin/v1/", StringComparison.Ordinal)).ToList();
        var others = endpoints.Except(admin).ToList();

        Assert.Equal(18, admin.Count);
        Assert.All(admin, endpoint =>
        {
            Assert.NotNull(endpoint.Metadata.GetMetadata<AdminEndpoint>());
            Assert.Equal([CloudFactory.AdminHost], endpoint.Metadata.GetMetadata<IHostMetadata>()?.Hosts);
        });
        Assert.NotEmpty(others);
        Assert.All(others, endpoint =>
        {
            Assert.Null(endpoint.Metadata.GetMetadata<AdminEndpoint>());
            Assert.Null(endpoint.Metadata.GetMetadata<IHostMetadata>());
        });
    }
}
