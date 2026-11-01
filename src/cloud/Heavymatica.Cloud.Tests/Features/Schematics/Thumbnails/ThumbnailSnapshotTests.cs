using System.Security.Cryptography;
using Heavymatica.Cloud.Features.Schematics.Thumbnails;
using Heavymatica.Cloud.Formats;

namespace Heavymatica.Cloud.Tests.Features.Schematics.Thumbnails;

/// <summary>
/// Renders conformance files with the shipped colour table into <c>thumbnails/</c> next to the test assembly, for
/// looking at, and pins one rendering. Set <c>HEAVYMATICA_THUMBNAIL_SAMPLES</c> to a directory to render its
/// schematics there as well.
/// </summary>
public sealed class ThumbnailSnapshotTests
{
    private static readonly string[] Fixtures =
    [
        "house.nbt", "house.v2.schem", "house.v3.schem", "house.litematica-v7.litematic", "entities.litematic", "modded.v3.schem",
    ];

    private static readonly string Output = Path.Combine(AppContext.BaseDirectory, "thumbnails");

    [Fact]
    public void Every_fixture_renders_to_a_thumbnail()
    {
        Directory.CreateDirectory(Output);
        foreach (var name in Fixtures)
        {
            var png = Render(Path.Combine(RepositoryFiles.Conformance, "files", name));
            Assert.NotNull(png);
            File.WriteAllBytes(Path.Combine(Output, name + ".png"), png);
        }

        var samples = Environment.GetEnvironmentVariable("HEAVYMATICA_THUMBNAIL_SAMPLES");
        if (!string.IsNullOrEmpty(samples))
        {
            foreach (var file in Directory.EnumerateFiles(samples).Where(file => Path.GetExtension(file) is ".nbt" or ".schem" or ".litematic" or ""))
            {
                try
                {
                    if (Render(file) is { } png)
                    {
                        File.WriteAllBytes(Path.Combine(Output, Path.GetFileName(file) + ".png"), png);
                    }
                }
                catch (SchematicRejectedException)
                {
                    // Not a schematic; the samples directory may hold other files.
                }
            }
        }
    }

    [Fact]
    public void The_house_renders_the_same_as_before()
    {
        var png = Render(Path.Combine(RepositoryFiles.Conformance, "files", "house.nbt"))!;

        Assert.Equal(KnownHouse, Convert.ToHexStringLower(SHA256.HashData(png)));
    }

    /// <summary>Changes when the rasteriser, the encoder or the colour table changes; look at the new rendering before updating it.</summary>
    private const string KnownHouse = "aed7e5ab8a859fcc100ca5445484504092a24bc8a4f23265ab0b03bc0ed53aa8";

    private static byte[]? Render(string path)
    {
        var model = SchematicReader.Read(File.ReadAllBytes(path));
        var pixels = IsometricRasterizer.Render(model, BlockColors.Embedded, 2_000_000);
        return pixels is null ? null : PngEncoder.Encode(pixels, IsometricRasterizer.Size, IsometricRasterizer.Size);
    }
}
