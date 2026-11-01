using System.Net;
using System.Net.Http.Json;
using Aspire.Hosting;
using Aspire.Hosting.Testing;

namespace Heavymatica.Cloud.Tests.Infrastructure;

[Trait("Category", "Integration")]
public sealed class AppHostTests
{
    [Fact]
    public async Task AppHost_starts_mongo_minio_and_a_healthy_service_with_github_sign_in_and_linking()
    {
        var cancellationToken = TestContext.Current.CancellationToken;
        // Ephemeral storage: a running development AppHost keeps its data volumes locked.
        var appHost = await DistributedApplicationTestingBuilder.CreateAsync<Projects.Heavymatica_Cloud_AppHost>(
        [
            "--Heavymatica:EphemeralStorage=true",
            "--Heavymatica:WithWebsite=false",
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
        using var player = app.CreateHttpClient("cloud");
        var session = await SignIn.AsAsync(player, Guid.NewGuid(), "AppHostPlayer");
        player.Authorize(session.AccessToken);
        var me = await player.GetFromJsonAsync<Contract.Account>(new Uri("/v1/me", UriKind.Relative), SignIn.Json, cancellationToken);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(HttpStatusCode.Redirect, signIn.StatusCode);
        Assert.Equal("github.com", signIn.Headers.Location?.Host);
        Assert.Contains("client_id=dummy-client-id", signIn.Headers.Location?.Query, StringComparison.Ordinal);
        Assert.True(me!.LinkingAvailable);
    }

    [Fact]
    public async Task AppHost_runs_the_website_dev_server_on_its_fixed_port_unless_disabled()
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
        await withWebsite.DisposeAsync();
        await withoutWebsite.DisposeAsync();
    }
}
