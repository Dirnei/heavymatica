using System.Security.Claims;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Features.Links;

/// <summary>
/// Link codes. The game issues a code as an account or a pending sign-in, the website redeems it as the complementary
/// kind, and nothing is linked until the issuing identity confirms. Every change of state is one conditional update,
/// so a code is redeemed and resolved at most once.
/// </summary>
internal sealed class LinksApi(
    IMongoDatabase database,
    Accounts accounts,
    AccountAudit audit,
    TimeProvider time,
    IOptions<AccountsOptions> options,
    IHttpContextAccessor http) : ILinksController
{
    private const int IssueAttempts = 5;

    private readonly IMongoCollection<LinkCodeDocument> codes = LinkCodeDocument.In(database);

    private AccountsOptions Options => options.Value;

    private HttpContext Context => http.HttpContext!;

    private string? ClientIp => Context.Connection.RemoteIpAddress?.ToString();

    public async Task<ActionResult<LinkCode>> CreateLinkCodeAsync(CancellationToken cancellationToken = default)
    {
        if (Options.LinkBaseUrl is not { } baseUrl)
        {
            return Problems.Result(StatusCodes.Status503ServiceUnavailable, ProblemCode.Linking_unavailable, "Linking sign-ins is not available right now.");
        }

        if (await CallerAsync(cancellationToken) is not { } issuer)
        {
            return AccountGone();
        }

        var now = time.GetUtcNow().UtcDateTime;
        var actor = ActorOf(issuer);
        if (await audit.CountSinceAsync(AuditKinds.LinkCodeIssued, actor, now - TimeSpan.FromHours(1), cancellationToken) >= Options.LinkIssuesPerHour)
        {
            return Problems.Result(StatusCodes.Status429TooManyRequests, ProblemCode.Rate_limited, "Too many link codes. Wait a while and try again.");
        }

        var filter = Builders<LinkCodeDocument>.Filter;
        await codes.UpdateManyAsync(
            IssuedBy(issuer) & filter.Eq(code => code.Status, LinkCodeStates.Open),
            Builders<LinkCodeDocument>.Update.Set(code => code.Status, LinkCodeStates.Cancelled).Set(code => code.ResolvedAt, now),
            cancellationToken: cancellationToken);

        for (var attempt = 1; ; attempt++)
        {
            var code = LinkCodeFormat.Generate();
            var document = new LinkCodeDocument
            {
                Id = AccountDocument.NewId(),
                CodeHash = LinkCodeFormat.Hash(code),
                Issuer = issuer,
                Status = LinkCodeStates.Open,
                CreatedAt = now,
                ExpiresAt = now + Options.LinkCodeLifetime,
            };
            try
            {
                await codes.InsertOneAsync(document, cancellationToken: cancellationToken);
            }
            catch (MongoWriteException e) when (e.WriteError.Category == ServerErrorCategory.DuplicateKey && attempt < IssueAttempts)
            {
                continue;
            }

            await audit.RecordCodeAsync(issuer.AccountId, AuditKinds.LinkCodeIssued, actor, document.Id, ClientIp, Options.LinkAuditRetention, cancellationToken);
            var contract = await ToContractAsync(document, cancellationToken);
            contract.Code = LinkCodeFormat.Format(code);
            contract.LinkUrl = LinkCodeFormat.LinkUrl(baseUrl, code);
            return new ObjectResult(contract) { StatusCode = StatusCodes.Status201Created };
        }
    }

    public async Task<ActionResult<LinkCode>> RedeemLinkCodeAsync(RedeemRequest body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        if (await CallerAsync(cancellationToken) is not { } redeemer)
        {
            return AccountGone();
        }

        var now = time.GetUtcNow().UtcDateTime;
        var normalised = LinkCodeFormat.Normalise(body.Code);
        var filter = Builders<LinkCodeDocument>.Filter;
        var redeemable = filter.Eq(code => code.Status, LinkCodeStates.Open) & filter.Gt(code => code.ExpiresAt, now);
        var found = LinkCodeFormat.IsWellFormed(normalised)
            ? await codes.Find(filter.Eq(code => code.CodeHash, LinkCodeFormat.Hash(normalised)) & redeemable).FirstOrDefaultAsync(cancellationToken)
            : null;
        if (found is null)
        {
            return await RedeemFailedAsync(redeemer, null, cancellationToken);
        }

        var complementary = found.Issuer.Kind == LinkPartyKinds.Account ? LinkPartyKinds.Pending : LinkPartyKinds.Account;
        if (redeemer.Kind != complementary)
        {
            await audit.RecordCodeAsync(TargetAccount(found.Issuer, redeemer), AuditKinds.LinkCodeRedeemFailed, ActorOf(redeemer), found.Id, ClientIp,
                Options.LinkAuditRetention, cancellationToken);
            return Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Link_code_not_applicable, found.Issuer.Kind == LinkPartyKinds.Account
                ? "This code adds a sign-in to an account. Use it while signed in with a sign-in that has no account yet."
                : "This code joins a new sign-in to an account. Use it while signed in to the account it should join.");
        }

        var redeemed = await codes.FindOneAndUpdateAsync(
            filter.Eq(code => code.Id, found.Id) & redeemable,
            Builders<LinkCodeDocument>.Update.Set(code => code.Status, LinkCodeStates.Redeemed)
                .Set(code => code.Redeemer, redeemer)
                .Set(code => code.RedeemedAt, now)
                .Set(code => code.ConfirmBy, now + Options.LinkConfirmLifetime),
            new FindOneAndUpdateOptions<LinkCodeDocument> { ReturnDocument = ReturnDocument.After },
            cancellationToken);
        if (redeemed is null)
        {
            return await RedeemFailedAsync(redeemer, found.Id, cancellationToken);
        }

        await audit.RecordCodeAsync(TargetAccount(redeemed.Issuer, redeemer), AuditKinds.LinkCodeRedeemed, ActorOf(redeemer), redeemed.Id, ClientIp,
            Options.LinkAuditRetention, cancellationToken);
        return await ToContractAsync(redeemed, cancellationToken);
    }

    public async Task<ActionResult<LinkCode>> GetLinkCodeAsync(string codeId, CancellationToken cancellationToken = default)
    {
        var caller = CallerClaims();
        var code = await codes.Find(candidate => candidate.Id == codeId).FirstOrDefaultAsync(cancellationToken);
        return code is null || !(IsParty(code.Issuer, caller) || (code.Redeemer is { } redeemer && IsParty(redeemer, caller)))
            ? CodeNotFound()
            : await ToContractAsync(code, cancellationToken);
    }

    public async Task<IActionResult> CancelLinkCodeAsync(string codeId, CancellationToken cancellationToken = default)
    {
        var caller = CallerClaims();
        var code = await codes.Find(candidate => candidate.Id == codeId).FirstOrDefaultAsync(cancellationToken);
        if (IssuerOnly(code, caller) is { } refused)
        {
            return refused;
        }

        var now = time.GetUtcNow().UtcDateTime;
        var filter = Builders<LinkCodeDocument>.Filter;
        var cancelled = await codes.UpdateOneAsync(
            filter.Eq(candidate => candidate.Id, codeId) & filter.Eq(candidate => candidate.Status, LinkCodeStates.Open),
            Builders<LinkCodeDocument>.Update.Set(candidate => candidate.Status, LinkCodeStates.Cancelled).Set(candidate => candidate.ResolvedAt, now),
            cancellationToken: cancellationToken);
        if (cancelled.ModifiedCount == 0
            && await DeclineAsync(filter.Eq(candidate => candidate.Id, codeId) & filter.Eq(candidate => candidate.Status, LinkCodeStates.Redeemed)
                & filter.Gt(candidate => candidate.ConfirmBy, now), now, cancellationToken) is { } declined)
        {
            await audit.RecordCodeAsync(TargetAccount(declined.Issuer, declined.Redeemer), AuditKinds.LinkCodeDeclined, ActorOf(declined.Issuer), declined.Id,
                ClientIp, Options.LinkAuditRetention, cancellationToken);
        }

        return new NoContentResult();
    }

    public async Task<ActionResult<LinkCode>> ConfirmLinkCodeAsync(string codeId, CancellationToken cancellationToken = default)
    {
        var caller = CallerClaims();
        var code = await codes.Find(candidate => candidate.Id == codeId).FirstOrDefaultAsync(cancellationToken);
        if (IssuerOnly(code, caller) is { } refused)
        {
            return refused;
        }

        if (await CallerAsync(cancellationToken) is null)
        {
            return AccountGone();
        }

        var now = time.GetUtcNow().UtcDateTime;
        var filter = Builders<LinkCodeDocument>.Filter;
        var linked = await codes.FindOneAndUpdateAsync(
            filter.Eq(candidate => candidate.Id, codeId) & filter.Eq(candidate => candidate.Status, LinkCodeStates.Redeemed)
                & filter.Gt(candidate => candidate.ConfirmBy, now),
            Builders<LinkCodeDocument>.Update.Set(candidate => candidate.Status, LinkCodeStates.Linked).Set(candidate => candidate.ResolvedAt, now),
            new FindOneAndUpdateOptions<LinkCodeDocument> { ReturnDocument = ReturnDocument.After },
            cancellationToken);
        if (linked is null)
        {
            return Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Link_code_invalid, "The code is not waiting for a confirmation.");
        }

        var issuer = linked.Issuer;
        var redeemer = linked.Redeemer!;
        var (accountId, joining) = issuer.Kind == LinkPartyKinds.Account ? (issuer.AccountId!, redeemer) : (redeemer.AccountId!, issuer);
        var outcome = await accounts.AddIdentityAsync(accountId, joining.Provider, joining.Subject!, joining.Name, ActorOf(issuer), cancellationToken);
        if (outcome != AddIdentityOutcome.Added)
        {
            await DeclineAsync(filter.Eq(candidate => candidate.Id, codeId) & filter.Eq(candidate => candidate.Status, LinkCodeStates.Linked), now, cancellationToken);
            await audit.RecordCodeAsync(accountId, AuditKinds.LinkCodeDeclined, ActorOf(issuer), linked.Id, ClientIp, Options.LinkAuditRetention, cancellationToken);
            return outcome == AddIdentityOutcome.AlreadyLinked
                ? Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Identity_already_linked, "This sign-in already belongs to an account.")
                : Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Too_many_identities,
                    $"An account can hold at most {Accounts.MaxIdentities} sign-ins.");
        }

        await audit.RecordCodeAsync(accountId, AuditKinds.LinkCodeConfirmed, ActorOf(issuer), linked.Id, ClientIp, Options.LinkAuditRetention, cancellationToken);
        return await ToContractAsync(linked, cancellationToken);
    }

    /// <summary>
    /// The calling principal as a party. An account caller must still hold the identity it signed in with, since
    /// access tokens outlive an unlink.
    /// </summary>
    private async Task<LinkPartyDocument?> CallerAsync(CancellationToken cancellationToken)
    {
        var caller = CallerClaims();
        if (caller.Kind == LinkPartyKinds.Account
            && (await accounts.FindIdentityAsync(caller.IdentityId!, cancellationToken) is not { } identity || identity.AccountId != caller.AccountId))
        {
            return null;
        }

        return caller;
    }

    private LinkPartyDocument CallerClaims()
    {
        var user = Context.User;
        if (user.Kind() == PrincipalKind.Pending)
        {
            var pending = user.PendingIdentity();
            return new LinkPartyDocument { Kind = LinkPartyKinds.Pending, Provider = pending.Provider, Subject = pending.Subject, Name = pending.Name };
        }

        return new LinkPartyDocument
        {
            Kind = LinkPartyKinds.Account,
            AccountId = user.AccountId(),
            IdentityId = user.IdentityId(),
            Provider = user.FindFirstValue(AccessTokens.ProviderClaim) ?? string.Empty,
            Name = user.FindFirstValue(AccessTokens.NameClaim),
        };
    }

    /// <summary>The same account and identity, or the same pending identity; a fresh pending sign-in of it counts.</summary>
    private static bool IsParty(LinkPartyDocument party, LinkPartyDocument caller) =>
        party.Kind == caller.Kind && (party.Kind == LinkPartyKinds.Account
            ? party.AccountId == caller.AccountId && party.IdentityId == caller.IdentityId
            : party.Provider == caller.Provider && party.Subject == caller.Subject);

    private static FilterDefinition<LinkCodeDocument> IssuedBy(LinkPartyDocument issuer)
    {
        var filter = Builders<LinkCodeDocument>.Filter;
        return issuer.Kind == LinkPartyKinds.Account
            ? filter.Eq(code => code.Issuer.Kind, LinkPartyKinds.Account) & filter.Eq(code => code.Issuer.AccountId, issuer.AccountId)
                & filter.Eq(code => code.Issuer.IdentityId, issuer.IdentityId)
            : filter.Eq(code => code.Issuer.Kind, LinkPartyKinds.Pending) & filter.Eq(code => code.Issuer.Provider, issuer.Provider)
                & filter.Eq(code => code.Issuer.Subject, issuer.Subject);
    }

    /// <summary>Not found for anyone who can't see the code, forbidden for its redeemer.</summary>
    private static ObjectResult? IssuerOnly(LinkCodeDocument? code, LinkPartyDocument caller)
    {
        if (code is not null && IsParty(code.Issuer, caller))
        {
            return null;
        }

        return code?.Redeemer is { } redeemer && IsParty(redeemer, caller)
            ? Problems.Result(StatusCodes.Status403Forbidden, ProblemCode.Request_forbidden, "Only the sign-in that issued the code can do this.")
            : CodeNotFound();
    }

    private Task<LinkCodeDocument?> DeclineAsync(FilterDefinition<LinkCodeDocument> filter, DateTime now, CancellationToken cancellationToken) =>
        codes.FindOneAndUpdateAsync(filter,
            Builders<LinkCodeDocument>.Update.Set(code => code.Status, LinkCodeStates.Declined).Set(code => code.ResolvedAt, now),
            new FindOneAndUpdateOptions<LinkCodeDocument> { ReturnDocument = ReturnDocument.After },
            cancellationToken)!;

    private async Task<ActionResult<LinkCode>> RedeemFailedAsync(LinkPartyDocument redeemer, string? codeId, CancellationToken cancellationToken)
    {
        await audit.RecordCodeAsync(redeemer.AccountId, AuditKinds.LinkCodeRedeemFailed, ActorOf(redeemer), codeId, ClientIp, Options.LinkAuditRetention,
            cancellationToken);
        return Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Link_code_invalid,
            "This code is unknown, expired or already used. Ask for a new code in the game.");
    }

    private static string? TargetAccount(LinkPartyDocument issuer, LinkPartyDocument? redeemer) => issuer.AccountId ?? redeemer?.AccountId;

    private static AuditActor ActorOf(LinkPartyDocument party) =>
        party.Kind == LinkPartyKinds.Account ? new AuditActor(party.Provider, party.IdentityId) : new AuditActor(party.Provider, null, party.Subject);

    private static ObjectResult CodeNotFound() => Problems.Result(StatusCodes.Status404NotFound, ProblemCode.Not_found, "No such link code.");

    private static ObjectResult AccountGone() =>
        Problems.Result(StatusCodes.Status401Unauthorized, ProblemCode.Unauthenticated, "The sign-in no longer belongs to the account.");

    private LinkCodeStatus StatusOf(LinkCodeDocument code)
    {
        var now = time.GetUtcNow().UtcDateTime;
        return code.Status switch
        {
            LinkCodeStates.Open => code.ExpiresAt > now ? LinkCodeStatus.Open : LinkCodeStatus.Expired,
            LinkCodeStates.Redeemed => code.ConfirmBy > now ? LinkCodeStatus.Redeemed : LinkCodeStatus.Expired,
            LinkCodeStates.Linked => LinkCodeStatus.Linked,
            LinkCodeStates.Declined => LinkCodeStatus.Declined,
            _ => LinkCodeStatus.Cancelled,
        };
    }

    private async Task<LinkCode> ToContractAsync(LinkCodeDocument code, CancellationToken cancellationToken) => new()
    {
        Id = code.Id,
        Status = StatusOf(code),
        ExpiresAt = new DateTimeOffset(code.ExpiresAt, TimeSpan.Zero),
        ConfirmBy = code.ConfirmBy is { } confirmBy ? new DateTimeOffset(confirmBy, TimeSpan.Zero) : null,
        Issuer = await PartyAsync(code.Issuer, cancellationToken),
        Redeemer = code.Redeemer is { } redeemer ? await PartyAsync(redeemer, cancellationToken) : null,
    };

    /// <summary>A pending party by its identity; an account by its Minecraft names and its web sign-ins. Never an email.</summary>
    private async Task<LinkParty> PartyAsync(LinkPartyDocument party, CancellationToken cancellationToken)
    {
        if (party.Kind == LinkPartyKinds.Pending)
        {
            return new LinkParty { Provider = party.Provider, Name = party.Name };
        }

        var identities = await accounts.ListIdentitiesAsync(party.AccountId!, cancellationToken);
        return new LinkParty
        {
            AccountNames = identities.Where(identity => identity.Provider == Providers.Minecraft).Select(identity => identity.Name ?? identity.Subject).ToList(),
            AccountProviders = identities.Where(identity => identity.Provider != Providers.Minecraft)
                .Select(identity => identity.Provider + ":" + (identity.Name ?? identity.Subject)).ToList(),
        };
    }
}
