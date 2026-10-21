using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Account;

/// <summary>A player's account, keyed by the Minecraft UUID Mojang reported (D9).</summary>
internal sealed class AccountDocument
{
    public const string Collection = "accounts";

    /// <summary>Minecraft UUID in its dashed lower-case form.</summary>
    [BsonId]
    public required string Id { get; init; }

    public required string Name { get; set; }

    public long UsedBytes { get; set; }

    public long QuotaBytes { get; set; }

    public DateTimeOffset CreatedAt { get; init; }

    public static IMongoCollection<AccountDocument> In(IMongoDatabase database) => database.GetCollection<AccountDocument>(Collection);
}
