using System.Text.Json;
using Heavymatica.Cloud.Formats;

namespace Heavymatica.Cloud.Tests.Formats;

/// <summary>Reads every file of the shared conformance set and compares the result with its expected JSON (D7).</summary>
public sealed class ConformanceTests
{
    public static TheoryData<string> Fixtures()
    {
        var files = Path.Combine(RepositoryFiles.Conformance, "files");
        var data = new TheoryData<string>();
        foreach (var file in Directory.EnumerateFiles(files, "*", SearchOption.AllDirectories).Order(StringComparer.Ordinal))
        {
            if (Path.GetFileName(file) != ".gitkeep")
            {
                data.Add(Path.GetRelativePath(files, file).Replace('\\', '/'));
            }
        }

        return data;
    }

    [Theory]
    [MemberData(nameof(Fixtures))]
    public void Reads_like_the_expected_result(string fixture)
    {
        var expectedPath = Path.Combine(RepositoryFiles.Conformance, "expected", fixture + ".json");
        Assert.True(File.Exists(expectedPath), $"{fixture} has no expected result.");
        using var expected = JsonDocument.Parse(File.ReadAllText(expectedPath));
        var root = expected.RootElement;
        var path = Path.Combine(RepositoryFiles.Conformance, "files", fixture);

        if (root.TryGetProperty("reject", out var reject))
        {
            var error = Assert.Throws<SchematicRejectedException>(() => SchematicReader.Read(path));
            Assert.Equal(reject.GetString(), error.Code.ToWireName());
            return;
        }

        var model = SchematicReader.Read(path);

        Assert.Equal(root.GetProperty("format").GetString(), model.Format.ToString().ToLowerInvariant());
        Assert.Equal(root.GetProperty("size").EnumerateArray().Select(value => value.GetInt32()), [model.Width, model.Height, model.Length]);
        Assert.Equal(root.GetProperty("dataVersion").ValueKind == JsonValueKind.Null ? null : root.GetProperty("dataVersion").GetInt32(), model.DataVersion);
        Assert.Equal(root.GetProperty("paletteDigest").GetString(), model.PaletteDigest());
        var counts = model.BlockCounts();
        Assert.Equal(root.GetProperty("blockTotal").GetInt64(), counts.Sum(count => count.Value));
        Assert.Equal(
            root.GetProperty("blockCounts").EnumerateObject().Select(property => (property.Name, property.Value.GetInt64())).Order(),
            counts.Select(count => (count.Key, count.Value)).Order());
        Assert.Equal(
            root.GetProperty("blockEntities").EnumerateArray()
                .Select(entity => (Pos: string.Join(',', entity.GetProperty("pos").EnumerateArray().Select(value => value.GetInt32())), Id: entity.GetProperty("id").GetString()))
                .Order(),
            model.BlockEntities.Select(entity => (Pos: $"{entity.Key.X},{entity.Key.Y},{entity.Key.Z}", Id: entity.Value.String("id"))).Order());
        Assert.Equal(root.GetProperty("skippedEntities").GetInt32(), model.SkippedEntities);
    }
}
