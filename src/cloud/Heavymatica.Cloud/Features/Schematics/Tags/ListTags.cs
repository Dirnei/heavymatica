using Heavymatica.Cloud.Contract;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Tags;

/// <summary>Tags are only what schematics carry, so the list is counted from one grouping.</summary>
internal sealed class ListTags(IMongoDatabase database)
{
    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);

    public async Task<TagList> ListAsync(string owner, CancellationToken cancellationToken)
    {
        var groups = await schematics.Aggregate()
            .Match(schematic => schematic.Owner == owner)
            .Unwind<SchematicDocument, BsonDocument>(schematic => schematic.Tags)
            .Group(new BsonDocument { { "_id", "$tags" }, { "count", new BsonDocument("$sum", 1) } })
            .ToListAsync(cancellationToken);

        var tags = groups
            .Select(group => new TagEntry { Tag = group["_id"].AsString, Count = group["count"].AsInt32 })
            .OrderBy(entry => entry.Tag, StringComparer.Ordinal)
            .ToList();
        return new TagList { Tags = tags };
    }
}
