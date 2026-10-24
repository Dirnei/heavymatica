using System.Net;
using System.Net.Http.Json;
using Heavymatica.Cloud.Contract;

namespace Heavymatica.Cloud.Tests.Features.Schematics;

[Trait("Category", "Integration")]
public sealed class TagTests(CloudInfrastructure infrastructure) : IDisposable
{
    private readonly List<IDisposable> disposables = [];

    public void Dispose() => disposables.ForEach(disposable => disposable.Dispose());

    [Fact]
    public async Task The_tag_list_counts_schematics_per_tag_sorted_by_tag()
    {
        var library = await Library.SignedInAsync(Factory());
        await NewTaggedAsync(library, "One", "wip");
        await NewTaggedAsync(library, "Two", "wip");
        await NewTaggedAsync(library, "Three", "wip", "spruce");
        await library.NewAsync("Untagged", Library.Fixture("house.nbt"));

        var list = await TagsAsync(library);

        Assert.Equal([("spruce", 1), ("wip", 3)], list.Tags.Select(entry => (entry.Tag, entry.Count)));
    }

    [Fact]
    public async Task Other_accounts_tags_are_invisible()
    {
        var factory = Factory();
        var owner = await Library.SignedInAsync(factory, "Owner");
        var other = await Library.SignedInAsync(factory, "Other");
        await NewTaggedAsync(other, "Hidden", "secret");
        await NewTaggedAsync(owner, "Mine", "mine");

        var list = await TagsAsync(owner);

        Assert.Equal(["mine"], list.Tags.Select(entry => entry.Tag));
    }

    [Fact]
    public async Task An_empty_library_has_no_tags()
    {
        var library = await Library.SignedInAsync(Factory());

        Assert.Empty((await TagsAsync(library)).Tags);
    }

    private static async Task NewTaggedAsync(Library library, string name, params string[] tags)
    {
        var id = (await library.NewAsync(name, Library.Fixture("house.nbt"))).SchematicId;
        using var response = await library.OrganizeAsync(id, new { tags });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    private static async Task<TagList> TagsAsync(Library library) =>
        (await library.Client.GetFromJsonAsync<TagList>(new Uri("/v1/tags", UriKind.Relative), SignIn.Json, TestContext.Current.CancellationToken))!;

    private CloudFactory Factory()
    {
        var factory = new CloudFactory(infrastructure);
        disposables.Add(factory);
        return factory;
    }
}
