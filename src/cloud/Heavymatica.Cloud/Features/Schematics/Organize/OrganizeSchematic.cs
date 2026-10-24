using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Organize;

/// <summary>Changes the schematic only, never a version.</summary>
internal sealed class OrganizeSchematic(IMongoDatabase database, TimeProvider time)
{
    private const int MaxTags = 20;

    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);
    private readonly IMongoCollection<VersionDocument> versions = VersionDocument.In(database);

    public async Task<ActionResult<SchematicDetails>> OrganizeAsync(string owner, string schematicId, OrganizeRequest request, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(request);
        var update = Builders<SchematicDocument>.Update.Set(schematic => schematic.UpdatedAt, Now());
        if (request.Name is not null)
        {
            var name = request.Name.Trim();
            if (name.Length is 0 or > 100)
            {
                return Invalid("A name has 1 to 100 characters.");
            }

            update = update.Set(schematic => schematic.Name, name).Set(schematic => schematic.NameLower, name.ToLowerInvariant());
        }

        if (request.Folder is not null)
        {
            if (request.Folder.Length > 200)
            {
                return Invalid("A folder path has at most 200 characters.");
            }

            update = update.Set(schematic => schematic.Folder, FolderPaths.Normalize(request.Folder));
        }

        if (request.Tags is not null)
        {
            var tags = request.Tags.Select(tag => tag.Trim()).Where(tag => tag.Length > 0).Distinct(StringComparer.Ordinal).ToList();
            if (tags.Count > MaxTags || tags.Any(tag => tag.Length > 50))
            {
                return Invalid($"At most {MaxTags} tags of up to 50 characters each.");
            }

            update = update.Set(schematic => schematic.Tags, tags);
        }

        var schematic = await schematics.FindOneAndUpdateAsync(
            document => document.Id == schematicId && document.Owner == owner,
            update,
            new FindOneAndUpdateOptions<SchematicDocument> { ReturnDocument = ReturnDocument.After },
            cancellationToken);
        if (schematic is null)
        {
            return SchematicProblems.NotFound();
        }

        var all = await versions.Find(version => version.SchematicId == schematicId).SortBy(version => version.Number).ToListAsync(cancellationToken);
        return schematic.ToDetails(all);
    }

    private DateTime Now() => DateTime.UnixEpoch.AddMilliseconds(time.GetUtcNow().ToUnixTimeMilliseconds());

    private static ObjectResult Invalid(string detail) => Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Invalid_request, detail);
}
