using Heavymatica.Cloud.Contract;
using Microsoft.AspNetCore.Authorization;

namespace Heavymatica.Cloud.Features.CloudStatus;

internal static class StatusServices
{
    public static WebApplicationBuilder AddStatus(this WebApplicationBuilder builder)
    {
        builder.Services.AddOptions<StatusOptions>().BindConfiguration(StatusOptions.Section);
        return builder;
    }

    /// <summary>The status is read by the game before anyone signs in, so it is open to anonymous requests.</summary>
    public static void ConfigureStatusEndpoint(EndpointBuilder endpoint)
    {
        ArgumentNullException.ThrowIfNull(endpoint);
        var action = endpoint.Metadata.OfType<Microsoft.AspNetCore.Mvc.Controllers.ControllerActionDescriptor>().FirstOrDefault();
        if (action?.ControllerTypeInfo.AsType() == typeof(StatusController))
        {
            endpoint.Metadata.Add(new AllowAnonymousAttribute());
        }
    }
}
