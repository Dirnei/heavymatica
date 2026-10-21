using System.Security.Cryptography;
using Akka.Actor;
using Akka.IO;
using Akka.Streams;
using Akka.Streams.Dsl;
using Akka.Streams.IO;
using Akka.Util;
using Heavymatica.Cloud.Formats;

namespace Heavymatica.Cloud.Features.Schematics.Upload;

/// <summary>An upload body written to a temporary file, with its size and SHA-256. Deletes the file on dispose.</summary>
internal sealed class SpooledUpload(string path, long size, string sha256) : IDisposable
{
    public string Path { get; } = path;

    public long Size { get; } = size;

    public string Sha256 { get; } = sha256;

    public void Dispose() => File.Delete(Path);
}

/// <summary>
/// Streams an upload body to a temporary file with Akka.Streams (D5): backpressure reaches the client, at most
/// 16 MiB is accepted, and the SHA-256 is computed on the way, so the file is never held in memory.
/// </summary>
internal sealed class UploadSpool(ActorSystem system)
{
    private const int ChunkSize = 64 * 1024;

    public async Task<SpooledUpload> SpoolAsync(Stream body, long maxBytes, CancellationToken cancellationToken)
    {
        var path = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "heavymatica-upload-" + Guid.NewGuid().ToString("N"));
        using var hash = IncrementalHash.CreateHash(HashAlgorithmName.SHA256);
        long total = 0;
        try
        {
            var written = await Source
                .UnfoldAsync<Stream, ByteString>(body, async stream =>
                {
                    var buffer = new byte[ChunkSize];
                    var read = await stream.ReadAsync(buffer, cancellationToken);
                    return read == 0 ? Option<(Stream, ByteString)>.None : (stream, ByteString.FromBytes(buffer, 0, read));
                })
                .Select(chunk =>
                {
                    total += chunk.Count;
                    if (total > maxBytes)
                    {
                        throw new SchematicRejectedException(RejectionCode.FileTooLarge, $"The file is larger than {maxBytes} bytes.");
                    }

                    hash.AppendData(chunk.ToArray());
                    return chunk;
                })
                .RunWith(FileIO.ToFile(new FileInfo(path)), system.Materializer());
            if (!written.WasSuccessful)
            {
                throw Unwrap(written.Error);
            }

            return new SpooledUpload(path, total, Convert.ToHexStringLower(hash.GetHashAndReset()));
        }
        catch (Exception e)
        {
            File.Delete(path);
            var cause = Unwrap(e);
            if (!ReferenceEquals(cause, e))
            {
                System.Runtime.ExceptionServices.ExceptionDispatchInfo.Throw(cause);
            }

            throw;
        }
    }

    private static Exception Unwrap(Exception error)
    {
        var cause = error;
        while (cause is AbruptIOTerminationException or AggregateException && cause.InnerException is not null)
        {
            cause = cause.InnerException;
        }

        return cause;
    }
}
