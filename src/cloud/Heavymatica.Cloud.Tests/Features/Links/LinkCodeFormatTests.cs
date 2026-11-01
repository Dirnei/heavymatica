using Heavymatica.Cloud.Features.Links;

namespace Heavymatica.Cloud.Tests.Features.Links;

public sealed class LinkCodeFormatTests
{
    [Fact]
    public void The_alphabet_has_30_symbols_and_no_look_alikes()
    {
        Assert.Equal(30, LinkCodeFormat.Alphabet.Length);
        Assert.Equal(30, LinkCodeFormat.Alphabet.Distinct().Count());
        foreach (var lookAlike in "01OILU")
        {
            Assert.DoesNotContain(lookAlike, LinkCodeFormat.Alphabet);
        }
    }

    [Fact]
    public void Generated_codes_are_8_symbols_from_the_alphabet_and_differ()
    {
        var generated = Enumerable.Range(0, 200).Select(_ => LinkCodeFormat.Generate()).ToList();

        Assert.All(generated, code =>
        {
            Assert.Equal(8, code.Length);
            Assert.True(LinkCodeFormat.IsWellFormed(code));
        });
        Assert.Equal(generated.Count, generated.Distinct().Count());
    }

    [Theory]
    [InlineData("K7QM3XRP", "K7QM-3XRP")]
    [InlineData("k7qm 3xrp", "K7QM-3XRP")]
    [InlineData("K7QM-3XRP", "K7QM-3XRP")]
    public void Codes_show_as_two_groups_of_four(string input, string shown) =>
        Assert.Equal(shown, LinkCodeFormat.Format(input));

    [Fact]
    public void Case_spaces_and_dashes_do_not_change_the_hash()
    {
        var hash = LinkCodeFormat.Hash("K7QM-3XRP");

        Assert.Equal(hash, LinkCodeFormat.Hash("k7qm 3xrp"));
        Assert.Equal(hash, LinkCodeFormat.Hash(" K7QM3XRP "));
        Assert.Matches("^[0-9a-f]{64}$", hash);
        Assert.NotEqual(hash, LinkCodeFormat.Hash("K7QM-3XRQ"));
    }

    [Theory]
    [InlineData("K7QM3XR")]
    [InlineData("K7QM3XRPP")]
    [InlineData("K7QM3XR0")]
    [InlineData("K7QM3XRU")]
    public void Malformed_input_is_not_a_code(string input) =>
        Assert.False(LinkCodeFormat.IsWellFormed(LinkCodeFormat.Normalise(input)));

    [Theory]
    [InlineData("http://localhost:4321", "http://localhost:4321/link/?code=K7QM-3XRP")]
    [InlineData("http://localhost:4321/", "http://localhost:4321/link/?code=K7QM-3XRP")]
    [InlineData("https://heavymatica.dev/play/", "https://heavymatica.dev/play/link/?code=K7QM-3XRP")]
    public void The_link_address_opens_the_link_page_with_the_shown_code(string baseUrl, string expected) =>
        Assert.Equal(expected, LinkCodeFormat.LinkUrl(new Uri(baseUrl), "k7qm3xrp").AbsoluteUri);

    [Fact]
    public void The_code_in_the_link_address_is_escaped()
    {
        var url = LinkCodeFormat.LinkUrl(new Uri("http://localhost:4321"), "AB&C D=EF");

        Assert.Equal("?code=AB%26C-D%3DEF", url.Query);
    }
}
