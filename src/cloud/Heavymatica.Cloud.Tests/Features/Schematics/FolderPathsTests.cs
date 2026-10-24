using Heavymatica.Cloud.Features.Schematics;

namespace Heavymatica.Cloud.Tests.Features.Schematics;

public sealed class FolderPathsTests
{
    [Fact]
    public void Prefixes_are_every_parent_and_the_path_shortest_first() =>
        Assert.Equal(["a", "a/b", "a/b/c"], FolderPaths.Prefixes("a/b/c"));

    [Fact]
    public void A_top_level_folder_is_its_only_prefix() =>
        Assert.Equal(["houses"], FolderPaths.Prefixes("houses"));

    [Theory]
    [InlineData("/houses//medieval/", "houses/medieval")]
    [InlineData(" houses / medieval ", "houses/medieval")]
    [InlineData("//", null)]
    [InlineData("", null)]
    public void Normalize_drops_empty_parts(string folder, string? expected) =>
        Assert.Equal(expected, FolderPaths.Normalize(folder));
}
