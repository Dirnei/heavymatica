using System.Net;
using Akka.Actor;
using Akka.Hosting;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Features.Account;
using Heavymatica.Cloud.Features.Schematics;
using Heavymatica.Cloud.Features.Schematics.Sweeper;
using Heavymatica.Cloud.Features.Schematics.Thumbnails;
using Heavymatica.Cloud.Infrastructure.S3;
using Heavymatica.Cloud.Tests.Features.Identity;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Time.Testing;
using MongoDB.Driver;

namespace Heavymatica.Cloud.Tests.Features.Schematics.Thumbnails;

[Trait("Category", "Integration")]
public sealed class ThumbnailRenderingTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly FakeTimeProvider clock = new(DateTimeOffset.UtcNow);
    private readonly List<IDisposable> disposables = [];
    private CloudFactory? factory;

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    private static CancellationToken Cancel => TestContext.Current.CancellationToken;

    private CloudFactory Factory => factory ??= Track(new CloudFactory(infrastructure) { Clock = clock });

    private ThumbnailWorker Worker => Factory.Services.GetRequiredService<ThumbnailWorker>();

    private BlobStore Blobs => Factory.Services.GetRequiredService<BlobStore>();

    private IMongoCollection<VersionDocument> Versions => VersionDocument.In(Factory.Database);

    [Fact]
    public async Task A_website_upload_is_rendered_on_the_next_run_and_counts_toward_usage()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        var usageBefore = (await web.MeAsync()).UsedBytes;

        var outcome = await Worker.RenderNextAsync(Cancel);
        var version = (await web.DetailsAsync(uploaded.SchematicId)).Versions.Single();
        var listed = (await web.ListAsync()).Items.Single().Latest;
        var stored = await Versions.Find(document => document.SchematicId == uploaded.SchematicId).SingleAsync(Cancel);

        Assert.Equal(RenderOutcome.Rendered, outcome);
        Assert.Equal((true, ThumbnailSource.Rendered), (version.HasThumbnail, version.ThumbnailSource));
        Assert.Equal((true, ThumbnailSource.Rendered), (listed.HasThumbnail, listed.ThumbnailSource));
        Assert.Equal(usageBefore + stored.ThumbnailBytes, (await web.MeAsync()).UsedBytes);
        var png = await Blobs.GetBytesAsync(BlobStore.ThumbnailKey(stored.Owner, stored.SchematicId, 1, rendered: true), Cancel);
        Assert.Equal((256, 256), AttachThumbnail.Dimensions(png));
        Assert.Equal(RenderOutcome.Idle, await Worker.RenderNextAsync(Cancel));
    }

    [Fact]
    public async Task A_game_upload_waits_for_the_grace_period()
    {
        var game = await GameLibraryAsync();
        var uploaded = await game.NewAsync("Hut", Library.Fixture("house.nbt"));

        var early = await Worker.RenderNextAsync(Cancel);
        clock.Advance(TimeSpan.FromMinutes(1) + TimeSpan.FromSeconds(1));
        var late = await Worker.RenderNextAsync(Cancel);

        Assert.Equal(RenderOutcome.Idle, early);
        Assert.Equal(RenderOutcome.Rendered, late);
        Assert.Equal(ThumbnailSource.Rendered, (await game.DetailsAsync(uploaded.SchematicId)).Versions.Single().ThumbnailSource);
    }

    [Fact]
    public async Task A_thumbnail_attached_in_the_grace_period_means_nothing_is_rendered()
    {
        var game = await GameLibraryAsync();
        var uploaded = await game.NewAsync("Hut", Library.Fixture("house.nbt"));
        using (var attach = await game.ThumbnailAsync(uploaded.SchematicId, 1, Library.Png(64, 64)))
        {
            Assert.Equal(HttpStatusCode.NoContent, attach.StatusCode);
        }

        clock.Advance(TimeSpan.FromMinutes(2));

        Assert.Equal(RenderOutcome.Idle, await Worker.RenderNextAsync(Cancel));
        Assert.Equal(ThumbnailSource.Uploaded, (await game.DetailsAsync(uploaded.SchematicId)).Versions.Single().ThumbnailSource);
    }

    [Fact]
    public async Task An_identical_upload_changes_nothing()
    {
        var web = await WebLibraryAsync();
        var first = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        var before = await Versions.Find(document => document.SchematicId == first.SchematicId).SingleAsync(Cancel);
        clock.Advance(TimeSpan.FromMinutes(5));

        using var again = await web.UploadVersionAsync(first.SchematicId, Library.Fixture("house.nbt"));
        var after = await Versions.Find(document => document.SchematicId == first.SchematicId).SingleAsync(Cancel);

        Assert.Equal(before.ThumbnailDueAt, after.ThumbnailDueAt);
        Assert.Equal(before.CreatedAt, after.ThumbnailDueAt);
    }

    [Fact]
    public async Task Due_times_are_now_for_the_website_and_after_the_grace_period_for_the_game()
    {
        var web = await WebLibraryAsync();
        var game = await GameLibraryAsync();
        var fromWeb = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        var fromGame = await game.NewAsync("Hut", Library.Fixture("house.v3.schem"));

        var webVersion = await Versions.Find(document => document.SchematicId == fromWeb.SchematicId).SingleAsync(Cancel);
        var gameVersion = await Versions.Find(document => document.SchematicId == fromGame.SchematicId).SingleAsync(Cancel);

        Assert.Equal(webVersion.CreatedAt, webVersion.ThumbnailDueAt);
        Assert.Equal(gameVersion.CreatedAt + TimeSpan.FromMinutes(1), gameVersion.ThumbnailDueAt);
    }

    [Fact]
    public async Task The_startup_step_queues_old_versions_once_and_leaves_thumbnailed_ones_alone()
    {
        var game = await GameLibraryAsync();
        var bare = await game.NewAsync("Old", Library.Fixture("house.nbt"));
        var pictured = await game.NewAsync("Pictured", Library.Fixture("house.v3.schem"));
        using (var attach = await game.ThumbnailAsync(pictured.SchematicId, 1, Library.Png(64, 64)))
        {
            Assert.Equal(HttpStatusCode.NoContent, attach.StatusCode);
        }

        // Versions stored before rendering existed have neither field.
        await Versions.UpdateManyAsync(_ => true, Builders<VersionDocument>.Update.Unset(document => document.ThumbnailDueAt).Unset(document => document.RenderAttempts),
            cancellationToken: Cancel);
        var backfill = Factory.Services.GetServices<IHostedService>().OfType<RenderBackfill>().Single();

        await backfill.StartAsync(Cancel);
        var queued = await Versions.Find(document => document.SchematicId == bare.SchematicId).SingleAsync(Cancel);
        await Versions.UpdateOneAsync(document => document.Id == queued.Id, Builders<VersionDocument>.Update.Set(document => document.ThumbnailDueAt, DateTime.UnixEpoch),
            cancellationToken: Cancel);
        await backfill.StartAsync(Cancel);

        Assert.Equal(queued.CreatedAt, queued.ThumbnailDueAt);
        Assert.Equal(DateTime.UnixEpoch, (await Versions.Find(document => document.Id == queued.Id).SingleAsync(Cancel)).ThumbnailDueAt);
        Assert.Null((await Versions.Find(document => document.SchematicId == pictured.SchematicId).SingleAsync(Cancel)).ThumbnailDueAt);
        Assert.Equal(RenderOutcome.Rendered, await Worker.RenderNextAsync(Cancel));
        Assert.True((await Versions.Find(document => document.Id == queued.Id).SingleAsync(Cancel)).HasThumbnail);
    }

    [Fact]
    public async Task A_version_queued_without_an_attempt_count_is_repaired_and_rendered()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        await Versions.UpdateManyAsync(_ => true, Builders<VersionDocument>.Update.Unset(document => document.RenderAttempts), cancellationToken: Cancel);
        var stranded = await Worker.RenderNextAsync(Cancel);

        await Factory.Services.GetServices<IHostedService>().OfType<RenderBackfill>().Single().StartAsync(Cancel);

        Assert.Equal(RenderOutcome.Idle, stranded);
        Assert.Equal(RenderOutcome.Rendered, await Worker.RenderNextAsync(Cancel));
        Assert.True((await web.DetailsAsync(uploaded.SchematicId)).Versions.Single().HasThumbnail);
    }

    [Fact]
    public async Task A_new_upload_is_rendered_before_an_older_backfill_version()
    {
        var game = await GameLibraryAsync();
        var old = await game.NewAsync("Old", Library.Fixture("house.nbt"));
        await Versions.UpdateOneAsync(document => document.SchematicId == old.SchematicId,
            Builders<VersionDocument>.Update.Set(document => document.ThumbnailDueAt, clock.GetUtcNow().UtcDateTime.AddDays(-30)), cancellationToken: Cancel);
        var web = await WebLibraryAsync();
        var fresh = await web.NewAsync("Fresh", Library.Fixture("house.v3.schem"));

        await Worker.RenderNextAsync(Cancel);

        Assert.True((await Versions.Find(document => document.SchematicId == fresh.SchematicId).SingleAsync(Cancel)).HasThumbnail);
        Assert.False((await Versions.Find(document => document.SchematicId == old.SchematicId).SingleAsync(Cancel)).HasThumbnail);
    }

    [Fact]
    public async Task An_account_at_its_quota_still_gets_its_thumbnail()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        var me = await web.MeAsync();
        await AccountDocument.In(Factory.Database).UpdateOneAsync(account => account.Id == me.Id,
            Builders<AccountDocument>.Update.Set(account => account.QuotaBytes, me.UsedBytes), cancellationToken: Cancel);

        Assert.Equal(RenderOutcome.Rendered, await Worker.RenderNextAsync(Cancel));
        var stored = await Versions.Find(document => document.SchematicId == uploaded.SchematicId).SingleAsync(Cancel);
        Assert.Equal(me.UsedBytes + stored.ThumbnailBytes, (await web.MeAsync()).UsedBytes);
    }

    [Fact]
    public async Task A_corrupt_file_is_tried_three_times_and_then_left_alone()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        await Blobs.PutBytesAsync(BlobStore.FileKey((await web.MeAsync()).Id, uploaded.Version.Sha256), [1, 2, 3, 4], "application/octet-stream", Cancel);

        var outcomes = new List<RenderOutcome>();
        for (var attempt = 0; attempt < 4; attempt++)
        {
            outcomes.Add(await Worker.RenderNextAsync(Cancel));
            clock.Advance(TimeSpan.FromMinutes(5) + TimeSpan.FromSeconds(1));
        }

        Assert.Equal([RenderOutcome.Failed, RenderOutcome.Failed, RenderOutcome.Failed, RenderOutcome.Idle], outcomes);
        var version = (await web.DetailsAsync(uploaded.SchematicId)).Versions.Single();
        Assert.False(version.HasThumbnail);
        Assert.Null(version.ThumbnailSource);
    }

    [Fact]
    public async Task A_claimed_version_is_not_taken_twice_while_its_claim_lasts()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        await Blobs.PutBytesAsync(BlobStore.FileKey((await web.MeAsync()).Id, uploaded.Version.Sha256), [1, 2, 3, 4], "application/octet-stream", Cancel);

        Assert.Equal(RenderOutcome.Failed, await Worker.RenderNextAsync(Cancel));
        Assert.Equal(RenderOutcome.Idle, await Worker.RenderNextAsync(Cancel));
    }

    [Fact]
    public async Task Only_air_is_never_rendered_and_not_tried_again()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Nothing", Library.Fixture("house.nbt"));
        var version = await Versions.Find(document => document.SchematicId == uploaded.SchematicId).SingleAsync(Cancel);
        var air = AirOnlyStructure();
        await Blobs.PutBytesAsync(BlobStore.FileKey(version.Owner, version.Sha256), air, "application/octet-stream", Cancel);

        Assert.Equal(RenderOutcome.NothingToDraw, await Worker.RenderNextAsync(Cancel));
        clock.Advance(TimeSpan.FromHours(1));
        Assert.Equal(RenderOutcome.Idle, await Worker.RenderNextAsync(Cancel));
        Assert.False((await web.DetailsAsync(uploaded.SchematicId)).Versions.Single().HasThumbnail);
    }

    [Fact]
    public async Task An_attached_thumbnail_replaces_a_rendered_one_and_its_usage()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        await Worker.RenderNextAsync(Cancel);
        var rendered = await Versions.Find(document => document.SchematicId == uploaded.SchematicId).SingleAsync(Cancel);
        var usageWithRendered = (await web.MeAsync()).UsedBytes;
        var png = Library.Png(128, 128, 500);

        using var attach = await web.ThumbnailAsync(uploaded.SchematicId, 1, png);
        using var second = await web.ThumbnailAsync(uploaded.SchematicId, 1, png);

        Assert.Equal(HttpStatusCode.NoContent, attach.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, second.StatusCode);
        Assert.Equal(ProblemCode.Thumbnail_exists, (await second.ProblemAsync()).Code);
        var version = (await web.DetailsAsync(uploaded.SchematicId)).Versions.Single();
        Assert.Equal((true, ThumbnailSource.Uploaded), (version.HasThumbnail, version.ThumbnailSource));
        Assert.Equal(ThumbnailSource.Uploaded, (await web.ListAsync()).Items.Single().Latest.ThumbnailSource);
        Assert.Equal(usageWithRendered - rendered.ThumbnailBytes + png.Length, (await web.MeAsync()).UsedBytes);
        Assert.False(await Blobs.ExistsAsync(BlobStore.ThumbnailKey(rendered.Owner, rendered.SchematicId, 1, rendered: true), Cancel));
        Assert.Equal(png, await Blobs.GetBytesAsync(BlobStore.ThumbnailKey(rendered.Owner, rendered.SchematicId, 1), Cancel));
    }

    [Fact]
    public async Task The_sweeper_keeps_a_rendered_thumbnail_in_use_and_removes_an_orphaned_one()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        await Worker.RenderNextAsync(Cancel);
        var owner = (await web.MeAsync()).Id;
        var inUse = BlobStore.ThumbnailKey(owner, uploaded.SchematicId, 1, rendered: true);
        var orphan = BlobStore.ThumbnailKey(owner, uploaded.SchematicId, 7, rendered: true);
        var uploadedKeyWithoutThumbnail = BlobStore.ThumbnailKey(owner, uploaded.SchematicId, 1);
        await Blobs.PutBytesAsync(orphan, [1], "image/png", Cancel);
        await Blobs.PutBytesAsync(uploadedKeyWithoutThumbnail, [1], "image/png", Cancel);
        clock.Advance(TimeSpan.FromHours(2));

        await Factory.Services.GetRequiredService<IRequiredActor<BlobSweeperActor>>().ActorRef
            .Ask<int>(new BlobSweeperActor.Sweep(), TimeSpan.FromSeconds(30), Cancel);

        Assert.True(await Blobs.ExistsAsync(inUse, Cancel));
        Assert.False(await Blobs.ExistsAsync(orphan, Cancel));
        Assert.False(await Blobs.ExistsAsync(uploadedKeyWithoutThumbnail, Cancel));
    }

    [Fact]
    public async Task Deleting_a_version_removes_its_rendered_thumbnail()
    {
        var web = await WebLibraryAsync();
        var uploaded = await web.NewAsync("Castle", Library.Fixture("house.nbt"));
        await Worker.RenderNextAsync(Cancel);
        var owner = (await web.MeAsync()).Id;

        using var deleted = await web.DeleteAsync($"/v1/schematics/{uploaded.SchematicId}");

        Assert.Equal(HttpStatusCode.NoContent, deleted.StatusCode);
        Assert.False(await Blobs.ExistsAsync(BlobStore.ThumbnailKey(owner, uploaded.SchematicId, 1, rendered: true), Cancel));
        Assert.Equal(0, (await web.MeAsync()).UsedBytes);
    }

    /// <summary>A structure-format file of 2 x 1 x 1 holding only air.</summary>
    private static byte[] AirOnlyStructure()
    {
        using var stream = new MemoryStream();
        using (var gzip = new System.IO.Compression.GZipStream(stream, System.IO.Compression.CompressionLevel.Fastest, leaveOpen: true))
        using (var writer = new BinaryWriter(gzip))
        {
            void Name(string name)
            {
                var bytes = System.Text.Encoding.UTF8.GetBytes(name);
                writer.Write(System.Buffers.Binary.BinaryPrimitives.ReverseEndianness((short)bytes.Length));
                writer.Write(bytes);
            }

            void Int(int value) => writer.Write(System.Buffers.Binary.BinaryPrimitives.ReverseEndianness(value));

            writer.Write((byte)10);
            Name(string.Empty);
            writer.Write((byte)3);
            Name("DataVersion");
            Int(4440);
            writer.Write((byte)9);
            Name("size");
            writer.Write((byte)3);
            Int(3);
            Int(2);
            Int(1);
            Int(1);
            writer.Write((byte)9);
            Name("palette");
            writer.Write((byte)10);
            Int(1);
            writer.Write((byte)8);
            Name("Name");
            Name("minecraft:air");
            writer.Write((byte)0);
            writer.Write((byte)9);
            Name("blocks");
            writer.Write((byte)10);
            Int(2);
            for (var x = 0; x < 2; x++)
            {
                writer.Write((byte)9);
                Name("pos");
                writer.Write((byte)3);
                Int(3);
                Int(x);
                Int(0);
                Int(0);
                writer.Write((byte)3);
                Name("state");
                Int(0);
                writer.Write((byte)0);
            }

            writer.Write((byte)9);
            Name("entities");
            writer.Write((byte)10);
            Int(0);
            writer.Write((byte)0);
        }

        return stream.ToArray();
    }

    private async Task<Library> GameLibraryAsync()
    {
        var library = await Library.SignedInAsync(Factory);
        Track(library.Client);
        return library;
    }

    /// <summary>A library whose requests carry a website session cookie, as the website sends them.</summary>
    private async Task<Library> WebLibraryAsync()
    {
        var (_, _, cookie) = await WebCookies.SessionAsync(Factory, "builder");
        var client = Track(Factory.CreateClient());
        client.DefaultRequestHeaders.Add("Cookie", $"{WebCookies.Name}={cookie}");
        client.DefaultRequestHeaders.Add("X-Heavymatica-Request", "1");
        client.DefaultRequestHeaders.Add("Origin", "http://localhost");
        return new Library(client);
    }

    private T Track<T>(T disposable)
        where T : IDisposable
    {
        disposables.Add(disposable);
        return disposable;
    }
}
