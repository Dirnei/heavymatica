using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Features.Schematics;

namespace Heavymatica.Cloud.Infrastructure.Contract;

/// <summary>Wires the controllers generated from the contract to the feature slices that implement them.</summary>
internal static class ContractServices
{
    public static IServiceCollection AddContract(this IServiceCollection services)
    {
        services.AddControllers(options => options.ModelBinderProviders.Insert(0, new StreamingBodyBinderProvider()));

        services.AddHttpContextAccessor();
        services.AddScoped<IAuthController, AuthApi>();
        services.AddScoped<IAccountController, AccountApi>();
        services.AddScoped<ISchematicsController, SchematicsApi>();
        return services;
    }

    /// <summary>Maps the generated controllers and serves the contract exactly as written (D2).</summary>
    public static WebApplication MapContract(this WebApplication app)
    {
        app.MapControllers().Add(IdentityServices.ConfigureAuthEndpoint);
        app.MapGet("/openapi/v1.yaml", () =>
        {
            var contract = typeof(ContractServices).Assembly.GetManifestResourceStream(ContractResource)
                ?? throw new InvalidOperationException($"The contract resource {ContractResource} is missing from the build.");
            return Results.Stream(contract, "application/yaml");
        }).AllowAnonymous();

        // Unknown routes are a plain 404, not a sign-in prompt from the fallback authorization policy.
        app.MapFallback(context => Problems.WriteAsync(context.Response, StatusCodes.Status404NotFound, ProblemCode.Not_found, "No such endpoint."))
            .AllowAnonymous();
        return app;
    }

    private const string ContractResource = "heavymatica-cloud.v1.yaml";
}
