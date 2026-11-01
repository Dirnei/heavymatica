using System.Security.Claims;
using System.Threading.RateLimiting;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Infrastructure.Contract;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Identity;

internal static class IdentityServices
{
    private const string SignInRateLimit = "sign-in";

    public static WebApplicationBuilder AddIdentity(this WebApplicationBuilder builder)
    {
        var services = builder.Services;
        services.AddOptions<IdentityOptions>().BindConfiguration(IdentityOptions.Section);
        services.TryAddSingleton(TimeProvider.System);
        services.AddSingleton(provider => SigningKey.From(
            provider.GetRequiredService<IOptions<IdentityOptions>>().Value,
            provider.GetRequiredService<IHostEnvironment>(),
            provider.GetRequiredService<ILoggerFactory>().CreateLogger<SigningKey>()));
        services.AddSingleton<AccessTokens>();
        services.AddSingleton<Challenges>();
        services.AddSingleton<Sessions>();
        services.AddHttpClient<MojangSessions>(MojangSessions.HttpClientName, client => client.Timeout = TimeSpan.FromSeconds(10));
        services.AddMongoIndexes<IdentityIndexes>();

        services.AddMongoDataProtection();
        // The default scheme takes any principal, so middleware before authorization (the request check, rate limits)
        // sees pending sign-ins too; endpoints still authenticate with their own policy's schemes.
        services.AddAuthentication(PendingSignIns.AnyScheme)
            .AddJwtBearer()
            .AddWebSessions(builder.Environment)
            .AddPendingSignIns(builder.Environment);
        services.AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
            .Configure<IOptions<IdentityOptions>, SigningKey>((jwt, identity, key) =>
            {
                jwt.MapInboundClaims = false;
                jwt.TokenValidationParameters = AccessTokens.Validation(identity.Value, key);
                jwt.Events = new JwtBearerEvents
                {
                    // Tokens issued before accounts had identities name a Minecraft UUID as subject; the client refreshes.
                    OnTokenValidated = context =>
                    {
                        if (context.Principal?.FindFirstValue(AccessTokens.IdentityClaim) is null)
                        {
                            context.Fail("The access token names no identity.");
                        }

                        return Task.CompletedTask;
                    },
                    OnChallenge = async context =>
                    {
                        context.HandleResponse();
                        await Problems.WriteAsync(context.Response, StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated,
                            "A valid access token is required.");
                    },
                };
            });

        services.AddOptions<JwtBearerOptions>(PendingSignIns.BearerScheme)
            .Configure<IOptions<IdentityOptions>, SigningKey>((jwt, identity, key) =>
            {
                jwt.MapInboundClaims = false;
                jwt.TokenValidationParameters = AccessTokens.PendingValidation(identity.Value, key);
                jwt.Events = new JwtBearerEvents
                {
                    OnTokenValidated = context =>
                    {
                        if (context.Principal?.Kind() != PrincipalKind.Pending)
                        {
                            context.Fail("The pending token names no identity.");
                        }

                        return Task.CompletedTask;
                    },
                    OnChallenge = async context =>
                    {
                        context.HandleResponse();
                        await Problems.WriteAsync(context.Response, StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated,
                            "A valid access token or pending sign-in is required.");
                    },
                };
            });

        // Every endpoint needs a signed-in player, through an access token or a website session, unless it opts out
        // (sign-in, health, the contract) or names one of the policies that also admit pending sign-ins.
        services.AddAuthorizationBuilder()
            .SetFallbackPolicy(new AuthorizationPolicyBuilder(WebSessions.PlayerScheme).RequireAuthenticatedUser().Build())
            .AddPolicy(PendingSignIns.AnyPolicy, policy => policy.AddAuthenticationSchemes(PendingSignIns.AnyScheme).RequireAuthenticatedUser()
                .RequireAssertion(context => context.User.Kind() != PrincipalKind.None))
            .AddPolicy(PendingSignIns.GamePolicy, policy => policy.AddAuthenticationSchemes(PendingSignIns.BearerScheme).RequireAuthenticatedUser()
                .RequireAssertion(context => context.User.Kind() == PrincipalKind.Pending))
            .AddPolicy(PendingSignIns.WebPolicy, policy => policy.AddAuthenticationSchemes(PendingSignIns.WebScheme).RequireAuthenticatedUser()
                .RequireAssertion(context => context.User.Kind() == PrincipalKind.Pending));

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

    /// <summary>
    /// Endpoint conventions for the generated controllers: the handshake and the start of a website sign-in need no
    /// sign-in and carry the sign-in rate limit, refresh and sign-out need none either, and the calls that finish a
    /// pending sign-in take only a pending one.
    /// </summary>
    public static void ConfigureAuthEndpoint(EndpointBuilder endpoint)
    {
        ArgumentNullException.ThrowIfNull(endpoint);
        var action = endpoint.Metadata.OfType<Microsoft.AspNetCore.Mvc.Controllers.ControllerActionDescriptor>().FirstOrDefault();
        var controller = action?.ControllerTypeInfo.AsType();
        if (controller == typeof(AuthController))
        {
            if (action!.ActionName == nameof(AuthController.CompleteSignIn))
            {
                endpoint.Metadata.Add(new AuthorizeAttribute(PendingSignIns.GamePolicy));
                return;
            }

            endpoint.Metadata.Add(new AllowAnonymousAttribute());
            if (action.ActionName is nameof(AuthController.CreateChallenge) or nameof(AuthController.Verify) or nameof(AuthController.SignIn))
            {
                endpoint.Metadata.Add(new Microsoft.AspNetCore.RateLimiting.EnableRateLimitingAttribute(SignInRateLimit));
            }

            return;
        }

        if (controller != typeof(WebController))
        {
            return;
        }

        switch (action!.ActionName)
        {
            case nameof(WebController.StartGitHubSignIn):
                endpoint.Metadata.Add(new AllowAnonymousAttribute());
                endpoint.Metadata.Add(new Microsoft.AspNetCore.RateLimiting.EnableRateLimitingAttribute(SignInRateLimit));
                break;
            case nameof(WebController.GetWebPending) or nameof(WebController.CreateWebAccount) or nameof(WebController.CompleteWebSignIn):
                endpoint.Metadata.Add(new AuthorizeAttribute(PendingSignIns.WebPolicy));
                break;
        }
    }

    /// <summary>The signed-in player's account ID, from an access token or a website session.</summary>
    public static string AccountId(this ClaimsPrincipal user) => Required(user, AccessTokens.AccountClaim);

    /// <summary>The ID of the identity the player signed in with.</summary>
    public static string IdentityId(this ClaimsPrincipal user) => Required(user, AccessTokens.IdentityClaim);

    public static string SessionId(this ClaimsPrincipal user) => Required(user, AccessTokens.SessionClaim);

    private static string Required(ClaimsPrincipal user, string claim)
    {
        ArgumentNullException.ThrowIfNull(user);
        return user.FindFirstValue(claim) ?? throw new InvalidOperationException("The request is not signed in.");
    }
}
