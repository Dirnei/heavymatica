using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using Heavymatica.Cloud.Contract;

namespace Heavymatica.Cloud.Tests;

/// <summary>Drives the sign-in handshake against the DevMojang slice, as a dev client does.</summary>
internal static class SignIn
{
    public static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    public static async Task<Challenge> ChallengeAsync(HttpClient client)
    {
        using var response = await client.PostAsync(new Uri("/v1/auth/challenge", UriKind.Relative), null, TestContext.Current.CancellationToken);
        response.EnsureSuccessStatusCode();
        return (await response.Content.ReadFromJsonAsync<Challenge>(Json, TestContext.Current.CancellationToken))!;
    }

    public static async Task JoinAsync(HttpClient client, Guid profile, string serverId)
    {
        using var response = await client.PostAsJsonAsync(new Uri("/dev/mojang/session/minecraft/join", UriKind.Relative),
            new { accessToken = "dev", selectedProfile = profile.ToString("N"), serverId }, TestContext.Current.CancellationToken);
        response.EnsureSuccessStatusCode();
    }

    public static Task<HttpResponseMessage> VerifyAsync(HttpClient client, string challengeId, string name) =>
        client.PostAsJsonAsync(new Uri("/v1/auth/verify", UriKind.Relative), new { challengeId, name }, TestContext.Current.CancellationToken);

    /// <summary>A complete sign-in: challenge, join, verify.</summary>
    public static async Task<Session> AsAsync(HttpClient client, Guid profile, string name)
    {
        var challenge = await ChallengeAsync(client);
        await JoinAsync(client, profile, challenge.ServerId);
        using var response = await VerifyAsync(client, challenge.ChallengeId, name);
        response.EnsureSuccessStatusCode();
        return (await response.Content.ReadFromJsonAsync<Session>(Json, TestContext.Current.CancellationToken))!;
    }

    public static HttpClient Authorize(this HttpClient client, string accessToken)
    {
        ArgumentNullException.ThrowIfNull(client);
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", accessToken);
        return client;
    }

    public static async Task<Problem> ProblemAsync(this HttpResponseMessage response)
    {
        ArgumentNullException.ThrowIfNull(response);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        return (await response.Content.ReadFromJsonAsync<Problem>(Json, TestContext.Current.CancellationToken))!;
    }
}
