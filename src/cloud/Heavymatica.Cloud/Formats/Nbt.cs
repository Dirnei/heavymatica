using System.Buffers.Binary;
using System.Text;

// NBT's tag names (Int, String, Long, ...) are fixed by the format; naming them otherwise would obscure it.
#pragma warning disable CA1720

namespace Heavymatica.Cloud.Formats;

/// <summary>An NBT value. Only the shapes schematic readers need are modelled.</summary>
public abstract record NbtTag;

public sealed record NbtNumber(long Value, NbtType Type) : NbtTag;

public sealed record NbtFloating(double Value) : NbtTag;

public sealed record NbtString(string Value) : NbtTag;

public sealed record NbtByteArray(byte[] Value) : NbtTag;

public sealed record NbtIntArray(int[] Value) : NbtTag;

public sealed record NbtLongArray(long[] Value) : NbtTag;

public sealed record NbtList(NbtType ItemType, IReadOnlyList<NbtTag> Items) : NbtTag;

/// <summary>A compound; keeps the entries in file order, which Litematica region merging depends on.</summary>
public sealed record NbtCompound(IReadOnlyList<KeyValuePair<string, NbtTag>> Entries) : NbtTag
{
    private readonly Dictionary<string, NbtTag> byName = Index(Entries);

    public NbtTag? this[string name] => byName.GetValueOrDefault(name);

    public bool Contains(string name) => byName.ContainsKey(name);

    public NbtCompound? Compound(string name) => this[name] as NbtCompound;

    public NbtList? List(string name) => this[name] as NbtList;

    public int? Int(string name) => this[name] is NbtNumber number ? checked((int)number.Value) : null;

    public string? String(string name) => this[name] is NbtString text ? text.Value : null;

    private static Dictionary<string, NbtTag> Index(IReadOnlyList<KeyValuePair<string, NbtTag>> entries)
    {
        var index = new Dictionary<string, NbtTag>(entries.Count, StringComparer.Ordinal);
        foreach (var (name, tag) in entries)
        {
            index[name] = tag;
        }

        return index;
    }
}

public enum NbtType : byte
{
    End = 0,
    Byte = 1,
    Short = 2,
    Int = 3,
    Long = 4,
    Float = 5,
    Double = 6,
    ByteArray = 7,
    String = 8,
    List = 9,
    Compound = 10,
    IntArray = 11,
    LongArray = 12,
}

/// <summary>
/// Reads big-endian (Java edition) NBT. Any structural problem, including a truncated stream, a negative length
/// or nesting deeper than 512 levels, is a <see cref="RejectionCode.MalformedNbt"/> rejection. Lengths are checked
/// against what the stream can still deliver through the caller's size-limited stream.
/// </summary>
public sealed class NbtReader
{
    public const int MaxDepth = 512;

    private readonly Stream input;
    private readonly byte[] buffer = new byte[8];

    private NbtReader(Stream input)
    {
        this.input = input;
    }

    /// <summary>Reads a named root compound and returns it (the root name is ignored).</summary>
    public static NbtCompound ReadRoot(Stream input)
    {
        var reader = new NbtReader(input);
        var type = (NbtType)reader.ReadByte();
        if (type != NbtType.Compound)
        {
            throw Malformed("The root tag is not a compound.");
        }

        reader.ReadString();
        return (NbtCompound)reader.ReadPayload(type, 0);
    }

    private NbtTag ReadPayload(NbtType type, int depth)
    {
        if (depth > MaxDepth)
        {
            throw Malformed($"NBT is nested deeper than {MaxDepth} levels.");
        }

        return type switch
        {
            NbtType.Byte => new NbtNumber((sbyte)ReadByte(), type),
            NbtType.Short => new NbtNumber(BinaryPrimitives.ReadInt16BigEndian(Fill(2)), type),
            NbtType.Int => new NbtNumber(ReadInt(), type),
            NbtType.Long => new NbtNumber(BinaryPrimitives.ReadInt64BigEndian(Fill(8)), type),
            NbtType.Float => new NbtFloating(BinaryPrimitives.ReadSingleBigEndian(Fill(4))),
            NbtType.Double => new NbtFloating(BinaryPrimitives.ReadDoubleBigEndian(Fill(8))),
            NbtType.ByteArray => new NbtByteArray(ReadBytes(ReadLength(1))),
            NbtType.String => new NbtString(ReadString()),
            NbtType.List => ReadList(depth),
            NbtType.Compound => ReadCompound(depth),
            NbtType.IntArray => new NbtIntArray(ReadInts(ReadLength(4))),
            NbtType.LongArray => new NbtLongArray(ReadLongs(ReadLength(8))),
            _ => throw Malformed($"Unknown NBT tag type {(byte)type}."),
        };
    }

