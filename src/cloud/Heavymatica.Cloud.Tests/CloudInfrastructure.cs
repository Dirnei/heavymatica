using Aspire.Hosting;
using Aspire.Hosting.ApplicationModel;
using Aspire.Hosting.Testing;
using MongoDB.Driver;

[assembly: AssemblyFixture(typeof(Heavymatica.Cloud.Tests.CloudInfrastructure))]

namespace Heavymatica.Cloud.Tests;

/// <summary>
/// Real MongoDB and MinIO for the whole test run, started through the AppHost with ephemeral storage. The service
/// itself is left out: tests host it in-process (<see cref="CloudFactory"/>) so they can replace the clock and
/// other services. Each test class gets its own database.
/// </summary>
public sealed class CloudInfrastructure : IAsyncLifetime
{
    private DistributedApplication? app;

    public string MongoConnectionString { get; private set; } = string.Empty;

    public Uri MinioEndpoint { get; private set; } = null!;

    public string MinioUser { get; } = "heavymatica";

    public string MinioPassword { get; } = "heavymatica-dev-secret";

    public async ValueTask InitializeAsync()
    {
        using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(5));
        var appHost = await DistributedApplicationTestingBuilder.CreateAsync<Projects.Heavymatica_Cloud_AppHost>(
            ["--Heavymatica:EphemeralStorage=true", "--Heavymatica:WithWebsite=false"], timeout.Token);
        appHost.Resources.Remove(appHost.Resources.Single(resource => resource.Name == "cloud"));

        app = await appHost.BuildAsync(timeout.Token);
        await app.StartAsync(timeout.Token);
        await app.ResourceNotifications.WaitForResourceHealthyAsync("heavymatica", timeout.Token);
        await app.ResourceNotifications.WaitForResourceHealthyAsync("minio", timeout.Token);

        MongoConnectionString = await app.GetConnectionStringAsync("heavymatica", timeout.Token)
            ?? throw new InvalidOperationException("MongoDB has no connection string.");
        MinioEndpoint = app.GetEndpoint("minio", "http");
    }

    /// <summary>A connection string for a fresh database, so test classes never see each other's data.</summary>
    public string MongoConnectionStringFor(string database)
    {
        var url = new MongoUrlBuilder(MongoConnectionString) { DatabaseName = database };
        return url.ToString();
    }

    public async ValueTask DisposeAsync()
    {
        if (app is not null)
        {
            await app.DisposeAsync();
        }
    }
}
