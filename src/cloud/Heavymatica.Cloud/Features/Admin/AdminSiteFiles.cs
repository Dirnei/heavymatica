using Microsoft.Extensions.FileProviders;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>
/// Serves the built admin site on the admin listener, for the admin host only. Its pages are static shells that load
/// everything through the admin API, so they are served before authentication; the API paths are never files.
/// </summary>
internal static class AdminSiteFiles
{
    public static IApplicationBuilder UseAdminSite(this WebApplication app)
    {
        ArgumentNullException.ThrowIfNull(app);
        var options = app.Services.GetRequiredService<IOptions<AdminOptions>>().Value;
        var root = Path.GetFullPath(options.SiteRoot, AppContext.BaseDirectory);
        if (!options.Configured || !Directory.Exists(root))
        {
            return app;
        }

        var files = new PhysicalFileProvider(root);
        var listener = app.Services.GetRequiredService<AdminListener>();
        app.UseWhen(
            context => listener.IsAdminListener(context)
                && string.Equals(context.Request.Host.Host, options.Host, StringComparison.OrdinalIgnoreCase)
                && !context.Request.Path.StartsWithSegments("/admin", StringComparison.OrdinalIgnoreCase),
            site => site
                .UseDefaultFiles(new DefaultFilesOptions { FileProvider = files })
                .UseStaticFiles(new StaticFileOptions
                {
                    FileProvider = files,
                    OnPrepareResponse = file => file.Context.Response.Headers.CacheControl =
                        file.Context.Request.Path.StartsWithSegments("/_astro", StringComparison.Ordinal)
                            ? "public, max-age=31536000, immutable"
                            : "no-cache",
                }));
        return app;
    }
}
