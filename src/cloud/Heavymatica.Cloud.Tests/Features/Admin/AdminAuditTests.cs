using System.Net;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Admin;
using Microsoft.Extensions.DependencyInjection;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminAuditTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public void Dispose() => site?.Dispose();

    [Fact]
    public async Task Admin_actions_and_failed_attempts_are_recorded_and_read_back_newest_first_across_pages()
    {
        var accounts = Site.Factory.Services.GetRequiredService<Accounts>();
        var (player, _) = await accounts.CreateWithIdentityAsync(Providers.Minecraft, Guid.NewGuid().ToString(), "Steve", Cancel);
        var alice = Site.Browser("198.51.100.20");
        using (var registered = await alice.RegisterAsync(new { setupCode = await Site.SetupCodeAsync(), displayName = "Alice" }, "Laptop"))
        {
            Assert.Equal(HttpStatusCode.OK, registered.StatusCode);
        }

        using (await alice.AddPasskeyAsync())
        {
        }

        Site.Clock.Advance(TimeSpan.FromSeconds(1));
        using (await alice.PostAsync("/admin/v1/sign-out"))
        {
        }

        using (var signedIn = await alice.SignInAsync())
        {
            Assert.Equal(HttpStatusCode.OK, signedIn.StatusCode);
        }

        Site.Clock.Advance(TimeSpan.FromSeconds(1));
        using (await alice.PostAsync("/admin/v1/invitations", new { kind = "admin" }))
        {
        }

        Site.Clock.Advance(TimeSpan.FromSeconds(1));
        using (await Site.Browser("198.51.100.66").PostAsync("/admin/v1/registration/options", new { setupCode = "2222-2222-2222", displayName = "Mallory" }))
        {
        }

        Site.Clock.Advance(TimeSpan.FromSeconds(1));
        using (var quota = await alice.SendAsync(HttpMethod.Put, $"/admin/v1/accounts/{player.Account.Id}/quota",
            new { quotaBytes = 1L << 30, reason = "creator programme" }))
        {
            Assert.Equal(HttpStatusCode.OK, quota.StatusCode);
        }

        using var first = await alice.GetAsync("/admin/v1/audit?limit=4");
        var page1 = await AdminBrowser.ReadAsync<AuditPage>(first);
        using var second = await alice.GetAsync("/admin/v1/audit?limit=4&cursor=" + Uri.EscapeDataString(page1.NextCursor!));
        var page2 = await AdminBrowser.ReadAsync<AuditPage>(second);
        var entries = page1.Items.Concat(page2.Items).ToList();
        var aliceId = (await alice.SessionAsync()).AdminId;

        Assert.Null(page2.NextCursor);
        Assert.Equal(
            [
                AdminAuditKinds.QuotaChanged, AdminAuditKinds.SetupCodeFailed, AdminAuditKinds.InvitationCreated, AdminAuditKinds.SignedIn,
                AdminAuditKinds.PasskeyAdded, AdminAuditKinds.PasskeyAdded, AdminAuditKinds.AdminCreated,
            ],
            entries.Select(entry => entry.Kind));
        Assert.True(entries.Zip(entries.Skip(1)).All(pair => pair.First.At >= pair.Second.At));
        Assert.All(entries.Where(entry => entry.Kind != AdminAuditKinds.SetupCodeFailed), entry =>
            Assert.Equal((aliceId, "Alice", "198.51.100.20"), (entry.AdminId, entry.AdminName, entry.Ip)));

        var quotaChange = entries[0];
        Assert.Equal(Site.Clock.GetUtcNow(), quotaChange.At, TimeSpan.FromSeconds(1));
        Assert.Equal(player.Account.Id, quotaChange.Target);
        Assert.Equal(new Dictionary<string, string>
        {
            ["accountId"] = player.Account.Id,
            ["oldBytes"] = (100L * 1024 * 1024).ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["newBytes"] = (1L << 30).ToString(System.Globalization.CultureInfo.InvariantCulture),
            ["reason"] = "creator programme",
        }, quotaChange.Details);

        var failed = entries[1];
        Assert.Equal(((string?)null, "198.51.100.66"), (failed.AdminId, failed.Ip));

        var firstPasskey = entries[^2];
        Assert.Equal((aliceId, alice.Authenticator.Credentials[0].IdText, "Laptop", "setup_code"),
            (firstPasskey.Target, firstPasskey.Details["passkeyId"], firstPasskey.Details["name"], firstPasskey.Details["via"]));
        Assert.Equal(alice.Authenticator.Credentials[0].IdText, entries[3].Details["passkeyId"]);
    }

    [Fact]
    public async Task A_failed_sign_in_and_a_failed_invitation_are_recorded()
    {
        var (alice, _) = await Site.AdminAsync("Alice");
        var stranger = Site.Browser("198.51.100.77");

        using (await alice.SignInAsync(rpIdOverride: "evil.example"))
        {
        }

        using (await stranger.PostAsync("/admin/v1/registration/options", new { invitationToken = "guess" }))
        {
        }

        var kinds = await AdminAuditDocument.In(Site.Factory.Database).Find(entry => entry.AdminId == null).SortBy(entry => entry.Id)
            .Project(entry => entry.Kind).ToListAsync(Cancel);
        Assert.Equal([AdminAuditKinds.SignInFailed, AdminAuditKinds.InvitationFailed], kinds);
    }

    [Fact]
    public async Task Entries_are_kept_for_400_days_and_bad_paging_is_rejected()
    {
        var (alice, _) = await Site.AdminAsync("Alice");

        using var badCursor = await alice.GetAsync("/admin/v1/audit?cursor=nope");
        using var badLimit = await alice.GetAsync("/admin/v1/audit?limit=101");

        var entry = await AdminAuditDocument.In(Site.Factory.Database).Find(_ => true).FirstAsync(Cancel);
        Assert.Equal(entry.At.AddDays(400), entry.ExpireAt, TimeSpan.FromSeconds(1));
        Assert.Equal(ProblemCode.Invalid_request, (await badCursor.AdminProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.BadRequest, badLimit.StatusCode);
    }
}
