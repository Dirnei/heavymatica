using Heavymatica.Cloud.Contract;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Details;

internal sealed class GetSchematic(IMongoDatabase database)
{
    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);
    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);

    public async Task<ActionResult<SchematicDetails>> GetAsync(string owner, string schematicId, CancellationToken cancellationToken)
    {
        var schematic = await schematics.Find(document => document.Id == schematicId && document.Owner == owner).FirstOrDefaultAsync(cancellationToken);
        if (schematic is null)
        {
            return SchematicProblems.NotFound();
        }

        var all = await versions.Find(version => version.SchematicId == schematicId).SortBy(version => version.Number).ToListAsync(cancellationToken);
        return schematic.ToDetails(all);
    }
}
