using Microsoft.Extensions.FileProviders;
using Microsoft.Extensions.Options;
using Microsoft.Net.Http.Headers;

namespace Heavymatica.Cloud.Features.Web;

internal sealed class WebOptions
{
    public const string Section = "Web";

    /// <summary>The built website. Relative paths are resolved against the executable's directory.</summary>
    public string Root { get; set; } = "wwwroot";
}

/// <summary>
/// Serves the built player website on the API's origin. The pages are static shells that load everything through the
/// API, so they are served before authentication. API paths are never served from files.
/// </summary>
internal static class WebsiteFiles
{
    private const string NotFoundPage = "404.html";

    private static readonly string[] ApiPaths = ["/v1", "/health", "/alive", "/dev", "/openapi"];

    public static bool IsApiPath(PathString path) => ApiPaths.Any(prefix => path.StartsWithSegments(prefix, StringComparison.OrdinalIgnoreCase));

    public static IApplicationBuilder UseWebsite(this WebApplication app)
    {
        ArgumentNullException.ThrowIfNull(app);
        if (Root(app.Services) is not { } root)
        {
            return app;
        }

        var files = new PhysicalFileProvider(root);
        app.UseWhen(context => !IsApiPath(context.Request.Path), website => website
            .UseDefaultFiles(new DefaultFilesOptions { FileProvider = files })
            .UseStaticFiles(new StaticFileOptions
            {
                FileProvider = files,
                OnPrepareResponse = file => file.Context.Response.Headers.CacheControl =
                    file.Context.Request.Path.StartsWithSegments("/_astro", StringComparison.Ordinal)
                        ? "public, max-age=31536000, immutable"
                        : "public, max-age=60",
            }));
        return app;
    }

    /// <summary>The website's 404 page for an unknown non-API path, or null when there is none.</summary>
    public static async Task<bool> TryWriteNotFoundPageAsync(HttpContext context)
    {
        ArgumentNullException.ThrowIfNull(context);
        if (IsApiPath(context.Request.Path) || Root(context.RequestServices) is not { } root
            || !File.Exists(Path.Combine(root, NotFoundPage)))
        {
            return false;
        }

        context.Response.StatusCode = StatusCodes.Status404NotFound;
        context.Response.ContentType = "text/html; charset=utf-8";
        context.Response.Headers[HeaderNames.CacheControl] = "public, max-age=60";
        await context.Response.SendFileAsync(Path.Combine(root, NotFoundPage), context.RequestAborted);
        return true;
    }

    private static string? Root(IServiceProvider services)
    {
        var configured = services.GetRequiredService<IOptions<WebOptions>>().Value.Root;
        var root = Path.GetFullPath(configured, AppContext.BaseDirectory);
        return Directory.Exists(root) ? root : null;
    }
}