    private NbtList ReadList(int depth)
    {
        var itemType = (NbtType)ReadByte();
        var count = ReadInt();
        if (count < 0)
        {
            throw Malformed("Negative list length.");
        }

        if (itemType == NbtType.End && count > 0)
        {
            throw Malformed("A non-empty list has no item type.");
        }

        var items = new List<NbtTag>(Math.Min(count, 4096));
        for (var i = 0; i < count; i++)
        {
            items.Add(ReadPayload(itemType, depth + 1));
        }

        return new NbtList(itemType, items);
    }

    private NbtCompound ReadCompound(int depth)
    {
        var entries = new List<KeyValuePair<string, NbtTag>>();
        while (true)
        {
            var type = (NbtType)ReadByte();
            if (type == NbtType.End)
            {
                return new NbtCompound(entries);
            }

            var name = ReadString();
            entries.Add(new(name, ReadPayload(type, depth + 1)));
        }
    }

    private int ReadLength(int elementSize)
    {
        var length = ReadInt();
        if (length < 0)
        {
            throw Malformed("Negative array length.");
        }

        // A length the size limit could never deliver is malformed; the limited stream catches the rest.
        if ((long)length * elementSize > SchematicLimits.MaxDecompressedBytes)
        {
            throw new SchematicRejectedException(RejectionCode.DecompressedTooLarge, "An array is larger than the decompression limit.");
        }

        return length;
    }

    private string ReadString()
    {
        var length = BinaryPrimitives.ReadUInt16BigEndian(Fill(2));
        var bytes = ReadBytes(length);
        // Java's modified UTF-8 only differs for NUL and supplementary characters, which block IDs never contain.
        return Encoding.UTF8.GetString(bytes);
    }

    private int ReadInt() => BinaryPrimitives.ReadInt32BigEndian(Fill(4));

    private byte ReadByte()
    {
        var value = input.ReadByte();
        return value < 0 ? throw Malformed("The NBT ends in the middle of a tag.") : (byte)value;
    }

    private ReadOnlySpan<byte> Fill(int count)
    {
        ReadExactly(buffer.AsSpan(0, count));
        return buffer.AsSpan(0, count);
    }

    private byte[] ReadBytes(int count)
    {
        if (count <= ChunkSize)
        {
            var bytes = new byte[count];
            ReadExactly(bytes);
            return bytes;
        }

        // Grow with the data actually received, so a tiny file cannot claim a huge array and force the allocation.
        using var collected = new MemoryStream(ChunkSize);
        var chunk = new byte[ChunkSize];
        for (var remaining = count; remaining > 0; remaining -= ChunkSize)
        {
            var part = chunk.AsSpan(0, Math.Min(ChunkSize, remaining));
            ReadExactly(part);
            collected.Write(part);
        }

        return collected.ToArray();
    }

    private const int ChunkSize = 1024 * 1024;

    private int[] ReadInts(int count)
    {
        var bytes = ReadBytes(checked(count * 4));
        var values = new int[count];
        for (var i = 0; i < count; i++)
        {
            values[i] = BinaryPrimitives.ReadInt32BigEndian(bytes.AsSpan(i * 4));
        }

        return values;
    }

    private long[] ReadLongs(int count)
    {
        var bytes = ReadBytes(checked(count * 8));
        var values = new long[count];
        for (var i = 0; i < count; i++)
        {
            values[i] = BinaryPrimitives.ReadInt64BigEndian(bytes.AsSpan(i * 8));
        }

        return values;
    }

    private void ReadExactly(Span<byte> target)
    {
        try
        {
            input.ReadExactly(target);
        }
        catch (EndOfStreamException e)
        {
            throw Malformed("The NBT ends in the middle of a tag.", e);
        }
        catch (InvalidDataException e)
        {
            throw Malformed("The compressed data is corrupt.", e);
        }
    }

    private static SchematicRejectedException Malformed(string message, Exception? inner = null) =>
        inner is null
            ? new SchematicRejectedException(RejectionCode.MalformedNbt, message)
            : new SchematicRejectedException(RejectionCode.MalformedNbt, message, inner);
}
