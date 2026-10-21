using Heavymatica.Cloud.Formats;

namespace Heavymatica.Cloud.Tests.Formats;

public sealed class NbtReaderTests
{
    [Fact]
    public void Reads_every_tag_type()
    {
        var bytes = NbtWriter.Root(w =>
        {
            w.Tag(NbtType.Byte, "byte").Byte(0xFE);
            w.Tag(NbtType.Short, "short").Short(-2);
            w.Tag(NbtType.Int, "int").Int(123_456);
            w.Tag(NbtType.Long, "long").Long(-9_000_000_000);
            w.Tag(NbtType.Float, "float").Float(1.5f);
            w.Tag(NbtType.Double, "double").Double(-2.25);
            w.Tag(NbtType.ByteArray, "bytes").Int(3).Raw([1, 2, 3]);
            w.Tag(NbtType.String, "string").Name("minecraft:stone");
            w.Tag(NbtType.List, "list").Byte((byte)NbtType.Int).Int(2).Int(7).Int(8);
            w.Tag(NbtType.List, "empty").Byte((byte)NbtType.End).Int(0);
            w.Tag(NbtType.Compound, "nested").Tag(NbtType.Int, "x").Int(5).Byte(0);
            w.Tag(NbtType.IntArray, "ints").Int(2).Int(-1).Int(1);
            w.Tag(NbtType.LongArray, "longs").Int(1).Long(long.MinValue);
        });

        var root = NbtReader.ReadRoot(new MemoryStream(bytes));

        Assert.Equal(-2, ((NbtNumber)root["byte"]!).Value);
        Assert.Equal(-2, ((NbtNumber)root["short"]!).Value);
        Assert.Equal(123_456, root.Int("int"));
        Assert.Equal(-9_000_000_000, ((NbtNumber)root["long"]!).Value);
        Assert.Equal(1.5, ((NbtFloating)root["float"]!).Value);
        Assert.Equal(-2.25, ((NbtFloating)root["double"]!).Value);
        Assert.Equal([1, 2, 3], ((NbtByteArray)root["bytes"]!).Value);
        Assert.Equal("minecraft:stone", root.String("string"));
        Assert.Equal([7L, 8L], root.List("list")!.Items.Select(item => ((NbtNumber)item).Value));
        Assert.Empty(root.List("empty")!.Items);
        Assert.Equal(5, root.Compound("nested")!.Int("x"));
        Assert.Equal([-1, 1], ((NbtIntArray)root["ints"]!).Value);
        Assert.Equal([long.MinValue], ((NbtLongArray)root["longs"]!).Value);
    }

    [Fact]
    public void Keeps_compound_entries_in_file_order()
    {
        var bytes = NbtWriter.Root(w =>
        {
            w.Tag(NbtType.Int, "zeta").Int(1);
            w.Tag(NbtType.Int, "alpha").Int(2);
            w.Tag(NbtType.Int, "mid").Int(3);
        });

        var root = NbtReader.ReadRoot(new MemoryStream(bytes));

        Assert.Equal(["zeta", "alpha", "mid"], root.Entries.Select(entry => entry.Key));
    }

    [Fact]
    public void Truncated_input_is_malformed()
    {
        var bytes = NbtWriter.Root(w => w.Tag(NbtType.String, "name").Name("minecraft:oak_planks"));

        var error = Assert.Throws<SchematicRejectedException>(() => NbtReader.ReadRoot(new MemoryStream(bytes[..^6])));

        Assert.Equal(RejectionCode.MalformedNbt, error.Code);
    }

    [Fact]
    public void Negative_lengths_are_malformed()
    {
        var bytes = NbtWriter.Root(w => w.Tag(NbtType.IntArray, "ints").Int(-4));

        var error = Assert.Throws<SchematicRejectedException>(() => NbtReader.ReadRoot(new MemoryStream(bytes)));

        Assert.Equal(RejectionCode.MalformedNbt, error.Code);
    }

    [Fact]
    public void A_root_that_is_not_a_compound_is_malformed()
    {
        var error = Assert.Throws<SchematicRejectedException>(() => NbtReader.ReadRoot(new MemoryStream([(byte)NbtType.Int, 0, 0, 0, 0, 0, 1])));

        Assert.Equal(RejectionCode.MalformedNbt, error.Code);
    }

    [Fact]
    public void Nesting_deeper_than_512_levels_is_malformed()
    {
        var bytes = NbtWriter.Root(w =>
        {
            for (var i = 0; i < 600; i++)
            {
                w.Tag(NbtType.Compound, "c");
            }

            for (var i = 0; i < 600; i++)
            {
                w.Byte(0);
            }
        });

        var error = Assert.Throws<SchematicRejectedException>(() => NbtReader.ReadRoot(new MemoryStream(bytes)));

        Assert.Equal(RejectionCode.MalformedNbt, error.Code);
    }

    [Fact]
    public void An_array_longer_than_the_decompression_limit_is_rejected_before_reading_it()
    {
        var bytes = NbtWriter.Root(w => w.Tag(NbtType.ByteArray, "bomb").Int(200 * 1024 * 1024));

        var error = Assert.Throws<SchematicRejectedException>(() => NbtReader.ReadRoot(new MemoryStream(bytes)));

        Assert.Equal(RejectionCode.DecompressedTooLarge, error.Code);
    }

    [Fact]
    public void Decompression_stops_at_128_MiB()
    {
        // Many 1 MiB arrays: each one is allowed, together they pass the limit.
        const int arrays = 130;
        using var raw = new MemoryStream();
        using (var gzip = new System.IO.Compression.GZipStream(raw, System.IO.Compression.CompressionLevel.Fastest, leaveOpen: true))
        {
            gzip.Write(NbtWriter.Root(w =>
            {
                for (var i = 0; i < arrays; i++)
                {
                    w.Tag(NbtType.ByteArray, "a" + i).Int(1024 * 1024).Raw(new byte[1024 * 1024]);
                }
            }));
        }

        var error = Assert.Throws<SchematicRejectedException>(() => SchematicReader.Read(raw.ToArray()));

        Assert.Equal(RejectionCode.DecompressedTooLarge, error.Code);
    }
}
