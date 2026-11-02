using System.Net;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class AdminSiteFilesTests : IDisposable
{
    private readonly string playerRoot = Directory.CreateTempSubdirectory("heavymatica-web-").FullName;
    private readonly string adminRoot = Directory.CreateTempSubdirectory("heavymatica-admin-").FullName;
    private readonly AdminSite site;

    public AdminSiteFilesTests(CloudInfrastructure infrastructure)
    {
        File.WriteAllText(Path.Combine(playerRoot, "index.html"), "<h1>player</h1>");
        File.WriteAllText(Path.Combine(adminRoot, "index.html"), "<h1>admin</h1>");
        Directory.CreateDirectory(Path.Combine(adminRoot, "accounts"));
        File.WriteAllText(Path.Combine(adminRoot, "accounts", "index.html"), "<h1>accounts</h1>");
        site = AdminSite.Start(infrastructure, factory => factory.With("Web:Root", playerRoot).With("Admin:SiteRoot", adminRoot));
    }

    public void Dispose()
    {
        site.Dispose();
        Directory.Delete(playerRoot, recursive: true);
        Directory.Delete(adminRoot, recursive: true);
    }

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    [Fact]
    public async Task The_admin_listener_serves_the_admin_site_and_the_public_one_the_player_site()
    {
        using var browser = site.Browser();
        using var adminPage = await browser.GetAsync("/");
        using var adminAccounts = await browser.GetAsync("/accounts/");
        using var publicClient = site.PublicClient();
        using var publicPage = await publicClient.GetAsync(new Uri("/", UriKind.Relative), Cancel);
        using var publicAccounts = await publicClient.GetAsync(new Uri("/accounts/", UriKind.Relative), Cancel);

        Assert.Equal("<h1>admin</h1>", await adminPage.Content.ReadAsStringAsync(Cancel));
        Assert.Equal("<h1>accounts</h1>", await adminAccounts.Content.ReadAsStringAsync(Cancel));
        Assert.Equal("<h1>player</h1>", await publicPage.Content.ReadAsStringAsync(Cancel));
        Assert.NotEqual("<h1>accounts</h1>", await publicAccounts.Content.ReadAsStringAsync(Cancel));
    }

    [Fact]
    public async Task The_admin_site_is_not_served_for_another_host_name()
    {
        using var browser = site.Browser();
        using var request = browser.Request(HttpMethod.Get, "/", marker: false);
        request.Headers.Host = "evil.example:" + site.Admin.Port;

        using var response = await browser.Client.SendAsync(request, Cancel);

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }
}
