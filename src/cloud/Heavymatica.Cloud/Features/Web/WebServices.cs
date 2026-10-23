namespace Heavymatica.Cloud.Features.Web;

internal static class WebServices
{
    public static WebApplicationBuilder AddWeb(this WebApplicationBuilder builder)
    {
        ArgumentNullException.ThrowIfNull(builder);
        builder.Services.AddOptions<WebOptions>().BindConfiguration(WebOptions.Section);
        builder.AddGitHubSignIn(builder.Services.AddAuthentication());
        return builder;
    }

    public static WebApplication MapWeb(this WebApplication app)
    {
        app.MapGitHubSignIn();
        return app;
    }
}
