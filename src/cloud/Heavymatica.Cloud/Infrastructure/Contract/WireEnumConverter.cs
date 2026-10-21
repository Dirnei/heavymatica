using System.Reflection;
using System.Runtime.Serialization;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Heavymatica.Cloud.Infrastructure.Contract;

/// <summary>
/// Serializes contract enums by their wire value (<c>[EnumMember(Value = "unsupported_format")]</c>).
/// NSwag annotates generated enum properties with <c>JsonStringEnumConverter</c>, which would write the C# member
/// names; the build rewrites those annotations to this converter (see GenerateContract in the project file).
/// </summary>
public sealed class WireEnumConverter<T> : JsonConverter<T>
    where T : struct, Enum
{
    private static readonly Dictionary<T, string> ToWire = typeof(T)
        .GetFields(BindingFlags.Public | BindingFlags.Static)
        .ToDictionary(field => (T)field.GetValue(null)!, field => field.GetCustomAttribute<EnumMemberAttribute>()?.Value ?? field.Name);

    private static readonly Dictionary<string, T> FromWire = ToWire.ToDictionary(pair => pair.Value, pair => pair.Key, StringComparer.Ordinal);

    public override T Read(ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        var text = reader.GetString();
        return text is not null && FromWire.TryGetValue(text, out var value)
            ? value
            : throw new JsonException($"'{text}' is not a valid {typeof(T).Name}.");
    }

    public override void Write(Utf8JsonWriter writer, T value, JsonSerializerOptions options)
    {
        ArgumentNullException.ThrowIfNull(writer);
        writer.WriteStringValue(ToWire[value]);
    }

    internal static string Wire(T value) => ToWire[value];
}

public static class WireEnums
{
    /// <summary>The wire value of a contract enum member, for example <c>unsupported_format</c>.</summary>
    public static string ToWire<T>(this T value)
        where T : struct, Enum => WireEnumConverter<T>.Wire(value);
}
