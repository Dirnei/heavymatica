using Heavymatica.Cloud.Features.Web;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Hosting.Server;
using Microsoft.AspNetCore.Hosting.Server.Features;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>Marks every admin endpoint; only these answer on the admin listener, and only there.</summary>
internal sealed class AdminEndpoint
{
    public static readonly AdminEndpoint Instance = new();
}

/// <summary>
/// The admin listener: a second server address from <c>Admin:Url</c>, told apart from the public one by the local
/// port of the connection, which a client cannot forge the way it can a Host header.
/// </summary>
internal sealed class AdminListener(IOptions<AdminOptions> options)
{
    public bool Configured => options.Value.Configured;

    public bool IsAdminListener(HttpContext context)
    {
        ArgumentNullException.ThrowIfNull(context);
        return Configured && context.Connection.LocalPort == options.Value.Port;
    }

    public static bool IsAdminEndpoint(HttpContext context)
    {
        ArgumentNullException.ThrowIfNull(context);
        return context.GetEndpoint()?.Metadata.GetMetadata<AdminEndpoint>() is not null;
    }

    /// <summary>What the admin listener serves: the admin endpoints, nothing of the player site or API.</summary>
    public static bool IsServedOnAdminListener(HttpContext context) => IsAdminEndpoint(context);

    public static IApplicationBuilder UseAdminListener(IApplicationBuilder app)
    {
        ArgumentNullException.ThrowIfNull(app);
        var listener = app.ApplicationServices.GetRequiredService<AdminListener>();
        if (!listener.Configured)
        {
            return app;
        }

        return app.Use(async (context, next) =>
        {
            if (listener.IsAdminListener(context))
            {
                if (!IsServedOnAdminListener(context))
                {
                    await AdminProblems.WriteAsync(context.Response, StatusCodes.Status404NotFound, Contract.Admin.ProblemCode.Not_found, "No such endpoint.");
                    return;
                }
            }
            else if (IsAdminEndpoint(context))
            {
                if (!await WebsiteFiles.TryWriteNotFoundPageAsync(context))
                {
                    await Problems.WriteAsync(context.Response, StatusCodes.Status404NotFound, Cloud.Contract.ProblemCode.Not_found, "No such endpoint.");
                }

                return;
            }

            await next(context);
        });
    }
}

/// <summary>
/// Adds the admin address to the server's addresses before the server starts. Kestrel then ignores the <c>urls</c>
/// setting, so the public addresses are carried over from it the way the web host would have read them.
/// </summary>
internal sealed class AdminAddresses(IServer server, IConfiguration configuration, IOptions<AdminOptions> options) : IHostedService
{
    public Task StartAsync(CancellationToken cancellationToken)
    {
        if (!options.Value.Configured || server.Features.Get<IServerAddressesFeature>()?.Addresses is not { IsReadOnly: false } addresses)
        {
            return Task.CompletedTask;
        }

        if (addresses.Count == 0)
        {
            foreach (var address in PublicAddresses(configuration))
            {
                addresses.Add(address);
            }
        }

        addresses.Add(options.Value.Url!.GetLeftPart(UriPartial.Authority));
        return Task.CompletedTask;
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;

    public static IEnumerable<string> PublicAddresses(IConfiguration configuration)
    {
        ArgumentNullException.ThrowIfNull(configuration);
        var urls = Split(configuration[WebHostDefaults.ServerUrlsKey]);
        if (urls.Length > 0)
        {
            return urls;
        }

        var ports = Split(configuration[WebHostDefaults.HttpPortsKey]).Select(port => "http://*:" + port)
            .Concat(Split(configuration[WebHostDefaults.HttpsPortsKey]).Select(port => "https://*:" + port))
            .ToList();
        return ports.Count > 0 ? ports : ["http://localhost:5000"];
    }

    private static string[] Split(string? value) =>
        value?.Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries) ?? [];
}
