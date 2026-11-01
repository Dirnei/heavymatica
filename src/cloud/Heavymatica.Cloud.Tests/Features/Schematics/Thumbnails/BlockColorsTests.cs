using System.Text;
using Heavymatica.Cloud.Features.Schematics.Thumbnails;

namespace Heavymatica.Cloud.Tests.Features.Schematics.Thumbnails;

public sealed class BlockColorsTests
{
    private static BlockColors Table(string json) => BlockColors.Parse(new MemoryStream(Encoding.UTF8.GetBytes(json)));

    [Fact]
    public void A_known_block_has_its_colours()
    {
        var table = Table("""{ "minecraft:grass_block": { "top": "#7cbd6b", "side": "#866043", "alpha": 1.0, "shape": "cube", "height": 1.0 } }""");

        Assert.Equal(new BlockColor(0x7CBD6B, 0x866043, 1f, BlockShape.Cube, 1f), table.Of("minecraft:grass_block"));
    }

    [Fact]
    public void An_unknown_block_is_a_neutral_grey_cube()
    {
        var unknown = Table("{}").Of("othermod:machine");

        Assert.Equal(BlockColors.Unknown, unknown);
        Assert.Equal((BlockShape.Cube, 1f, 1f), (unknown.Shape, unknown.Alpha, unknown.Height));
        Assert.Equal(unknown.Top, unknown.Side);
    }

    [Theory]
    [InlineData("cube", "Cube")]
    [InlineData("slab", "Slab")]
    [InlineData("cross", "Cross")]
    [InlineData("none", "None")]
    public void Every_shape_is_read(string name, string expected)
    {
        var shape = Enum.Parse<BlockShape>(expected);
        var table = Table($$"""{ "minecraft:x": { "top": "#010203", "side": "#040506", "alpha": 0.5, "shape": "{{name}}", "height": 0.5 } }""");

        Assert.Equal(shape, table.Of("minecraft:x").Shape);
    }

    [Fact]
    public void Only_an_opaque_cube_hides_its_neighbours_faces()
    {
        Assert.True(new BlockColor(0, 0, 1f, BlockShape.Cube, 1f).IsOpaqueCube);
        Assert.False(new BlockColor(0, 0, 0.35f, BlockShape.Cube, 1f).IsOpaqueCube);
        Assert.False(new BlockColor(0, 0, 1f, BlockShape.Slab, 0.5f).IsOpaqueCube);
    }

    [Fact]
    public void A_malformed_colour_is_refused()
    {
        Assert.Throws<InvalidDataException>(() => Table("""{ "minecraft:x": { "top": "red", "side": "#000000" } }"""));
    }

    [Fact]
    public void The_embedded_table_loads()
    {
        Assert.True(BlockColors.Embedded.Count > 0);
        Assert.Equal(BlockShape.Cube, BlockColors.Embedded.Of("minecraft:stone").Shape);
    }
}
