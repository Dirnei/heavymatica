using System.Security.Cryptography;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Identity;

internal enum ChallengeOutcome
{
    Accepted,
    Unknown,
    Used,
    Expired,
}

internal sealed class Challenges(IMongoDatabase database, TimeProvider time, IOptions<IdentityOptions> options)
{
    private readonly IMongoCollection<ChallengeDocument> challenges = ChallengeDocument.In(database);

    public async Task<ChallengeDocument> IssueAsync(CancellationToken cancellationToken)
    {
        var challenge = new ChallengeDocument
        {
            Id = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(16)),
            ServerId = Convert.ToHexStringLower(RandomNumberGenerator.GetBytes(20)),
            ExpiresAt = (time.GetUtcNow() + options.Value.ChallengeLifetime).UtcDateTime,
        };
        await challenges.InsertOneAsync(challenge, cancellationToken: cancellationToken);
        return challenge;
    }

    /// <summary>
    /// Marks the challenge used in one atomic step, so it is accepted at most once even across service instances,
    /// whether or not the sign-in then succeeds.
    /// </summary>
    public async Task<(ChallengeOutcome Outcome, ChallengeDocument? Challenge)> ConsumeAsync(string challengeId, CancellationToken cancellationToken)
    {
        var claimed = await challenges.FindOneAndUpdateAsync(
            Builders<ChallengeDocument>.Filter.Eq(challenge => challenge.Id, challengeId) & Builders<ChallengeDocument>.Filter.Eq(challenge => challenge.Used, false),
            Builders<ChallengeDocument>.Update.Set(challenge => challenge.Used, true),
            cancellationToken: cancellationToken);
        if (claimed is null)
        {
            var existing = await challenges.Find(challenge => challenge.Id == challengeId).AnyAsync(cancellationToken);
            return (existing ? ChallengeOutcome.Used : ChallengeOutcome.Unknown, null);
        }

        return claimed.ExpiresAt <= time.GetUtcNow().UtcDateTime
            ? (ChallengeOutcome.Expired, claimed)
            : (ChallengeOutcome.Accepted, claimed);
    }
}
