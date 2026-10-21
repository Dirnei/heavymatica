using System.Security.Cryptography;
using System.Text;
using Heavymatica.Cloud.Formats;

namespace Heavymatica.Cloud.Tests.Formats;

public sealed class SchematicModelTests
{
    private const string StairsNorth = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";
    private const string StairsEast = "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]";

    [Fact]
    public void Stairs_facing_different_ways_count_as_one_block_and_air_is_not_counted()
    {
        // 4 north, 6 east, 10 air.
        var blocks = Enumerable.Repeat(0, 4).Concat(Enumerable.Repeat(1, 6)).Concat(Enumerable.Repeat(2, 10)).ToArray();
        var model = Model([StairsNorth, StairsEast, BlockStates.Air], blocks, 10, 1, 2);

        var counts = model.BlockCounts();

        var count = Assert.Single(counts);
        Assert.Equal("minecraft:oak_stairs", count.Key);
        Assert.Equal(10, count.Value);
    }

    [Fact]
    public void Every_air_variant_and_structure_void_are_excluded_from_counts()
    {
        var palette = new[] { BlockStates.Air, "minecraft:cave_air", "minecraft:void_air", BlockStates.StructureVoid, "minecraft:stone" };
        var model = Model(palette, [0, 1, 2, 3, 4, 4], 6, 1, 1);

        Assert.Equal([new KeyValuePair<string, long>("minecraft:stone", 2)], model.BlockCounts());
    }

    [Fact]
    public void Counts_are_most_common_first()
    {
        var model = Model(["minecraft:glass", "minecraft:stone"], [0, 1, 1, 1], 4, 1, 1);

        Assert.Equal(["minecraft:stone", "minecraft:glass"], model.BlockCounts().Select(count => count.Key));
    }

    [Fact]
    public void Palette_digest_hashes_each_position_with_a_newline_in_canonical_order()
    {
        // Two palette entries in an order that differs from position order, so the digest depends on the positions.
        var model = Model(["minecraft:b", "minecraft:a"], [1, 0, 0], 3, 1, 1);
        var expected = Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes("minecraft:a\nminecraft:b\nminecraft:b\n")));

        Assert.Equal(expected, model.PaletteDigest());
    }

    [Fact]
    public void Index_runs_x_fastest_then_z_then_y()
    {
        Assert.Equal(0, SchematicModel.Index(0, 0, 0, 3, 4));
        Assert.Equal(1, SchematicModel.Index(1, 0, 0, 3, 4));
        Assert.Equal(3, SchematicModel.Index(0, 0, 1, 3, 4));
        Assert.Equal(12, SchematicModel.Index(0, 1, 0, 3, 4));
    }

    [Theory]
    [InlineData("minecraft:stone", "minecraft:stone")]
    [InlineData("stone", "minecraft:stone")]
    [InlineData("minecraft:oak_stairs[waterlogged=false,facing=north]", "minecraft:oak_stairs[facing=north,waterlogged=false]")]
    [InlineData("servus_foundation:utility_duct[north=true]", "servus_foundation:utility_duct[north=true]")]
    public void Block_states_are_canonical(string input, string canonical)
    {
        Assert.Equal(canonical, BlockStates.Canonical(input));
    }

    private static SchematicModel Model(string[] palette, int[] blocks, int width, int height, int length) =>
        new(SchematicFormat.Sponge, width, height, length, palette, blocks, new Dictionary<BlockPosition, NbtCompound>(), null, 0);
}
