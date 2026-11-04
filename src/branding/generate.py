"""Writes the Heavymatica logo: two SVGs and the PNG exports, from one list of shapes.

Usage: python src/branding/generate.py

Needs Pillow (python -m pip install pillow) for the PNG exports. The PNGs are rasterised from the same shapes as the
SVGs, at 4x and scaled down, so no SVG renderer is needed.
"""

import math
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit("Pillow is missing: python -m pip install pillow")

HERE = Path(__file__).resolve().parent
DOCS_FAVICON = HERE.parent.parent / "docs" / "public" / "favicon.svg"
MOD_LOGO = HERE.parent / "mod" / "heavymatica" / "common" / "src" / "main" / "resources" / "heavymatica.png"

SIZE = 512
CYAN = "#7FD8FF"
TOP, LEFT, RIGHT = "#F7F5EE", "#E4E0D3", "#B9B3A3"

GHOST_EDGES = [
    [(126, 262), (126, 195), (256, 120), (386, 195), (386, 262)],
    [(126, 195), (256, 270), (386, 195)],
    [(256, 270), (256, 337)],
]
LAYER_LINE = [(126, 262), (256, 337), (386, 262)]


def shapes(small):
    """The logo as plain shapes, back to front. Coordinates are in a 512 x 512 box."""
    out = [{"kind": "tile", "top": "#1C232C", "bottom": "#0E1218", "rx": 112}]
    if not small:
        out.append({"kind": "rim", "inset": 6, "rx": 106, "color": "#FFFFFF", "opacity": 0.12, "width": 4})
        out.append({"kind": "glow", "cx": 256, "cy": 270, "r": 230, "color": CYAN, "opacity": 0.30})
    out += [
        {"kind": "polygon", "points": [(126, 262), (256, 337), (256, 420), (126, 345)], "color": LEFT, "opacity": 1},
        {"kind": "polygon", "points": [(256, 337), (386, 262), (386, 345), (256, 420)], "color": RIGHT, "opacity": 1},
        {"kind": "polygon", "points": [(126, 262), (256, 187), (386, 262), (256, 337)], "color": TOP, "opacity": 1},
    ]
    ghost = (0.45, 0.25, 0.35) if small else (0.24, 0.10, 0.16)
    out += [
        {"kind": "polygon", "points": [(126, 195), (256, 120), (386, 195), (256, 270)], "color": CYAN, "opacity": ghost[0]},
        {"kind": "polygon", "points": [(126, 195), (256, 270), (256, 337), (126, 262)], "color": CYAN, "opacity": ghost[1]},
        {"kind": "polygon", "points": [(256, 270), (386, 195), (386, 262), (256, 337)], "color": CYAN, "opacity": ghost[2]},
    ]
    if small:
        out.append({"kind": "line", "points": LAYER_LINE, "color": CYAN, "opacity": 1, "width": 16})
    else:
        for edge in GHOST_EDGES:
            out.append({"kind": "line", "points": edge, "color": CYAN, "opacity": 1, "width": 7, "dash": (16, 11)})
        out.append({"kind": "line", "points": LAYER_LINE, "color": CYAN, "opacity": 0.30, "width": 24})
        out.append({"kind": "line", "points": LAYER_LINE, "color": CYAN, "opacity": 1, "width": 8})
    return out


def svg(shape_list):
    defs, body = [], []
    for i, s in enumerate(shape_list):
        kind = s["kind"]
        if kind == "tile":
            defs.append(f'<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{s["top"]}"/>'
                        f'<stop offset="1" stop-color="{s["bottom"]}"/></linearGradient>')
            body.append(f'<rect x="0" y="0" width="{SIZE}" height="{SIZE}" rx="{s["rx"]}" fill="url(#bg)"/>')
        elif kind == "rim":
            size = SIZE - 2 * s["inset"]
            body.append(f'<rect x="{s["inset"]}" y="{s["inset"]}" width="{size}" height="{size}" rx="{s["rx"]}" fill="none" '
                        f'stroke="{s["color"]}" stroke-opacity="{s["opacity"]}" stroke-width="{s["width"]}"/>')
        elif kind == "glow":
            defs.append(f'<radialGradient id="glow{i}" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="{s["color"]}" '
                        f'stop-opacity="{s["opacity"]}"/><stop offset="1" stop-color="{s["color"]}" stop-opacity="0"/></radialGradient>')
            body.append(f'<circle cx="{s["cx"]}" cy="{s["cy"]}" r="{s["r"]}" fill="url(#glow{i})"/>')
        elif kind == "polygon":
            points = " ".join(f"{x},{y}" for x, y in s["points"])
            body.append(f'<polygon points="{points}" fill="{s["color"]}" fill-opacity="{s["opacity"]}"/>')
        elif kind == "line":
            points = " ".join(f"{x},{y}" for x, y in s["points"])
            dash = f' stroke-dasharray="{s["dash"][0]} {s["dash"][1]}"' if "dash" in s else ""
            body.append(f'<polyline points="{points}" fill="none" stroke="{s["color"]}" stroke-opacity="{s["opacity"]}" '
                        f'stroke-width="{s["width"]}" stroke-linejoin="round" stroke-linecap="round"{dash}/>')
    lines = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {SIZE} {SIZE}" width="{SIZE}" height="{SIZE}">',
             "  <defs>", *("    " + d for d in defs), "  </defs>", *("  " + b for b in body), "</svg>", ""]
    return "\n".join(lines)


