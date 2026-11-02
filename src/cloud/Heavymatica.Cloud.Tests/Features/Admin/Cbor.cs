using System.Text;

namespace Heavymatica.Cloud.Tests.Features.Admin;

/// <summary>
/// The CBOR an authenticator writes: integers, byte and text strings and maps. Map entries are written in the order
/// given, so callers list keys in canonical order.
/// </summary>
internal static class Cbor
{
    public static byte[] Encode(object value)
    {
        using var output = new MemoryStream();
        Write(output, value);
        return output.ToArray();
    }

    private static void Write(MemoryStream output, object value)
    {
        switch (value)
        {
            case int number when number >= 0:
                Head(output, 0, (ulong)number);
                break;
            case int number:
                Head(output, 1, (ulong)(-1L - number));
                break;
            case byte[] bytes:
                Head(output, 2, (ulong)bytes.Length);
                output.Write(bytes);
                break;
            case string text:
                var utf8 = Encoding.UTF8.GetBytes(text);
                Head(output, 3, (ulong)utf8.Length);
                output.Write(utf8);
                break;
            case IReadOnlyList<KeyValuePair<object, object>> map:
                Head(output, 5, (ulong)map.Count);
                foreach (var (key, item) in map)
                {
                    Write(output, key);
                    Write(output, item);
                }

                break;
            default:
                throw new NotSupportedException($"CBOR for {value.GetType().Name} is not needed.");
        }
    }

    public static IReadOnlyList<KeyValuePair<object, object>> Map(params (object Key, object Value)[] entries) =>
        entries.Select(entry => new KeyValuePair<object, object>(entry.Key, entry.Value)).ToList();

    private static void Head(MemoryStream output, int major, ulong argument)
    {
        var type = (byte)(major << 5);
        switch (argument)
        {
            case < 24:
                output.WriteByte((byte)(type | (byte)argument));
                break;
            case <= byte.MaxValue:
                output.WriteByte((byte)(type | 24));
                output.WriteByte((byte)argument);
                break;
            case <= ushort.MaxValue:
                output.WriteByte((byte)(type | 25));
                output.WriteByte((byte)(argument >> 8));
                output.WriteByte((byte)argument);
                break;
            default:
                output.WriteByte((byte)(type | 26));
                for (var shift = 24; shift >= 0; shift -= 8)
                {
                    output.WriteByte((byte)(argument >> shift));
                }

                break;
        }
    }
}
