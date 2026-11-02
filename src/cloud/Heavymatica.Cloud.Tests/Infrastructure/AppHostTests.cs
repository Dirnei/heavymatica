using System.Net;
using System.Net.Http.Json;
using Aspire.Hosting;
using Aspire.Hosting.Testing;

namespace Heavymatica.Cloud.Tests.Infrastructure;

[Trait("Category", "Integration")]
public sealed class AppHostTests
{
    [Fact]
    public async Task AppHost_starts_mongo_minio_and_a_healthy_service_with_github_sign_in_linking_and_thumbnails()
    {
        var cancellationToken = TestContext.Current.CancellationToken;
        // A free port: a running development AppHost holds the default admin port.
        var adminPort = FreePort();
        // Ephemeral storage: a running development AppHost keeps its data volumes locked.
        var appHost = await DistributedApplicationTestingBuilder.CreateAsync<Projects.Heavymatica_Cloud_AppHost>(
        [
            "--Heavymatica:EphemeralStorage=true",
            "--Heavymatica:WithWebsite=false",
            "--Heavymatica:AdminPort=" + adminPort,
            "--Parameters:github-client-id=dummy-client-id",
            "--Parameters:github-client-secret=dummy-client-secret",
        ], cancellationToken);
        await using var app = await appHost.BuildAsync(cancellationToken);
        await app.StartAsync(cancellationToken);

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromMinutes(3));
        await app.ResourceNotifications.WaitForResourceHealthyAsync("mongo", timeout.Token);
        await app.ResourceNotifications.WaitForResourceHealthyAsync("minio", timeout.Token);
        await app.ResourceNotifications.WaitForResourceHealthyAsync("cloud", timeout.Token);

        using var client = app.CreateHttpClient("cloud");
        using var response = await client.GetAsync(new Uri("/health", UriKind.Relative), cancellationToken);
        using var browser = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false }) { BaseAddress = app.GetEndpoint("cloud", "http") };
        using var signIn = await browser.GetAsync(new Uri("/v1/web/sign-in/github?intent=login", UriKind.Relative), cancellationToken);
        using var adminListener = new HttpClient { BaseAddress = new Uri("http://localhost:" + adminPort) };
        using var adminSession = await adminListener.GetAsync(new Uri("/admin/v1/session", UriKind.Relative), cancellationToken);
        using var adminOnPublic = await client.GetAsync(new Uri("/admin/v1/session", UriKind.Relative), cancellationToken);
        using var player = app.CreateHttpClient("cloud");
        var session = await SignIn.AsAsync(player, Guid.NewGuid(), "AppHostPlayer");
        player.Authorize(session.AccessToken);
        var me = await player.GetFromJsonAsync<Contract.Account>(new Uri("/v1/me", UriKind.Relative), SignIn.Json, cancellationToken);
        using var file = new ByteArrayContent(File.ReadAllBytes(Path.Combine(RepositoryFiles.Conformance, "files", "house.nbt")));
        file.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
        using var upload = await player.PostAsync(new Uri("/v1/schematics?name=AppHostHouse", UriKind.Relative), file, cancellationToken);
        var uploaded = (await upload.Content.ReadFromJsonAsync<Contract.UploadResult>(SignIn.Json, cancellationToken))!;
        // A game upload waits the one-minute grace period, then the worker's timer picks it up.
        var rendered = false;
        for (var waited = TimeSpan.Zero; !rendered && waited < TimeSpan.FromMinutes(2); waited += TimeSpan.FromSeconds(2))
        {
            await Task.Delay(TimeSpan.FromSeconds(2), cancellationToken);
            var details = await player.GetFromJsonAsync<Contract.SchematicDetails>(new Uri("/v1/schematics/" + uploaded.SchematicId, UriKind.Relative),
                SignIn.Json, cancellationToken);
            rendered = details!.Versions.Single().ThumbnailSource == Contract.ThumbnailSource.Rendered;
        }

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(HttpStatusCode.Redirect, signIn.StatusCode);
        Assert.Equal("github.com", signIn.Headers.Location?.Host);
        Assert.Contains("client_id=dummy-client-id", signIn.Headers.Location?.Query, StringComparison.Ordinal);
        Assert.True(me!.LinkingAvailable);
        Assert.Equal(HttpStatusCode.Unauthorized, adminSession.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, adminOnPublic.StatusCode);
        Assert.Equal(HttpStatusCode.Created, upload.StatusCode);
        Assert.True(rendered, "The worker rendered no thumbnail within two minutes.");
    }

    [Fact]
    public async Task AppHost_runs_the_website_and_admin_dev_servers_on_their_fixed_ports_unless_disabled()
    {
        var cancellationToken = TestContext.Current.CancellationToken;

        var withWebsite = await DistributedApplicationTestingBuilder.CreateAsync<Projects.Heavymatica_Cloud_AppHost>(
            ["--Heavymatica:EphemeralStorage=true"], cancellationToken);
        var withoutWebsite = await DistributedApplicationTestingBuilder.CreateAsync<Projects.Heavymatica_Cloud_AppHost>(
            ["--Heavymatica:EphemeralStorage=true", "--Heavymatica:WithWebsite=false"], cancellationToken);

        var website = Assert.Single(withWebsite.Resources, resource => resource.Name == "website");
        var endpoints = website.Annotations.OfType<Aspire.Hosting.ApplicationModel.EndpointAnnotation>();
        Assert.Contains(endpoints, endpoint => endpoint.Port == 4321 && !endpoint.IsProxied);
        Assert.DoesNotContain(withoutWebsite.Resources, resource => resource.Name == "website");
        var adminWebsite = Assert.Single(withWebsite.Resources, resource => resource.Name == "admin-website");
        Assert.Contains(adminWebsite.Annotations.OfType<Aspire.Hosting.ApplicationModel.EndpointAnnotation>(), endpoint => endpoint.Port == 4322 && !endpoint.IsProxied);
        Assert.DoesNotContain(withoutWebsite.Resources, resource => resource.Name == "admin-website");
        await withWebsite.DisposeAsync();
        await withoutWebsite.DisposeAsync();
    }

    private static int FreePort()
    {
        using var listener = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        return ((IPEndPoint)listener.LocalEndpoint).Port;
    }
}
