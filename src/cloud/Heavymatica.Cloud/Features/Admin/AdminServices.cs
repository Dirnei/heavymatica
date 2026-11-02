using System.Threading.RateLimiting;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Links;
using Heavymatica.Cloud.Infrastructure.Mongo;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Identity;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.ApplicationModels;
using Microsoft.AspNetCore.Mvc.Controllers;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.Routing;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Admin;

internal static class AdminServices
{
    public const string SignInRateLimit = "admin-sign-in";
    public const string CodeRateLimit = "admin-code";

    private static readonly string AdminNamespace = typeof(AdminSessionController).Namespace!;

    public static WebApplicationBuilder AddAdmin(this WebApplicationBuilder builder)
    {
        ArgumentNullException.ThrowIfNull(builder);
        var services = builder.Services;
        var configured = builder.Configuration.GetSection(AdminOptions.Section).Get<AdminOptions>() ?? new AdminOptions();
        configured.Validate();

        services.AddOptions<AdminOptions>().BindConfiguration(AdminOptions.Section);
        services.TryAddSingleton(TimeProvider.System);
        services.AddSingleton<AdminListener>();
        services.AddSingleton<AdminSessions>();
        services.AddSingleton<AdminAudit>();
        services.AddSingleton<Admins>();
        services.AddSingleton<SetupCodes>();
        services.AddSingleton<Invitations>();
        services.AddSingleton<Ceremonies>();
        services.AddMongoIndexes<AdminIndexes>();
        services.AddHostedService<AdminAddresses>();

        services.AddIdentityCore<AdminUser>().AddUserStore<AdminUserStore>();
        services.TryAddScoped<IPasskeyHandler<AdminUser>, PasskeyHandler<AdminUser>>();
        services.AddOptions<IdentityPasskeyOptions>().Configure<IOptions<AdminOptions>>((passkeys, admin) => passkeys.ServerDomain = admin.Value.Host);

        AdminSessions.AddScheme(services.AddAuthentication(), builder.Environment);
        services.AddAuthorizationBuilder()
            .AddPolicy(AdminSessions.Policy, policy => policy.AddAuthenticationSchemes(AdminSessions.Scheme).RequireAuthenticatedUser());

        services.AddOptions<RateLimiterOptions>().Configure(limiter =>
        {
            limiter.AddPolicy(SignInRateLimit, new AdminRateLimit(options => (options.SignInAttemptsPerMinute, TimeSpan.FromMinutes(1)),
                "Too many sign-in attempts. Wait a minute and try again."));
            limiter.AddPolicy(CodeRateLimit, new AdminRateLimit(options => (options.CodeAttempts, options.CodeAttemptWindow),
                "Too many setup code or invitation attempts. Wait a few minutes and try again."));
        });

        services.AddOptions<MvcOptions>().Configure<IOptions<AdminOptions>>((mvc, admin) =>
            mvc.Conventions.Add(new AdminControllers(admin.Value.Configured)));

        services.AddScoped<IAdminSessionController, SessionApi>();
        services.AddScoped<IAdminRegistrationController, RegistrationApi>();
        services.AddScoped<IAdminAdminsController, AdminsApi>();
        services.AddScoped<IAdminAccountsController, AccountsApi>();
        services.AddScoped<IAdminAuditController, AuditApi>();
        return builder;
    }

    /// <summary>
    /// Answers not found for an admin endpoint on the public listener, the same way as for an unknown path, and for
    /// anything else on the admin listener. Runs after routing, before authentication.
    /// </summary>
    public static IApplicationBuilder UseAdminListener(this IApplicationBuilder app) => AdminListener.UseAdminListener(app);

    /// <summary>
    /// Endpoint conventions for the generated admin controllers: the admin marker and host, the admin session policy,
    /// and for the sign-in and registration calls no session but their rate limits.
    /// </summary>
    public static void ConfigureAdminEndpoint(EndpointBuilder endpoint)
    {
        ArgumentNullException.ThrowIfNull(endpoint);
        var action = endpoint.Metadata.OfType<ControllerActionDescriptor>().FirstOrDefault();
        if (action?.ControllerTypeInfo.Namespace != AdminNamespace)
        {
            return;
        }

        var options = endpoint.ApplicationServices.GetRequiredService<IOptions<AdminOptions>>().Value;
        endpoint.Metadata.Add(AdminEndpoint.Instance);
        endpoint.Metadata.Add(new HostAttribute(options.Host!));

        var limit = (action.ControllerTypeInfo.AsType(), action.ActionName) switch
        {
            (var type, nameof(AdminSessionController.CreateSignInOptions) or nameof(AdminSessionController.SignIn)) when type == typeof(AdminSessionController)
                => SignInRateLimit,
            (var type, nameof(AdminRegistrationController.CreateRegistrationOptions) or nameof(AdminRegistrationController.Register))
                when type == typeof(AdminRegistrationController) => CodeRateLimit,
            _ => null,
        };
        if (limit is null)
        {
            endpoint.Metadata.Add(new AuthorizeAttribute(AdminSessions.Policy));
            return;
        }

        endpoint.Metadata.Add(new AllowAnonymousAttribute());
        endpoint.Metadata.Add(new EnableRateLimitingAttribute(limit));
    }

    /// <summary>Without an admin listener the admin controllers are not mapped at all; with one they get the admin filters.</summary>
    private sealed class AdminControllers(bool configured) : IApplicationModelConvention
    {
        public void Apply(ApplicationModel application)
        {
            ArgumentNullException.ThrowIfNull(application);
            var admin = application.Controllers.Where(controller => controller.ControllerType.Namespace == AdminNamespace).ToList();
            foreach (var controller in admin)
            {
                if (configured)
                {
                    controller.Filters.Add(new AdminFilter());
                }
                else
                {
                    application.Controllers.Remove(controller);
                }
            }
        }
    }

    /// <summary>Rejects invalid requests with the admin contract's problem.</summary>
    private sealed class AdminFilter : IActionFilter
    {
        public void OnActionExecuting(ActionExecutingContext context)
        {
            if (!context.ModelState.IsValid)
            {
                var error = context.ModelState.Values.SelectMany(entry => entry.Errors).FirstOrDefault();
                context.Result = AdminProblems.Invalid(error?.ErrorMessage is { Length: > 0 } message ? message : "The request is not valid.");
            }
        }

        public void OnActionExecuted(ActionExecutedContext context)
        {
        }
    }

    /// <summary>A fixed window per client address on the service clock, rejected with the admin contract's problem.</summary>
    private sealed class AdminRateLimit(Func<AdminOptions, (int Permits, TimeSpan Window)> limits, string detail) : IRateLimiterPolicy<string>
    {
        public Func<OnRejectedContext, CancellationToken, ValueTask>? OnRejected => async (context, _) =>
            await AdminProblems.WriteAsync(context.HttpContext.Response, StatusCodes.Status429TooManyRequests, ProblemCode.Rate_limited, detail);

        public RateLimitPartition<string> GetPartition(HttpContext httpContext)
        {
            var services = httpContext.RequestServices;
            return RateLimitPartition.Get(httpContext.Connection.RemoteIpAddress?.ToString() ?? "unknown", _ =>
            {
                var (permits, window) = limits(services.GetRequiredService<IOptions<AdminOptions>>().Value);
                return new ClockedWindowLimiter(services.GetRequiredService<TimeProvider>(), permits, window);
            });
        }
    }
}
