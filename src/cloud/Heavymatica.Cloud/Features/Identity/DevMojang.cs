using System.Collections.Concurrent;
using System.Text.Json.Serialization;

namespace Heavymatica.Cloud.Features.Identity;

/// <summary>
/// A stand-in for Mojang's session server, mapped only in Development (D3). Dev clients have no Microsoft-signed
/// session, so they join through <c>/dev/mojang/session/minecraft/join</c> with any token; the service then asks
/// <c>hasJoined</c> here instead of at Mojang. It accepts every join, so it must never be reachable in production.
/// </summary>
internal static class DevMojang
{
    public const string BasePath = "/dev/mojang";

    private static readonly ConcurrentDictionary<string, Guid> Joins = new(StringComparer.Ordinal);

    public static WebApplication MapDevMojang(this WebApplication app)
    {
        if (!app.Environment.IsDevelopment())
        {
            return app;
        }

        var session = app.MapGroup(BasePath + "/session/minecraft").AllowAnonymous();
        session.MapPost("/join", (JoinRequest request) =>
        {
            if (string.IsNullOrEmpty(request.ServerId) || !Guid.TryParse(request.SelectedProfile, out var profile))
            {
                return Results.BadRequest();
            }

            Joins[request.ServerId] = profile;
            return Results.NoContent();
        });
        session.MapGet("/hasJoined", (string username, string serverId) =>
            Joins.TryRemove(serverId, out var profile)
                ? Results.Ok(new { id = profile.ToString("N"), name = username })
                : Results.NoContent());
        return app;
    }

    internal sealed record JoinRequest(
        [property: JsonPropertyName("accessToken")] string? AccessToken,
        [property: JsonPropertyName("selectedProfile")] string? SelectedProfile,
        [property: JsonPropertyName("serverId")] string? ServerId);
}
