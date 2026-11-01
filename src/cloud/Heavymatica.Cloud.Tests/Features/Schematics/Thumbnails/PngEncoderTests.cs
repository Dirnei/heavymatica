using System.Buffers.Binary;
using System.IO.Compression;
using Heavymatica.Cloud.Features.Schematics.Thumbnails;

namespace Heavymatica.Cloud.Tests.Features.Schematics.Thumbnails;

public sealed class PngEncoderTests
{
    [Fact]
    public void The_output_is_a_png_the_attach_check_accepts()
    {
        var png = PngEncoder.Encode(new byte[256 * 256 * 4], 256, 256);

        Assert.Equal((256, 256), AttachThumbnail.Dimensions(png));
    }

    [Fact]
    public void It_decodes_to_the_same_pixels_with_valid_checksums()
    {
        var pixels = new byte[7 * 5 * 4];
        new Random(42).NextBytes(pixels);

        var decoded = TestPng.Decode(PngEncoder.Encode(pixels, 7, 5));

        Assert.Equal((7, 5), (decoded.Width, decoded.Height));
        Assert.Equal(pixels, decoded.Rgba);
    }

    [Fact]
    public void A_fully_transparent_thumbnail_stays_under_2_KiB()
    {
        Assert.True(PngEncoder.Encode(new byte[256 * 256 * 4], 256, 256).Length < 2048);
    }

    [Fact]
    public void A_buffer_of_the_wrong_size_is_refused()
    {
        Assert.Throws<ArgumentException>(() => PngEncoder.Encode(new byte[10], 2, 2));
    }
}

/// <summary>A minimal PNG reader for 8-bit RGBA images with filter types None and Sub; it checks every chunk's CRC.</summary>
internal static class TestPng
{
    public static (int Width, int Height, byte[] Rgba) Decode(byte[] png)
    {
        ArgumentNullException.ThrowIfNull(png);
        var position = 8;
        int width = 0, height = 0;
        using var idat = new MemoryStream();
        while (position < png.Length)
        {
            var length = BinaryPrimitives.ReadInt32BigEndian(png.AsSpan(position));
            var type = System.Text.Encoding.ASCII.GetString(png, position + 4, 4);
            var data = png.AsSpan(position + 8, length);
            var crc = BinaryPrimitives.ReadUInt32BigEndian(png.AsSpan(position + 8 + length));
            Assert.Equal(BitwiseCrc(png.AsSpan(position + 4, length + 4)), crc);
            switch (type)
            {
                case "IHDR":
                    width = BinaryPrimitives.ReadInt32BigEndian(data);
                    height = BinaryPrimitives.ReadInt32BigEndian(data[4..]);
                    Assert.Equal(8, data[8]);
                    Assert.Equal(6, data[9]);
                    break;
                case "IDAT":
                    idat.Write(data);
                    break;
            }

            position += 12 + length;
        }

        idat.Position = 0;
        using var inflated = new MemoryStream();
        using (var zlib = new ZLibStream(idat, CompressionMode.Decompress))
        {
            zlib.CopyTo(inflated);
        }

        var raw = inflated.ToArray();
        var stride = width * 4;
        var rgba = new byte[stride * height];
        for (var y = 0; y < height; y++)
        {
            var filter = raw[y * (stride + 1)];
            for (var i = 0; i < stride; i++)
            {
                var value = raw[(y * (stride + 1)) + 1 + i];
                var left = i >= 4 ? rgba[(y * stride) + i - 4] : 0;
                rgba[(y * stride) + i] = filter switch
                {
                    0 => value,
                    1 => (byte)(value + left),
                    _ => throw new NotSupportedException("Filter " + filter),
                };
            }
        }

        return (width, height, rgba);
    }

    private static uint BitwiseCrc(ReadOnlySpan<byte> bytes)
    {
        var crc = 0xFFFFFFFFu;
        foreach (var value in bytes)
        {
            crc ^= value;
            for (var bit = 0; bit < 8; bit++)
            {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB88320u : crc >> 1;
            }
        }

        return ~crc;
    }
}
