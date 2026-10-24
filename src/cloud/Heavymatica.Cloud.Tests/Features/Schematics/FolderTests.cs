using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;

namespace Heavymatica.Cloud.Tests.Features.Schematics;

[Trait("Category", "Integration")]
public sealed class FolderTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    [Theory]
    [InlineData("houses")]
    [InlineData("v1.2")]
    [InlineData("a+b")]
    [InlineData("(old)")]
    [InlineData("[wip]")]
    public async Task Including_subfolders_matches_the_folder_and_below_but_not_lookalikes(string folder)
    {
        var library = await Library.SignedInAsync(Factory());
        var direct = await NewInAsync(library, "Direct", folder);
        var below = await NewInAsync(library, "Below", folder + "/medieval");
        await NewInAsync(library, "Lookalike", folder + "small");
        await NewInAsync(library, "Elsewhere", "other/" + folder);

        var page = await library.ListAsync("?includeSubfolders=true&folder=" + Uri.EscapeDataString(folder));

        Assert.Equal([below, direct], page.Items.Select(item => item.Id));
    }

    [Fact]
    public async Task An_upload_can_go_straight_into_a_folder()
    {
        var library = await Library.SignedInAsync(Factory());

        var uploaded = await library.NewAsync("Filed", Library.Fixture("house.nbt"), "/builds//medieval/");
        using var tooLong = await library.UploadNewAsync("Too deep", Library.Fixture("house.nbt"), new string('x', 201));

        Assert.Equal("builds/medieval", (await library.DetailsAsync(uploaded.SchematicId)).Folder);
        Assert.Equal(HttpStatusCode.BadRequest, tooLong.StatusCode);
        Assert.Single((await library.ListAsync()).Items);
    }

    [Fact]
    public async Task The_folder_filter_is_exact_by_default()
    {
        var library = await Library.SignedInAsync(Factory());
        var direct = await NewInAsync(library, "Direct", "houses");
        await NewInAsync(library, "Below", "houses/medieval");

        var page = await library.ListAsync("?folder=houses");

        Assert.Equal([direct], page.Items.Select(item => item.Id));
    }

    [Fact]
    public async Task Unfiled_lists_only_schematics_in_no_folder()
    {
        var library = await Library.SignedInAsync(Factory());
        var loose = (await library.NewAsync("Loose", Library.Fixture("house.nbt"))).SchematicId;
        await NewInAsync(library, "Filed", "houses");

        var page = await library.ListAsync("?unfiled=true");

        Assert.Equal([loose], page.Items.Select(item => item.Id));
    }

    [Fact]
    public async Task Unfiled_together_with_a_folder_is_invalid()
    {
        var library = await Library.SignedInAsync(Factory());

        using var response = await library.Client.GetAsync(new Uri("/v1/schematics?unfiled=true&folder=houses", UriKind.Relative), TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(ProblemCode.Invalid_request, (await response.ProblemAsync()).Code);
    }

    [Fact]
    public async Task A_subfolder_listing_pages_without_repeats()
    {
        var library = await Library.SignedInAsync(Factory());
        var ids = new List<string>();
        foreach (var (name, folder) in new[] { ("A", "village"), ("B", "village/houses"), ("C", "village/houses/oak"), ("D", "village"), ("E", "village/farms") })
        {
            ids.Add(await NewInAsync(library, name, folder));
        }

        await NewInAsync(library, "Outside", "castle");

        var seen = new List<string>();
        string? cursor = null;
        do
        {
            var page = await library.ListAsync("?includeSubfolders=true&folder=village&limit=2" + (cursor is null ? string.Empty : "&cursor=" + cursor));
            seen.AddRange(page.Items.Select(item => item.Id));
            cursor = page.NextCursor;
        }
        while (cursor is not null);

        Assert.Equal(Enumerable.Reverse(ids), seen);
    }

    [Fact]
    public async Task The_folder_list_includes_parents_with_direct_and_total_counts()
    {
        var library = await Library.SignedInAsync(Factory());
        await NewInAsync(library, "Tower 1", "castle/towers");
        await NewInAsync(library, "Tower 2", "castle/towers");
        await NewInAsync(library, "Keep", "castle");
        await NewInAsync(library, "Mill", "village/farms/wheat");
        await library.NewAsync("Loose", Library.Fixture("house.nbt"));

        var list = await library.FoldersAsync();

        Assert.Equal(
            [("castle", 1, 3), ("castle/towers", 2, 2), ("village", 0, 1), ("village/farms", 0, 1), ("village/farms/wheat", 1, 1)],
            list.Folders.Select(folder => (folder.Path, folder.DirectCount, folder.TotalCount)));
        Assert.Equal(1, list.UnfiledCount);
    }

    [Fact]
    public async Task Other_accounts_folders_are_invisible()
    {
        var factory = Factory();
        var owner = await Library.SignedInAsync(factory, "Owner");
        var other = await Library.SignedInAsync(factory, "Other");
        await NewInAsync(other, "Hidden", "secret");

        var list = await owner.FoldersAsync();

        Assert.Empty(list.Folders);
        Assert.Equal(0, list.UnfiledCount);
    }

    [Fact]
    public async Task Moving_several_schematics_reports_how_many_moved_and_creates_no_version()
    {
        var library = await Library.SignedInAsync(Factory());
        var ids = new List<string>();
        foreach (var name in new[] { "One", "Two", "Three" })
        {
            ids.Add((await library.NewAsync(name, Library.Fixture("house.nbt"))).SchematicId);
        }

        Assert.Equal(3, await MoveAsync(library, ids, "/village//"));

        foreach (var id in ids)
        {
            var details = await library.DetailsAsync(id);
            Assert.Equal("village", details.Folder);
            Assert.Single(details.Versions);
        }
    }

    [Fact]
    public async Task Moving_skips_other_accounts_schematics_and_unknown_ids()
    {
        var factory = Factory();
        var owner = await Library.SignedInAsync(factory, "Owner");
        var other = await Library.SignedInAsync(factory, "Other");
        var mine = new[]
        {
            (await owner.NewAsync("Mine 1", Library.Fixture("house.nbt"))).SchematicId,
            (await owner.NewAsync("Mine 2", Library.Fixture("house.nbt"))).SchematicId,
        };
        var theirs = await NewInAsync(other, "Theirs", "kept");

        Assert.Equal(2, await MoveAsync(owner, [.. mine, theirs, "000000000000000000000000"], "village"));

        Assert.Equal("kept", (await other.DetailsAsync(theirs)).Folder);
    }

    [Fact]
    public async Task An_empty_folder_moves_out_of_every_folder_and_the_emptied_folder_disappears()
    {
        var library = await Library.SignedInAsync(Factory());
        var only = await NewInAsync(library, "Only", "drafts");

        Assert.Equal(1, await MoveAsync(library, [only], ""));

        Assert.Null((await library.DetailsAsync(only)).Folder);
        var list = await library.FoldersAsync();
        Assert.Empty(list.Folders);
        Assert.Equal(1, list.UnfiledCount);
    }

    [Theory]
    [InlineData(0)]
    [InlineData(101)]
    public async Task Moving_needs_1_to_100_ids(int count)
    {
        var library = await Library.SignedInAsync(Factory());
        var kept = await NewInAsync(library, "Kept", "home");
        var ids = Enumerable.Range(0, count).Select(index => index == 0 ? kept : index.ToString("x24", System.Globalization.CultureInfo.InvariantCulture));

        using var response = await library.MoveAsync(ids, "village");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("home", (await library.DetailsAsync(kept)).Folder);
    }

    [Fact]
    public async Task Moving_rejects_duplicate_ids()
    {
        var library = await Library.SignedInAsync(Factory());
        var kept = await NewInAsync(library, "Kept", "home");

        using var response = await library.MoveAsync([kept, kept], "village");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("home", (await library.DetailsAsync(kept)).Folder);
    }

    [Fact]
    public async Task Renaming_a_folder_carries_its_subfolders_but_not_lookalikes()
    {
        var library = await Library.SignedInAsync(Factory());
        var keep = await NewInAsync(library, "Keep", "castle");
        var tower = await NewInAsync(library, "Tower", "castle/towers");
        var lookalike = await NewInAsync(library, "Lookalike", "castles");

        Assert.Equal(2, await RenameAsync(library, "castle", "builds/castle"));

        Assert.Equal("builds/castle", (await library.DetailsAsync(keep)).Folder);
        Assert.Equal("builds/castle/towers", (await library.DetailsAsync(tower)).Folder);
        Assert.Equal("castles", (await library.DetailsAsync(lookalike)).Folder);
    }

    [Fact]
    public async Task Renaming_onto_an_existing_folder_merges_them()
    {
        var library = await Library.SignedInAsync(Factory());
        var draft = await NewInAsync(library, "Draft", "drafts");
        var wip = await NewInAsync(library, "Wip", "wip");

        Assert.Equal(1, await RenameAsync(library, "drafts", "wip"));

        Assert.Equal("wip", (await library.DetailsAsync(draft)).Folder);
        Assert.Equal("wip", (await library.DetailsAsync(wip)).Folder);
        Assert.Equal(["wip"], (await library.FoldersAsync()).Folders.Select(folder => folder.Path));
    }

    [Fact]
    public async Task Renaming_into_its_own_subfolder_nests_everything()
    {
        var library = await Library.SignedInAsync(Factory());
        var top = await NewInAsync(library, "Top", "a");
        var below = await NewInAsync(library, "Below", "a/x");

        Assert.Equal(2, await RenameAsync(library, "a", "a/b"));

        Assert.Equal("a/b", (await library.DetailsAsync(top)).Folder);
        Assert.Equal("a/b/x", (await library.DetailsAsync(below)).Folder);
    }

    [Fact]
    public async Task Renaming_keeps_non_ascii_paths_intact()
    {
        var library = await Library.SignedInAsync(Factory());
        var below = await NewInAsync(library, "Below", "🏰 burg/türme");

        Assert.Equal(1, await RenameAsync(library, "🏰 burg", "castle"));

        Assert.Equal("castle/türme", (await library.DetailsAsync(below)).Folder);
    }

    [Fact]
    public async Task Renaming_an_unknown_folder_is_not_found()
    {
        var factory = Factory();
        var owner = await Library.SignedInAsync(factory, "Owner");
        var other = await Library.SignedInAsync(factory, "Other");
        var theirs = await NewInAsync(other, "Theirs", "castle");

        using var response = await owner.RenameFolderAsync("castle", "mine");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(ProblemCode.Not_found, (await response.ProblemAsync()).Code);
        Assert.Equal("castle", (await other.DetailsAsync(theirs)).Folder);
    }

    [Fact]
    public async Task A_rename_that_makes_a_path_too_long_moves_nothing()
    {
        var library = await Library.SignedInAsync(Factory());
        var deep = await NewInAsync(library, "Deep", "a/" + new string('x', 198));
        var shallow = await NewInAsync(library, "Shallow", "a");

        using var response = await library.RenameFolderAsync("a", "ab");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(ProblemCode.Invalid_request, (await response.ProblemAsync()).Code);
        Assert.Equal("a/" + new string('x', 198), (await library.DetailsAsync(deep)).Folder);
        Assert.Equal("a", (await library.DetailsAsync(shallow)).Folder);
    }

    [Fact]
    public async Task Renaming_a_folder_to_itself_moves_nothing()
    {
        var library = await Library.SignedInAsync(Factory());
        await NewInAsync(library, "Keep", "castle");

        Assert.Equal(0, await RenameAsync(library, "castle", "/castle/"));
    }

    private static async Task<string> NewInAsync(Library library, string name, string folder)
    {
        var id = (await library.NewAsync(name, Library.Fixture("house.nbt"))).SchematicId;
        using var response = await library.OrganizeAsync(id, new { folder });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return id;
    }

    private static async Task<int> MoveAsync(Library library, IEnumerable<string> ids, string folder)
    {
        using var response = await library.MoveAsync(ids, folder);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<MoveResult>(SignIn.Json, TestContext.Current.CancellationToken))!.Moved;
    }

    private static async Task<int> RenameAsync(Library library, string from, string to)
    {
        using var response = await library.RenameFolderAsync(from, to);
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return (await response.Content.ReadFromJsonAsync<MoveResult>(SignIn.Json, TestContext.Current.CancellationToken))!.Moved;
    }

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure);
        disposables.Add(factory);
        return factory;
    }
}
