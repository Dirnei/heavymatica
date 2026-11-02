using System.Globalization;
using Microsoft.Extensions.Options;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>Who acted, and from where. The admin is unknown on failed sign-ins and code attempts.</summary>
internal sealed record AdminActor(string? AdminId, string? AdminName, string Ip)
{
    public static AdminActor Anonymous(string ip) => new(null, null, ip);

    public static AdminActor Of(AdminUser admin, string ip)
    {
        ArgumentNullException.ThrowIfNull(admin);
        return new AdminActor(admin.Id, admin.DisplayName, ip);
    }
}

/// <summary>Every admin action and every failed attempt to get admin access, newest first.</summary>
internal sealed class AdminAudit(IMongoDatabase database, TimeProvider time, IOptions<AdminOptions> options)
{
    private readonly IMongoCollection<AdminAuditDocument> entries = AdminAuditDocument.In(database);

    public Task RecordAsync(AdminActor actor, string kind, string? target, Dictionary<string, string>? details, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(actor);
        var now = time.GetUtcNow().UtcDateTime;
        return entries.InsertOneAsync(new AdminAuditDocument
        {
            Id = ObjectId.GenerateNewId(),
            At = now,
            AdminId = actor.AdminId,
            AdminName = actor.AdminName,
            Ip = actor.Ip,
            Kind = kind,
            Target = target,
            Details = details ?? [],
            ExpireAt = now + options.Value.AuditRetention,
        }, cancellationToken: cancellationToken);
    }

    /// <summary>One page, newest first, and the cursor of the next page when there is one.</summary>
    public async Task<(List<AdminAuditDocument> Items, string? NextCursor)?> PageAsync(string? cursor, int limit, CancellationToken cancellationToken)
    {
        var filter = Builders<AdminAuditDocument>.Filter;
        var query = filter.Empty;
        if (!string.IsNullOrEmpty(cursor))
        {
            if (Parse(cursor) is not { } after)
            {
                return null;
            }

            query = filter.Lt(entry => entry.At, after.At) | (filter.Eq(entry => entry.At, after.At) & filter.Lt(entry => entry.Id, after.Id));
        }

        var page = await entries.Find(query).SortByDescending(entry => entry.At).ThenByDescending(entry => entry.Id).Limit(limit + 1)
            .ToListAsync(cancellationToken);
        if (page.Count <= limit)
        {
            return (page, null);
        }

        page.RemoveAt(limit);
        var last = page[^1];
        return (page, last.At.Ticks.ToString(CultureInfo.InvariantCulture) + "." + last.Id);
    }

    private static (DateTime At, ObjectId Id)? Parse(string cursor)
    {
        var dot = cursor.IndexOf('.', StringComparison.Ordinal);
        return dot > 0
            && long.TryParse(cursor.AsSpan(0, dot), NumberStyles.None, CultureInfo.InvariantCulture, out var ticks)
            && ticks <= DateTime.MaxValue.Ticks
            && ObjectId.TryParse(cursor[(dot + 1)..], out var id)
                ? (new DateTime(ticks, DateTimeKind.Utc), id)
                : null;
    }
}
