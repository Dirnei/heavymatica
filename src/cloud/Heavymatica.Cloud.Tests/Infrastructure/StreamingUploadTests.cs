using System.Net;
using System.Net.Http.Headers;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure;
using Microsoft.AspNetCore.Hosting.Server;
using Microsoft.AspNetCore.Hosting.Server.Features;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;

namespace Heavymatica.Cloud.Tests.Infrastructure;

/// <summary>
/// Uploads must reach the slice as a live stream. The client sends the first MiB and then waits until
/// the slice has read from the body before sending the rest. If anything buffered the whole body before calling
/// the slice, the client would never get that signal and the upload would time out.
/// </summary>
public sealed class StreamingUploadTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly CloudFactory factory = new(infrastructure);

    public void Dispose() => factory.Dispose();

    private const int BodySize = 16 * 1024 * 1024;
    private const int FirstPart = 1024 * 1024;

    [Fact]
    public async Task Upload_body_reaches_the_slice_while_the_client_is_still_sending()
    {
        var drain = new DrainingSchematics();
        using var host = factory.WithWebHostBuilder(builder =>
            builder.ConfigureTestServices(services => services.AddScoped<ISchematicsController>(_ => drain)));
        // Real Kestrel and a plain HttpClient: the in-memory test client buffers request bodies itself.
        host.UseKestrel(0);
        host.StartServer();
        var address = host.Services.GetRequiredService<IServer>().Features.GetRequiredFeature<IServerAddressesFeature>().Addresses.First();
        using var client = new HttpClient { BaseAddress = new Uri(address) };
        client.Authorize(host.Services.GetRequiredService<AccessTokens>().Issue(TestIdentities.Minecraft(AccountDocument.NewId(), "Streamer"), "session").Token);

        using var content = new PausingContent(drain.FirstBytesRead.Task);
        content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(TestContext.Current.CancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(30));

        using var response = await client.PostAsync(new Uri("/v1/schematics?name=big", UriKind.Relative), content, timeout.Token);

        Assert.Equal(HttpStatusCode.Accepted, response.StatusCode);
        Assert.Equal(BodySize, drain.LastBytesRead);
        Assert.True(drain.BytesReadBeforeClientFinished < BodySize, "The slice only saw the body after it was fully received.");
    }

    /// <summary>Sends the first MiB, waits for the slice to start reading, then sends the rest from one reused buffer.</summary>
    private sealed class PausingContent(Task sliceStartedReading) : HttpContent
    {
        protected override async Task SerializeToStreamAsync(Stream stream, TransportContext? context)
        {
            var chunk = new byte[64 * 1024];
            for (var written = 0; written < BodySize; written += chunk.Length)
            {
                if (written == FirstPart)
                {
                    await stream.FlushAsync();
                    await sliceStartedReading.WaitAsync(TimeSpan.FromSeconds(20));
                }

                await stream.WriteAsync(chunk);
            }
        }

        protected override bool TryComputeLength(out long length)
        {
            length = BodySize;
            return true;
        }
    }

    private sealed class DrainingSchematics : ISchematicsController
    {
        public TaskCompletionSource FirstBytesRead { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);

        public long LastBytesRead { get; private set; }

        public long BytesReadBeforeClientFinished { get; private set; } = -1;

        public async Task<ActionResult<UploadResult>> UploadSchematicAsync(string name, string? folder, IFormFile body, CancellationToken cancellationToken = default)
        {
            var buffer = new byte[64 * 1024];
            await using var stream = body.OpenReadStream();
            long total = 0;
            int read;
            while ((read = await stream.ReadAsync(buffer, cancellationToken)) > 0)
            {
                if (total == 0)
                {
                    BytesReadBeforeClientFinished = read;
                    FirstBytesRead.TrySetResult();
                }

                total += read;
            }

            LastBytesRead = total;
            return new StatusCodeResult(StatusCodes.Status202Accepted);
        }

        public Task<ActionResult<SchematicPage>> ListSchematicsAsync(string? q, string? folder, bool includeSubfolders, bool unfiled, string? tag, string? cursor, int limit,
            CancellationToken cancellationToken = default) => throw new NotSupportedException();

        public Task<ActionResult<MoveResult>> MoveSchematicsAsync(MoveRequest body, CancellationToken cancellationToken = default) => throw new NotSupportedException();

        public Task<ActionResult<SchematicDetails>> GetSchematicAsync(string schematicId, CancellationToken cancellationToken = default) => throw new NotSupportedException();

        public Task<ActionResult<SchematicDetails>> OrganizeSchematicAsync(OrganizeRequest body, string schematicId, CancellationToken cancellationToken = default) => throw new NotSupportedException();

        public Task<IActionResult> DeleteSchematicAsync(string schematicId, CancellationToken cancellationToken = default) => throw new NotSupportedException();

        public Task<ActionResult<UploadResult>> UploadVersionAsync(IFormFile body, string schematicId, CancellationToken cancellationToken = default) => throw new NotSupportedException();

        public Task<IActionResult> DeleteVersionAsync(string schematicId, int versionNumber, CancellationToken cancellationToken = default) => throw new NotSupportedException();

        public Task<IActionResult> AttachThumbnailAsync(IFormFile body, string schematicId, int versionNumber, CancellationToken cancellationToken = default) => throw new NotSupportedException();

        public Task<ActionResult<Problem>> GetThumbnailAsync(string schematicId, int versionNumber, CancellationToken cancellationToken = default) =>
            throw new NotSupportedException();

        public Task<ActionResult<DownloadLinks>> CreateLinksAsync(string schematicId, int versionNumber, CancellationToken cancellationToken = default) => throw new NotSupportedException();
    }
}
