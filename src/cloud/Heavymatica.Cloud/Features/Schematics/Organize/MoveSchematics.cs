using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Organize;

/// <summary>Changes the folder of many schematics in one update, never a version.</summary>
internal sealed class MoveSchematics(IMongoDatabase database, TimeProvider time)
{
    private const int MaxSchematics = 100;

    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);

    public async Task<ActionResult<MoveResult>> MoveAsync(string owner, MoveRequest request, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(request);
        var ids = request.SchematicIds ?? [];
        if (ids.Count is 0 or > MaxSchematics || ids.Distinct(StringComparer.Ordinal).Count() != ids.Count)
        {
            return Invalid($"Send 1 to {MaxSchematics} different schematic IDs.");
        }

        if (request.Folder is null || request.Folder.Length > FolderPaths.MaxLength)
        {
            return Invalid("A folder path has at most 200 characters.");
        }

        var now = DateTime.UnixEpoch.AddMilliseconds(time.GetUtcNow().ToUnixTimeMilliseconds());
        var result = await schematics.UpdateManyAsync(
            schematic => schematic.Owner == owner && ids.Contains(schematic.Id),
            Builders<SchematicDocument>.Update
                .Set(schematic => schematic.Folder, FolderPaths.Normalize(request.Folder))
                .Set(schematic => schematic.UpdatedAt, now),
            cancellationToken: cancellationToken);
        return new MoveResult { Moved = (int)result.ModifiedCount };
    }

    private static ObjectResult Invalid(string detail) => Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Invalid_request, detail);
}
