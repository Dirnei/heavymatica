using System.Buffers.Binary;
using System.IO.Compression;
using System.Text;
using Heavymatica.Cloud.Formats;

namespace Heavymatica.Cloud.Tests.Formats;

/// <summary>Writes NBT for reader tests. Only used to build inputs, so it follows the format literally.</summary>
internal sealed class NbtWriter : IDisposable
{
    private readonly MemoryStream output = new();

    public static byte[] Root(Action<NbtWriter> body, string name = "")
    {
        using var writer = new NbtWriter();
        writer.Byte((byte)NbtType.Compound).Name(name);
        body(writer);
        writer.Byte(0);
        return writer.output.ToArray();
    }

    public static byte[] Gzip(byte[] data)
    {
        using var compressed = new MemoryStream();
        using (var gzip = new GZipStream(compressed, CompressionLevel.SmallestSize, leaveOpen: true))
        {
            gzip.Write(data);
        }

        return compressed.ToArray();
    }

    public void Dispose() => output.Dispose();

    public NbtWriter Tag(NbtType type, string name)
    {
        Byte((byte)type);
        return Name(name);
    }

    public NbtWriter Byte(byte value)
    {
        output.WriteByte(value);
        return this;
    }

    public NbtWriter Short(short value)
    {
        Span<byte> bytes = stackalloc byte[2];
        BinaryPrimitives.WriteInt16BigEndian(bytes, value);
        output.Write(bytes);
        return this;
    }

    public NbtWriter Int(int value)
    {
        Span<byte> bytes = stackalloc byte[4];
        BinaryPrimitives.WriteInt32BigEndian(bytes, value);
        output.Write(bytes);
        return this;
    }

    public NbtWriter Long(long value)
    {
        Span<byte> bytes = stackalloc byte[8];
        BinaryPrimitives.WriteInt64BigEndian(bytes, value);
        output.Write(bytes);
        return this;
    }

    public NbtWriter Float(float value)
    {
        Span<byte> bytes = stackalloc byte[4];
        BinaryPrimitives.WriteSingleBigEndian(bytes, value);
        output.Write(bytes);
        return this;
    }

    public NbtWriter Double(double value)
    {
        Span<byte> bytes = stackalloc byte[8];
        BinaryPrimitives.WriteDoubleBigEndian(bytes, value);
        output.Write(bytes);
        return this;
    }

    public NbtWriter Name(string value)
    {
        var bytes = Encoding.UTF8.GetBytes(value);
        Span<byte> length = stackalloc byte[2];
        BinaryPrimitives.WriteUInt16BigEndian(length, (ushort)bytes.Length);
        output.Write(length);
        output.Write(bytes);
        return this;
    }

    public NbtWriter Raw(byte[] bytes)
    {
        output.Write(bytes);
        return this;
    }
}
