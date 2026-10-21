namespace Heavymatica.Cloud.Formats;

/// <summary>Why a schematic file was rejected. Each code maps to the contract's ProblemCode of the same name.</summary>
public enum RejectionCode
{
    UnsupportedFormat,
    FileTooLarge,
    DecompressedTooLarge,
    DimensionsTooLarge,
    VolumeTooLarge,
    MalformedNbt,
    PaletteIndexOutOfRange,
    SizeMismatch,
}

public static class RejectionCodes
{
    /// <summary>The snake_case wire name used by the contract and the conformance set.</summary>
    public static string ToWireName(this RejectionCode code) => code switch
    {
        RejectionCode.UnsupportedFormat => "unsupported_format",
        RejectionCode.FileTooLarge => "file_too_large",
        RejectionCode.DecompressedTooLarge => "decompressed_too_large",
        RejectionCode.DimensionsTooLarge => "dimensions_too_large",
        RejectionCode.VolumeTooLarge => "volume_too_large",
        RejectionCode.MalformedNbt => "malformed_nbt",
        RejectionCode.PaletteIndexOutOfRange => "palette_index_out_of_range",
        RejectionCode.SizeMismatch => "size_mismatch",
        _ => throw new ArgumentOutOfRangeException(nameof(code), code, null),
    };
}

/// <summary>Thrown while reading when a file breaks a format rule or limit.</summary>
public sealed class SchematicRejectedException : Exception
{
    public SchematicRejectedException(RejectionCode code, string message)
        : base(message)
    {
        Code = code;
    }

    public SchematicRejectedException(RejectionCode code, string message, Exception innerException)
        : base(message, innerException)
    {
        Code = code;
    }

    public SchematicRejectedException()
        : this(RejectionCode.MalformedNbt, "The file is not a valid schematic.")
    {
    }

    public SchematicRejectedException(string message)
        : this(RejectionCode.MalformedNbt, message)
    {
    }

    public SchematicRejectedException(string message, Exception innerException)
        : this(RejectionCode.MalformedNbt, message, innerException)
    {
    }

    public RejectionCode Code { get; }
}
