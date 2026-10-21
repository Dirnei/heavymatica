using System.Net;
using System.Net.Http.Json;
using System.Security.Cryptography;
using Heavymatica.Cloud.Contract;
using Heavymatica.Cloud.Infrastructure.S3;
using Microsoft.Extensions.DependencyInjection;

namespace Heavymatica.Cloud.Tests.Features.Schematics;

[Trait("Category", "Integration")]
public sealed class UploadTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    [Fact]
    public async Task Blob_store_round_trips_an_object_through_minio()
    {
        var factory = Factory();
        using var client = factory.CreateClient();
        var blobs = factory.Services.GetRequiredService<BlobStore>();
        var key = BlobStore.FileKey("round-trip", "abc");

        await blobs.PutBytesAsync(key, [1, 2, 3], "application/octet-stream", TestContext.Current.CancellationToken);
        var link = await blobs.LinkAsync(key, DateTimeOffset.UtcNow.AddMinutes(1));
        using var raw = new HttpClient();
        var fetched = await raw.GetByteArrayAsync(new Uri(link), TestContext.Current.CancellationToken);
        await blobs.DeleteAsync(key, TestContext.Current.CancellationToken);

        Assert.Equal([1, 2, 3], fetched);
        Assert.False(await blobs.ExistsAsync(key, TestContext.Current.CancellationToken));
    }

    [Fact]
    public async Task A_new_schematic_starts_at_version_1_with_its_metadata()
    {
        var library = await Library.SignedInAsync(Factory());
        var file = Library.Fixture("house.nbt");

        var result = await library.NewAsync("House", file);

        Assert.Equal(1, result.Version.Number);
        Assert.Equal(Convert.ToHexStringLower(SHA256.HashData(file)), result.Version.Sha256);
        Assert.Equal(SchematicFormat.Structure, result.Version.Format);
        Assert.Equal(file.Length, result.Version.SizeBytes);
        Assert.Equal((5, 4, 6), (result.Version.Dimensions.Width, result.Version.Dimensions.Height, result.Version.Dimensions.Length));
        Assert.Equal(4671, result.Version.DataVersion);
        Assert.False(result.Version.HasThumbnail);
        Assert.Equal("minecraft:oak_planks", result.Version.BlockCounts.First().Block);
        Assert.Equal(result.Version.BlockTotal, result.Version.BlockCounts.Sum(count => count.Count));
        var details = await library.DetailsAsync(result.SchematicId);
        Assert.Equal("House", details.Name);
    }

    [Fact]
    public async Task New_versions_take_the_next_number_and_keep_older_ones()
    {
        var library = await Library.SignedInAsync(Factory());
        var first = await library.NewAsync("Tower", Library.Fixture("house.nbt"));

        var second = await library.VersionAsync(first.SchematicId, Library.Fixture("house.v2.schem"));
        var third = await library.VersionAsync(first.SchematicId, Library.Fixture("house.v3.schem"));

        Assert.Equal([2, 3], [second.Version.Number, third.Version.Number]);
        var details = await library.DetailsAsync(first.SchematicId);
        Assert.Equal([1, 2, 3], details.Versions.Select(version => version.Number));
        Assert.Equal(first.Version.Sha256, details.Versions[0].Sha256);
    }

    [Fact]
    public async Task An_invalid_file_is_rejected_with_its_reason_and_leaves_nothing_behind()
    {
        var factory = Factory();
        var library = await Library.SignedInAsync(factory);

        using var response = await library.UploadNewAsync("Broken", Library.Fixture("truncated.litematic"));

        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        Assert.Equal(ProblemCode.Malformed_nbt, (await response.ProblemAsync()).Code);
        Assert.Empty((await library.ListAsync()).Items);
        Assert.Equal(0, (await library.MeAsync()).UsedBytes);
        await AssertNoBlobsAsync(factory, (await library.MeAsync()).Uuid);
    }

    [Theory]
    [InlineData("unsupported-text.txt", HttpStatusCode.UnprocessableEntity, ProblemCode.Unsupported_format)]
    [InlineData("file-too-large.bin", HttpStatusCode.RequestEntityTooLarge, ProblemCode.File_too_large)]
    [InlineData("decompression-bomb.nbt", HttpStatusCode.RequestEntityTooLarge, ProblemCode.Decompressed_too_large)]
    [InlineData("dimensions-too-large.v3.schem", HttpStatusCode.UnprocessableEntity, ProblemCode.Dimensions_too_large)]
    public async Task Rejections_carry_the_format_reason_code(string fixture, HttpStatusCode status, ProblemCode code)
    {
        var library = await Library.SignedInAsync(Factory());

        using var response = await library.UploadNewAsync("Bad", Library.Fixture(fixture));

        Assert.Equal(status, response.StatusCode);
        Assert.Equal(code, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task An_upload_over_quota_is_rejected_and_stores_nothing()
    {
        var factory = Factory().With("Storage:DefaultQuotaBytes", "500");
        var library = await Library.SignedInAsync(factory);

        using var response = await library.UploadNewAsync("Too big", Library.Fixture("house.nbt"));

        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        Assert.Equal(ProblemCode.Over_quota, (await response.ProblemAsync()).Code);
        Assert.Empty((await library.ListAsync()).Items);
        Assert.Equal(0, (await library.MeAsync()).UsedBytes);
        await AssertNoBlobsAsync(factory, (await library.MeAsync()).Uuid);
    }

    [Fact]
    public async Task Uploading_the_latest_version_again_creates_nothing_and_uses_no_number()
    {
        var library = await Library.SignedInAsync(Factory());
        var first = await library.NewAsync("Barn", Library.Fixture("house.nbt"));
        var usage = (await library.MeAsync()).UsedBytes;

        using var again = await library.UploadVersionAsync(first.SchematicId, Library.Fixture("house.nbt"));
        var repeated = (await again.Content.ReadFromJsonAsync<UploadResult>(SignIn.Json, TestContext.Current.CancellationToken))!;
        var next = await library.VersionAsync(first.SchematicId, Library.Fixture("house.v2.schem"));

        Assert.Equal(HttpStatusCode.OK, again.StatusCode);
        Assert.Equal(1, repeated.Version.Number);
        Assert.Equal(2, next.Version.Number);
        Assert.Equal(usage + Library.Fixture("house.v2.schem").Length, (await library.MeAsync()).UsedBytes);
    }

    [Fact]
    public async Task Usage_counts_every_stored_version()
    {
        var library = await Library.SignedInAsync(Factory());
        var first = await library.NewAsync("Count", Library.Fixture("house.nbt"));
        await library.VersionAsync(first.SchematicId, Library.Fixture("house.v2.schem"));

        var me = await library.MeAsync();

        Assert.Equal(Library.Fixture("house.nbt").Length + Library.Fixture("house.v2.schem").Length, me.UsedBytes);
    }

    [Fact]
    public async Task Me_reports_the_configured_default_quota()
    {
        var library = await Library.SignedInAsync(Factory().With("Storage:DefaultQuotaBytes", "12345"));

        var me = await library.MeAsync();

        Assert.Equal(12345, me.QuotaBytes);
        Assert.Equal(0, me.UsedBytes);
    }

    private static async Task AssertNoBlobsAsync(CloudFactory factory, Guid account)
    {
        var blobs = factory.Services.GetRequiredService<BlobStore>();
        var stored = new List<StoredBlob>();
        await foreach (var blob in blobs.ListAsync($"accounts/{account}/", TestContext.Current.CancellationToken))
        {
            stored.Add(blob);
        }

        Assert.Empty(stored);
    }

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure);
        disposables.Add(factory);
        return factory;
    }
}
