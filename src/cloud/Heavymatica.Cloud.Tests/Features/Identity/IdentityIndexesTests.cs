using Heavymatica.Cloud.Features.Identity;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Identity;

[Trait("Category", "Integration")]
public sealed class IdentityIndexesTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly CloudFactory factory = new(infrastructure);

    public void Dispose() => factory.Dispose();

    [Fact]
    public async Task Startup_creates_the_ttl_and_lookup_indexes()
    {
        using var client = factory.CreateClient();

        var challenges = await Indexes(ChallengeDocument.Collection);
        var sessions = await Indexes(SessionDocument.Collection);

        Assert.Equal(0, challenges["expiresAt_ttl"]["expireAfterSeconds"].ToInt64());
        Assert.Equal((long)TimeSpan.FromDays(30).TotalSeconds, sessions["lastUsedAt_ttl"]["expireAfterSeconds"].ToInt64());
        Assert.True(sessions.ContainsKey("accountId"));
    }

    private async Task<Dictionary<string, BsonDocument>> Indexes(string collection)
    {
        using var cursor = await factory.Database.GetCollection<BsonDocument>(collection).Indexes.ListAsync(TestContext.Current.CancellationToken);
        var indexes = await cursor.ToListAsync(TestContext.Current.CancellationToken);
        return indexes.ToDictionary(index => index["name"].AsString);
    }
}
