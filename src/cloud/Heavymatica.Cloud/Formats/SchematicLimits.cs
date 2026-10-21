namespace Heavymatica.Cloud.Formats;

/// <summary>Size limits from the schematic-formats spec.</summary>
public static class SchematicLimits
{
    public const long MaxFileBytes = 16L * 1024 * 1024;
    public const long MaxDecompressedBytes = 128L * 1024 * 1024;
    public const int MaxDimension = 1024;
    public const long MaxVolume = 16_777_216;

    /// <summary>Rejects dimensions before any block data is decoded.</summary>
    public static void CheckDimensions(int width, int height, int length)
    {
        if (width <= 0 || height <= 0 || length <= 0)
        {
            throw new SchematicRejectedException(RejectionCode.MalformedNbt, $"Invalid dimensions {width} x {height} x {length}.");
        }

        if (width > MaxDimension || height > MaxDimension || length > MaxDimension)
        {
            throw new SchematicRejectedException(RejectionCode.DimensionsTooLarge,
                $"Dimensions {width} x {height} x {length} exceed {MaxDimension} blocks.");
        }

        if ((long)width * height * length > MaxVolume)
        {
            throw new SchematicRejectedException(RejectionCode.VolumeTooLarge,
                $"Volume {(long)width * height * length} exceeds {MaxVolume} blocks.");
        }
    }
}

/// <summary>Passes reads through until <paramref name="limit"/> bytes, then rejects the file as too large.</summary>
internal sealed class LimitedReadStream(Stream inner, long limit, RejectionCode code) : Stream
{
    private long total;

    public override bool CanRead => true;

    public override bool CanSeek => false;

    public override bool CanWrite => false;

    public override long Length => throw new NotSupportedException();

    public override long Position
    {
        get => total;
        set => throw new NotSupportedException();
    }

    public override int Read(byte[] buffer, int offset, int count) => Read(buffer.AsSpan(offset, count));

    public override int Read(Span<byte> buffer)
    {
        var read = inner.Read(buffer);
        total += read;
        if (total > limit)
        {
            throw new SchematicRejectedException(code, $"More than {limit} bytes.");
        }

        return read;
    }

    public override void Flush()
    {
    }

    public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();

    public override void SetLength(long value) => throw new NotSupportedException();

    public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            inner.Dispose();
        }

        base.Dispose(disposing);
    }
}
