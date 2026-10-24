using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Schematics.Folders;

internal sealed class FoldersApi(IHttpContextAccessor http, ListFolders list, RenameFolder rename) : IFoldersController
{
    private string Owner => http.HttpContext!.User.AccountId();

    public async Task<ActionResult<FolderList>> ListFoldersAsync(CancellationToken cancellationToken = default) =>
        await list.ListAsync(Owner, cancellationToken);

    public Task<ActionResult<MoveResult>> RenameFolderAsync(RenameFolderRequest body, CancellationToken cancellationToken = default) =>
        rename.RenameAsync(Owner, body, cancellationToken);
}
