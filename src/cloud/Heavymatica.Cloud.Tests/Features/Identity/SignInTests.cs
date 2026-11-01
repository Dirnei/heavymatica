using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Tests.Features.Links;
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

        var profile = Guid.NewGuid();

        var session = await SignIn.AsAsync(client, profile, "Newcomer");

        Assert.Equal(0, session.Account.UsedBytes);
        Assert.Equal(100L * 1024 * 1024, session.Account.QuotaBytes);
        var account = Assert.Single(await AccountDocument.In(factory.Database).Find(_ => true).ToListAsync(TestContext.Current.CancellationToken));
        var identity = Assert.Single(await IdentityDocument.In(factory.Database).Find(_ => true).ToListAsync(TestContext.Current.CancellationToken));
        Assert.Equal(session.Account.Id, account.Id);
        Assert.Matches("^[0-9a-f]{32}$", account.Id);
        Assert.Equal((account.Id, "minecraft", profile.ToString("D"), "Newcomer"), (identity.AccountId, identity.Provider, identity.Subject, identity.Name));
    }

    [Fact]
    public async Task A_repeat_sign_in_reaches_the_same_account()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();
        var first = await SignIn.AsAsync(client, profile, "Regular");

        var second = await SignIn.AsAsync(client, profile, "Regular");

        Assert.Equal(first.Account.Id, second.Account.Id);
        Assert.Equal(1, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: TestContext.Current.CancellationToken));
        Assert.Equal(1, await IdentityDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task A_renamed_player_keeps_their_account_with_the_new_name()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();
        var before = await SignIn.AsAsync(client, profile, "OldName");

        var session = await SignIn.AsAsync(client, profile, "NewName");

        Assert.Equal("NewName", session.Account.Name);
        Assert.Equal(before.Account.Id, session.Account.Id);
        var identities = await IdentityDocument.In(factory.Database).Find(_ => true).ToListAsync(TestContext.Current.CancellationToken);
        Assert.Equal("NewName", Assert.Single(identities).Name);
    }

    [Fact]
    public async Task Me_answers_with_the_signed_in_account()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();
        var session = await SignIn.AsAsync(client, profile, "Viewer");

        var me = await client.Authorize(session.AccessToken)
            .GetFromJsonAsync<Contract.Account>(new Uri("/v1/me", UriKind.Relative), SignIn.Json, TestContext.Current.CancellationToken);

        Assert.Equal(session.Account.Id, me!.Id);
        Assert.Equal("minecraft", me.Provider);
        Assert.Equal(profile, me.Uuid);
        Assert.Equal("Viewer", me.Name);
    }

    [Fact]
    public async Task The_sign_in_call_signs_a_known_identity_in_without_questions()
    {
        var factory = Factory().On();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();
        var first = await SignIn.AsAsync(client, profile, "Known");

        var result = await Linking.SignInAsync(client, profile, "Known");

        Assert.Null(result.Pending);
        Assert.Equal(first.Account.Id, result.Session!.Account.Id);
        Assert.False(string.IsNullOrEmpty(result.Session.RefreshToken));
    }

    [Fact]
    public async Task An_unknown_identity_gets_an_account_at_once_while_linking_is_off()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();

        var result = await Linking.SignInAsync(client, profile, "Direct");

        Assert.Null(result.Pending);
        Assert.Equal(profile, result.Session!.Account.Uuid);
        Assert.Equal(1, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task An_unknown_identity_gets_a_pending_sign_in_and_no_account_while_linking_is_on()
    {
        var factory = Factory().On();
        using var client = factory.CreateClient();

        var result = await Linking.SignInAsync(client, Guid.NewGuid(), "Pending");

        Assert.Null(result.Session);
        Assert.False(string.IsNullOrEmpty(result.Pending!.PendingToken));
        Assert.Equal(("minecraft", "Pending"), (result.Pending.Identity.Provider, result.Pending.Identity.Name));
        Assert.True(result.Pending.JoinAvailable);
        Assert.Equal(clock.GetUtcNow().AddMinutes(10), result.Pending.ExpiresAt, TimeSpan.FromSeconds(1));
        Assert.Equal(0, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
        Assert.Equal(0, await IdentityDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_pending_sign_in_creates_an_empty_account_and_gets_a_session()
    {
        var factory = Factory().On();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();
        var pending = await Linking.PendingAsync(client, profile, "Creator");

        using var response = await Linking.CompleteSignInAsync(client, Caller.Bearer(pending.PendingToken), createAccount: true);
        var session = await response.ReadAsync<Session>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal((profile, "Creator", 0L), (session.Account.Uuid, session.Account.Name, session.Account.UsedBytes));
        var identity = await IdentityDocument.In(factory.Database).Find(_ => true).SingleAsync(Cancel);
        Assert.Equal((session.Account.Id, profile.ToString("D")), (identity.AccountId, identity.Subject));
        Assert.Empty((await new Schematics.Library(client.Authorize(session.AccessToken)).ListAsync()).Items);
    }

    [Fact]
    public async Task A_replayed_create_returns_the_same_account()
    {
        var factory = Factory().On();
        using var client = factory.CreateClient();
        var pending = await Linking.PendingAsync(client, Guid.NewGuid(), "Replayer");

        using var first = await Linking.CompleteSignInAsync(client, Caller.Bearer(pending.PendingToken), createAccount: true);
        using var replay = await Linking.CompleteSignInAsync(client, Caller.Bearer(pending.PendingToken), createAccount: true);

        Assert.Equal(HttpStatusCode.OK, replay.StatusCode);
        Assert.Equal((await first.ReadAsync<Session>()).Account.Id, (await replay.ReadAsync<Session>()).Account.Id);
        Assert.Equal(1, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task Completing_without_creating_before_a_join_asks_for_the_choice()
    {
        var factory = Factory().On();
        using var client = factory.CreateClient();
        var pending = await Linking.PendingAsync(client, Guid.NewGuid(), "Undecided");

        using var response = await Linking.CompleteSignInAsync(client, Caller.Bearer(pending.PendingToken), createAccount: false);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(ProblemCode.Account_choice_required, (await response.ProblemAsync()).Code);
        Assert.Equal(0, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task An_older_mod_signing_in_through_verify_still_gets_an_account_while_linking_is_on()
    {
        var factory = Factory().On();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();

        var session = await SignIn.AsAsync(client, profile, "OldMod");

        Assert.Equal(profile, session.Account.Uuid);
        Assert.Equal(1, await AccountDocument.In(factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure) { Clock = clock };
        disposables.Add(factory);
        return factory;
    }
}
