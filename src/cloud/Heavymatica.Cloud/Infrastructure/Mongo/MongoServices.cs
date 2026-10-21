using Microsoft.Extensions.Diagnostics.HealthChecks;
using MongoDB.Bson;
using MongoDB.Bson.Serialization.Conventions;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Infrastructure.Mongo;

/// <summary>Creates a slice's indexes at startup. Each slice registers its own.</summary>
internal interface IMongoIndexes
{
    Task CreateAsync(IMongoDatabase database, CancellationToken cancellationToken);
}

internal static class MongoServices
{
    public const string ConnectionName = "heavymatica";

    public static WebApplicationBuilder AddMongo(this WebApplicationBuilder builder)
    {
        ConventionRegistry.Register("heavymatica", new ConventionPack
        {
            new CamelCaseElementNameConvention(),
            new IgnoreExtraElementsConvention(true),
            new EnumRepresentationConvention(BsonType.String),
        }, _ => true);

        var connectionString = builder.Configuration.GetConnectionString(ConnectionName)
            ?? throw new InvalidOperationException($"ConnectionStrings:{ConnectionName} is not configured.");
        var url = MongoUrl.Create(connectionString);
        builder.Services.AddSingleton<IMongoClient>(_ => new MongoClient(url));
        builder.Services.AddSingleton(services => services.GetRequiredService<IMongoClient>().GetDatabase(url.DatabaseName ?? ConnectionName));
        builder.Services.AddHostedService<MongoIndexSetup>();
        builder.Services.AddHealthChecks().AddCheck<MongoHealthCheck>("mongo");
        return builder;
    }

    public static IServiceCollection AddMongoIndexes<T>(this IServiceCollection services)
        where T : class, IMongoIndexes
    {
        services.AddSingleton<IMongoIndexes, T>();
        return services;
    }
}

/// <summary>Runs every slice's index creation before the service accepts requests.</summary>
internal sealed class MongoIndexSetup(IMongoDatabase database, IEnumerable<IMongoIndexes> indexes) : IHostedService
{
    public async Task StartAsync(CancellationToken cancellationToken)
    {
        foreach (var slice in indexes)
        {
            await slice.CreateAsync(database, cancellationToken);
        }
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;
}

internal sealed class MongoHealthCheck(IMongoDatabase database) : IHealthCheck
{
    public async Task<HealthCheckResult> CheckHealthAsync(HealthCheckContext context, CancellationToken cancellationToken = default)
    {
        try
        {
            await database.RunCommandAsync((Command<BsonDocument>)"{ping:1}", cancellationToken: cancellationToken);
            return HealthCheckResult.Healthy();
        }
        catch (Exception e) when (e is MongoException or TimeoutException)
        {
            return HealthCheckResult.Unhealthy("MongoDB is unreachable.", e);
        }
    }
}
