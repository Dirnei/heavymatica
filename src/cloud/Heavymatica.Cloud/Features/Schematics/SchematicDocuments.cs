using Heavymatica.Cloud.Formats;
using Heavymatica.Cloud.Infrastructure.Mongo;
using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics;

/// <summary>Holds a copy of its latest version for single-query listings.</summary>
internal sealed class SchematicDocument
{
    public const string Collection = "schematics";

    [BsonId]
    public required string Id { get; init; }

    public required string Owner { get; init; }

    public required string Name { get; set; }

    public required string NameLower { get; set; }

    public string? Folder { get; set; }

    public List<string> Tags { get; set; } = [];

    public int NextVersion { get; set; }

    public required VersionDocument Latest { get; set; }

    public DateTime UpdatedAt { get; set; }

    public static IMongoCollection<SchematicDocument> In(IMongoDatabase database) => database.GetCollection<SchematicDocument>(Collection);
}

/// <summary>An immutable version: once stored, nothing but its thumbnail flag ever changes.</summary>
internal sealed class VersionDocument
{
    public const string Collection = "versions";

    [BsonId]
    public required string Id { get; init; }

    public required string SchematicId { get; init; }

    public required string Owner { get; init; }

    public int Number { get; init; }

    public required string Sha256 { get; init; }

    public SchematicFormat Format { get; init; }

    public long SizeBytes { get; init; }

    public int Width { get; init; }

    public int Height { get; init; }

    public int Length { get; init; }

    public int? DataVersion { get; init; }

    public long BlockTotal { get; init; }

    public List<BlockCountDocument> BlockCounts { get; init; } = [];

    public int SkippedEntities { get; init; }

    public bool HasThumbnail { get; set; }

    public long ThumbnailBytes { get; set; }

    public DateTime CreatedAt { get; init; }

    public static string KeyOf(string schematicId, int number) => $"{schematicId}:{number}";

    public static IMongoCollection<VersionDocument> In(IMongoDatabase database) => database.GetCollection<VersionDocument>(Collection);
}

internal sealed record BlockCountDocument(string Block, long Count);

internal sealed class SchematicIndexes : IMongoIndexes
{
    public async Task CreateAsync(IMongoDatabase database, CancellationToken cancellationToken)
    {
        var schematicKeys = Builders<SchematicDocument>.IndexKeys;
        await SchematicDocument.In(database).Indexes.CreateManyAsync(
        [
            new CreateIndexModel<SchematicDocument>(
                schematicKeys.Ascending(schematic => schematic.Owner).Descending(schematic => schematic.UpdatedAt).Descending(schematic => schematic.Id),
                new CreateIndexOptions { Name = "owner_updatedAt" }),
            new CreateIndexModel<SchematicDocument>(
                schematicKeys.Ascending(schematic => schematic.Owner).Ascending(schematic => schematic.Folder),
                new CreateIndexOptions { Name = "owner_folder" }),
            new CreateIndexModel<SchematicDocument>(
                schematicKeys.Ascending(schematic => schematic.Owner).Ascending(schematic => schematic.Tags),
                new CreateIndexOptions { Name = "owner_tags" }),
        ], cancellationToken);

        var versionKeys = Builders<VersionDocument>.IndexKeys;
        await VersionDocument.In(database).Indexes.CreateManyAsync(
        [
            new CreateIndexModel<VersionDocument>(
                versionKeys.Ascending(version => version.SchematicId).Ascending(version => version.Number),
                new CreateIndexOptions { Name = "schematic_number", Unique = true }),
            new CreateIndexModel<VersionDocument>(
                versionKeys.Ascending(version => version.Owner).Ascending(version => version.Sha256),
                new CreateIndexOptions { Name = "owner_sha256" }),
        ], cancellationToken);
    }
}
