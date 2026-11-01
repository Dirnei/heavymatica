using System.Threading.RateLimiting;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Links;

/// <summary>Marks the redeem operation for the redemption rate limit.</summary>
internal sealed class LinkRedeemLimit
{
    public static readonly LinkRedeemLimit Instance = new();
}

internal static class LinkServices
{
    public static WebApplicationBuilder AddLinks(this WebApplicationBuilder builder)
    {
        ArgumentNullException.ThrowIfNull(builder);
        builder.Services.AddMongoIndexes<LinkIndexes>();
        builder.Services.AddOptions<RateLimiterOptions>().Configure(limiter =>
        {
            // Redemption attempts are limited per redeemer and per client address before the code is looked at.
            limiter.GlobalLimiter = PartitionedRateLimiter.CreateChained(
                PartitionedRateLimiter.Create<HttpContext, string>(context =>
                    IsRedeem(context) && RedeemerKey(context) is { } redeemer
                        ? Partition("redeemer:" + redeemer, context, options => options.LinkRedeemsPerRedeemer)
                        : RateLimitPartition.GetNoLimiter(string.Empty)),
                PartitionedRateLimiter.Create<HttpContext, string>(context =>
                    IsRedeem(context)
                        ? Partition("address:" + (context.Connection.RemoteIpAddress?.ToString() ?? "unknown"), context, options => options.LinkRedeemsPerAddress)
                        : RateLimitPartition.GetNoLimiter(string.Empty)));

            var rejected = limiter.OnRejected;
            limiter.OnRejected = async (context, cancellationToken) =>
            {
                if (!IsRedeem(context.HttpContext))
                {
                    if (rejected is not null)
                    {
                        await rejected(context, cancellationToken);
                    }

                    return;
                }

                await Problems.WriteAsync(context.HttpContext.Response, StatusCodes.Status429TooManyRequests, ProblemCode.Rate_limited,
                    "Too many link code attempts. Wait a few minutes and try again.");
            };
        });
        return builder;
    }

    /// <summary>Endpoint conventions for the generated Links controller: every operation admits pending sign-ins.</summary>
    public static void ConfigureLinksEndpoint(EndpointBuilder endpoint)
    {
        ArgumentNullException.ThrowIfNull(endpoint);
        var action = endpoint.Metadata.OfType<Microsoft.AspNetCore.Mvc.Controllers.ControllerActionDescriptor>().FirstOrDefault();
        if (action?.ControllerTypeInfo.AsType() != typeof(LinksController))
        {
            return;
        }

        endpoint.Metadata.Add(new AuthorizeAttribute(PendingSignIns.AnyPolicy));
        if (action.ActionName == nameof(LinksController.RedeemLinkCode))
        {
            endpoint.Metadata.Add(LinkRedeemLimit.Instance);
        }
    }

    private static bool IsRedeem(HttpContext context) => context.GetEndpoint()?.Metadata.GetMetadata<LinkRedeemLimit>() is not null;

    private static string? RedeemerKey(HttpContext context) => context.User.Kind() switch
    {
        PrincipalKind.Account => "account:" + context.User.AccountId(),
        PrincipalKind.Pending => "pending:" + context.User.PendingIdentity().Provider + ":" + context.User.PendingIdentity().Subject,
        _ => null,
    };

    private static RateLimitPartition<string> Partition(string key, HttpContext context, Func<AccountsOptions, int> permits)
    {
        var services = context.RequestServices;
        return RateLimitPartition.Get(key, _ =>
        {
            var options = services.GetRequiredService<IOptions<AccountsOptions>>().Value;
            return new ClockedWindowLimiter(services.GetRequiredService<TimeProvider>(), permits(options), options.LinkRedeemWindow);
        });
    }
}
