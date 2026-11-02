using Heavymatica.Cloud.Contract;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.CloudStatus;

internal sealed class StatusApi(IOptionsMonitor<StatusOptions> options) : IStatusController
{
    public Task<ActionResult<ServiceStatus>> GetStatusAsync(CancellationToken cancellationToken = default)
    {
        var current = options.CurrentValue;
        var status = new ServiceStatus
        {
            SignIn = current.SignIn,
            MinClient = string.IsNullOrWhiteSpace(current.MinClient) ? null : current.MinClient.Trim(),
        };
        return Task.FromResult<ActionResult<ServiceStatus>>(status);
    }
}
