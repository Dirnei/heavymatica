using System.Security.Claims;
using System.Threading.RateLimiting;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Identity;

internal static class IdentityServices
{
    private const string SignInRateLimit = "sign-in";

    public static WebApplicationBuilder AddIdentity(this WebApplicationBuilder builder)
    {
        var services = builder.Services;
        services.AddOptions<IdentityOptions>().BindConfiguration(IdentityOptions.Section);
        services.AddOptions<QuotaOptions>().BindConfiguration(QuotaOptions.Section);
        services.AddSingleton(TimeProvider.System);
        services.AddSingleton(provider => SigningKey.From(
            provider.GetRequiredService<IOptions<IdentityOptions>>().Value,
            provider.GetRequiredService<IHostEnvironment>(),
            provider.GetRequiredService<ILoggerFactory>().CreateLogger<SigningKey>()));
        services.AddSingleton<AccessTokens>();
        services.AddSingleton<Challenges>();
        services.AddSingleton<Sessions>();
        services.AddSingleton<Accounts>();
        services.AddHttpClient<MojangSessions>(MojangSessions.HttpClientName, client => client.Timeout = TimeSpan.FromSeconds(10));
        services.AddMongoIndexes<IdentityIndexes>();

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer();
        services.AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
            .Configure<IOptions<IdentityOptions>, SigningKey>((jwt, identity, key) =>
            {
                jwt.MapInboundClaims = false;
                jwt.TokenValidationParameters = AccessTokens.Validation(identity.Value, key);
                jwt.Events = new JwtBearerEvents
                {
                    OnChallenge = async context =>
                    {
                        context.HandleResponse();
                        await Problems.WriteAsync(context.Response, StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated,
                            "A valid access token is required.");
                    },
                };
            });

        // Every endpoint needs a signed-in player unless it opts out (sign-in, health, the contract).
        services.AddAuthorizationBuilder()
            .SetFallbackPolicy(new AuthorizationPolicyBuilder().RequireAuthenticatedUser().Build());

        services.AddRateLimiter(limiter =>
        {
            limiter.OnRejected = async (context, _) =>
                await Problems.WriteAsync(context.HttpContext.Response, StatusCodes.Status429TooManyRequests, ProblemCode.Rate_limited,
                    "Too many sign-in attempts. Wait a minute and try again.");
            limiter.AddPolicy(SignInRateLimit, context =>
            {
                var perMinute = context.RequestServices.GetRequiredService<IOptions<IdentityOptions>>().Value.SignInRequestsPerMinute;
                return RateLimitPartition.GetFixedWindowLimiter(
                    context.Connection.RemoteIpAddress?.ToString() ?? "unknown",
                    _ => new FixedWindowRateLimiterOptions { PermitLimit = perMinute, Window = TimeSpan.FromMinutes(1), QueueLimit = 0 });
            });
        });
        return builder;
    }

    /// <summary>Endpoint conventions for the generated Auth controller: no sign-in required, sign-in rate limit on the handshake.</summary>
    public static void ConfigureAuthEndpoint(EndpointBuilder endpoint)
    {
        ArgumentNullException.ThrowIfNull(endpoint);
        var action = endpoint.Metadata.OfType<Microsoft.AspNetCore.Mvc.Controllers.ControllerActionDescriptor>().FirstOrDefault();
        if (action?.ControllerTypeInfo.AsType() != typeof(AuthController))
        {
            return;
        }

        endpoint.Metadata.Add(new AllowAnonymousAttribute());
        if (action.ActionName is nameof(AuthController.CreateChallenge) or nameof(AuthController.Verify))
        {
            endpoint.Metadata.Add(new Microsoft.AspNetCore.RateLimiting.EnableRateLimitingAttribute(SignInRateLimit));
        }
    }

    /// <summary>The signed-in player's account ID (their Minecraft UUID).</summary>
    public static string AccountId(this ClaimsPrincipal user)
    {
        ArgumentNullException.ThrowIfNull(user);
        return user.FindFirstValue(AccessTokens.AccountClaim) ?? throw new InvalidOperationException("The request is not signed in.");
    }
}
