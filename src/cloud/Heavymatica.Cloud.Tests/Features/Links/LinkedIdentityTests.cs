using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Tests.Features.Identity;
using Heavymatica.Cloud.Tests.Features.Schematics;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Links;

/// <summary>Accounts with several identities: what they see, unlinking, and the limits on link codes.</summary>
[Trait("Category", "Integration")]
public sealed class LinkedIdentityTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly FakeTimeProvider clock = new(DateTimeOffset.UtcNow);
    private readonly List<IDisposable> disposables = [];
    private CloudFactory? factory;
    private HttpClient? clientInstance;

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    private CloudFactory Factory => factory ??= Track(new CloudFactory(infrastructure) { Clock = clock }.On());

    private HttpClient Client => clientInstance ??= Track(Factory.CreateClient());

    private Accounts Accounts => Factory.Services.GetRequiredService<Accounts>();

    [Fact]
    public async Task Me_lists_the_identities_and_marks_the_one_in_use_from_the_game_and_the_website()
    {
        var (session, game) = await GameAccountAsync("Steve");
        var github = await LinkGitHubAsync(session.Account.Id, "steve-builds");

        var fromGame = await Linking.MeAsync(Client, game);
        var fromWebsite = await Linking.MeAsync(Client, github.Caller);

        Assert.True(fromGame.LinkingAvailable);
        Assert.Equal([("minecraft", "Steve", true), ("github", "steve-builds", false)],
            fromGame.Identities!.Select(identity => (identity.Provider, identity.Name, identity.Current)).OrderBy(identity => identity.Provider == "github"));
        Assert.Equal([("minecraft", "Steve", false), ("github", "steve-builds", true)],
            fromWebsite.Identities!.Select(identity => (identity.Provider, identity.Name, identity.Current)).OrderBy(identity => identity.Provider == "github"));
        Assert.Equal(fromGame.Id, fromWebsite.Id);
    }

    [Fact]
    public async Task Without_a_link_address_me_says_linking_is_not_available()
    {
        using var off = Track(new CloudFactory(infrastructure));
        using var client = off.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Steve");

        var me = await Linking.MeAsync(client, Caller.Bearer(session.AccessToken));

        Assert.False(me.LinkingAvailable);
        Assert.Equal([("minecraft", "Steve", true)], me.Identities!.Select(identity => (identity.Provider, identity.Name, identity.Current)));
    }

    [Fact]
    public async Task The_eleventh_redemption_of_one_redeemer_is_rate_limited_and_leaves_a_valid_code_open()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var valid = await Linking.IssueAsync(Client, game);
        var redeemer = Caller.WebPending(Linking.PendingCookie(Factory, "4711", "guesser"));
        for (var attempt = 0; attempt < 10; attempt++)
        {
            using var wrong = await Linking.RedeemCallAsync(Client, redeemer, "2222-2222");
            Assert.Equal(HttpStatusCode.BadRequest, wrong.StatusCode);
        }

        using var limited = await Linking.RedeemCallAsync(Client, redeemer, valid.Code!);

        Assert.Equal(HttpStatusCode.TooManyRequests, limited.StatusCode);
        Assert.Equal(ProblemCode.Rate_limited, (await limited.ProblemAsync()).Code);
        Assert.Equal(LinkCodeStatus.Open, (await Linking.GetAsync(Client, game, valid.Id)).Status);
    }

    [Fact]
    public async Task The_redemption_limit_resets_after_10_minutes()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var redeemer = Caller.WebPending(Linking.PendingCookie(Factory, "4711", "guesser"));
        for (var attempt = 0; attempt < 10; attempt++)
        {
            using var wrong = await Linking.RedeemCallAsync(Client, redeemer, "2222-2222");
        }

        clock.Advance(TimeSpan.FromMinutes(10) + TimeSpan.FromSeconds(1));
        var valid = await Linking.IssueAsync(Client, game);

        Assert.Equal(LinkCodeStatus.Redeemed, (await Linking.RedeemAsync(Client, Caller.WebPending(Linking.PendingCookie(Factory, "4711", "guesser")), valid.Code!)).Status);
    }

    [Fact]
    public async Task One_client_address_gets_30_redemptions_whoever_sends_them()
    {
        const string address = "198.51.100.7";
        for (var attempt = 0; attempt < 30; attempt++)
        {
            var redeemer = Caller.WebPending(Linking.PendingCookie(Factory, (5000 + attempt).ToString(System.Globalization.CultureInfo.InvariantCulture), "guesser")).From(address);
            using var wrong = await Linking.RedeemCallAsync(Client, redeemer, "2222-2222");
            Assert.Equal(HttpStatusCode.BadRequest, wrong.StatusCode);
        }

        using var limited = await Linking.RedeemCallAsync(Client, Caller.WebPending(Linking.PendingCookie(Factory, "9999", "guesser")).From(address), "2222-2222");
        using var elsewhere = await Linking.RedeemCallAsync(Client, Caller.WebPending(Linking.PendingCookie(Factory, "9998", "guesser")).From("198.51.100.8"), "2222-2222");

        Assert.Equal(HttpStatusCode.TooManyRequests, limited.StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, elsewhere.StatusCode);
    }

    [Fact]
    public async Task An_issuer_gets_10_codes_per_hour()
    {
        var (_, game) = await GameAccountAsync("Steve");
        for (var issued = 0; issued < 10; issued++)
        {
            await Linking.IssueAsync(Client, game);
        }

        using var limited = await Linking.CreateAsync(Client, game);
        clock.Advance(TimeSpan.FromHours(1) + TimeSpan.FromSeconds(1));
        using var later = await Linking.CreateAsync(Client, game);

        Assert.Equal(HttpStatusCode.TooManyRequests, limited.StatusCode);
        Assert.Equal(ProblemCode.Rate_limited, (await limited.ProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.Created, later.StatusCode);
    }

    [Fact]
    public async Task Unlinking_another_accounts_identity_is_not_found()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var (stranger, _) = await GameAccountAsync("Alex");
        var strangerIdentity = Assert.Single(await Accounts.ListIdentitiesAsync(stranger.Account.Id, Cancel));

        using var response = await UnlinkAsync(game, strangerIdentity.Id);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Single(await Accounts.ListIdentitiesAsync(stranger.Account.Id, Cancel));
    }

    [Fact]
    public async Task The_last_identity_cannot_be_unlinked()
    {
        var (session, game) = await GameAccountAsync("Steve");
        var only = Assert.Single(await Accounts.ListIdentitiesAsync(session.Account.Id, Cancel));

        using var response = await UnlinkAsync(game, only.Id);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(ProblemCode.Last_identity, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task Unlinking_the_callers_own_identity_ends_its_sessions_and_it_signs_in_as_unknown_afterwards()
    {
        var profile = Guid.NewGuid();
        using var gameClient = Factory.CreateClient();
        var session = await SignIn.AsAsync(gameClient, profile, "Steve");
        var github = await LinkGitHubAsync(session.Account.Id, "steve-builds");
        var own = (await Accounts.ListIdentitiesAsync(session.Account.Id, Cancel)).Single(identity => identity.Provider == Providers.Minecraft);

        using var unlinked = await UnlinkAsync(Caller.Bearer(session.AccessToken), own.Id);
        using var refresh = await Client.PostAsJsonAsync(new Uri("/v1/auth/refresh", UriKind.Relative), new { refreshToken = session.RefreshToken }, Cancel);
        var again = await Linking.SignInAsync(Client, profile, "Steve");

        Assert.Equal(HttpStatusCode.NoContent, unlinked.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, refresh.StatusCode);
        Assert.Null(again.Session);
        Assert.NotNull(again.Pending);
        Assert.Equal([("github", "steve-builds", true)], (await Linking.MeAsync(Client, github.Caller)).Identities!.Select(identity => (identity.Provider, identity.Name, identity.Current)));
        var entry = await AuditEntryDocument.In(Factory.Database).Find(candidate => candidate.AccountId == session.Account.Id && candidate.Kind == AuditKinds.IdentityRemoved)
            .SingleAsync(Cancel);
        Assert.Equal(own.Id, entry.IdentityId);
    }

    [Fact]
    public async Task An_unlinked_github_identitys_website_cookie_answers_401()
    {
        var (session, game) = await GameAccountAsync("Steve");
        var github = await LinkGitHubAsync(session.Account.Id, "steve-builds");

        using var unlinked = await UnlinkAsync(game, github.IdentityId);
        using var me = await Linking.SendAsync(Client, github.Caller, HttpMethod.Get, "/v1/me");

        Assert.Equal(HttpStatusCode.NoContent, unlinked.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, me.StatusCode);
    }

    [Fact]
    public async Task Two_identities_of_one_account_share_the_library_its_schematics_and_the_quota()
    {
        var limited = Factory.With("Storage:DefaultQuotaBytes", "1300");
        var steve = await LibraryOfNewAccountAsync(limited, "Steve");
        var alt = await LibraryOfAltAsync(limited, steve, "SteveBuilds");

        var house = await steve.NewAsync("House", Library.Fixture("house.nbt"));
        var altSees = await alt.ListAsync();
        using var altVersion = await alt.UploadVersionAsync(house.SchematicId, Library.Fixture("house.v3.schem"));
        using var overQuota = await alt.UploadNewAsync("Barn", Library.Fixture("house.litematica-v7.litematic"));

        Assert.Equal([house.SchematicId], altSees.Items.Select(item => item.Id));
        Assert.Equal(HttpStatusCode.Created, altVersion.StatusCode);
        Assert.Equal([1, 2], (await steve.DetailsAsync(house.SchematicId)).Versions.Select(version => version.Number).Order());
        Assert.Equal(HttpStatusCode.UnprocessableEntity, overQuota.StatusCode);
        Assert.Equal(ProblemCode.Over_quota, (await overQuota.ProblemAsync()).Code);
        Assert.Equal((await steve.MeAsync()).UsedBytes, (await alt.MeAsync()).UsedBytes);
    }

    [Fact]
    public async Task An_unlinked_identity_starts_a_new_account_that_cannot_see_the_old_library()
    {
        var steve = await LibraryOfNewAccountAsync(Factory, "Steve");
        var altProfile = Guid.NewGuid();
        var alt = await LibraryOfAltAsync(Factory, steve, "SteveBuilds", altProfile);
        var house = await steve.NewAsync("House", Library.Fixture("house.nbt"));
        var accountId = (await steve.MeAsync()).Id;
        var altIdentity = (await Accounts.ListIdentitiesAsync(accountId, Cancel)).Single(identity => identity.Subject == altProfile.ToString("D"));
        Assert.Equal(RemoveIdentityOutcome.Removed, await Accounts.RemoveIdentityAsync(accountId, altIdentity.Id, new AuditActor("minecraft", "test"), Cancel));

        var pending = await Linking.PendingAsync(alt.Client, altProfile, "SteveBuilds");
        using var created = await Linking.CompleteSignInAsync(alt.Client, Caller.Bearer(pending.PendingToken), createAccount: true);
        var fresh = await created.ReadAsync<Session>();
        alt.Client.Authorize(fresh.AccessToken);
        using var oldHouse = await alt.Client.GetAsync(new Uri("/v1/schematics/" + house.SchematicId, UriKind.Relative), Cancel);

        Assert.NotEqual(accountId, fresh.Account.Id);
        Assert.Empty((await alt.ListAsync()).Items);
        Assert.Equal(HttpStatusCode.NotFound, oldHouse.StatusCode);
    }

    private async Task<(Session Session, Caller Caller)> GameAccountAsync(string name)
    {
        using var client = Factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), name);
        return (session, Caller.Bearer(session.AccessToken));
    }

    /// <summary>Adds a GitHub identity to the account, as a confirmed link does, and starts a website session of it.</summary>
    private async Task<(string IdentityId, Caller Caller)> LinkGitHubAsync(string accountId, string login)
    {
        var subject = Random.Shared.Next(1, int.MaxValue).ToString(System.Globalization.CultureInfo.InvariantCulture);
        Assert.Equal(AddIdentityOutcome.Added, await Accounts.AddIdentityAsync(accountId, Providers.GitHub, subject, login, new AuditActor("minecraft", "test"), Cancel));
        var signedIn = (await Accounts.FindByIdentityAsync(Providers.GitHub, subject, Cancel))!;
        var sessionId = await Factory.Services.GetRequiredService<Sessions>().StartWebAsync(accountId, signedIn.Identity.Id, Cancel);
        return (signedIn.Identity.Id, Caller.WebSession(WebCookies.CookieFor(Factory, signedIn, sessionId)));
    }

    private Task<HttpResponseMessage> UnlinkAsync(Caller caller, string identityId) =>
        Linking.SendAsync(Client, caller, HttpMethod.Delete, "/v1/me/identities/" + identityId);

    private async Task<Library> LibraryOfNewAccountAsync(CloudFactory cloud, string name)
    {
        var library = await Library.SignedInAsync(cloud, name);
        Track(library.Client);
        return library;
    }

    /// <summary>Adds a second Minecraft identity to the library's account and signs it in.</summary>
    private async Task<Library> LibraryOfAltAsync(CloudFactory cloud, Library main, string name, Guid? profile = null)
    {
        var alt = profile ?? Guid.NewGuid();
        var accountId = (await main.MeAsync()).Id;
        Assert.Equal(AddIdentityOutcome.Added, await cloud.Services.GetRequiredService<Accounts>()
            .AddIdentityAsync(accountId, Providers.Minecraft, alt.ToString("D"), name, new AuditActor("minecraft", "test"), Cancel));
        var client = Track(cloud.CreateClient());
        var session = await SignIn.AsAsync(client, alt, name);
        Assert.Equal(accountId, session.Account.Id);
        client.Authorize(session.AccessToken);
        return new Library(client);
    }

    private T Track<T>(T disposable)
        where T : IDisposable
    {
        disposables.Add(disposable);
        return disposable;
    }
}
