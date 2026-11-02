using System.Net;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Admin;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminRateLimitTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public void Dispose() => site?.Dispose();

    [Fact]
    public async Task The_11th_sign_in_attempt_in_a_minute_is_rate_limited_per_client_address()
    {
        var browser = Site.Browser("198.51.100.200");
        for (var attempt = 1; attempt <= 10; attempt++)
        {
            using var allowed = await browser.PostAsync(attempt % 2 == 0 ? "/admin/v1/sign-in" : "/admin/v1/sign-in/options", new { credential = new { } });
            Assert.NotEqual(HttpStatusCode.TooManyRequests, allowed.StatusCode);
        }

        using var limited = await browser.PostAsync("/admin/v1/sign-in/options");
        using var otherAddress = await Site.Browser("198.51.100.201").PostAsync("/admin/v1/sign-in/options");
        Site.Clock.Advance(TimeSpan.FromMinutes(1));
        using var nextMinute = await browser.PostAsync("/admin/v1/sign-in/options");

        Assert.Equal(HttpStatusCode.TooManyRequests, limited.StatusCode);
        Assert.Equal(ProblemCode.Rate_limited, (await limited.AdminProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.OK, otherAddress.StatusCode);
        Assert.Equal(HttpStatusCode.OK, nextMinute.StatusCode);
    }

    [Fact]
    public async Task The_6th_code_attempt_in_10_minutes_is_rate_limited_without_checking_the_code()
    {
        var code = await Site.SetupCodeAsync();
        var guesser = Site.Browser("198.51.100.210");
        for (var attempt = 1; attempt <= 5; attempt++)
        {
            using var wrong = await guesser.PostAsync("/admin/v1/registration/options", new { setupCode = "2222-3333-444" + attempt, displayName = "Mallory" });
            Assert.Equal(ProblemCode.Setup_code_invalid, (await wrong.AdminProblemAsync()).Code);
        }

        using var sixth = await guesser.PostAsync("/admin/v1/registration/options", new { setupCode = code, displayName = "Mallory" });
        using var invitation = await guesser.PostAsync("/admin/v1/registration/options", new { invitationToken = "guess", displayName = "Mallory" });

        Assert.Equal(HttpStatusCode.TooManyRequests, sixth.StatusCode);
        Assert.Equal(ProblemCode.Rate_limited, (await sixth.AdminProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.TooManyRequests, invitation.StatusCode);
        Assert.Equal(5, await AdminAuditDocument.In(Site.Factory.Database).CountDocumentsAsync(entry => entry.Kind == AdminAuditKinds.SetupCodeFailed,
            cancellationToken: Cancel));

        Site.Clock.Advance(TimeSpan.FromMinutes(10));
        using var later = await guesser.RegisterAsync(new { setupCode = code, displayName = "Alice" });
        Assert.Equal(HttpStatusCode.OK, later.StatusCode);
    }
}
