using System.Text.Json;
using Heavymatica.Cloud.Contract;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Infrastructure.Contract;

/// <summary>Error answers as the contract's problem details (RFC 7807 with a stable reason code).</summary>
internal static class Problems
{
    public const string ContentType = "application/problem+json";

    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    public static Problem Of(int status, ProblemCode code, string detail) => new()
    {
        Type = "https://heavymatica.dev/problems/" + code.ToWire(),
        Title = Titles.GetValueOrDefault(code, "Request failed"),
        Status = status,
        Detail = detail,
        Code = code,
    };

    /// <summary>An MVC result, for slices answering through the generated controllers.</summary>
    public static ObjectResult Result(int status, ProblemCode code, string detail) =>
        new(Of(status, code, detail)) { StatusCode = status, ContentTypes = { ContentType } };

    /// <summary>Writes a problem straight to the response, for middleware such as authentication and rate limiting.</summary>
    public static async Task WriteAsync(HttpResponse response, int status, ProblemCode code, string detail)
    {
        ArgumentNullException.ThrowIfNull(response);
        response.StatusCode = status;
        response.ContentType = ContentType;
        await JsonSerializer.SerializeAsync(response.Body, Of(status, code, detail), Json, response.HttpContext.RequestAborted);
    }

    private static readonly Dictionary<ProblemCode, string> Titles = new()
    {
        [ProblemCode.Unsupported_format] = "Unsupported schematic format",
        [ProblemCode.File_too_large] = "File too large",
        [ProblemCode.Decompressed_too_large] = "File too large when decompressed",
        [ProblemCode.Dimensions_too_large] = "Schematic too large",
        [ProblemCode.Volume_too_large] = "Schematic too large",
        [ProblemCode.Malformed_nbt] = "Corrupt schematic",
        [ProblemCode.Palette_index_out_of_range] = "Corrupt schematic",
        [ProblemCode.Size_mismatch] = "Corrupt schematic",
        [ProblemCode.Challenge_expired] = "Sign-in challenge expired",
        [ProblemCode.Challenge_used] = "Sign-in challenge already used",
        [ProblemCode.Challenge_unknown] = "Unknown sign-in challenge",
        [ProblemCode.Identity_not_confirmed] = "Minecraft identity not confirmed",
        [ProblemCode.Identity_provider_unavailable] = "Mojang is unavailable",
        [ProblemCode.Unauthenticated] = "Sign-in required",
        [ProblemCode.Refresh_token_invalid] = "Session expired",
        [ProblemCode.Not_found] = "Not found",
        [ProblemCode.Over_quota] = "Storage quota exceeded",
        [ProblemCode.Last_version] = "Last remaining version",
        [ProblemCode.Thumbnail_exists] = "Thumbnail already attached",
        [ProblemCode.Thumbnail_invalid] = "Invalid thumbnail",
        [ProblemCode.Invalid_request] = "Invalid request",
        [ProblemCode.Rate_limited] = "Too many requests",
        [ProblemCode.Unexpected] = "Unexpected error",
    };
}
