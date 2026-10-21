using Microsoft.AspNetCore.Mvc.ModelBinding;

namespace Heavymatica.Cloud.Infrastructure.Contract;

/// <summary>
/// The raw request body of a binary operation (octet-stream or PNG). NSwag types binary bodies as
/// <see cref="IFormFile"/>; this implementation reads straight from the request without buffering,
/// so uploads keep backpressure (D2, D5). The stream can be read once.
/// </summary>
internal sealed class StreamingBody(HttpRequest request) : IFormFile
{
    public string ContentType => request.ContentType ?? string.Empty;

    public string ContentDisposition => string.Empty;

    public IHeaderDictionary Headers => request.Headers;

    /// <summary>The declared Content-Length, or -1 when the client streams without one.</summary>
    public long Length => request.ContentLength ?? -1;

    public string Name => "body";

    public string FileName => string.Empty;

    public Stream OpenReadStream() => request.Body;

    public void CopyTo(Stream target) => request.Body.CopyTo(target);

    public Task CopyToAsync(Stream target, CancellationToken cancellationToken = default) =>
        request.Body.CopyToAsync(target, cancellationToken);
}

/// <summary>Binds <see cref="IFormFile"/> parameters of non-multipart requests to <see cref="StreamingBody"/>.</summary>
internal sealed class StreamingBodyBinder : IModelBinder
{
    public Task BindModelAsync(ModelBindingContext bindingContext)
    {
        var request = bindingContext.HttpContext.Request;
        if (request.HasFormContentType)
        {
            bindingContext.Result = ModelBindingResult.Failed();
            return Task.CompletedTask;
        }

        bindingContext.Result = ModelBindingResult.Success(new StreamingBody(request));
        return Task.CompletedTask;
    }
}

internal sealed class StreamingBodyBinderProvider : IModelBinderProvider
{
    public IModelBinder? GetBinder(ModelBinderProviderContext context) =>
        context.Metadata.ModelType == typeof(IFormFile) ? new StreamingBodyBinder() : null;
}
