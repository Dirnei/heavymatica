using System.Xml.Linq;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.AspNetCore.DataProtection.KeyManagement;
using Microsoft.AspNetCore.DataProtection.Repositories;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Identity;

internal sealed class DataProtectionKeyDocument
{
    public const string Collection = "data_protection_keys";

    [BsonId]
    public required string Id { get; init; }

    public required string Xml { get; init; }

    public DateTime CreatedAt { get; init; }

    public static IMongoCollection<DataProtectionKeyDocument> In(IMongoDatabase database) => database.GetCollection<DataProtectionKeyDocument>(Collection);
}

/// <summary>
/// Keeps the Data Protection key ring in MongoDB, so website cookies and OAuth state survive restarts and work on
/// every instance. The interface is synchronous.
/// </summary>
internal sealed class MongoXmlRepository(IMongoDatabase database, TimeProvider time) : IXmlRepository
{
    private readonly IMongoCollection<DataProtectionKeyDocument> keys = DataProtectionKeyDocument.In(database);

    public IReadOnlyCollection<XElement> GetAllElements() =>
        keys.Find(_ => true).ToList().Select(key => XElement.Parse(key.Xml)).ToList();

    public void StoreElement(XElement element, string friendlyName)
    {
        ArgumentNullException.ThrowIfNull(element);
        keys.InsertOne(new DataProtectionKeyDocument
        {
            Id = string.IsNullOrEmpty(friendlyName) ? Guid.NewGuid().ToString("D") : friendlyName,
            Xml = element.ToString(SaveOptions.DisableFormatting),
            CreatedAt = time.GetUtcNow().UtcDateTime,
        });
    }
}

internal static class DataProtectionKeys
{
    public static IServiceCollection AddMongoDataProtection(this IServiceCollection services)
    {
        services.AddDataProtection().SetApplicationName("heavymatica-cloud");
        services.AddOptions<KeyManagementOptions>()
            .Configure<IMongoDatabase, TimeProvider>((options, database, time) => options.XmlRepository = new MongoXmlRepository(database, time));
        return services;
    }
}
