using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Authentication;

namespace Heavymatica.Cloud.Features.Identity;

/// <summary>
/// Rejects data-changing requests that a website session or pending cookie authenticates unless they come from the
/// service's own origin and carry the website's request marker. The service sends no CORS headers, so another site
/// cannot add the marker. Requests with an access or pending token carry no ambient credential and pass unchecked.
/// </summary>
internal static class RequestCheck
{
    public const string MarkerHeader = "X-Heavymatica-Request";

    public static IApplicationBuilder UseWebRequestCheck(this IApplicationBuilder app) =>
        app.Use(async (context, next) =>
        {
            if (!IsSafe(context.Request.Method) && !IsFromWebsite(context.Request) && await IsCookieAuthenticatedAsync(context))
            {
                await Problems.WriteAsync(context.Response, StatusCodes.Status403Forbidden, ProblemCode.Request_forbidden,
                    "This request did not come from the Heavymatica website.");
                return;
            }

            await next(context);
        });

    /// <summary>
    /// The default authentication picks the website session when both cookies are present, so the pending cookie is
    /// looked at as well.
    /// </summary>
    private static async Task<bool> IsCookieAuthenticatedAsync(HttpContext context)
    {
        if (context.User.IsWebSession() || context.User.IsWebPending())
        {
            return true;
        }

        return !context.Request.Headers.Authorization.ToString().StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase)
            && (await context.AuthenticateAsync(PendingSignIns.WebScheme)).Succeeded;
    }

    private static bool IsSafe(string method) => HttpMethods.IsGet(method) || HttpMethods.IsHead(method) || HttpMethods.IsOptions(method);

    private static bool IsFromWebsite(HttpRequest request)
    {
        if (request.Headers[MarkerHeader] != "1")
        {
            return false;
        }

        var origin = request.Headers.Origin.ToString();
        return origin.Length == 0
            ? request.Headers["Sec-Fetch-Site"] == "same-origin"
            : string.Equals(origin, $"{request.Scheme}://{request.Host}", StringComparison.OrdinalIgnoreCase);
    }
}
