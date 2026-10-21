using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Features.Schematics.Delete;
using Heavymatica.Cloud.Features.Schematics.Details;
using Heavymatica.Cloud.Features.Schematics.Links;
using Heavymatica.Cloud.Features.Schematics.Listing;
using Heavymatica.Cloud.Features.Schematics.Organize;
using Heavymatica.Cloud.Features.Schematics.Thumbnails;
using Heavymatica.Cloud.Features.Schematics.Upload;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Schematics;

internal sealed class SchematicsApi(
    IHttpContextAccessor http,
    ListSchematics list,
    GetSchematic get,
    UploadSchematic upload,
    OrganizeSchematic organize,
    DeleteSchematics delete,
    AttachThumbnail thumbnails,
    CreateLinks links) : ISchematicsController
{
    private string Owner => http.HttpContext!.User.AccountId();

    public Task<ActionResult<SchematicPage>> ListSchematicsAsync(string? q, string? folder, string? tag, string? cursor, int limit, CancellationToken cancellationToken = default) =>
        list.ListAsync(Owner, q, folder, tag, cursor, limit, cancellationToken);

    public Task<ActionResult<UploadResult>> UploadSchematicAsync(string name, IFormFile body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        return upload.NewAsync(Owner, name, body.OpenReadStream(), cancellationToken);
    }

    public Task<ActionResult<SchematicDetails>> GetSchematicAsync(string schematicId, CancellationToken cancellationToken = default) =>
        get.GetAsync(Owner, schematicId, cancellationToken);

    public Task<ActionResult<SchematicDetails>> OrganizeSchematicAsync(OrganizeRequest body, string schematicId, CancellationToken cancellationToken = default) =>
        organize.OrganizeAsync(Owner, schematicId, body, cancellationToken);

    public Task<IActionResult> DeleteSchematicAsync(string schematicId, CancellationToken cancellationToken = default) =>
        delete.DeleteSchematicAsync(Owner, schematicId, cancellationToken);

    public Task<ActionResult<UploadResult>> UploadVersionAsync(IFormFile body, string schematicId, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        return upload.VersionAsync(Owner, schematicId, body.OpenReadStream(), cancellationToken);
    }

    public Task<IActionResult> DeleteVersionAsync(string schematicId, int versionNumber, CancellationToken cancellationToken = default) =>
        delete.DeleteVersionAsync(Owner, schematicId, versionNumber, cancellationToken);

    public Task<IActionResult> AttachThumbnailAsync(IFormFile body, string schematicId, int versionNumber, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        return thumbnails.AttachAsync(Owner, schematicId, versionNumber, body.OpenReadStream(), cancellationToken);
    }

    public Task<ActionResult<DownloadLinks>> CreateLinksAsync(string schematicId, int versionNumber, CancellationToken cancellationToken = default) =>
        links.CreateAsync(Owner, schematicId, versionNumber, cancellationToken);
}
