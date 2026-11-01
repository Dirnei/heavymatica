using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Infrastructure.Contract;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

namespace Heavymatica.Cloud.Features.Identity;

internal sealed class AuthApi(
    Challenges challenges,
    MojangSessions mojang,
    Accounts accounts,
    Sessions sessions,
    AccessTokens tokens,
    IOptions<AccountsOptions> accountsOptions,
    IHttpContextAccessor http,
    ILogger<AuthApi> logger) : IAuthController
{
    public async Task<ActionResult<Challenge>> CreateChallengeAsync(CancellationToken cancellationToken = default)
    {
        var challenge = await challenges.IssueAsync(cancellationToken);
        return new Challenge
        {
            ChallengeId = challenge.Id,
            ServerId = challenge.ServerId,
            ExpiresAt = new DateTimeOffset(challenge.ExpiresAt, TimeSpan.Zero),
        };
    }

    public async Task<ActionResult<Session>> VerifyAsync(VerifyRequest body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        var (profile, problem) = await HandshakeAsync(body, cancellationToken);
        if (problem is not null)
        {
            return problem;
        }

        var (signedIn, _) = await accounts.CreateWithIdentityAsync(Providers.Minecraft, MinecraftSubject(profile!.Id), profile.Name, cancellationToken);
        return await StartSessionAsync(signedIn, cancellationToken);
    }

    public async Task<ActionResult<Contract.SignInResult>> SignInAsync(VerifyRequest body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        var (profile, problem) = await HandshakeAsync(body, cancellationToken);
        if (problem is not null)
        {
            return problem;
        }

        var subject = MinecraftSubject(profile!.Id);
        if (await accounts.SignInAsync(Providers.Minecraft, subject, profile.Name, cancellationToken) is { } known)
        {
            return new Contract.SignInResult { Session = await StartSessionAsync(known, cancellationToken) };
        }

        if (!accountsOptions.Value.LinkingAvailable)
        {
            var (created, _) = await accounts.CreateWithIdentityAsync(Providers.Minecraft, subject, profile.Name, cancellationToken);
            return new Contract.SignInResult { Session = await StartSessionAsync(created, cancellationToken) };
        }

        var (pendingToken, expiresAt) = tokens.IssuePending(Providers.Minecraft, subject, profile.Name);
        return new Contract.SignInResult
        {
            Pending = new PendingSignIn
            {
                PendingToken = pendingToken,
                ExpiresAt = expiresAt,
                Identity = new PendingIdentity { Provider = Providers.Minecraft, Name = profile.Name },
                JoinAvailable = true,
            },
        };
    }

    public async Task<ActionResult<Session>> CompleteSignInAsync(CompleteSignInRequest body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        var pending = http.HttpContext!.User.PendingIdentity();
        var signedIn = body.CreateAccount
            ? (await accounts.CreateWithIdentityAsync(pending.Provider, pending.Subject, pending.Name, cancellationToken)).SignedIn
            : await accounts.SignInAsync(pending.Provider, pending.Subject, pending.Name, cancellationToken);
        return signedIn is null
            ? Problems.Result(StatusCodes.Status409Conflict, ProblemCode.Account_choice_required,
                "This sign-in has no account yet. Create one, or join one with a link code first.")
            : await StartSessionAsync(signedIn, cancellationToken);
    }

    /// <summary>Consumes the challenge and asks Mojang who joined; either the verified profile or the problem to answer.</summary>
    private async Task<(MojangProfile? Profile, ObjectResult? Problem)> HandshakeAsync(VerifyRequest body, CancellationToken cancellationToken)
    {
        var (outcome, challenge) = await challenges.ConsumeAsync(body.ChallengeId, cancellationToken);
        switch (outcome)
        {
            case ChallengeOutcome.Unknown:
                return (null, Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Challenge_unknown, "The sign-in challenge does not exist."));
            case ChallengeOutcome.Used:
                return (null, Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Challenge_used, "The sign-in challenge was already used. Start a new sign-in."));
            case ChallengeOutcome.Expired:
                return (null, Problems.Result(StatusCodes.Status400BadRequest, ProblemCode.Challenge_expired, "The sign-in challenge expired. Start a new sign-in."));
        }

        MojangProfile? profile;
        try
        {
            profile = await mojang.HasJoinedAsync(body.Name, challenge!.ServerId, cancellationToken);
        }
        catch (MojangUnavailableException e)
        {
            IdentityLog.MojangUnavailable(logger, e);
            return (null, Problems.Result(StatusCodes.Status503ServiceUnavailable, ProblemCode.Identity_provider_unavailable,
                "Mojang's session server is unavailable. Try again later."));
        }

        return profile is null
            ? (null, Problems.Result(StatusCodes.Status401Unauthorized, ProblemCode.Identity_not_confirmed,
                "Mojang did not confirm this player. Cloud sign-in needs a Microsoft-authenticated Minecraft account."))
            : (profile, null);
    }

    private async Task<Session> StartSessionAsync(SignedInIdentity signedIn, CancellationToken cancellationToken)
    {
        var (sessionId, refreshToken) = await sessions.StartAsync(signedIn.Account.Id, signedIn.Identity.Id, cancellationToken);
        return SessionFor(signedIn, sessionId, refreshToken);
    }

    public async Task<ActionResult<Session>> RefreshAsync(RefreshRequest body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        if (await sessions.RotateAsync(body.RefreshToken, cancellationToken) is not { } rotated)
        {
            return SessionEnded();
        }

        if (rotated.IdentityId is null
            || await accounts.FindIdentityAsync(rotated.IdentityId, cancellationToken) is not { } identity
            || identity.AccountId != rotated.AccountId
            || await accounts.FindAsync(rotated.AccountId, cancellationToken) is not { } account)
        {
            await sessions.EndAsync(rotated.SessionId, cancellationToken);
            return SessionEnded();
        }

        return SessionFor(new SignedInIdentity(account, identity), rotated.SessionId, rotated.RefreshToken);
    }

    /// <summary>The subject of a Minecraft identity: the UUID in its dashed lower-case form.</summary>
    public static string MinecraftSubject(Guid uuid) => uuid.ToString("D");

    private static ObjectResult SessionEnded() =>
        Problems.Result(StatusCodes.Status401Unauthorized, ProblemCode.Refresh_token_invalid, "The session has ended. Sign in again.");

    public async Task<IActionResult> RevokeSessionAsync(RefreshRequest body, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(body);
        await sessions.RevokeAsync(body.RefreshToken, cancellationToken);
        return new NoContentResult();
    }

    private Session SessionFor(SignedInIdentity signedIn, string sessionId, string refreshToken)
    {
        var (accessToken, expiresAt) = tokens.Issue(signedIn, sessionId);
        return new Session
        {
            AccessToken = accessToken,
            AccessTokenExpiresAt = expiresAt,
            RefreshToken = refreshToken,
            Account = Accounts.ToContract(signedIn.Account, signedIn.Identity),
        };
    }
}
