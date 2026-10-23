using Heavymatica.Cloud.Infrastructure.Mongo;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Heavymatica.Cloud.Features.Account;

internal static class AccountServices
{
    public static WebApplicationBuilder AddAccounts(this WebApplicationBuilder builder)
    {
        var services = builder.Services;
        services.AddOptions<QuotaOptions>().BindConfiguration(QuotaOptions.Section);
        services.TryAddSingleton(TimeProvider.System);
        services.AddSingleton<AccountAudit>();
        services.AddSingleton<Accounts>();
        services.AddMongoIndexes<AccountIndexes>();
        services.TryAddSingleton<IMigrationObserver, NoMigrationObserver>();
        // Hosted services start in registration order: after the indexes and the bucket, before anything that serves data.
        services.AddHostedService<AccountMigration>();
        return builder;
    }
}
