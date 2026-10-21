using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Listing;

/// <summary>
/// The caller's schematics, newest change first, filtered by name text, folder and tag (D8). Pages continue from an
/// opaque cursor over (updatedAt, id), so inserts between pages never repeat or skip entries.
/// </summary>
internal sealed class ListSchematics(IMongoDatabase database)
{
    private const int DefaultLimit = 50;
    private const int MaxLimit = 100;

    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);

    public async Task<ActionResult<SchematicPage>> ListAsync(string owner, string? q, string? folder, string? tag, string? cursor, int limit,
        CancellationToken cancellationToken)
    {
        var size = limit <= 0 ? DefaultLimit : Math.Min(limit, MaxLimit);
        var filters = Builders<SchematicDocument>.Filter;
        var filter = filters.Eq(schematic => schematic.Owner, owner);
        if (!string.IsNullOrWhiteSpace(q))
        {
            filter &= filters.Regex(schematic => schematic.NameLower, new BsonRegularExpression(Regex.Escape(q.Trim().ToLowerInvariant())));
        }

        if (!string.IsNullOrWhiteSpace(folder))
        {
            filter &= filters.Eq(schematic => schematic.Folder, Folders.Normalize(folder));
        }

        if (!string.IsNullOrWhiteSpace(tag))
        {
            filter &= filters.AnyEq(schematic => schematic.Tags, tag.Trim());
        }

        if (!string.IsNullOrEmpty(cursor))
        {
            if (Cursor.Parse(cursor) is not { } after)
            {
                return Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Invalid_request, "The cursor is not valid.");
            }

            filter &= filters.Lt(schematic => schematic.UpdatedAt, after.UpdatedAt)
                | (filters.Eq(schematic => schematic.UpdatedAt, after.UpdatedAt) & filters.Lt(schematic => schematic.Id, after.Id));
        }

        var page = await schematics.Find(filter)
            .SortByDescending(schematic => schematic.UpdatedAt).ThenByDescending(schematic => schematic.Id)
            .Limit(size + 1)
            .ToListAsync(cancellationToken);
        var more = page.Count > size;
        var items = page.Take(size).ToList();
        return new SchematicPage
        {
            Items = items.Select(schematic => schematic.ToSummary()).ToList(),
            NextCursor = more ? Cursor.Format(items[^1].UpdatedAt, items[^1].Id) : null,
        };
    }

    private static class Cursor
    {
        public static string Format(DateTime updatedAt, string id) =>
            Convert.ToBase64String(Encoding.UTF8.GetBytes(updatedAt.Ticks.ToString(CultureInfo.InvariantCulture) + ":" + id))
                .TrimEnd('=').Replace('+', '-').Replace('/', '_');

        public static (DateTime UpdatedAt, string Id)? Parse(string cursor)
        {
            try
            {
                var padded = cursor.Replace('-', '+').Replace('_', '/');
                padded += new string('=', (4 - (padded.Length % 4)) % 4);
                var text = Encoding.UTF8.GetString(Convert.FromBase64String(padded));
                var colon = text.IndexOf(':', StringComparison.Ordinal);
                return colon > 0 && long.TryParse(text[..colon], NumberStyles.None, CultureInfo.InvariantCulture, out var ticks)
                    ? (new DateTime(ticks, DateTimeKind.Utc), text[(colon + 1)..])
                    : null;
            }
            catch (FormatException)
            {
                return null;
            }
        }
    }
}

/// <summary>Folder paths: slash-separated, without leading, trailing or repeated slashes.</summary>
internal static class Folders
{
    public static string? Normalize(string? folder)
    {
        if (folder is null)
        {
            return null;
        }

        var parts = folder.Split('/', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
        return parts.Length == 0 ? null : string.Join('/', parts);
    }
}