def rgba(color, opacity):
    value = int(color[1:], 16)
    return (value >> 16) & 255, (value >> 8) & 255, value & 255, round(255 * opacity)


def dashes(points, on, off):
    """Splits a polyline into dash polylines, continuing the pattern across corners as SVG does."""
    out, current, drawing, left = [], [points[0]], True, on
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        length, done = math.dist((x0, y0), (x1, y1)), 0.0
        while length - done > 1e-9:
            step = min(left, length - done)
            done += step
            left -= step
            point = (x0 + (x1 - x0) * done / length, y0 + (y1 - y0) * done / length)
            if drawing:
                current.append(point)
            if left <= 1e-9:
                if drawing:
                    out.append(current)
                drawing = not drawing
                left = on if drawing else off
                current = [point]
    if drawing and len(current) > 1:
        out.append(current)
    return out


def stroke(draw, points, width, fill):
    draw.line(points, fill=fill, width=round(width), joint="curve")
    radius = width / 2
    for x, y in (points[0], points[-1]):
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=fill)


def raster(shape_list, out_size):
    scale = 4 * out_size / SIZE
    big = round(out_size * 4)
    image = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    tile_mask = Image.new("L", (big, big), 0)

    def at(points):
        return [(x * scale, y * scale) for x, y in points]

    for s in shape_list:
        layer = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        kind = s["kind"]
        if kind == "tile":
            top, bottom = rgba(s["top"], 1), rgba(s["bottom"], 1)
            gradient = Image.new("RGBA", (1, big))
            for y in range(big):
                t = y / (big - 1)
                gradient.putpixel((0, y), tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
            ImageDraw.Draw(tile_mask).rounded_rectangle((0, 0, big - 1, big - 1), radius=s["rx"] * scale, fill=255)
            layer = Image.composite(gradient.resize((big, big)), layer, tile_mask)
        elif kind == "rim":
            inset = s["inset"] * scale
            draw.rounded_rectangle((inset, inset, big - 1 - inset, big - 1 - inset), radius=s["rx"] * scale,
                                   outline=rgba(s["color"], s["opacity"]), width=round(s["width"] * scale))
        elif kind == "glow":
            diameter = round(2 * s["r"] * scale)
            falloff = Image.radial_gradient("L").resize((diameter, diameter))
            alpha = falloff.point(lambda v: round(max(0, 255 - v) * s["opacity"]))
            color = Image.new("RGBA", (diameter, diameter), rgba(s["color"], 1))
            color.putalpha(alpha)
            layer.paste(color, (round((s["cx"] - s["r"]) * scale), round((s["cy"] - s["r"]) * scale)))
            layer.putalpha(Image.composite(layer.getchannel("A"), Image.new("L", (big, big), 0), tile_mask))
        elif kind == "polygon":
            draw.polygon(at(s["points"]), fill=rgba(s["color"], s["opacity"]))
        elif kind == "line":
            parts = dashes(at(s["points"]), s["dash"][0] * scale, s["dash"][1] * scale) if "dash" in s else [at(s["points"])]
            # Strokes are drawn opaque on their own layer, then faded, so overlapping joins don't add up.
            for part in parts:
                stroke(draw, part, s["width"] * scale, rgba(s["color"], 1))
            layer.putalpha(layer.getchannel("A").point(lambda v: round(v * s["opacity"])))
        image = Image.alpha_composite(image, layer)
    return image.resize((out_size, out_size), Image.LANCZOS)


def main():
    large, small = shapes(small=False), shapes(small=True)
    (HERE / "heavymatica.svg").write_text(svg(large), encoding="utf-8", newline="\n")
    (HERE / "heavymatica-small.svg").write_text(svg(small), encoding="utf-8", newline="\n")
    DOCS_FAVICON.write_text(svg(small), encoding="utf-8", newline="\n")
    raster(large, 512).save(HERE / "heavymatica-512.png", optimize=True)
    raster(small, 64).save(HERE / "heavymatica-small-64.png", optimize=True)
    raster(large, 256).save(MOD_LOGO, optimize=True)
    print("Wrote", HERE / "heavymatica.svg", HERE / "heavymatica-small.svg", HERE / "heavymatica-512.png",
          HERE / "heavymatica-small-64.png", MOD_LOGO, DOCS_FAVICON, sep="\n  ")


if __name__ == "__main__":
    main()
