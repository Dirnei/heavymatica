using System.Net;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Links;
using Heavymatica.Cloud.Tests.Features.Identity;
using Heavymatica.Cloud.Tests.Features.Schematics;
using Heavymatica.Cloud.Tests.Features.Web;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Links;

[Trait("Category", "Integration")]
public sealed class LinkCodeTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly FakeTimeProvider clock = new(DateTimeOffset.UtcNow);
    private readonly List<IDisposable> disposables = [];
    private CloudFactory? factory;

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    private CloudFactory Factory => factory ??= Track(new CloudFactory(infrastructure) { Clock = clock }.On());

    private HttpClient Client => clientInstance ??= Track(Factory.CreateClient());

    private HttpClient? clientInstance;

    [Fact]
    public async Task A_signed_in_player_gets_a_code_with_its_link_address_and_expiry()
    {
        var (session, game) = await GameAccountAsync("Steve");

        var code = await Linking.IssueAsync(Client, game);

        Assert.Matches("^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}$", code.Code);
        Assert.Equal(new Uri(Linking.LinkBaseUrl + "/link/?code=" + code.Code), code.LinkUrl);
        Assert.Equal(LinkCodeStatus.Open, code.Status);
        Assert.Equal(clock.GetUtcNow().AddMinutes(5), code.ExpiresAt, TimeSpan.FromSeconds(1));
        Assert.Equal(["Steve"], code.Issuer.AccountNames!);
        Assert.Null(code.Redeemer);
        var stored = await LinkCodeDocument.In(Factory.Database).Find(candidate => candidate.Id == code.Id).SingleAsync(Cancel);
        Assert.Equal(LinkCodeFormat.Hash(code.Code!), stored.CodeHash);
        Assert.DoesNotContain(code.Code!.Replace("-", string.Empty, StringComparison.Ordinal), stored.ToBsonDocument().ToString(), StringComparison.Ordinal);
        Assert.Equal(session.Account.Id, stored.Issuer.AccountId);
    }

    [Fact]
    public async Task A_pending_sign_in_gets_a_code_too()
    {
        var (_, pending) = await PendingGameAsync("Newbie");

        var code = await Linking.IssueAsync(Client, pending);

        Assert.Equal(LinkCodeStatus.Open, code.Status);
        Assert.Equal(("minecraft", "Newbie"), (code.Issuer.Provider, code.Issuer.Name));
    }

    [Fact]
    public async Task A_second_code_cancels_the_first()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var first = await Linking.IssueAsync(Client, game);

        var second = await Linking.IssueAsync(Client, game);
        using var redeemFirst = await Linking.RedeemCallAsync(Client, PendingWeb("1"), first.Code!);

        Assert.Equal(LinkCodeStatus.Cancelled, (await Linking.GetAsync(Client, game, first.Id)).Status);
        Assert.Equal(LinkCodeStatus.Open, (await Linking.GetAsync(Client, game, second.Id)).Status);
        Assert.Equal(ProblemCode.Link_code_invalid, (await redeemFirst.ProblemAsync()).Code);
    }

    [Fact]
    public async Task The_issuer_sees_the_code_without_its_secret_and_a_stranger_does_not_see_it()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var (_, stranger) = await GameAccountAsync("Stranger");
        var code = await Linking.IssueAsync(Client, game);

        var seen = await Linking.GetAsync(Client, game, code.Id);
        using var strangerSees = await Linking.GetCallAsync(Client, stranger, code.Id);
        using var missing = await Linking.GetCallAsync(Client, stranger, "0123456789abcdef0123456789abcdef");

        Assert.Equal((code.Id, LinkCodeStatus.Open, (string?)null, (Uri?)null), (seen.Id, seen.Status, seen.Code, seen.LinkUrl));
        Assert.Equal(HttpStatusCode.NotFound, strangerSees.StatusCode);
        Assert.Equal(await missing.Content.ReadAsStringAsync(Cancel), await strangerSees.Content.ReadAsStringAsync(Cancel));
    }

    [Fact]
    public async Task A_cancelled_code_can_no_longer_be_redeemed()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);

        using var cancelled = await Linking.CancelCallAsync(Client, game, code.Id);
        using var redeem = await Linking.RedeemCallAsync(Client, PendingWeb("1"), code.Code!);

        Assert.Equal(HttpStatusCode.NoContent, cancelled.StatusCode);
        Assert.Equal(LinkCodeStatus.Cancelled, (await Linking.GetAsync(Client, game, code.Id)).Status);
        Assert.Equal(HttpStatusCode.BadRequest, redeem.StatusCode);
    }

    [Fact]
    public async Task Without_a_link_address_codes_are_unavailable_and_the_account_says_so()
    {
        var off = Track(new CloudFactory(infrastructure) { Clock = clock });
        using var client = off.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Offline");

        using var response = await Linking.CreateAsync(client, Caller.Bearer(session.AccessToken));
        var me = await Linking.MeAsync(client, Caller.Bearer(session.AccessToken));

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal(ProblemCode.Linking_unavailable, (await response.ProblemAsync()).Code);
        Assert.False(me.LinkingAvailable);
    }

    [Fact]
    public async Task An_open_code_reads_as_expired_after_5_minutes()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);

        clock.Advance(TimeSpan.FromMinutes(5) + TimeSpan.FromSeconds(1));

        Assert.Equal(LinkCodeStatus.Expired, (await Linking.GetAsync(Client, game, code.Id)).Status);
    }

    [Fact]
    public async Task A_pending_github_sign_in_redeems_a_game_code_and_waits_for_the_issuer()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);
        var github = new FakeGitHub { UserId = 4711, Login = "alex-builds" };
        var web = Track(github.Cloud(infrastructure, Factory.DatabaseName, clock).On());
        using var browser = FakeGitHub.Browser(web);
        await FakeGitHub.SignInAsync(browser, "login");

        using var redeemRequest = new HttpRequestMessage(HttpMethod.Post, new Uri("/v1/link-codes/redeem", UriKind.Relative))
        {
            Content = System.Net.Http.Json.JsonContent.Create(new { code = code.Code!.ToLowerInvariant().Replace("-", " ", StringComparison.Ordinal) }),
        };
        redeemRequest.Headers.Add("X-Heavymatica-Request", "1");
        redeemRequest.Headers.Add("Origin", "http://localhost");
        using var response = await browser.SendAsync(redeemRequest, Cancel);
        var redeemed = await response.ReadAsync<LinkCode>();
        var issuerView = await Linking.GetAsync(Client, game, code.Id);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(LinkCodeStatus.Redeemed, redeemed.Status);
        Assert.Null(redeemed.Code);
        Assert.Equal(["Steve"], redeemed.Issuer.AccountNames!);
        Assert.Equal((LinkCodeStatus.Redeemed, "github", "alex-builds"), (issuerView.Status, issuerView.Redeemer!.Provider, issuerView.Redeemer.Name));
        Assert.Equal(clock.GetUtcNow().AddMinutes(5), issuerView.ConfirmBy!.Value, TimeSpan.FromSeconds(1));
        Assert.Equal(1, await IdentityDocument.In(Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_website_account_redeems_a_pending_game_code_and_waits_for_the_issuer()
    {
        var (_, pending) = await PendingGameAsync("Newbie");
        var (_, web) = await WebAccountAsync("octocat");
        var code = await Linking.IssueAsync(Client, pending);

        var redeemed = await Linking.RedeemAsync(Client, web, code.Code!);
        var issuerView = await Linking.GetAsync(Client, pending, code.Id);

        Assert.Equal(LinkCodeStatus.Redeemed, redeemed.Status);
        Assert.Equal(("minecraft", "Newbie"), (redeemed.Issuer.Provider, redeemed.Issuer.Name));
        Assert.Equal(["github:octocat"], issuerView.Redeemer!.AccountProviders!);
        Assert.Empty(issuerView.Redeemer.AccountNames!);
    }

    [Fact]
    public async Task An_account_cannot_redeem_another_accounts_code_and_the_code_stays_open()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var (_, other) = await WebAccountAsync("merger");
        var code = await Linking.IssueAsync(Client, game);

        using var response = await Linking.RedeemCallAsync(Client, other, code.Code!);
        var later = await Linking.RedeemAsync(Client, PendingWeb("1"), code.Code!);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(ProblemCode.Link_code_not_applicable, (await response.ProblemAsync()).Code);
        Assert.Equal(LinkCodeStatus.Redeemed, later.Status);
    }

    [Fact]
    public async Task A_pending_sign_in_cannot_redeem_a_pending_code()
    {
        var (_, pending) = await PendingGameAsync("Newbie");
        var code = await Linking.IssueAsync(Client, pending);

        using var response = await Linking.RedeemCallAsync(Client, PendingWeb("1"), code.Code!);

        Assert.Equal(ProblemCode.Link_code_not_applicable, (await response.ProblemAsync()).Code);
        Assert.Equal(LinkCodeStatus.Open, (await Linking.GetAsync(Client, pending, code.Id)).Status);
    }

    [Fact]
    public async Task Expired_used_and_unknown_codes_get_the_same_answer()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var (_, second) = await GameAccountAsync("Alex");
        var used = await Linking.IssueAsync(Client, game);
        await Linking.RedeemAsync(Client, PendingWeb("1"), used.Code!);
        var expiring = await Linking.IssueAsync(Client, second);
        clock.Advance(TimeSpan.FromMinutes(6));

        using var expired = await Linking.RedeemCallAsync(Client, PendingWeb("2"), expiring.Code!);
        using var usedAgain = await Linking.RedeemCallAsync(Client, PendingWeb("3"), used.Code!);
        using var unknown = await Linking.RedeemCallAsync(Client, PendingWeb("4"), "ZZZZ-ZZZZ");
        using var garbage = await Linking.RedeemCallAsync(Client, PendingWeb("5"), "not a code");

        var answers = new[] { expired, usedAgain, unknown, garbage };
        Assert.All(answers, answer => Assert.Equal(HttpStatusCode.BadRequest, answer.StatusCode));
        var bodies = await Task.WhenAll(answers.Select(answer => answer.Content.ReadAsStringAsync(Cancel)));
        Assert.Single(bodies.Distinct());
        Assert.Contains("link_code_invalid", bodies[0], StringComparison.Ordinal);
    }

    [Fact]
    public async Task Of_two_concurrent_redemptions_exactly_one_wins()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);

        var responses = await Task.WhenAll(Enumerable.Range(1, 6).Select(i => Linking.RedeemCallAsync(Client, PendingWeb(i.ToString(System.Globalization.CultureInfo.InvariantCulture)), code.Code!)));

        Assert.Single(responses, response => response.StatusCode == HttpStatusCode.OK);
        Assert.All(responses.Where(response => response.StatusCode != HttpStatusCode.OK), response => Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode));
        foreach (var response in responses)
        {
            response.Dispose();
        }
    }

    [Fact]
    public async Task Confirming_links_the_redeeming_github_identity_to_the_issuing_account()
    {
        var (session, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);
        await Linking.RedeemAsync(Client, PendingWeb("4711", "alex-builds"), code.Code!);

        var confirmed = await Linking.ConfirmAsync(Client, game, code.Id);

        Assert.Equal(LinkCodeStatus.Linked, confirmed.Status);
        var linked = await IdentityDocument.In(Factory.Database).Find(identity => identity.Provider == "github").SingleAsync(Cancel);
        Assert.Equal((session.Account.Id, "4711", "alex-builds"), (linked.AccountId, linked.Subject, linked.Name));
        Assert.Equal(LinkCodeStatus.Linked, (await Linking.GetAsync(Client, game, code.Id)).Status);
    }

    [Fact]
    public async Task Declining_links_nothing_and_ends_the_code()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);
        var redeemer = PendingWeb("4711");
        await Linking.RedeemAsync(Client, redeemer, code.Code!);

        using var declined = await Linking.CancelCallAsync(Client, game, code.Id);
        using var confirm = await Linking.ConfirmCallAsync(Client, game, code.Id);

        Assert.Equal(HttpStatusCode.NoContent, declined.StatusCode);
        Assert.Equal(LinkCodeStatus.Declined, (await Linking.GetAsync(Client, redeemer, code.Id)).Status);
        Assert.Equal(HttpStatusCode.Conflict, confirm.StatusCode);
        Assert.Equal(1, await IdentityDocument.In(Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_code_not_confirmed_within_5_minutes_of_its_redemption_expires_and_links_nothing()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);
        clock.Advance(TimeSpan.FromMinutes(4));
        await Linking.RedeemAsync(Client, PendingWeb("4711"), code.Code!);
        clock.Advance(TimeSpan.FromMinutes(4));

        var waiting = await Linking.GetAsync(Client, game, code.Id);
        clock.Advance(TimeSpan.FromMinutes(1) + TimeSpan.FromSeconds(1));
        var late = await Linking.GetAsync(Client, game, code.Id);
        using var confirm = await Linking.ConfirmCallAsync(Client, game, code.Id);

        Assert.Equal(LinkCodeStatus.Redeemed, waiting.Status);
        Assert.Equal(LinkCodeStatus.Expired, late.Status);
        Assert.Equal(HttpStatusCode.Conflict, confirm.StatusCode);
        Assert.Equal(1, await IdentityDocument.In(Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task Another_identity_of_the_issuing_account_cannot_confirm_and_the_code_keeps_waiting()
    {
        var (session, game) = await GameAccountAsync("Steve");
        var alt = Guid.NewGuid();
        await Factory.Services.GetRequiredService<Accounts>().AddIdentityAsync(session.Account.Id, Providers.Minecraft, alt.ToString("D"), "SteveBuilds",
            new AuditActor("minecraft", "test"), Cancel);
        using var altClient = Factory.CreateClient();
        var altSession = await SignIn.AsAsync(altClient, alt, "SteveBuilds");
        var code = await Linking.IssueAsync(Client, game);
        var redeemer = PendingWeb("4711");
        await Linking.RedeemAsync(Client, redeemer, code.Code!);

        using var byAlt = await Linking.ConfirmCallAsync(Client, Caller.Bearer(altSession.AccessToken), code.Id);
        using var altDeclines = await Linking.CancelCallAsync(Client, Caller.Bearer(altSession.AccessToken), code.Id);
        using var byRedeemer = await Linking.ConfirmCallAsync(Client, redeemer, code.Id);

        Assert.Equal(altSession.Account.Id, session.Account.Id);
        Assert.Equal(HttpStatusCode.NotFound, byAlt.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, altDeclines.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, byRedeemer.StatusCode);
        Assert.Equal(LinkCodeStatus.Redeemed, (await Linking.GetAsync(Client, game, code.Id)).Status);
        Assert.Equal(LinkCodeStatus.Linked, (await Linking.ConfirmAsync(Client, game, code.Id)).Status);
    }

    [Fact]
    public async Task A_fresh_pending_sign_in_of_the_issuing_identity_may_confirm()
    {
        var (profile, pending) = await PendingGameAsync("Newbie");
        var (_, web) = await WebAccountAsync("octocat");
        var code = await Linking.IssueAsync(Client, pending);
        await Linking.RedeemAsync(Client, web, code.Code!);
        var fresh = await Linking.PendingAsync(Client, profile, "Newbie");

        Assert.Equal(LinkCodeStatus.Linked, (await Linking.ConfirmAsync(Client, Caller.Bearer(fresh.PendingToken), code.Id)).Status);
    }

    [Fact]
    public async Task An_identity_linked_elsewhere_in_between_is_rejected_and_the_code_is_declined()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);
        var redeemer = PendingWeb("4711");
        await Linking.RedeemAsync(Client, redeemer, code.Code!);
        using var created = await Linking.SendAsync(Client, redeemer, HttpMethod.Post, "/v1/web/pending/account");

        using var confirm = await Linking.ConfirmCallAsync(Client, game, code.Id);

        Assert.Equal(HttpStatusCode.OK, created.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, confirm.StatusCode);
        Assert.Equal(ProblemCode.Identity_already_linked, (await confirm.ProblemAsync()).Code);
        Assert.Equal(LinkCodeStatus.Declined, (await Linking.GetAsync(Client, game, code.Id)).Status);
        Assert.Equal(2, await AccountDocument.In(Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task An_account_with_10_identities_takes_no_more()
    {
        var (session, game) = await GameAccountAsync("Steve");
        var accounts = Factory.Services.GetRequiredService<Accounts>();
        for (var i = 0; i < 9; i++)
        {
            await accounts.AddIdentityAsync(session.Account.Id, Providers.Minecraft, Guid.NewGuid().ToString("D"), "Alt" + i, new AuditActor("minecraft", "test"), Cancel);
        }

        var code = await Linking.IssueAsync(Client, game);
        await Linking.RedeemAsync(Client, PendingWeb("4711"), code.Code!);

        using var confirm = await Linking.ConfirmCallAsync(Client, game, code.Id);

        Assert.Equal(ProblemCode.Too_many_identities, (await confirm.ProblemAsync()).Code);
        Assert.Equal(10, await IdentityDocument.In(Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_pending_game_sign_in_joins_a_website_account_and_gets_its_library()
    {
        var (profile, pending) = await PendingGameAsync("Newbie");
        var (webAccount, web) = await WebAccountAsync("octocat");
        using var webClient = Factory.CreateClient();
        webClient.DefaultRequestHeaders.Add("Cookie", $"{WebCookies.Name}={web.Cookie}");
        webClient.DefaultRequestHeaders.Add("X-Heavymatica-Request", "1");
        webClient.DefaultRequestHeaders.Add("Origin", "http://localhost");
        var uploaded = await new Library(webClient).NewAsync("From the web", Library.Fixture("house.nbt"));
        var code = await Linking.IssueAsync(Client, pending);
        await Linking.RedeemAsync(Client, web, code.Code!);
        await Linking.ConfirmAsync(Client, pending, code.Id);

        using var completed = await Linking.CompleteSignInAsync(Client, pending, createAccount: false);
        var session = await completed.ReadAsync<Session>();
        using var gameClient = Factory.CreateClient();
        var library = await new Library(gameClient.Authorize(session.AccessToken)).ListAsync();

        Assert.Equal(HttpStatusCode.OK, completed.StatusCode);
        Assert.Equal((webAccount.Account.Id, profile, "Newbie"), (session.Account.Id, session.Account.Uuid, session.Account.Name));
        Assert.Equal([uploaded.SchematicId], library.Items.Select(item => item.Id));
        Assert.Equal(1, await AccountDocument.In(Factory.Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_pending_github_sign_in_joins_a_game_account_and_gets_a_website_session_of_it()
    {
        var (session, game) = await GameAccountAsync("Steve");
        using var gameClient = Factory.CreateClient();
        var uploaded = await new Library(gameClient.Authorize(session.AccessToken)).NewAsync("From the game", Library.Fixture("house.nbt"));
        var code = await Linking.IssueAsync(Client, game);
        var github = new FakeGitHub { UserId = 4711, Login = "alex-builds" };
        var web = Track(github.Cloud(infrastructure, Factory.DatabaseName, clock).On());
        using var browser = FakeGitHub.Browser(web);
        await FakeGitHub.SignInAsync(browser, "login");
        using var redeem = await SendFromWebsiteAsync(browser, HttpMethod.Post, "/v1/link-codes/redeem", new { code = code.Code });
        await Linking.ConfirmAsync(Client, game, code.Id);
        var redeemerView = await browser.GetFromJsonAsyncChecked<LinkCode>("/v1/link-codes/" + code.Id);

        using var completed = await PendingWebSignInTests.PostAsync(browser, "/v1/web/pending/session");
        var me = await browser.GetFromJsonAsyncChecked<Contract.Account>("/v1/me");
        var library = await new Library(browser).ListAsync();

        Assert.Equal(HttpStatusCode.OK, redeem.StatusCode);
        Assert.Equal(LinkCodeStatus.Linked, redeemerView.Status);
        Assert.Equal(HttpStatusCode.OK, completed.StatusCode);
        Assert.Equal((session.Account.Id, "github", "alex-builds"), (me.Id, me.Provider, me.Name));
        Assert.Equal([uploaded.SchematicId], library.Items.Select(item => item.Id));
    }

    [Fact]
    public async Task A_confirmed_link_is_recorded_with_account_actor_and_client_address()
    {
        var (session, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);
        using var wrong = await Linking.RedeemCallAsync(Client, PendingWeb("4711").From("198.51.100.7"), "ZZZZ-ZZZZ");
        await Linking.RedeemAsync(Client, PendingWeb("4711").From("198.51.100.7"), code.Code!);
        await Linking.ConfirmAsync(Client, game, code.Id);

        var entries = await AuditEntryDocument.In(Factory.Database).Find(entry => entry.Kind != AuditKinds.AccountCreated).SortBy(entry => entry.Id).ToListAsync(Cancel);

        var gameIdentity = await IdentityDocument.In(Factory.Database).Find(identity => identity.Provider == "minecraft").SingleAsync(Cancel);
        var linked = await IdentityDocument.In(Factory.Database).Find(identity => identity.Provider == "github").SingleAsync(Cancel);
        var steve = new AuditActor("minecraft", gameIdentity.Id);
        var redeemer = new AuditActor("github", null, "4711");
        Assert.Equal(
        [
            (AuditKinds.LinkCodeIssued, (string?)session.Account.Id, steve, (string?)code.Id, (string?)CloudFactory.DefaultClientAddress),
            (AuditKinds.LinkCodeRedeemFailed, null, redeemer, null, "198.51.100.7"),
            (AuditKinds.LinkCodeRedeemed, session.Account.Id, redeemer, code.Id, "198.51.100.7"),
            (AuditKinds.IdentityAdded, session.Account.Id, steve, null, null),
            (AuditKinds.LinkCodeConfirmed, session.Account.Id, steve, code.Id, CloudFactory.DefaultClientAddress),
        ], entries.Select(entry => (entry.Kind, entry.AccountId, entry.Actor, entry.CodeId, entry.Ip)));
        Assert.Equal(linked.Id, entries.Single(entry => entry.Kind == AuditKinds.IdentityAdded).IdentityId);
        Assert.All(entries.Where(entry => entry.CodeId is not null || entry.Kind == AuditKinds.LinkCodeRedeemFailed),
            entry => Assert.Equal(entry.At.AddDays(90), entry.ExpireAt!.Value, TimeSpan.FromSeconds(1)));
        Assert.Null(entries.Single(entry => entry.Kind == AuditKinds.IdentityAdded).ExpireAt);
        using var indexes = await Factory.Database.GetCollection<BsonDocument>(AuditEntryDocument.Collection).Indexes.ListAsync(Cancel);
        Assert.Contains(await indexes.ToListAsync(Cancel), index => index["name"] == "expireAt_ttl" && index["expireAfterSeconds"].ToInt64() == 0);
    }

    [Fact]
    public async Task Declines_are_recorded()
    {
        var (_, game) = await GameAccountAsync("Steve");
        var code = await Linking.IssueAsync(Client, game);
        await Linking.RedeemAsync(Client, PendingWeb("4711"), code.Code!);

        using var declined = await Linking.CancelCallAsync(Client, game, code.Id);

        Assert.Equal(1, await AuditEntryDocument.In(Factory.Database).CountDocumentsAsync(entry => entry.Kind == AuditKinds.LinkCodeDeclined && entry.CodeId == code.Id,
            cancellationToken: Cancel));
    }

    [Fact]
    public async Task Startup_creates_the_link_code_indexes()
    {
        _ = Client;

        using var indexes = await Factory.Database.GetCollection<BsonDocument>(LinkCodeDocument.Collection).Indexes.ListAsync(Cancel);
        var byName = (await indexes.ToListAsync(Cancel)).ToDictionary(index => index["name"].AsString);

        Assert.True(byName["codeHash_open"]["unique"].AsBoolean);
        Assert.Equal(new BsonDocument("status", "open"), byName["codeHash_open"]["partialFilterExpression"]);
        Assert.Equal(86400, byName["expiresAt_ttl"]["expireAfterSeconds"].ToInt64());
    }

    private async Task<(Session Session, Caller Caller)> GameAccountAsync(string name)
    {
        using var client = Factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), name);
        return (session, Caller.Bearer(session.AccessToken));
    }

    private async Task<(Guid Profile, Caller Caller)> PendingGameAsync(string name)
    {
        var profile = Guid.NewGuid();
        var pending = await Linking.PendingAsync(Client, profile, name);
        return (profile, Caller.Bearer(pending.PendingToken));
    }

    private async Task<(SignedInIdentity SignedIn, Caller Caller)> WebAccountAsync(string login)
    {
        var (signedIn, _, cookie) = await WebCookies.SessionAsync(Factory, login);
        return (signedIn, Caller.WebSession(cookie));
    }

    private Caller PendingWeb(string subject, string login = "visitor") => Caller.WebPending(Linking.PendingCookie(Factory, subject, login));

    private static async Task<HttpResponseMessage> SendFromWebsiteAsync(HttpClient browser, HttpMethod method, string path, object body)
    {
        using var request = new HttpRequestMessage(method, new Uri(path, UriKind.Relative)) { Content = System.Net.Http.Json.JsonContent.Create(body) };
        request.Headers.Add("X-Heavymatica-Request", "1");
        request.Headers.Add("Origin", "http://localhost");
        return await browser.SendAsync(request, Cancel);
    }

    private T Track<T>(T disposable)
        where T : IDisposable
    {
        disposables.Add(disposable);
        return disposable;
    }
}

internal static class BrowserReads
{
    public static async Task<T> GetFromJsonAsyncChecked<T>(this HttpClient browser, string path)
    {
        ArgumentNullException.ThrowIfNull(browser);
        using var response = await browser.GetAsync(new Uri(path, UriKind.Relative), TestContext.Current.CancellationToken);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return await response.ReadAsync<T>();
    }
}
