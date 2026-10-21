namespace Heavymatica.Cloud.Features.Identity;

internal static partial class IdentityLog
{
    [LoggerMessage(EventId = 1001, Level = LogLevel.Warning,
        Message = "No Identity:SigningKey configured; using a temporary key. Access tokens stop working when the service restarts.")]
    public static partial void TemporarySigningKey(ILogger logger);

    [LoggerMessage(EventId = 1002, Level = LogLevel.Warning, Message = "Mojang's session server was unavailable during a sign-in.")]
    public static partial void MojangUnavailable(ILogger logger, Exception exception);
}
