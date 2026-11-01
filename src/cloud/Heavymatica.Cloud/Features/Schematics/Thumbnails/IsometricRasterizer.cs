using System.Globalization;
using Heavymatica.Cloud.Formats;

namespace Heavymatica.Cloud.Features.Schematics.Thumbnails;

/// <summary>
/// Draws a schematic as flat-shaded isometric blocks seen from the south-east and above: each block's top, east and
/// south faces, back to front, into a square RGBA image with a transparent background.
/// </summary>
internal static class IsometricRasterizer
{
    public const int Size = 256;

    public const int Margin = 8;

    private const float TopShade = 1f;
    private const float EastShade = 0.8f;
    private const float SouthShade = 0.64f;

    private static readonly float Cos30 = MathF.Cos(MathF.PI / 6);

    /// <summary>How one palette entry is drawn.</summary>
    private readonly record struct Drawn(BlockColor Color, float Bottom, float Top, bool Visible, bool Opaque);

    /// <returns>The RGBA pixels, or null when the model has nothing to draw.</returns>
    public static byte[]? Render(SchematicModel model, BlockColors colors, int maxDrawnBlocks)
    {
        ArgumentNullException.ThrowIfNull(model);
        ArgumentNullException.ThrowIfNull(colors);
        var drawn = model.Palette.Select(state => Describe(state, colors)).ToArray();
        var visibleCount = 0L;
        foreach (var index in model.Blocks)
        {
            if (drawn[index].Visible)
            {
                visibleCount++;
            }
        }

        if (visibleCount == 0)
        {
            return null;
        }

        var step = 1;
        while (visibleCount / ((long)step * step * step) > maxDrawnBlocks)
        {
            step++;
        }

        var canvas = new Canvas(Fit(model.Width, model.Height, model.Length));
        var (width, height, length) = (model.Width, model.Height, model.Length);
        var anything = false;
        for (var y = 0; y < height; y += step)
        {
            for (var sum = 0; sum <= width + length - 2; sum += step)
            {
                for (var x = Math.Max(0, sum - length + 1); x <= Math.Min(width - 1, sum); x++)
                {
                    var z = sum - x;
                    if (x % step != 0 || z % step != 0)
                    {
                        continue;
                    }

                    var block = drawn[model.Blocks[SchematicModel.Index(x, y, z, width, length)]];
                    if (!block.Visible)
                    {
                        continue;
                    }

                    bool OpaqueAt(int nx, int ny, int nz) =>
                        nx < width && ny < height && nz < length && drawn[model.Blocks[SchematicModel.Index(nx, ny, nz, width, length)]].Opaque;

                    anything |= DrawBlock(canvas, x, y, z, step, block,
                        hiddenTop: block.Top >= 1f && OpaqueAt(x, y + step, z),
                        hiddenEast: OpaqueAt(x + step, y, z),
                        hiddenSouth: OpaqueAt(x, y, z + step));
                }
            }
        }

        return anything ? canvas.Pixels : null;
    }

    private static Drawn Describe(string state, BlockColors colors)
    {
        var id = BlockStates.Id(state);
        if (BlockStates.IsAirLike(id))
        {
            return default;
        }

        var color = colors.Of(id);
        if (color.Shape == BlockShape.None || color.Alpha <= 0f)
        {
            return default;
        }

        var (bottom, top) = (0f, color.Shape == BlockShape.Slab ? color.Height : 1f);
        if (color.Shape == BlockShape.Slab)
        {
            switch (Property(state, "type"))
            {
                case "top":
                    (bottom, top) = (1f - color.Height, 1f);
                    break;
                case "double":
                    top = 1f;
                    break;
            }

            if (int.TryParse(Property(state, "layers"), NumberStyles.Integer, CultureInfo.InvariantCulture, out var layers))
            {
                top = Math.Clamp(layers, 1, 8) / 8f;
            }
        }

        var opaque = color.Alpha >= 1f && (color.Shape == BlockShape.Cube || (color.Shape == BlockShape.Slab && bottom <= 0f && top >= 1f));
        return new Drawn(color, bottom, top, true, opaque);
    }

    private static string? Property(string state, string key)
    {
        var bracket = state.IndexOf('[', StringComparison.Ordinal);
        if (bracket < 0)
        {
            return null;
        }

        foreach (var pair in state[(bracket + 1)..^1].Split(','))
        {
            var equals = pair.IndexOf('=', StringComparison.Ordinal);
            if (equals > 0 && pair.AsSpan(0, equals).SequenceEqual(key))
            {
                return pair[(equals + 1)..];
            }
        }

        return null;
    }

