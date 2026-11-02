using System.Security.Cryptography;
using System.Text;
using Heavymatica.Cloud.Features.Links;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Admin;

/// <summary>A setup code as the server command prints it, and where to enter it.</summary>
internal sealed record IssuedSetupCode(string Code, DateTime ExpiresAt, string? AdminId, Uri Site);

/// <summary>
/// One-time setup codes from the server command: 12 symbols of the link-code alphabet, shown as
/// <c>XXXX-XXXX-XXXX</c>. Only the SHA-256 of the normalised code is stored. A new code replaces the previous one.
/// </summary>
internal sealed class SetupCodes(IMongoDatabase database, TimeProvider time, IOptions<AdminOptions> options)
{
    public const int Length = 12;

    private readonly IMongoCollection<SetupCodeDocument> codes = SetupCodeDocument.In(database);

    public static string Hash(string input) => Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(LinkCodeFormat.Normalise(input))));

    public static string Format(string code) => code.Length == Length ? $"{code[..4]}-{code[4..8]}-{code[8..]}" : code;

    public static bool IsWellFormed(string input)
    {
        var normalised = LinkCodeFormat.Normalise(input);
        return normalised.Length == Length && normalised.All(c => LinkCodeFormat.Alphabet.Contains(c, StringComparison.Ordinal));
    }

    /// <summary>A new code, replacing any previous one; for a recovery code the admin must exist.</summary>
    public async Task<IssuedSetupCode?> IssueAsync(string? adminId, CancellationToken cancellationToken)
    {
        if (adminId is not null && !await AdminUser.In(database).Find(admin => admin.Id == adminId).AnyAsync(cancellationToken))
        {
            return null;
        }

        Span<char> symbols = stackalloc char[Length];
        for (var i = 0; i < Length; i++)
        {
            symbols[i] = LinkCodeFormat.Alphabet[RandomNumberGenerator.GetInt32(LinkCodeFormat.Alphabet.Length)];
        }

        var code = new string(symbols);
        var now = time.GetUtcNow().UtcDateTime;
        var document = new SetupCodeDocument
        {
            Id = SetupCodeDocument.CurrentId,
            CodeHash = Hash(code),
            AdminId = adminId,
            CreatedAt = now,
            ExpiresAt = now + options.Value.SetupCodeLifetime,
        };
        await codes.ReplaceOneAsync(candidate => candidate.Id == SetupCodeDocument.CurrentId, document, new ReplaceOptions { IsUpsert = true },
            cancellationToken);
        return new IssuedSetupCode(Format(code), document.ExpiresAt, adminId, options.Value.Site);
    }

    /// <summary>The open code with this hash, or null when it is unknown, expired or used.</summary>
    public async Task<SetupCodeDocument?> FindOpenAsync(string codeHash, CancellationToken cancellationToken) =>
        await codes.Find(Open(codeHash)).FirstOrDefaultAsync(cancellationToken);

    /// <summary>Uses the code up; false when it was used, replaced or expired in the meantime.</summary>
    public async Task<bool> ConsumeAsync(string codeHash, CancellationToken cancellationToken)
    {
        var result = await codes.UpdateOneAsync(Open(codeHash),
            Builders<SetupCodeDocument>.Update.Set(code => code.UsedAt, time.GetUtcNow().UtcDateTime), cancellationToken: cancellationToken);
        return result.ModifiedCount == 1;
    }

    public Task ReleaseAsync(string codeHash, CancellationToken cancellationToken) =>
        codes.UpdateOneAsync(code => code.Id == SetupCodeDocument.CurrentId && code.CodeHash == codeHash,
            Builders<SetupCodeDocument>.Update.Unset(code => code.UsedAt), cancellationToken: cancellationToken);

    private FilterDefinition<SetupCodeDocument> Open(string codeHash)
    {
        var filter = Builders<SetupCodeDocument>.Filter;
        return filter.Eq(code => code.Id, SetupCodeDocument.CurrentId)
            & filter.Eq(code => code.CodeHash, codeHash)
            & filter.Eq(code => code.UsedAt, null)
            & filter.Gt(code => code.ExpiresAt, time.GetUtcNow().UtcDateTime);
    }
}
