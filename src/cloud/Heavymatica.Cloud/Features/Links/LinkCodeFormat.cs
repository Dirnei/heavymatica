using System.Security.Cryptography;
using System.Text;

namespace Heavymatica.Cloud.Features.Links;

/// <summary>
/// Link codes: 8 symbols from an alphabet without look-alikes (no 0/O, 1/I/L or U), shown as <c>XXXX-XXXX</c>. Case,
/// spaces and dashes do not matter when a code is entered. Only the SHA-256 of the normalised code is stored.
/// </summary>
internal static class LinkCodeFormat
{
    public const string Alphabet = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    public const int Length = 8;

    /// <summary>A new code, normalised (no dash).</summary>
    public static string Generate()
    {
        Span<char> code = stackalloc char[Length];
        for (var i = 0; i < Length; i++)
        {
            code[i] = Alphabet[RandomNumberGenerator.GetInt32(Alphabet.Length)];
        }

        return new string(code);
    }

    /// <summary>The code as shown: two groups of four.</summary>
    public static string Format(string code)
    {
        var normalised = Normalise(code);
        return normalised.Length == Length ? normalised[..4] + "-" + normalised[4..] : normalised;
    }

    public static string Normalise(string input)
    {
        ArgumentNullException.ThrowIfNull(input);
        var builder = new StringBuilder(input.Length);
        foreach (var c in input)
        {
            if (!char.IsWhiteSpace(c) && c != '-')
            {
                builder.Append(char.ToUpperInvariant(c));
            }
        }

        return builder.ToString();
    }

    /// <summary>Whether the normalised input could be a code at all.</summary>
    public static bool IsWellFormed(string normalised)
    {
        ArgumentNullException.ThrowIfNull(normalised);
        return normalised.Length == Length && normalised.All(c => Alphabet.Contains(c, StringComparison.Ordinal));
    }

    public static string Hash(string input) => Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(Normalise(input))));

    /// <summary>The website page that redeems the code: <c>{base}/link/?code=XXXX-XXXX</c>.</summary>
    public static Uri LinkUrl(Uri baseUrl, string code)
    {
        ArgumentNullException.ThrowIfNull(baseUrl);
        return new Uri(baseUrl.GetLeftPart(UriPartial.Path).TrimEnd('/') + "/link/?code=" + Uri.EscapeDataString(Format(code)));
    }
}
