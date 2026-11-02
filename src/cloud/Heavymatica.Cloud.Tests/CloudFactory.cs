using System.Net;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure;
using Microsoft.AspNetCore.Builder;
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

    /// <summary>The admin host name of a factory with <see cref="WithAdmin"/>.</summary>
    public const string AdminHost = "localhost";

    /// <summary>The public listener of a factory with <see cref="WithAdmin"/>, on Kestrel.</summary>
    public Uri? PublicAddress { get; private set; }

    /// <summary>The admin listener of a factory with <see cref="WithAdmin"/>, on Kestrel.</summary>
    public Uri? AdminAddress { get; private set; }

    /// <summary>
    /// Hosts the service on Kestrel with a public and an admin listener on free ports, since the test server has no
    /// local ports to tell them apart. Clients are then plain HTTP clients to <see cref="PublicAddress"/> and
    /// <see cref="AdminAddress"/>; <see cref="WebApplicationFactory{TEntryPoint}.Server"/> is not available.
    /// </summary>
    public CloudFactory WithAdmin()
    {
        PublicAddress = new Uri($"http://{AdminHost}:{FreePort()}");
        AdminAddress = new Uri($"http://{AdminHost}:{FreePort()}");
        With("urls", PublicAddress.GetLeftPart(UriPartial.Authority));
        With("Admin:Url", AdminAddress.GetLeftPart(UriPartial.Authority));
        With("Admin:Host", AdminHost);
        UseKestrel();
        return this;
    }

    private static int FreePort()
    {
        using var listener = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        return ((IPEndPoint)listener.LocalEndpoint).Port;
    }

    /// <summary>The client address every in-process request comes from, unless it names another in <see cref="ClientAddressHeader"/>.</summary>
    public const string DefaultClientAddress = "203.0.113.10";

    public const string ClientAddressHeader = "X-Test-Client-Address";

    /// <summary>The test server leaves the remote address empty; the service records and limits by it.</summary>
    private sealed class ClientAddressFilter : IStartupFilter
    {
        public Action<IApplicationBuilder> Configure(Action<IApplicationBuilder> next) => app =>
        {
            app.Use((context, nextMiddleware) =>
            {
                if (context.Request.Headers.TryGetValue(ClientAddressHeader, out var address))
                {
                    context.Connection.RemoteIpAddress = IPAddress.Parse(address.ToString());
                }

                context.Connection.RemoteIpAddress ??= IPAddress.Parse(DefaultClientAddress);
                return nextMiddleware(context);
            });
            next(app);
        };
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        ArgumentNullException.ThrowIfNull(builder);
        builder.UseEnvironment(Environment);
        builder.UseSetting("ConnectionStrings:heavymatica", infrastructure.MongoConnectionStringFor(DatabaseName));
        builder.UseSetting("S3:ServiceUrl", infrastructure.MinioEndpoint.ToString());
        builder.UseSetting("S3:AccessKey", infrastructure.MinioUser);
        builder.UseSetting("S3:SecretKey", infrastructure.MinioPassword);
        builder.UseSetting("S3:Bucket", BucketFor(DatabaseName));

        var inProcessMojang = !settings.ContainsKey(MojangUrlKey) && PublicAddress is null;
        if (inProcessMojang)
        {
            builder.UseSetting(MojangUrlKey, "http://localhost" + DevMojang.BasePath);
        }
        else if (PublicAddress is not null && !settings.ContainsKey(MojangUrlKey))
        {
            builder.UseSetting(MojangUrlKey, PublicAddress.GetLeftPart(UriPartial.Authority) + DevMojang.BasePath);
        }

        // Tests drive the thumbnail worker themselves; its timer would render behind their backs.
        builder.UseSetting("Thumbnails:Enabled", "false");

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

            services.AddSingleton<IStartupFilter, ClientAddressFilter>();
            if (Clock is not null)
            {
                services.AddSingleton<TimeProvider>(Clock);
            }

            TestServices?.Invoke(services);
        });
    }
}
