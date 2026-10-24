using System.Text.RegularExpressions;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics;

/// <summary>
/// Folder paths: slash-separated, without leading, trailing or repeated slashes. A folder includes the folders below
/// it, so <c>houses</c> holds <c>houses/medieval</c> but not <c>housesmall</c>.
/// </summary>
internal static class FolderPaths
{
    public const int MaxLength = 200;

    public static string? Normalize(string? folder)
    {
        if (folder is null)
        {
            return null;
        }

        var parts = folder.Split('/', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        return parts.Length == 0 ? null : string.Join('/', parts);
    }

    /// <summary>The path itself and every parent, shortest first: <c>a/b/c</c> gives <c>a</c>, <c>a/b</c>, <c>a/b/c</c>.</summary>
    public static IEnumerable<string> Prefixes(string path)
    {
        ArgumentNullException.ThrowIfNull(path);
        for (var slash = path.IndexOf('/', StringComparison.Ordinal); slash >= 0; slash = path.IndexOf('/', slash + 1))
        {
            yield return path[..slash];
        }

        yield return path;
    }

    /// <summary>Matches schematics in the normalized <paramref name="folder"/> or below it.</summary>
    public static FilterDefinition<SchematicDocument> InOrBelow(string folder)
    {
        ArgumentNullException.ThrowIfNull(folder);
        var filters = Builders<SchematicDocument>.Filter;
        // Anchored without options, so the owner_folder index serves it as a prefix range.
        return filters.Eq(schematic => schematic.Folder, folder)
            | filters.Regex(schematic => schematic.Folder, new BsonRegularExpression("^" + Regex.Escape(folder) + "/"));
    }
}
