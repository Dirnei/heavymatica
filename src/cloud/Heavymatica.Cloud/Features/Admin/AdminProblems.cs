using System.Text.Json;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>Error answers as the admin contract's problem details.</summary>
internal static class AdminProblems
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    public static Problem Of(int status, ProblemCode code, string detail) => new()
    {
        Type = "https://heavymatica.dev/problems/admin/" + code.ToWire(),
        Title = Titles.GetValueOrDefault(code, "Request failed"),
        Status = status,
        Detail = detail,
        Code = code,
    };

    public static ObjectResult Result(int status, ProblemCode code, string detail) =>
        new(Of(status, code, detail)) { StatusCode = status, ContentTypes = { Problems.ContentType } };

    public static async Task WriteAsync(HttpResponse response, int status, ProblemCode code, string detail)
    {
        ArgumentNullException.ThrowIfNull(response);
        response.StatusCode = status;
        response.ContentType = Problems.ContentType;
        await JsonSerializer.SerializeAsync(response.Body, Of(status, code, detail), Json, response.HttpContext.RequestAborted);
    }

    public static ObjectResult NotFound(string detail) => Result(StatusCodes.Status404NotFound, ProblemCode.Not_found, detail);

    public static ObjectResult Invalid(string detail) => Result(StatusCodes.Status400BadRequest, ProblemCode.Invalid_request, detail);

    private static readonly Dictionary<ProblemCode, string> Titles = new()
    {
        [ProblemCode.Unauthenticated] = "Sign-in required",
        [ProblemCode.Request_forbidden] = "Request forbidden",
        [ProblemCode.Not_found] = "Not found",
        [ProblemCode.Invalid_request] = "Invalid request",
        [ProblemCode.Rate_limited] = "Too many requests",
        [ProblemCode.Internal_error] = "Unexpected error",
        [ProblemCode.Setup_code_invalid] = "Setup code not valid",
        [ProblemCode.Invitation_invalid] = "Invitation not valid",
        [ProblemCode.Passkey_required] = "Passkey still needed",
        [ProblemCode.Last_admin] = "Last admin",
        [ProblemCode.Cannot_remove_self] = "Cannot remove yourself",
        [ProblemCode.Passkey_failed] = "Passkey not accepted",
    };
}