    /// <returns>Whether any face was drawn.</returns>
    private static bool DrawBlock(Canvas canvas, int x, int y, int z, int size, Drawn block, bool hiddenTop, bool hiddenEast, bool hiddenSouth)
    {
        var color = block.Color;
        float x0 = x, x1 = x + size, z0 = z, z1 = z + size;
        var sideColor = color.Side;
        var topColor = color.Top;
        if (color.Shape == BlockShape.Cross)
        {
            var inset = size * 0.35f;
            (x0, x1, z0, z1) = (x + inset, x + size - inset, z + inset, z + size - inset);
            topColor = sideColor;
            (hiddenTop, hiddenEast, hiddenSouth) = (false, false, false);
        }

        float y0 = y + (block.Bottom * size), y1 = y + (block.Top * size);
        var drawnAny = false;
        if (!hiddenTop)
        {
            drawnAny |= canvas.Fill([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], Shade(topColor, TopShade), color.Alpha);
        }

        if (!hiddenEast)
        {
            drawnAny |= canvas.Fill([(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], Shade(sideColor, EastShade), color.Alpha);
        }

        if (!hiddenSouth)
        {
            drawnAny |= canvas.Fill([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], Shade(sideColor, SouthShade), color.Alpha);
        }

        return drawnAny || hiddenTop || hiddenEast || hiddenSouth;
    }

    private static int Shade(int rgb, float factor) =>
        ((int)(((rgb >> 16) & 0xFF) * factor) << 16) | ((int)(((rgb >> 8) & 0xFF) * factor) << 8) | (int)((rgb & 0xFF) * factor);

    /// <summary>Screen position of a world point before fitting: x right, y down.</summary>
    private static (float U, float V) Project(float x, float y, float z) => ((x - z) * Cos30, ((x + z) * 0.5f) - y);

    /// <summary>The scale and offset that fit the model's bounding box into the image.</summary>
    private static Transform Fit(int width, int height, int length)
    {
        float minU = float.MaxValue, maxU = float.MinValue, minV = float.MaxValue, maxV = float.MinValue;
        foreach (var cx in new[] { 0, width })
        {
            foreach (var cy in new[] { 0, height })
            {
                foreach (var cz in new[] { 0, length })
                {
                    var (u, v) = Project(cx, cy, cz);
                    (minU, maxU, minV, maxV) = (Math.Min(minU, u), Math.Max(maxU, u), Math.Min(minV, v), Math.Max(maxV, v));
                }
            }
        }

        const float available = Size - (2 * Margin);
        var scale = Math.Min(available / (maxU - minU), available / (maxV - minV));
        var offsetU = ((Size - ((maxU - minU) * scale)) / 2) - (minU * scale);
        var offsetV = ((Size - ((maxV - minV) * scale)) / 2) - (minV * scale);
        return new Transform(scale, offsetU, offsetV);
    }

    private readonly record struct Transform(float Scale, float OffsetU, float OffsetV);

    /// <summary>An RGBA buffer that fills convex quads with alpha blending.</summary>
    private sealed class Canvas(Transform transform)
    {
        public byte[] Pixels { get; } = new byte[Size * Size * 4];

        /// <returns>Whether any pixel was covered.</returns>
        public bool Fill(ReadOnlySpan<(float X, float Y, float Z)> corners, int rgb, float alpha)
        {
            Span<float> us = stackalloc float[4];
            Span<float> vs = stackalloc float[4];
            for (var i = 0; i < 4; i++)
            {
                var (u, v) = Project(corners[i].X, corners[i].Y, corners[i].Z);
                us[i] = (u * transform.Scale) + transform.OffsetU;
                vs[i] = (v * transform.Scale) + transform.OffsetV;
            }

            var left = Math.Max(0, (int)MathF.Floor(Min(us)));
            var right = Math.Min(Size - 1, (int)MathF.Ceiling(Max(us)));
            var topRow = Math.Max(0, (int)MathF.Floor(Min(vs)));
            var bottomRow = Math.Min(Size - 1, (int)MathF.Ceiling(Max(vs)));
            var covered = false;
            for (var py = topRow; py <= bottomRow; py++)
            {
                for (var px = left; px <= right; px++)
                {
                    if (Inside(us, vs, px + 0.5f, py + 0.5f))
                    {
                        Blend(px, py, rgb, alpha);
                        covered = true;
                    }
                }
            }

            return covered;
        }

        private static bool Inside(ReadOnlySpan<float> us, ReadOnlySpan<float> vs, float u, float v)
        {
            bool positive = false, negative = false;
            for (var i = 0; i < 4; i++)
            {
                var next = (i + 1) % 4;
                var cross = ((us[next] - us[i]) * (v - vs[i])) - ((vs[next] - vs[i]) * (u - us[i]));
                positive |= cross > 0;
                negative |= cross < 0;
                if (positive && negative)
                {
                    return false;
                }
            }

            return true;
        }

        private void Blend(int px, int py, int rgb, float alpha)
        {
            var at = ((py * Size) + px) * 4;
            var destinationAlpha = Pixels[at + 3] / 255f;
            var outAlpha = alpha + (destinationAlpha * (1 - alpha));
            if (outAlpha <= 0f)
            {
                return;
            }

            for (var channel = 0; channel < 3; channel++)
            {
                var source = (rgb >> (16 - (channel * 8))) & 0xFF;
                var blended = ((source * alpha) + (Pixels[at + channel] * destinationAlpha * (1 - alpha))) / outAlpha;
                Pixels[at + channel] = (byte)Math.Clamp(MathF.Round(blended), 0, 255);
            }

            Pixels[at + 3] = (byte)Math.Clamp(MathF.Round(outAlpha * 255), 0, 255);
        }

        private static float Min(ReadOnlySpan<float> values)
        {
            var min = values[0];
            foreach (var value in values)
            {
                min = Math.Min(min, value);
            }

            return min;
        }

        private static float Max(ReadOnlySpan<float> values)
        {
            var max = values[0];
            foreach (var value in values)
            {
                max = Math.Max(max, value);
            }

            return max;
        }
    }
}
