using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests;

/// <summary>
/// Hosts the service in-process against the shared <see cref="CloudInfrastructure"/>, with its own database.
/// Mojang calls go to the service's own DevMojang slice unless a test points <c>Identity:MojangSessionUrl</c> elsewhere.
/// </summary>
public class CloudFactory : WebApplicationFactory<ServiceAssembly>
{
    private const string MojangUrlKey = "Identity:MojangSessionUrl";

    private readonly CloudInfrastructure infrastructure;
    private readonly Dictionary<string, string?> settings = new(StringComparer.Ordinal);

    public CloudFactory(CloudInfrastructure infrastructure)
        : this(infrastructure, NewDatabaseName())
    {
    }

    /// <summary>A factory on an existing test database and its bucket, for example a second instance of the service.</summary>
    public CloudFactory(CloudInfrastructure infrastructure, string databaseName)
    {
        ArgumentNullException.ThrowIfNull(infrastructure);
        this.infrastructure = infrastructure;
        DatabaseName = databaseName;
    }

    public static string NewDatabaseName() => "test_" + Guid.NewGuid().ToString("N")[..12];

    public static string BucketFor(string databaseName)
    {
        ArgumentNullException.ThrowIfNull(databaseName);
        return databaseName.Replace('_', '-');
    }

    public string DatabaseName { get; }

    /// <summary>Further service replacements, applied after the service's own registrations.</summary>
    public Action<IServiceCollection>? TestServices { get; init; }

    public string Environment { get; init; } = "Development";

    /// <summary>When set, replaces the service clock, so tests can move time forward.</summary>
    public FakeTimeProvider? Clock { get; init; }

    public CloudFactory With(string key, string? value)
    {
        settings[key] = value;
        return this;
    }

    public IMongoDatabase Database => Services.GetRequiredService<IMongoDatabase>();

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        ArgumentNullException.ThrowIfNull(builder);
        builder.UseEnvironment(Environment);
        builder.UseSetting("ConnectionStrings:heavymatica", infrastructure.MongoConnectionStringFor(DatabaseName));
        builder.UseSetting("S3:ServiceUrl", infrastructure.MinioEndpoint.ToString());
        builder.UseSetting("S3:AccessKey", infrastructure.MinioUser);
        builder.UseSetting("S3:SecretKey", infrastructure.MinioPassword);
        builder.UseSetting("S3:Bucket", BucketFor(DatabaseName));

        var inProcessMojang = !settings.ContainsKey(MojangUrlKey);
        if (inProcessMojang)
        {
            builder.UseSetting(MojangUrlKey, "http://localhost" + DevMojang.BasePath);
        }

        // Host settings are visible while the service registers its services; app configuration only afterwards.
        foreach (var (key, value) in settings)
        {
            builder.UseSetting(key, value);
        }

        builder.ConfigureAppConfiguration(configuration => configuration.AddInMemoryCollection(settings));
        builder.ConfigureTestServices(services =>
        {
            if (inProcessMojang)
            {
                services.AddHttpClient(MojangSessions.HttpClientName).ConfigurePrimaryHttpMessageHandler(() => Server.CreateHandler());
            }

            if (Clock is not null)
            {
                services.AddSingleton<TimeProvider>(Clock);
            }

            TestServices?.Invoke(services);
        });
    }
}
