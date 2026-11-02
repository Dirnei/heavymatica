using Heavymatica.Cloud.Contract.Admin;
using Microsoft.AspNetCore.Mvc;

namespace Heavymatica.Cloud.Features.Admin;

internal sealed class AuditApi(AdminAudit audit) : IAdminAuditController
{
    public async Task<ActionResult<AuditPage>> ListAuditAsync(string? cursor, int limit, CancellationToken cancellationToken = default)
    {
        if (limit is < 1 or > 100)
        {
            return AdminProblems.Invalid("The limit is 1 to 100.");
        }

        if (await audit.PageAsync(cursor, limit, cancellationToken) is not { } page)
        {
            return AdminProblems.Invalid("The cursor is not valid.");
        }

        return new AuditPage
        {
            Items = page.Items.Select(entry => new AdminAuditEntry
            {
                Id = entry.Id.ToString(),
                At = AdminContract.Utc(entry.At),
                AdminId = entry.AdminId,
                AdminName = entry.AdminName,
                Ip = entry.Ip,
                Kind = entry.Kind,
                Target = entry.Target,
                Details = entry.Details,
            }).ToList(),
            NextCursor = page.NextCursor,
        };
    }
}
