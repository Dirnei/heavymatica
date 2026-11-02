using System.Net;
using System.Text.Json;
using Microsoft.Extensions.DependencyInjection;

namespace Heavymatica.Cloud.Tests.Features.CloudStatus;

[Trait("Category", "Integration")]
public sealed class StatusTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    [Fact]
    public async Task Anonymous_request_gets_closed_by_default()
    {
        using var client = Factory().CreateClient();

        using var body = await StatusAsync(client);

        Assert.Equal("closed", body.RootElement.GetProperty("signIn").GetString());
        Assert.False(body.RootElement.TryGetProperty("minClient", out var minClient) && minClient.ValueKind != JsonValueKind.Null);
    }

    [Fact]
    public async Task Configured_values_are_reported()
    {
        using var client = Factory().With("Status:SignIn", "open").With("Status:MinClient", "0.3.0").CreateClient();

        using var body = await StatusAsync(client);

        Assert.Equal("open", body.RootElement.GetProperty("signIn").GetString());
        Assert.Equal("0.3.0", body.RootElement.GetProperty("minClient").GetString());
    }

    [Fact]
    public async Task A_signed_in_request_gets_the_same_answer()
    {
        using var client = Factory().With("Status:SignIn", "open").CreateClient();
        using var anonymous = await StatusAsync(client);
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Tester");

        client.Authorize(session.AccessToken);
        using var signedIn = await StatusAsync(client);

        Assert.Equal(anonymous.RootElement.GetRawText(), signedIn.RootElement.GetRawText());
    }

    [Fact]
    public async Task Sign_in_still_works_while_closed()
    {
        using var client = Factory().CreateClient();

        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "EarlyBird");

        Assert.False(string.IsNullOrEmpty(session.AccessToken));
    }

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure);
        disposables.Add(factory);
        return factory;
    }

    private static async Task<JsonDocument> StatusAsync(HttpClient client)
    {
        using var response = await client.GetAsync(new Uri("/v1/status", UriKind.Relative), TestContext.Current.CancellationToken);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await JsonDocument.ParseAsync(await response.Content.ReadAsStreamAsync(TestContext.Current.CancellationToken), cancellationToken: TestContext.Current.CancellationToken);
    }
}
