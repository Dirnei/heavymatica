using System.Buffers.Text;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>
/// Invitation links: 32 random bytes, base64url, in the fragment of <c>{site}/invite/#token</c>, so the token never
/// reaches server logs or Referer headers. Only the SHA-256 of the token is stored.
/// </summary>
internal sealed class Invitations(IMongoDatabase database, TimeProvider time, IOptions<AdminOptions> options)
{
    private readonly IMongoCollection<InvitationDocument> invitations = InvitationDocument.In(database);

    public static string Hash(string token) => Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(token.Trim())));

    public Uri LinkFor(string token) => new(options.Value.Site.GetLeftPart(UriPartial.Path).TrimEnd('/') + "/invite/#" + token);

    public async Task<(InvitationDocument Invitation, string Token)> CreateAsync(string kind, string? adminId, string createdBy, CancellationToken cancellationToken)
    {
        var token = Base64Url.EncodeToString(RandomNumberGenerator.GetBytes(32));
        var now = time.GetUtcNow().UtcDateTime;
        var invitation = new InvitationDocument
        {
            Id = AdminUser.NewId(),
            TokenHash = Hash(token),
            Kind = kind,
            AdminId = adminId,
            CreatedBy = createdBy,
            CreatedAt = now,
            ExpiresAt = now + options.Value.InvitationLifetime,
        };
        await invitations.InsertOneAsync(invitation, cancellationToken: cancellationToken);
        return (invitation, token);
    }

    public Task<List<InvitationDocument>> ListOpenAsync(CancellationToken cancellationToken) =>
        invitations.Find(Open()).SortByDescending(invitation => invitation.CreatedAt).ToListAsync(cancellationToken);

    public async Task<InvitationDocument?> FindOpenByTokenAsync(string token, CancellationToken cancellationToken) =>
        await invitations.Find(Open() & Builders<InvitationDocument>.Filter.Eq(invitation => invitation.TokenHash, Hash(token))).FirstOrDefaultAsync(cancellationToken);

    public async Task<InvitationDocument?> FindOpenAsync(string invitationId, CancellationToken cancellationToken) =>
        await invitations.Find(Open() & Builders<InvitationDocument>.Filter.Eq(invitation => invitation.Id, invitationId)).FirstOrDefaultAsync(cancellationToken);

    public async Task<bool> ConsumeAsync(string invitationId, CancellationToken cancellationToken)
    {
        var result = await invitations.UpdateOneAsync(Open() & Builders<InvitationDocument>.Filter.Eq(invitation => invitation.Id, invitationId),
            Builders<InvitationDocument>.Update.Set(invitation => invitation.UsedAt, time.GetUtcNow().UtcDateTime), cancellationToken: cancellationToken);
        return result.ModifiedCount == 1;
    }

    public Task ReleaseAsync(string invitationId, CancellationToken cancellationToken) =>
        invitations.UpdateOneAsync(invitation => invitation.Id == invitationId && invitation.RevokedAt == null,
            Builders<InvitationDocument>.Update.Unset(invitation => invitation.UsedAt), cancellationToken: cancellationToken);

    public async Task<bool> RevokeAsync(string invitationId, CancellationToken cancellationToken)
    {
        var result = await invitations.UpdateOneAsync(Open() & Builders<InvitationDocument>.Filter.Eq(invitation => invitation.Id, invitationId),
            Builders<InvitationDocument>.Update.Set(invitation => invitation.RevokedAt, time.GetUtcNow().UtcDateTime), cancellationToken: cancellationToken);
        return result.ModifiedCount == 1;
    }

    private FilterDefinition<InvitationDocument> Open()
    {
        var filter = Builders<InvitationDocument>.Filter;
        return filter.Eq(invitation => invitation.UsedAt, null)
            & filter.Eq(invitation => invitation.RevokedAt, null)
            & filter.Gt(invitation => invitation.ExpiresAt, time.GetUtcNow().UtcDateTime);
    }
}
