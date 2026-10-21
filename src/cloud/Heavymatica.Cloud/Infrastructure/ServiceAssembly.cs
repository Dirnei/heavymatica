namespace Heavymatica.Cloud.Infrastructure;

/// <summary>
/// Names the service assembly for in-process test hosting. The AppHost has its own
/// top-level <c>Program</c>, so tests cannot refer to the service by that name.
/// </summary>
public sealed class ServiceAssembly
{
    private ServiceAssembly()
    {
    }
}
