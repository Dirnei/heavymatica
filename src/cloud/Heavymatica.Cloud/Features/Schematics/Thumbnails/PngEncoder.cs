using System.Buffers.Binary;
using System.IO.Compression;

namespace Heavymatica.Cloud.Features.Schematics.Thumbnails;

/// <summary>Writes 8-bit RGBA images as PNG: one IDAT, the Sub filter on every row.</summary>
internal static class PngEncoder
{
    private static readonly byte[] Signature = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];

    private static readonly uint[] CrcTable = BuildCrcTable();

    /// <param name="rgba">Rows top to bottom, 4 bytes per pixel, not premultiplied.</param>
    public static byte[] Encode(ReadOnlySpan<byte> rgba, int width, int height)
    {
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(width);
        ArgumentOutOfRangeException.ThrowIfNegativeOrZero(height);
        var stride = width * 4;
        if (rgba.Length != stride * height)
        {
            throw new ArgumentException("The pixel buffer does not match the size.", nameof(rgba));
        }

        using var png = new MemoryStream();
        png.Write(Signature);

        Span<byte> header = stackalloc byte[13];
        BinaryPrimitives.WriteInt32BigEndian(header, width);
        BinaryPrimitives.WriteInt32BigEndian(header[4..], height);
        header[8] = 8; // bit depth
        header[9] = 6; // colour type RGBA
        WriteChunk(png, "IHDR"u8, header);

        using (var compressed = new MemoryStream())
        {
            using (var zlib = new ZLibStream(compressed, CompressionLevel.SmallestSize, leaveOpen: true))
            {
                var row = new byte[stride + 1];
                row[0] = 1; // Sub
                for (var y = 0; y < height; y++)
                {
                    var source = rgba.Slice(y * stride, stride);
                    for (var i = 0; i < stride; i++)
                    {
                        row[i + 1] = (byte)(source[i] - (i >= 4 ? source[i - 4] : 0));
                    }

                    zlib.Write(row);
                }
            }

            WriteChunk(png, "IDAT"u8, compressed.GetBuffer().AsSpan(0, (int)compressed.Length));
        }

        WriteChunk(png, "IEND"u8, []);
        return png.ToArray();
    }

    private static void WriteChunk(Stream png, ReadOnlySpan<byte> type, ReadOnlySpan<byte> data)
    {
        Span<byte> length = stackalloc byte[4];
        BinaryPrimitives.WriteInt32BigEndian(length, data.Length);
        png.Write(length);
        png.Write(type);
        png.Write(data);
        var crc = Crc32(Crc32(0xFFFFFFFFu, type), data) ^ 0xFFFFFFFFu;
        Span<byte> checksum = stackalloc byte[4];
        BinaryPrimitives.WriteUInt32BigEndian(checksum, crc);
        png.Write(checksum);
    }

    private static uint Crc32(uint crc, ReadOnlySpan<byte> bytes)
    {
        foreach (var value in bytes)
        {
            crc = CrcTable[(crc ^ value) & 0xFF] ^ (crc >> 8);
        }

        return crc;
    }

    private static uint[] BuildCrcTable()
    {
        var table = new uint[256];
        for (uint n = 0; n < 256; n++)
        {
            var c = n;
            for (var k = 0; k < 8; k++)
            {
                c = (c & 1) != 0 ? 0xEDB88320u ^ (c >> 1) : c >> 1;
            }

            table[n] = c;
        }

        return table;
    }
}
