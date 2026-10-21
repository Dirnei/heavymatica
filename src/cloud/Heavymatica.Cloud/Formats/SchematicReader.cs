using System.IO.Compression;

namespace Heavymatica.Cloud.Formats;

public static class SchematicReader
{
    private static readonly byte[] GzipMagic = [0x1f, 0x8b];

    /// <summary>Reads a complete file. The format is detected from the content, never from a file name.</summary>
    public static SchematicModel Read(ReadOnlyMemory<byte> file)
    {
        if (file.Length > SchematicLimits.MaxFileBytes)
        {
            throw new SchematicRejectedException(RejectionCode.FileTooLarge, $"The file is larger than {SchematicLimits.MaxFileBytes} bytes.");
        }

        var root = ReadNbt(file);
        return Detect(root) switch
        {
            SchematicFormat.Litematica => LitematicaFormat.Read(root),
            SchematicFormat.Sponge => SpongeFormat.Read(root),
            SchematicFormat.Structure => StructureFormat.Read(root),
            _ => throw new InvalidOperationException(),
        };
    }

    /// <summary>Reads a file from disk, rejecting it on size before reading its content.</summary>
    public static SchematicModel Read(string path)
    {
        if (new FileInfo(path).Length > SchematicLimits.MaxFileBytes)
        {
            throw new SchematicRejectedException(RejectionCode.FileTooLarge, $"The file is larger than {SchematicLimits.MaxFileBytes} bytes.");
        }

        return Read(File.ReadAllBytes(path));
    }

    private static NbtCompound ReadNbt(ReadOnlyMemory<byte> file)
    {
        var span = file.Span;
        Stream raw = new MemoryStream(file.ToArray(), writable: false);
        if (span.Length >= 2 && span[..2].SequenceEqual(GzipMagic))
        {
            raw = new GZipStream(raw, CompressionMode.Decompress);
        }
        else if (span.Length == 0 || span[0] != (byte)NbtType.Compound)
        {
            throw new SchematicRejectedException(RejectionCode.UnsupportedFormat, "The file is neither gzip-compressed nor NBT.");
        }

        using var limited = new LimitedReadStream(raw, SchematicLimits.MaxDecompressedBytes, RejectionCode.DecompressedTooLarge);
        using var buffered = new BufferedStream(limited, 64 * 1024);
        try
        {
            return NbtReader.ReadRoot(buffered);
        }
        catch (InvalidDataException e)
        {
            throw new SchematicRejectedException(RejectionCode.MalformedNbt, "The compressed data is corrupt.", e);
        }
    }

    internal static SchematicFormat Detect(NbtCompound root)
    {
        if (root.Compound("Regions") is not null && (root.Contains("MinecraftDataVersion") || root.Contains("Version")))
        {
            return SchematicFormat.Litematica;
        }

        if (root.Compound("Schematic") is { } v3 && v3.Int("Version") == 3)
        {
            return SchematicFormat.Sponge;
        }

        if (root.Int("Version") == 2 && root.Contains("BlockData"))
        {
            return SchematicFormat.Sponge;
        }

        if (root.List("size") is not null && root.List("blocks") is not null && (root.List("palette") is not null || root.List("palettes") is not null))
        {
            return SchematicFormat.Structure;
        }

        throw new SchematicRejectedException(RejectionCode.UnsupportedFormat, "The NBT is not a Litematica, Sponge or structure file.");
    }

    internal static SchematicRejectedException Malformed(string message) => new(RejectionCode.MalformedNbt, message);

    internal static int[] IntTriple(NbtTag? tag, string what)
    {
        var values = tag switch
        {
            NbtIntArray array => array.Value,
            NbtList list when list.Items.All(item => item is NbtNumber) => list.Items.Select(item => checked((int)((NbtNumber)item).Value)).ToArray(),
            _ => null,
        };
        return values is { Length: 3 } ? values : throw Malformed($"{what} is not three integers.");
    }

    /// <summary>A block entity in canonical form: its data with the block entity ID under <c>id</c>.</summary>
    internal static NbtCompound BlockEntity(string id, IEnumerable<KeyValuePair<string, NbtTag>> data)
    {
        var entries = new List<KeyValuePair<string, NbtTag>> { new("id", new NbtString(id)) };
        entries.AddRange(data.Where(pair => pair.Key is not ("id" or "Id" or "Pos" or "x" or "y" or "z")));
        return new NbtCompound(entries);
    }
}
