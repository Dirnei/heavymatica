using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.Contract;

namespace Heavymatica.Cloud.Features.Identity;

/// <summary>
/// Rejects data-changing requests that a website session authenticates unless they come from the service's own origin
/// and carry the website's request marker. The service sends no CORS headers, so another site cannot add the marker.
/// Requests with an access token carry no ambient credential and pass unchecked.
/// </summary>
internal static class RequestCheck
{
    public const string MarkerHeader = "X-Heavymatica-Request";

    public static IApplicationBuilder UseWebRequestCheck(this IApplicationBuilder app) =>
        app.Use(async (context, next) =>
        {
            if (context.User.IsWebSession() && !IsSafe(context.Request.Method) && !IsFromWebsite(context.Request))
            {
                await Problems.WriteAsync(context.Response, StatusCodes.Status403Forbidden, ProblemCode.Request_forbidden,
                    "This request did not come from the Heavymatica website.");
                return;
            }

            await next(context);
        });

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
