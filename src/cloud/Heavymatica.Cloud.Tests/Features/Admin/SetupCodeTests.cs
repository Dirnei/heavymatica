using System.Net;
using Heavymatica.Cloud.Contract.Admin;
using Heavymatica.Cloud.Features.Admin;
using Microsoft.Extensions.DependencyInjection;
using MongoDB.Bson;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Admin;

[Trait("Category", "Integration")]
public sealed class SetupCodeTests(CloudInfrastructure infrastructure) : IDisposable
{
    private AdminSite? site;

    private AdminSite Site => site ??= AdminSite.Start(infrastructure);

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    public void Dispose() => site?.Dispose();

    private IMongoDatabase Database => Site.Factory.Database;

    [Fact]
    public async Task The_command_prints_a_code_and_the_admin_address_and_stores_only_its_hash()
    {
        using var output = new StringWriter();
        using var error = new StringWriter();

        var exit = await SetupCodeCommand.IssueAsync(Site.Factory.Services.GetRequiredService<SetupCodes>(), null, output, error);

        Assert.Equal(0, exit);
        var code = Assert.Single(output.ToString().Split('\n').Select(line => line.Trim()),
            line => System.Text.RegularExpressions.Regex.IsMatch(line, "^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}-[23456789ABCDEFGHJKMNPQRSTVWXYZ]{4}$"));
        Assert.Contains("https://localhost/", output.ToString(), StringComparison.Ordinal);
        var stored = await SetupCodeDocument.In(Database).Find(_ => true).SingleAsync(Cancel);
        Assert.Equal(SetupCodes.Hash(code), stored.CodeHash);
        Assert.Equal(Site.Clock.GetUtcNow().UtcDateTime.AddMinutes(15), stored.ExpiresAt, TimeSpan.FromSeconds(1));
        Assert.DoesNotContain(code.Replace("-", string.Empty, StringComparison.Ordinal), stored.ToBsonDocument().ToString(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task The_command_refuses_a_recovery_code_for_an_unknown_admin()
    {
        using var output = new StringWriter();
        using var error = new StringWriter();

        var exit = await SetupCodeCommand.IssueAsync(Site.Factory.Services.GetRequiredService<SetupCodes>(), AdminUser.NewId(), output, error);

        Assert.Equal(1, exit);
        Assert.Contains("no admin", error.ToString(), StringComparison.Ordinal);
        Assert.Equal(0, await SetupCodeDocument.In(Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Theory]
    [InlineData("admin setup-code", null, "")]
    [InlineData("admin setup-code --admin abc", "abc", "")]
    [InlineData("admin setup-code -- --ConnectionStrings:heavymatica=mongodb://x/y", null, "--ConnectionStrings:heavymatica=mongodb://x/y")]
    public void The_verb_parses_its_arguments(string args, string? adminId, string hostArgs)
    {
        var parsed = SetupCodeCommand.Parse(Words(args));

        Assert.NotNull(parsed);
        Assert.Null(parsed.Value.Error);
        Assert.Equal(adminId, parsed.Value.Parsed!.AdminId);
        Assert.Equal(Words(hostArgs), parsed.Value.Parsed.HostArgs);
    }

    [Theory]
    [InlineData("admin setup-code --admin")]
    [InlineData("admin setup-code --verbose")]
    [InlineData("admin setup-code --admin a --admin b")]
    public async Task Unknown_arguments_are_an_error_and_issue_nothing(string args)
    {
        using var output = new StringWriter();
        using var error = new StringWriter();
        var parsed = SetupCodeCommand.Parse(Words(args));

        var exit = await SetupCodeCommand.RunAsync(parsed!.Value, output, error);

        Assert.Equal(2, exit);
        Assert.Contains("Usage", error.ToString(), StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("")]
    [InlineData("--urls http://localhost:5000")]
    [InlineData("admin")]
    [InlineData("setup-code")]
    public void Other_arguments_start_the_web_host(string args) => Assert.Null(SetupCodeCommand.Parse(Words(args)));

    private static string[] Words(string text) => text.Split(' ', StringSplitOptions.RemoveEmptyEntries);

    [Fact]
    public async Task The_command_reads_the_service_configuration_and_writes_the_code_to_its_database()
    {
        using var output = new StringWriter();
        using var error = new StringWriter();
        var url = new MongoUrlBuilder(infrastructure.MongoConnectionStringFor(Site.Factory.DatabaseName)).ToString();

        var exit = await SetupCodeCommand.RunAsync(SetupCodeCommand.Parse(
            ["admin", "setup-code", "--", $"--ConnectionStrings:heavymatica={url}", "--Admin:Url=http://localhost:5181", "--Admin:Host=admin.example"])!.Value,
            output, error);

        Assert.Equal(0, exit);
        Assert.Contains("https://admin.example/", output.ToString(), StringComparison.Ordinal);
        Assert.Equal(1, await SetupCodeDocument.In(Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_valid_code_creates_the_first_admin_with_the_registered_passkey_and_signs_them_in()
    {
        var browser = Site.Browser();
        var code = await Site.SetupCodeAsync();

        using var response = await browser.RegisterAsync(new { setupCode = code.ToLowerInvariant().Replace("-", " ", StringComparison.Ordinal), displayName = " Alice " },
            "YubiKey");
        var session = await AdminBrowser.ReadAsync<AdminSession>(response);

        Assert.Equal(("Alice", 1, true), (session.DisplayName, session.PasskeyCount, session.NeedsSecondPasskey));
        var admin = await AdminUser.In(Database).Find(_ => true).SingleAsync(Cancel);
        var passkey = await AdminPasskeyDocument.In(Database).Find(_ => true).SingleAsync(Cancel);
        Assert.Equal((session.AdminId, "Alice"), (admin.Id, admin.DisplayName));
        Assert.Equal((admin.Id, browser.Authenticator.Credentials[0].IdText, "YubiKey"), (passkey.AdminId, passkey.Id, passkey.Name));
        Assert.Equal(HttpStatusCode.OK, (await browser.GetAsync("/admin/v1/session")).StatusCode);
    }

    [Fact]
    public async Task A_code_for_a_new_admin_needs_a_display_name()
    {
        var browser = Site.Browser();

        using var response = await browser.PostAsync("/admin/v1/registration/options", new { setupCode = await Site.SetupCodeAsync() });
        var problem = await response.AdminProblemAsync();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(ProblemCode.Invalid_request, problem.Code);
        Assert.Contains("display name", problem.Detail, StringComparison.Ordinal);
    }

    [Fact]
    public async Task An_expired_code_is_rejected_and_no_admin_is_created()
    {
        var code = await Site.SetupCodeAsync();
        Site.Clock.Advance(TimeSpan.FromMinutes(15) + TimeSpan.FromSeconds(1));

        using var response = await Site.Browser().RegisterAsync(new { setupCode = code, displayName = "Alice" });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(ProblemCode.Setup_code_invalid, (await response.AdminProblemAsync()).Code);
        Assert.Equal(0, await AdminUser.In(Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_code_that_expires_between_the_options_and_the_registration_is_rejected()
    {
        var browser = Site.Browser();
        var code = await Site.SetupCodeAsync();
        Site.Clock.Advance(TimeSpan.FromMinutes(14));
        using var options = await browser.PostAsync("/admin/v1/registration/options", new { setupCode = code, displayName = "Alice" });
        var json = (await System.Net.Http.Json.HttpContentJsonExtensions.ReadFromJsonAsync<System.Text.Json.JsonElement>(options.Content, SignIn.Json, Cancel))
            .GetProperty("options");
        Site.Clock.Advance(TimeSpan.FromMinutes(2));

        using var response = await browser.PostAsync("/admin/v1/registration", new { credential = browser.Authenticator.Create(json, Site.Origin) });

        Assert.Equal(ProblemCode.Setup_code_invalid, (await response.AdminProblemAsync()).Code);
        Assert.Equal(0, await AdminUser.In(Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
    }

    [Fact]
    public async Task A_used_code_is_rejected_the_second_time()
    {
        var code = await Site.SetupCodeAsync();
        using var first = await Site.Browser().RegisterAsync(new { setupCode = code, displayName = "Alice" });

        using var second = await Site.Browser().RegisterAsync(new { setupCode = code, displayName = "Mallory" });

        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.Equal(ProblemCode.Setup_code_invalid, (await second.AdminProblemAsync()).Code);
        Assert.Equal(["Alice"], (await AdminUser.In(Database).Find(_ => true).ToListAsync(Cancel)).Select(admin => admin.DisplayName));
    }

    [Fact]
    public async Task A_second_run_invalidates_the_first_code()
    {
        var first = await Site.SetupCodeAsync();
        var second = await Site.SetupCodeAsync();

        using var withFirst = await Site.Browser().RegisterAsync(new { setupCode = first, displayName = "Alice" });
        using var withSecond = await Site.Browser().RegisterAsync(new { setupCode = second, displayName = "Alice" });

        Assert.Equal(ProblemCode.Setup_code_invalid, (await withFirst.AdminProblemAsync()).Code);
        Assert.Equal(HttpStatusCode.OK, withSecond.StatusCode);
    }

    [Fact]
    public async Task A_recovery_code_adds_a_passkey_to_the_named_admin()
    {
        var (_, alice) = await Site.AdminAsync("Alice");
        var recovering = Site.Browser();

        using var response = await recovering.RegisterAsync(new { setupCode = await Site.SetupCodeAsync(alice.AdminId), displayName = "ignored" });
        var session = await AdminBrowser.ReadAsync<AdminSession>(response);
        using (await recovering.PostAsync("/admin/v1/sign-out"))
        {
        }

        using var signIn = await recovering.SignInAsync();

        Assert.Equal((alice.AdminId, "Alice", 3), (session.AdminId, session.DisplayName, session.PasskeyCount));
        Assert.Equal(1, await AdminUser.In(Database).CountDocumentsAsync(_ => true, cancellationToken: Cancel));
        Assert.Equal(alice.AdminId, (await AdminBrowser.ReadAsync<AdminSession>(signIn)).AdminId);
    }

    [Fact]
    public async Task Malformed_and_unknown_codes_answer_alike()
    {
        _ = await Site.SetupCodeAsync();

        using var malformed = await Site.Browser().PostAsync("/admin/v1/registration/options", new { setupCode = "nope", displayName = "Alice" });
        using var unknown = await Site.Browser().PostAsync("/admin/v1/registration/options", new { setupCode = "2222-2222-2222", displayName = "Alice" });
        using var both = await Site.Browser().PostAsync("/admin/v1/registration/options", new { setupCode = "2222-2222-2222", invitationToken = "x" });

        Assert.Equal(await malformed.Content.ReadAsStringAsync(Cancel), await unknown.Content.ReadAsStringAsync(Cancel));
        Assert.Equal(ProblemCode.Setup_code_invalid, (await unknown.AdminProblemAsync()).Code);
        Assert.Equal(ProblemCode.Invalid_request, (await both.AdminProblemAsync()).Code);
    }
}
