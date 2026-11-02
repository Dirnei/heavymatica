using System.Globalization;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>
/// <c>Heavymatica.Cloud admin setup-code [--admin &lt;adminId&gt;]</c>: prints a one-time setup code instead of starting the
/// web host. It reads the same configuration as the service and writes the code's hash to its database.
/// </summary>
internal static class SetupCodeCommand
{
    public sealed record Arguments(string? AdminId, string[] HostArgs);

    /// <summary>
    /// Null when the arguments do not start with the verb; otherwise the parsed arguments, or an error message for
    /// arguments the verb does not understand. Anything after <c>--</c> goes to the configuration.
    /// </summary>
    public static (Arguments? Parsed, string? Error)? Parse(string[] args)
    {
        ArgumentNullException.ThrowIfNull(args);
        if (args.Length < 2 || args[0] != "admin" || args[1] != "setup-code")
        {
            return null;
        }

        string? adminId = null;
        var i = 2;
        for (; i < args.Length && args[i] != "--"; i++)
        {
            if (args[i] == "--admin" && i + 1 < args.Length && adminId is null)
            {
                adminId = args[++i];
            }
            else
            {
                return (null, $"Unknown argument '{args[i]}'. Usage: admin setup-code [--admin <adminId>] [-- <configuration>]");
            }
        }

        return (new Arguments(adminId, args.Skip(i + 1).ToArray()), null);
    }

    public static async Task<int> RunAsync((Arguments? Parsed, string? Error) command, TextWriter output, TextWriter error)
    {
        ArgumentNullException.ThrowIfNull(output);
        ArgumentNullException.ThrowIfNull(error);
        if (command.Parsed is not { } arguments)
        {
            await error.WriteLineAsync(command.Error);
            return 2;
        }

        var builder = WebApplication.CreateBuilder(arguments.HostArgs);
        var connectionString = builder.Configuration.GetConnectionString(MongoServices.ConnectionName);
        if (connectionString is null)
        {
            await error.WriteLineAsync($"ConnectionStrings:{MongoServices.ConnectionName} is not configured.");
            return 1;
        }

        var options = builder.Configuration.GetSection(AdminOptions.Section).Get<AdminOptions>() ?? new AdminOptions();
        if (!options.Configured)
        {
            await error.WriteLineAsync("Admin:Url and Admin:Host are not configured, so the service has no admin listener.");
            return 1;
        }

        MongoServices.RegisterConventions();
        var url = MongoUrl.Create(connectionString);
        var database = new MongoClient(url).GetDatabase(url.DatabaseName ?? MongoServices.ConnectionName);
        return await IssueAsync(new SetupCodes(database, TimeProvider.System, Options.Create(options)), arguments.AdminId, output, error);
    }

    public static async Task<int> IssueAsync(SetupCodes codes, string? adminId, TextWriter output, TextWriter error)
    {
        ArgumentNullException.ThrowIfNull(codes);
        ArgumentNullException.ThrowIfNull(output);
        ArgumentNullException.ThrowIfNull(error);
        if (await codes.IssueAsync(adminId, CancellationToken.None) is not { } issued)
        {
            await error.WriteLineAsync($"There is no admin with the ID {adminId}.");
            return 1;
        }

        await output.WriteLineAsync(issued.AdminId is null
            ? "Setup code for a new admin:"
            : $"Recovery code for the admin {issued.AdminId}:");
        await output.WriteLineAsync();
        await output.WriteLineAsync("    " + issued.Code);
        await output.WriteLineAsync();
        await output.WriteLineAsync(string.Create(CultureInfo.InvariantCulture,
            $"Enter it at {issued.Site} before {issued.ExpiresAt:yyyy-MM-dd HH:mm} UTC. It works once; a new code replaces it."));
        return 0;
    }
}
