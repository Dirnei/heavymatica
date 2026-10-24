using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Identity;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Schematics.Tags;

internal sealed class TagsApi(IHttpContextAccessor http, ListTags list) : ITagsController
{
    public async Task<ActionResult<TagList>> ListTagsAsync(CancellationToken cancellationToken = default) =>
        await list.ListAsync(http.HttpContext!.User.AccountId(), cancellationToken);
}
