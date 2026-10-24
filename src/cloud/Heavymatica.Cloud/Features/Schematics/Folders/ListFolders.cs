using Heavymatica.Cloud.Contract;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Schematics.Folders;

/// <summary>Folders are only the paths schematics carry, so the tree is computed from one grouping by path.</summary>
internal sealed class ListFolders(IMongoDatabase database)
{
    private readonly IMongoCollection<SchematicDocument> schematics = SchematicDocument.In(database);

    public async Task<FolderList> ListAsync(string owner, CancellationToken cancellationToken)
    {
        var groups = await schematics.Aggregate()
            .Match(schematic => schematic.Owner == owner)
            .Group(schematic => schematic.Folder, group => new { Path = group.Key, Count = group.Count() })
            .ToListAsync(cancellationToken);

        var folders = new SortedDictionary<string, FolderEntry>(StringComparer.Ordinal);
        var unfiled = 0;
        foreach (var group in groups)
        {
            if (group.Path is null)
            {
                unfiled += group.Count;
                continue;
            }

            foreach (var prefix in FolderPaths.Prefixes(group.Path))
            {
                if (!folders.TryGetValue(prefix, out var entry))
                {
                    entry = new FolderEntry { Path = prefix };
                    folders[prefix] = entry;
                }

                entry.TotalCount += group.Count;
            }

            folders[group.Path].DirectCount += group.Count;
        }

        return new FolderList { Folders = [.. folders.Values], UnfiledCount = unfiled };
    }
}
