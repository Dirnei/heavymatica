using System.Net;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Identity;

/// <summary>A player as Mojang's session server reports them.</summary>
internal sealed record MojangProfile(Guid Id, string Name);

/// <summary>Mojang could not be asked: unreachable, timed out or answered with an error.</summary>
internal sealed class MojangUnavailableException(string message, Exception? inner = null) : Exception(message, inner);

/// <summary>Asks Mojang's session server whether a player joined a server ID (D3).</summary>
internal sealed class MojangSessions(HttpClient http, IOptions<IdentityOptions> options)
{
    public const string HttpClientName = "mojang";

    /// <summary>The verified profile, or null when Mojang says the player did not join.</summary>
    public async Task<MojangProfile?> HasJoinedAsync(string name, string serverId, CancellationToken cancellationToken)
    {
        var url = new Uri(
            options.Value.MojangSessionUrl.ToString().TrimEnd('/')
            + "/session/minecraft/hasJoined?username=" + Uri.EscapeDataString(name) + "&serverId=" + Uri.EscapeDataString(serverId));
        HttpResponseMessage response;
        try
        {
            response = await http.GetAsync(url, cancellationToken);
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException && !cancellationToken.IsCancellationRequested)
        {
            throw new MojangUnavailableException("Mojang's session server is unreachable.", e);
        }

        using (response)
        {
            if (response.StatusCode is HttpStatusCode.NoContent or HttpStatusCode.NotFound)
            {
                return null;
            }

            if (!response.IsSuccessStatusCode)
            {
                throw new MojangUnavailableException($"Mojang's session server answered {(int)response.StatusCode}.");
            }

            var profile = await response.Content.ReadFromJsonAsync<HasJoinedAnswer>(cancellationToken);
            return profile is { Id: { Length: > 0 } id, Name: { Length: > 0 } profileName } && Guid.TryParseExact(id, "N", out var uuid)
                ? new MojangProfile(uuid, profileName)
                : throw new MojangUnavailableException("Mojang's session server answered with an unreadable profile.");
        }
    }

    private sealed record HasJoinedAnswer([property: JsonPropertyName("id")] string? Id, [property: JsonPropertyName("name")] string? Name);
}
