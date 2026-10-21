using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Formats;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Schematics;

internal static class SchematicProblems
{
    /// <summary>
    /// The same answer for a schematic that does not exist and one owned by another account, so nobody can probe
    /// for other players' schematics.
    /// </summary>
    public static ObjectResult NotFound() =>
        Problems.Result(StatusCodes.Status404NotFound, ProblemCode.Not_found, "No such schematic or version.");

    public static ObjectResult Rejected(SchematicRejectedException rejected)
    {
        ArgumentNullException.ThrowIfNull(rejected);
        var code = Enum.GetValues<ProblemCode>().Single(candidate => candidate.ToWire() == rejected.Code.ToWireName());
        var status = rejected.Code is RejectionCode.FileTooLarge or RejectionCode.DecompressedTooLarge
            ? StatusCodes.Status413PayloadTooLarge
            : StatusCodes.Status422UnprocessableEntity;
        return Problems.Result(status, code, rejected.Message);
    }
}
