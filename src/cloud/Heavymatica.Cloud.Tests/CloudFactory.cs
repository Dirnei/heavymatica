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
    {
        ArgumentNullException.ThrowIfNull(infrastructure);
        this.infrastructure = infrastructure;
        DatabaseName = "test_" + Guid.NewGuid().ToString("N")[..12];
    }

    public string DatabaseName { get; }

    public string Environment { get; init; } = "Development";

    /// <summary>When set, replaces the service clock, so tests can move time forward.</summary>
    public FakeTimeProvider? Clock { get; init; }

    /// <summary>Overrides configuration for this factory, for example limits or the Mojang address.</summary>
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
        builder.UseSetting("S3:Bucket", DatabaseName.Replace('_', '-'));

        var inProcessMojang = !settings.ContainsKey(MojangUrlKey);
        if (inProcessMojang)
        {
            builder.UseSetting(MojangUrlKey, "http://localhost" + DevMojang.BasePath);
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
        });
    }
}
