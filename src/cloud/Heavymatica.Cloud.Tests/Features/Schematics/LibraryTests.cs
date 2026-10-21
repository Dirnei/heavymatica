using System.Net;
using System.Net.Http.Json;
using System.Security.Cryptography;
using Akka.Actor;
using Akka.Hosting;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Schematics.Sweeper;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Time.Testing;

namespace Heavymatica.Cloud.Tests.Features.Schematics;

/// <summary>cloud/schematic-storage: listing, access, organizing, links, thumbnails, deletion and sweeping.</summary>
[Trait("Category", "Integration")]
public sealed class LibraryTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    [Fact]
    public async Task Listing_combines_text_folder_and_tag_filters()
    {
        var library = await Library.SignedInAsync(Factory());
        var tower = await library.NewAsync("Watch Tower", Library.Fixture("house.nbt"));
        var otherTower = await library.NewAsync("Tower of Light", Library.Fixture("house.v2.schem"));
        var barn = await library.NewAsync("Barn", Library.Fixture("house.v3.schem"));
        await Organize(library, tower.SchematicId, new { folder = "houses", tags = new[] { "spruce" } });
        await Organize(library, otherTower.SchematicId, new { folder = "towers", tags = new[] { "spruce" } });
        await Organize(library, barn.SchematicId, new { folder = "houses", tags = new[] { "spruce" } });

        var page = await library.ListAsync("?folder=houses&tag=spruce&q=TOW");

        Assert.Equal([tower.SchematicId], page.Items.Select(item => item.Id));
    }

    [Fact]
    public async Task Listing_is_newest_change_first_and_pages_without_repeats()
    {
        var library = await Library.SignedInAsync(Factory());
        var ids = new List<string>();
        foreach (var fixture in new[] { "house.nbt", "house.v2.schem", "house.v3.schem", "stairs.v2.schem", "modded.v3.schem" })
        {
            ids.Add((await library.NewAsync(fixture, Library.Fixture(fixture))).SchematicId);
        }

        var seen = new List<string>();
        string? cursor = null;
        do
        {
            var page = await library.ListAsync("?limit=2" + (cursor is null ? string.Empty : "&cursor=" + cursor));
            Assert.True(page.Items.Count <= 2);
            seen.AddRange(page.Items.Select(item => item.Id));
            cursor = page.NextCursor;
        }
        while (cursor is not null);

        Assert.Equal(Enumerable.Reverse(ids), seen);
    }

    [Fact]
    public async Task Listing_entries_include_the_latest_version()
    {
        var library = await Library.SignedInAsync(Factory());
        var first = await library.NewAsync("Grow", Library.Fixture("house.nbt"));
        await library.VersionAsync(first.SchematicId, Library.Fixture("stairs.v2.schem"));

        var entry = Assert.Single((await library.ListAsync()).Items);

        Assert.Equal(2, entry.Latest.Number);
        Assert.Equal(10, entry.Latest.Dimensions.Width);
    }

    [Fact]
    public async Task Another_players_schematic_is_indistinguishable_from_a_missing_one()
    {
        var factory = Factory();
        var owner = await Library.SignedInAsync(factory, "Owner");
        var intruder = await Library.SignedInAsync(factory, "Intruder");
        var mine = await owner.NewAsync("Secret", Library.Fixture("house.nbt"));
        var missing = "000000000000000000000000";

        foreach (var (method, path) in new[]
        {
            ("GET", "/v1/schematics/{0}"),
            ("PATCH", "/v1/schematics/{0}"),
            ("DELETE", "/v1/schematics/{0}"),
            ("POST", "/v1/schematics/{0}/versions"),
            ("DELETE", "/v1/schematics/{0}/versions/1"),
            ("PUT", "/v1/schematics/{0}/versions/1/thumbnail"),
            ("POST", "/v1/schematics/{0}/versions/1/links"),
        })
        {
            var foreign = await Send(intruder, method, string.Format(System.Globalization.CultureInfo.InvariantCulture, path, mine.SchematicId));
            var absent = await Send(intruder, method, string.Format(System.Globalization.CultureInfo.InvariantCulture, path, missing));

            Assert.Equal(HttpStatusCode.NotFound, foreign.Status);
            Assert.Equal(absent, foreign);
        }

        Assert.Single((await owner.DetailsAsync(mine.SchematicId)).Versions);
        Assert.Empty((await intruder.ListAsync()).Items);
    }

    [Fact]
    public async Task Organizing_changes_name_folder_and_tags_but_no_version()
    {
        var library = await Library.SignedInAsync(Factory());
        var created = await library.NewAsync("Old", Library.Fixture("house.nbt"));

        await Organize(library, created.SchematicId, new { name = "Medieval House", folder = "/houses//medieval/", tags = new[] { "wip", "spruce", "wip" } });
        var details = await library.DetailsAsync(created.SchematicId);

        Assert.Equal("Medieval House", details.Name);
        Assert.Equal("houses/medieval", details.Folder);
        Assert.Equal(["wip", "spruce"], details.Tags);
        Assert.Equal(created.Version.Sha256, Assert.Single(details.Versions).Sha256);

        await Organize(library, created.SchematicId, new { folder = "" });
        Assert.Null((await library.DetailsAsync(created.SchematicId)).Folder);
    }

    [Fact]
    public async Task Download_links_return_the_exact_stored_bytes()
    {
        var library = await Library.SignedInAsync(Factory());
        var file = Library.Fixture("house.litematica-v6.litematic");
        var created = await library.NewAsync("Linked", file);

        using var response = await library.LinksAsync(created.SchematicId, 1);
        var links = (await response.Content.ReadFromJsonAsync<DownloadLinks>(SignIn.Json, TestContext.Current.CancellationToken))!;
        using var anonymous = new HttpClient();
        var downloaded = await anonymous.GetByteArrayAsync(links.FileUrl, TestContext.Current.CancellationToken);

        Assert.Equal(created.Version.Sha256, Convert.ToHexStringLower(SHA256.HashData(downloaded)));
        Assert.Null(links.ThumbnailUrl);
        Assert.True(links.ExpiresAt > DateTimeOffset.UtcNow.AddMinutes(4));
    }

    [Fact]
    public async Task Expired_links_return_no_content()
    {
        var library = await Library.SignedInAsync(Factory().With("Storage:LinkLifetime", "00:00:01"));
        var created = await library.NewAsync("Short", Library.Fixture("house.nbt"));
        using var response = await library.LinksAsync(created.SchematicId, 1);
        var links = (await response.Content.ReadFromJsonAsync<DownloadLinks>(SignIn.Json, TestContext.Current.CancellationToken))!;

        await Task.Delay(TimeSpan.FromSeconds(2.5), TestContext.Current.CancellationToken);
        using var anonymous = new HttpClient();
        using var expired = await anonymous.GetAsync(links.FileUrl, TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Forbidden, expired.StatusCode);
    }

    [Fact]
    public async Task A_thumbnail_can_be_attached_once_and_counts_toward_usage()
    {
        var library = await Library.SignedInAsync(Factory());
        var created = await library.NewAsync("Pictured", Library.Fixture("house.nbt"));
        var png = Library.Png(256, 256, 1000);
        var before = (await library.MeAsync()).UsedBytes;

        using var attached = await library.ThumbnailAsync(created.SchematicId, 1, png);
        using var second = await library.ThumbnailAsync(created.SchematicId, 1, png);
        using var linksResponse = await library.LinksAsync(created.SchematicId, 1);
        var links = (await linksResponse.Content.ReadFromJsonAsync<DownloadLinks>(SignIn.Json, TestContext.Current.CancellationToken))!;
        using var anonymous = new HttpClient();
        var thumbnail = await anonymous.GetByteArrayAsync(links.ThumbnailUrl!, TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.NoContent, attached.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, second.StatusCode);
        Assert.Equal(ProblemCode.Thumbnail_exists, (await second.ProblemAsync()).Code);
        Assert.True(Assert.Single((await library.DetailsAsync(created.SchematicId)).Versions).HasThumbnail);
        Assert.True(Assert.Single((await library.ListAsync()).Items).Latest.HasThumbnail);
        Assert.Equal(png, thumbnail);
        Assert.Equal(before + png.Length, (await library.MeAsync()).UsedBytes);
    }

    [Theory]
    [InlineData("not-png", HttpStatusCode.UnprocessableEntity)]
    [InlineData("too-wide", HttpStatusCode.UnprocessableEntity)]
    [InlineData("too-heavy", HttpStatusCode.RequestEntityTooLarge)]
    public async Task Invalid_thumbnails_are_rejected_and_change_nothing(string kind, HttpStatusCode status)
    {
        var library = await Library.SignedInAsync(Factory());
        var created = await library.NewAsync("Plain", Library.Fixture("house.nbt"));
        var bytes = kind switch
        {
            "not-png" => Library.Fixture("house.nbt"),
            "too-wide" => Library.Png(300, 200),
            _ => Library.Png(64, 64, (256 * 1024) + 1),
        };

        using var response = await library.ThumbnailAsync(created.SchematicId, 1, bytes);

        Assert.Equal(status, response.StatusCode);
        Assert.Equal(ProblemCode.Thumbnail_invalid, (await response.ProblemAsync()).Code);
        Assert.False(Assert.Single((await library.DetailsAsync(created.SchematicId)).Versions).HasThumbnail);
    }

    [Fact]
    public async Task Deleting_a_version_keeps_the_others_and_frees_its_space()
    {
        var library = await Library.SignedInAsync(Factory());
        var files = new[] { "house.nbt", "house.v2.schem", "house.v3.schem", "stairs.v2.schem" };
        var first = await library.NewAsync("Versions", Library.Fixture(files[0]));
        foreach (var file in files[1..])
        {
            await library.VersionAsync(first.SchematicId, Library.Fixture(file));
        }

        var before = (await library.MeAsync()).UsedBytes;

        using var response = await library.DeleteAsync($"/v1/schematics/{first.SchematicId}/versions/2");

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        Assert.Equal([1, 3, 4], (await library.DetailsAsync(first.SchematicId)).Versions.Select(version => version.Number));
        Assert.Equal(before - Library.Fixture(files[1]).Length, (await library.MeAsync()).UsedBytes);
    }

    [Fact]
    public async Task The_only_version_cannot_be_deleted()
    {
        var library = await Library.SignedInAsync(Factory());
        var created = await library.NewAsync("Single", Library.Fixture("house.nbt"));

        using var response = await library.DeleteAsync($"/v1/schematics/{created.SchematicId}/versions/1");

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(ProblemCode.Last_version, (await response.ProblemAsync()).Code);
        Assert.Single((await library.DetailsAsync(created.SchematicId)).Versions);
    }

    [Fact]
    public async Task Deleting_a_schematic_frees_its_space_and_stops_new_links()
    {
        var library = await Library.SignedInAsync(Factory());
        var created = await library.NewAsync("Gone", Library.Fixture("house.nbt"));
        await library.VersionAsync(created.SchematicId, Library.Fixture("house.v2.schem"));
        using (await library.ThumbnailAsync(created.SchematicId, 2, Library.Png(16, 16)))
        {
        }

        using var response = await library.DeleteAsync($"/v1/schematics/{created.SchematicId}");
        using var links = await library.LinksAsync(created.SchematicId, 1);

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, links.StatusCode);
        Assert.Equal(0, (await library.MeAsync()).UsedBytes);
        Assert.Empty((await library.ListAsync()).Items);
    }

    [Fact]
    public async Task The_sweeper_removes_old_orphan_blobs_and_keeps_referenced_ones()
    {
        var clock = new FakeTimeProvider(DateTimeOffset.UtcNow);
        var factory = new CloudFactory(infrastructure) { Clock = clock };
        disposables.Add(factory);
        var library = await Library.SignedInAsync(factory);
        var kept = await library.NewAsync("Kept", Library.Fixture("house.nbt"));
        var account = (await library.MeAsync()).Uuid.ToString();
        var blobs = factory.Services.GetRequiredService<BlobStore>();
        var orphan = BlobStore.FileKey(account, new string('0', 64));
        await blobs.PutBytesAsync(orphan, [1, 2, 3], "application/octet-stream", TestContext.Current.CancellationToken);
        var sweeper = factory.Services.GetRequiredService<IRequiredActor<BlobSweeperActor>>();

        var young = await sweeper.ActorRef.Ask<int>(new BlobSweeperActor.Sweep(), TimeSpan.FromSeconds(30), TestContext.Current.CancellationToken);
        clock.Advance(TimeSpan.FromHours(2));
        var old = await sweeper.ActorRef.Ask<int>(new BlobSweeperActor.Sweep(), TimeSpan.FromSeconds(30), TestContext.Current.CancellationToken);

        Assert.Equal(0, young);
        Assert.True(old >= 1);
        Assert.False(await blobs.ExistsAsync(orphan, TestContext.Current.CancellationToken));
        Assert.True(await blobs.ExistsAsync(BlobStore.FileKey(account, kept.Version.Sha256), TestContext.Current.CancellationToken));
    }

    private static async Task Organize(Library library, string schematicId, object request)
    {
        using var response = await library.OrganizeAsync(schematicId, request);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    private static async Task<(HttpStatusCode Status, string? ContentType, string Body)> Send(Library library, string method, string path)
    {
        using var request = new HttpRequestMessage(new HttpMethod(method), new Uri(path, UriKind.Relative));
        if (method is "PATCH")
        {
            request.Content = JsonContent.Create(new { name = "Stolen" });
        }
        else if (method is "POST" or "PUT" && !path.EndsWith("/links", StringComparison.Ordinal))
        {
            request.Content = new ByteArrayContent(Library.Fixture("house.nbt"));
            request.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue(method == "PUT" ? "image/png" : "application/octet-stream");
        }

        using var response = await library.Client.SendAsync(request, TestContext.Current.CancellationToken);
        return (response.StatusCode, response.Content.Headers.ContentType?.ToString(), await response.Content.ReadAsStringAsync(TestContext.Current.CancellationToken));
    }

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure);
        disposables.Add(factory);
        return factory;
    }
}
