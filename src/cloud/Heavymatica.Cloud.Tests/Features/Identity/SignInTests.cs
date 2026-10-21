using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Identity;

[Trait("Category", "Integration")]
public sealed class SignInTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly FakeTimeProvider clock = new(DateTimeOffset.UtcNow);
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    [Fact]
    public async Task Verified_sign_in_returns_tokens_for_the_uuid_mojang_reported()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();

        var session = await SignIn.AsAsync(client, profile, "Dirnei");

        Assert.Equal(profile, session.Account.Uuid);
        Assert.Equal("Dirnei", session.Account.Name);
        Assert.False(string.IsNullOrEmpty(session.AccessToken));
        Assert.False(string.IsNullOrEmpty(session.RefreshToken));
        Assert.Equal(clock.GetUtcNow().AddMinutes(15), session.AccessTokenExpiresAt, TimeSpan.FromSeconds(1));
    }

    [Fact]
    public async Task Challenges_are_unique_and_expire_after_60_seconds()
    {
        var factory = Factory();
        using var client = factory.CreateClient();

        var first = await SignIn.ChallengeAsync(client);
        var second = await SignIn.ChallengeAsync(client);

        Assert.NotEqual(first.ServerId, second.ServerId);
        Assert.NotEqual(first.ChallengeId, second.ChallengeId);
        Assert.Equal(clock.GetUtcNow().AddSeconds(60), first.ExpiresAt, TimeSpan.FromSeconds(1));
    }

    [Fact]
    public async Task An_expired_challenge_is_rejected_and_issues_no_tokens()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var challenge = await SignIn.ChallengeAsync(client);
        await SignIn.JoinAsync(client, Guid.NewGuid(), challenge.ServerId);

        clock.Advance(TimeSpan.FromSeconds(61));
        using var response = await SignIn.VerifyAsync(client, challenge.ChallengeId, "Late");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(ProblemCode.Challenge_expired, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task A_challenge_is_accepted_only_once()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var challenge = await SignIn.ChallengeAsync(client);
        await SignIn.JoinAsync(client, Guid.NewGuid(), challenge.ServerId);
        using var first = await SignIn.VerifyAsync(client, challenge.ChallengeId, "Once");

        using var replay = await SignIn.VerifyAsync(client, challenge.ChallengeId, "Once");

        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, replay.StatusCode);
        Assert.Equal(ProblemCode.Challenge_used, (await replay.ProblemAsync()).Code);
    }

    [Fact]
    public async Task A_failed_attempt_also_uses_up_the_challenge()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var challenge = await SignIn.ChallengeAsync(client);
        using var notJoined = await SignIn.VerifyAsync(client, challenge.ChallengeId, "Early");
        await SignIn.JoinAsync(client, Guid.NewGuid(), challenge.ServerId);

        using var retry = await SignIn.VerifyAsync(client, challenge.ChallengeId, "Early");

        Assert.Equal(HttpStatusCode.Unauthorized, notJoined.StatusCode);
        Assert.Equal(ProblemCode.Challenge_used, (await retry.ProblemAsync()).Code);
    }

    [Fact]
    public async Task A_player_mojang_does_not_confirm_is_rejected()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var challenge = await SignIn.ChallengeAsync(client);

        using var response = await SignIn.VerifyAsync(client, challenge.ChallengeId, "Impostor");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Identity_not_confirmed, (await response.ProblemAsync()).Code);
        Assert.Equal(0, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task Mojang_being_unreachable_is_a_distinct_error()
    {
        var factory = Factory().With("Identity:MojangSessionUrl", "http://127.0.0.1:1");
        using var client = factory.CreateClient();
        var challenge = await SignIn.ChallengeAsync(client);

        using var response = await SignIn.VerifyAsync(client, challenge.ChallengeId, "Anyone");

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal(ProblemCode.Identity_provider_unavailable, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task First_sign_in_creates_an_empty_account()
    {
        var factory = Factory();
        using var client = factory.CreateClient();

        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Newcomer");

        Assert.Equal(0, session.Account.UsedBytes);
        Assert.Equal(100L * 1024 * 1024, session.Account.QuotaBytes);
    }

    [Fact]
    public async Task A_renamed_player_keeps_their_account_with_the_new_name()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();
        await SignIn.AsAsync(client, profile, "OldName");

        var session = await SignIn.AsAsync(client, profile, "NewName");

        Assert.Equal("NewName", session.Account.Name);
        var accounts = await AccountDocument.In(factory.Database).Find(_ => true).ToListAsync(TestContext.Current.CancellationToken);
        Assert.Equal("NewName", Assert.Single(accounts).Name);
    }

    [Fact]
    public async Task Me_answers_with_the_signed_in_account()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Viewer");

        var me = await client.Authorize(session.AccessToken)
            .GetFromJsonAsync<Contract.Account>(new Uri("/v1/me", UriKind.Relative), SignIn.Json, TestContext.Current.CancellationToken);

        Assert.Equal(session.Account.Uuid, me!.Uuid);
        Assert.Equal("Viewer", me.Name);
    }

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure) { Clock = clock };
        disposables.Add(factory);
        return factory;
    }
}
