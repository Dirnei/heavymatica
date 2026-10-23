using System.Net;
using System.Net.Http.Json;
using System.Security.Cryptography;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Identity;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Identity;

[Trait("Category", "Integration")]
public sealed class TokenTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    [Fact]
    public async Task Calls_without_a_token_are_unauthenticated()
    {
        using var client = Factory().CreateClient();

        using var response = await client.GetAsync(new Uri("/v1/me", UriKind.Relative), TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task Expired_tokens_are_unauthenticated()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Sleeper");
        var past = new FakeTimeProvider(DateTimeOffset.UtcNow.AddMinutes(-20));
        var oldTokens = new AccessTokens(factory.Services.GetRequiredService<IOptions<IdentityOptions>>(), past, factory.Services.GetRequiredService<SigningKey>());

        client.Authorize(oldTokens.Issue(TestIdentities.Minecraft(session.Account.Id, "Sleeper"), "s").Token);
        using var response = await client.GetAsync(new Uri("/v1/me", UriKind.Relative), TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task Tampered_tokens_are_unauthenticated()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Forger");
        var parts = session.AccessToken.Split('.');
        // Change the claims but keep the signature.
        var forged = parts[0] + "." + parts[1][..^2] + (parts[1][^2] == 'A' ? "B" : "A") + parts[1][^1] + "." + parts[2];

        client.Authorize(forged);
        using var response = await client.GetAsync(new Uri("/v1/me", UriKind.Relative), TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Tokens_signed_with_another_key_are_unauthenticated()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        using var otherKey = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var other = new CloudFactory(infrastructure).With("Identity:SigningKey", otherKey.ExportECPrivateKeyPem());
        disposables.Add(other);
        var foreign = other.Services.GetRequiredService<AccessTokens>().Issue(TestIdentities.Minecraft(AccountDocument.NewId(), "Other"), "s").Token;

        client.Authorize(foreign);
        using var response = await client.GetAsync(new Uri("/v1/me", UriKind.Relative), TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Refreshing_rotates_both_tokens_and_the_old_refresh_token_stops_working()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Rotator");

        using var refreshed = await Refresh(client, session.RefreshToken);
        var next = (await refreshed.Content.ReadFromJsonAsync<Session>(SignIn.Json, TestContext.Current.CancellationToken))!;
        using var reused = await Refresh(client, session.RefreshToken);

        Assert.Equal(HttpStatusCode.OK, refreshed.StatusCode);
        Assert.NotEqual(session.RefreshToken, next.RefreshToken);
        Assert.NotEqual(session.AccessToken, next.AccessToken);
        Assert.Equal(HttpStatusCode.Unauthorized, reused.StatusCode);
        Assert.Equal(ProblemCode.Refresh_token_invalid, (await reused.ProblemAsync()).Code);
    }

    [Fact]
    public async Task Reusing_a_rotated_refresh_token_revokes_the_whole_session()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Victim");
        using var refreshed = await Refresh(client, session.RefreshToken);
        var current = (await refreshed.Content.ReadFromJsonAsync<Session>(SignIn.Json, TestContext.Current.CancellationToken))!;

        using var stolen = await Refresh(client, session.RefreshToken);
        using var legitimate = await Refresh(client, current.RefreshToken);

        Assert.Equal(HttpStatusCode.Unauthorized, stolen.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, legitimate.StatusCode);
    }

    [Fact]
    public async Task Refresh_tokens_expire_after_30_days_without_use()
    {
        var clock = new FakeTimeProvider(DateTimeOffset.UtcNow);
        var factory = new CloudFactory(infrastructure) { Clock = clock };
        disposables.Add(factory);
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Absent");

        clock.Advance(TimeSpan.FromDays(30) + TimeSpan.FromMinutes(1));
        using var response = await Refresh(client, session.RefreshToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Signing_out_revokes_the_refresh_token()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Leaver");

        using var signOut = await client.PostAsJsonAsync(new Uri("/v1/auth/signout", UriKind.Relative),
            new { refreshToken = session.RefreshToken }, TestContext.Current.CancellationToken);
        using var refresh = await Refresh(client, session.RefreshToken);

        Assert.Equal(HttpStatusCode.NoContent, signOut.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, refresh.StatusCode);
    }

    [Fact]
    public async Task Sign_in_requests_are_rate_limited_per_client()
    {
        var factory = Factory().With("Identity:SignInRequestsPerMinute", "3");
        using var client = factory.CreateClient();
        for (var i = 0; i < 3; i++)
        {
            await SignIn.ChallengeAsync(client);
        }

        using var response = await client.PostAsync(new Uri("/v1/auth/challenge", UriKind.Relative), null, TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.TooManyRequests, response.StatusCode);
        Assert.Equal(ProblemCode.Rate_limited, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task DevMojang_is_mapped_in_development()
    {
        using var client = Factory().CreateClient();

        using var response = await client.GetAsync(new Uri("/dev/mojang/session/minecraft/hasJoined?username=a&serverId=b", UriKind.Relative),
            TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
    }

    [Fact]
    public async Task DevMojang_does_not_exist_in_production()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var factory = new CloudFactory(infrastructure) { Environment = "Production" }.With("Identity:SigningKey", key.ExportECPrivateKeyPem());
        disposables.Add(factory);
        using var client = factory.CreateClient();

        using var join = await client.PostAsJsonAsync(new Uri("/dev/mojang/session/minecraft/join", UriKind.Relative),
            new { accessToken = "x", selectedProfile = Guid.NewGuid().ToString("N"), serverId = "s" }, TestContext.Current.CancellationToken);
        using var hasJoined = await client.GetAsync(new Uri("/dev/mojang/session/minecraft/hasJoined?username=a&serverId=s", UriKind.Relative),
            TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.NotFound, join.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, hasJoined.StatusCode);
    }

    [Fact]
    public void Production_refuses_to_start_without_a_signing_key()
    {
        var factory = new CloudFactory(infrastructure) { Environment = "Production" };
        disposables.Add(factory);

        var error = Assert.ThrowsAny<Exception>(() => factory.Services.GetRequiredService<SigningKey>());

        Assert.Contains("Identity:SigningKey", error.ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task Access_tokens_name_the_account_as_subject_and_the_identity_that_signed_in()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var profile = Guid.NewGuid();

        var session = await SignIn.AsAsync(client, profile, "Steve");

        var token = new Microsoft.IdentityModel.JsonWebTokens.JsonWebToken(session.AccessToken);
        var identity = await IdentityDocument.In(factory.Database)
            .Find(candidate => candidate.Provider == Providers.Minecraft && candidate.Subject == profile.ToString("D"))
            .SingleAsync(TestContext.Current.CancellationToken);
        Assert.Matches("^[0-9a-f]{32}$", token.Subject);
        Assert.NotEqual(profile.ToString("D"), token.Subject);
        Assert.Equal(identity.AccountId, token.Subject);
        Assert.Equal(identity.Id, token.GetClaim("iid").Value);
        Assert.Equal("minecraft", token.GetClaim("idp").Value);
        Assert.Equal("Steve", token.GetClaim("name").Value);
    }

    [Fact]
    public async Task Access_tokens_without_an_identity_are_unauthenticated()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Veteran");
        var legacy = factory.Services.GetRequiredService<AccessTokens>().Issue(
        [
            new System.Security.Claims.Claim("sub", session.Account.Id),
            new System.Security.Claims.Claim("name", "Veteran"),
            new System.Security.Claims.Claim("sid", "s"),
        ]).Token;

        client.Authorize(legacy);
        using var response = await client.GetAsync(new Uri("/v1/me", UriKind.Relative), TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Unauthenticated, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task Refresh_ends_the_session_once_its_identity_is_gone()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var session = await SignIn.AsAsync(client, Guid.NewGuid(), "Unlinked");
        await IdentityDocument.In(factory.Database).DeleteManyAsync(identity => identity.AccountId == session.Account.Id,
            TestContext.Current.CancellationToken);

        using var response = await Refresh(client, session.RefreshToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(ProblemCode.Refresh_token_invalid, (await response.ProblemAsync()).Code);
        var stored = await SessionDocument.In(factory.Database).Find(candidate => candidate.AccountId == session.Account.Id)
            .SingleAsync(TestContext.Current.CancellationToken);
        Assert.True(stored.Revoked);
    }

    private static Task<HttpResponseMessage> Refresh(HttpClient client, string refreshToken) =>
        client.PostAsJsonAsync(new Uri("/v1/auth/refresh", UriKind.Relative), new { refreshToken }, TestContext.Current.CancellationToken);

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure);
        disposables.Add(factory);
        return factory;
    }
}
