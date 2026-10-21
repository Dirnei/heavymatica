using System.Net;
using Heavymatica.Cloud.Infrastructure;
using Microsoft.AspNetCore.Mvc.Testing;

namespace Heavymatica.Cloud.Tests.Infrastructure;

public sealed class HealthTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly CloudFactory factory = new(infrastructure);

    public void Dispose() => factory.Dispose();

    [Fact]
    public async Task Health_endpoint_answers_ok()
    {
        using var client = factory.CreateClient();

        using var response = await client.GetAsync(new Uri("/health", UriKind.Relative), TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }
}
