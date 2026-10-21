using System.Net;
using Aspire.Hosting;
using Aspire.Hosting.Testing;

namespace Heavymatica.Cloud.Tests.Infrastructure;

[Trait("Category", "Integration")]
public sealed class AppHostTests
{
    [Fact]
    public async Task AppHost_starts_mongo_minio_and_a_healthy_service()
    {
        var cancellationToken = TestContext.Current.CancellationToken;
        // Ephemeral storage: a running development AppHost keeps its data volumes locked.
        var appHost = await DistributedApplicationTestingBuilder.CreateAsync<Projects.Heavymatica_Cloud_AppHost>(
            ["--Heavymatica:EphemeralStorage=true"], cancellationToken);
        await using var app = await appHost.BuildAsync(cancellationToken);
        await app.StartAsync(cancellationToken);

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromMinutes(3));
        await app.ResourceNotifications.WaitForResourceHealthyAsync("mongo", timeout.Token);
        await app.ResourceNotifications.WaitForResourceHealthyAsync("minio", timeout.Token);
        await app.ResourceNotifications.WaitForResourceHealthyAsync("cloud", timeout.Token);

        using var client = app.CreateHttpClient("cloud");
        using var response = await client.GetAsync(new Uri("/health", UriKind.Relative), cancellationToken);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }
}
