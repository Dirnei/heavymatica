using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Folders;

/// <summary>Rewrites the path prefix of every schematic in the folder or below it in one update.</summary>
internal sealed class RenameFolder(IMongoDatabase database, TimeProvider time)
{
    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);

    public async Task<ActionResult<MoveResult>> RenameAsync(string owner, RenameFolderRequest request, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(request);
        var from = FolderPaths.Normalize(request.From);
        var to = FolderPaths.Normalize(request.To);
        if (from is null || to is null || to.Length > FolderPaths.MaxLength)
        {
            return Invalid("Both folder paths need 1 to 200 characters.");
        }

        var filter = Builders<SchematicDocument>.Filter.Eq(schematic => schematic.Owner, owner) & FolderPaths.InOrBelow(from);
        var paths = await (await schematics.DistinctAsync(schematic => schematic.Folder, filter, cancellationToken: cancellationToken))
            .ToListAsync(cancellationToken);
        if (paths.Count == 0)
        {
            return Problems.Result(StatusCodes.Status404NotFound, ProblemCode.Not_found, "No such folder.");
        }

        if (from == to)
        {
            return new MoveResult { Moved = 0 };
        }

        if (paths.Max(path => path!.Length) - from.Length + to.Length > FolderPaths.MaxLength)
        {
            return Invalid("The renamed folder paths would be longer than 200 characters.");
        }

        // An update pipeline, so each schematic keeps the part of its path below the renamed folder.
        var now = DateTime.UnixEpoch.AddMilliseconds(time.GetUtcNow().ToUnixTimeMilliseconds());
        var rewrite = new BsonDocument("$set", new BsonDocument
        {
            { "folder", new BsonDocument("$concat", new BsonArray
                {
                    to,
                    new BsonDocument("$substrCP", new BsonArray
                    {
                        "$folder",
                        // $substrCP counts code points, not UTF-16 units.
                        from.EnumerateRunes().Count(),
                        new BsonDocument("$strLenCP", "$folder"),
                    }),
                })
            },
            { "updatedAt", now },
        });
        var result = await schematics.UpdateManyAsync(filter, new PipelineUpdateDefinition<SchematicDocument>(new[] { rewrite }),
            cancellationToken: cancellationToken);
        return new MoveResult { Moved = (int)result.ModifiedCount };
    }

    private static ObjectResult Invalid(string detail) => Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Invalid_request, detail);
}
