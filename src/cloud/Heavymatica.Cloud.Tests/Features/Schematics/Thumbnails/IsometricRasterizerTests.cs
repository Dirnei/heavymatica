using System.Diagnostics;
using System.Text;
using Heavymatica.Cloud.Features.Schematics.Thumbnails;
using Heavymatica.Cloud.Formats;

namespace Heavymatica.Cloud.Tests.Features.Schematics.Thumbnails;

public sealed class IsometricRasterizerTests
{
    private static readonly BlockColors Colors = BlockColors.Parse(new MemoryStream(Encoding.UTF8.GetBytes("""
        {
          "minecraft:stone": { "top": "#808080", "side": "#808080", "alpha": 1.0, "shape": "cube", "height": 1.0 },
          "minecraft:oak_slab": { "top": "#a0a000", "side": "#a0a000", "alpha": 1.0, "shape": "slab", "height": 0.5 },
          "minecraft:barrier": { "top": "#000000", "side": "#000000", "alpha": 0.0, "shape": "none", "height": 1.0 }
        }
        """)));

    [Fact]
    public void A_single_block_shows_three_differently_shaded_faces_in_the_middle()
    {
        var pixels = Render(Model(1, 1, 1, ["minecraft:stone"], [0]))!;

        var opaque = OpaquePixels(pixels).ToList();
        Assert.Equal(3, opaque.Select(pixel => pixel.Rgb).Distinct().Count());
        Assert.Contains(0x808080, opaque.Select(pixel => pixel.Rgb));
        Assert.InRange(opaque.Average(pixel => pixel.X), 125, 131);
        Assert.InRange(opaque.Average(pixel => pixel.Y), 125, 131);
        Assert.InRange(opaque.Min(pixel => pixel.Y), IsometricRasterizer.Margin - 1, IsometricRasterizer.Margin + 1);
        Assert.InRange(opaque.Max(pixel => pixel.Y), IsometricRasterizer.Size - IsometricRasterizer.Margin - 2, IsometricRasterizer.Size - IsometricRasterizer.Margin);
    }

    [Fact]
    public void A_block_hidden_on_all_visible_sides_draws_nothing()
    {
        var full = Enumerable.Repeat(0, 27).ToArray();
        var hollow = full.ToArray();
        hollow[SchematicModel.Index(1, 1, 1, 3, 3)] = 1;
        string[] palette = ["minecraft:stone", "minecraft:air"];

        Assert.Equal(Render(Model(3, 3, 3, palette, hollow)), Render(Model(3, 3, 3, palette, full)));
    }

    [Fact]
    public void A_bottom_slab_is_lower_than_a_full_block()
    {
        var cubeTop = OpaquePixels(Render(Model(1, 1, 1, ["minecraft:stone"], [0]))!).Min(pixel => pixel.Y);
        var slabTop = OpaquePixels(Render(Model(1, 1, 1, ["minecraft:oak_slab[type=bottom,waterlogged=false]"], [0]))!).Min(pixel => pixel.Y);
        var topSlabTop = OpaquePixels(Render(Model(1, 1, 1, ["minecraft:oak_slab[type=top,waterlogged=false]"], [0]))!).Min(pixel => pixel.Y);

        Assert.True(slabTop > cubeTop + 20, $"slab top {slabTop}, cube top {cubeTop}");
        Assert.Equal(cubeTop, topSlabTop);
    }

    [Fact]
    public void Only_air_and_undrawn_blocks_leave_nothing_to_draw()
    {
        Assert.Null(Render(Model(2, 1, 1, ["minecraft:air", "minecraft:structure_void"], [0, 1])));
        Assert.Null(Render(Model(1, 1, 1, ["minecraft:barrier"], [0])));
    }

    [Fact]
    public void An_unknown_block_is_drawn_in_grey()
    {
        var opaque = OpaquePixels(Render(Model(1, 1, 1, ["othermod:machine"], [0]))!).ToList();

        Assert.Contains(BlockColors.Unknown.Top, opaque.Select(pixel => pixel.Rgb));
    }

    [Fact]
    public void A_huge_model_renders_within_the_block_budget()
    {
        var model = Model(1024, 16, 1024, ["minecraft:stone"], new int[1024 * 16 * 1024]);
        var watch = Stopwatch.StartNew();

        var pixels = IsometricRasterizer.Render(model, Colors, 2_000_000);

        Assert.NotNull(pixels);
        Assert.True(watch.Elapsed < TimeSpan.FromSeconds(30), $"took {watch.Elapsed}");
    }

    private static byte[]? Render(SchematicModel model) => IsometricRasterizer.Render(model, Colors, 2_000_000);

    private static SchematicModel Model(int width, int height, int length, string[] palette, int[] blocks) =>
        new(SchematicFormat.Sponge, width, height, length, palette, blocks, new Dictionary<BlockPosition, NbtCompound>(), null, 0);

    private static IEnumerable<(int X, int Y, int Rgb)> OpaquePixels(byte[] pixels)
    {
        for (var y = 0; y < IsometricRasterizer.Size; y++)
        {
            for (var x = 0; x < IsometricRasterizer.Size; x++)
            {
                var at = ((y * IsometricRasterizer.Size) + x) * 4;
                if (pixels[at + 3] == 255)
                {
                    yield return (x, y, (pixels[at] << 16) | (pixels[at + 1] << 8) | pixels[at + 2]);
                }
            }
        }
    }
}
