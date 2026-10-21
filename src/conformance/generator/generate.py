"""Generates the Heavymatica conformance set.

Writes sample schematic files to ../files/ and the expected reading results to ../expected/.
The files are produced exactly as each format's specification and its reference writer describe;
the expected results are computed from the generator's own model, independently of both readers.

Uses only the Python standard library, so it shares no code with the Java or .NET readers.
Run from anywhere: python src/conformance/generator/generate.py
"""

from __future__ import annotations

import gzip
import hashlib
import io
import json
import struct
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = ROOT / "files"
EXPECTED = ROOT / "expected"

DATA_VERSION = 4671
OLD_DATA_VERSION = 3955

AIR_BLOCKS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void"}
AIR = "minecraft:air"
STRUCTURE_VOID = "minecraft:structure_void"

# --------------------------------------------------------------------------------------------
# NBT writer (big-endian, Java edition)
# --------------------------------------------------------------------------------------------

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    def __init__(self, kind: int, value):
        self.kind = kind
        self.value = value


def byte(v): return Tag(BYTE, v)
def short(v): return Tag(SHORT, v)
def int_(v): return Tag(INT, v)
def long_(v): return Tag(LONG, v)
def double(v): return Tag(DOUBLE, v)
def string(v): return Tag(STRING, v)
def byte_array(v: bytes): return Tag(BYTE_ARRAY, v)
def int_array(v): return Tag(INT_ARRAY, list(v))
def long_array(v): return Tag(LONG_ARRAY, list(v))
def compound(**items): return Tag(COMPOUND, dict(items))
def compound_of(items: dict): return Tag(COMPOUND, dict(items))


def list_(kind: int, items):
    return Tag(LIST, (kind, list(items)))


def _write_string(out: io.BytesIO, text: str):
    data = text.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _write_payload(out: io.BytesIO, tag: Tag):
    k, v = tag.kind, tag.value
    if k == BYTE:
        out.write(struct.pack(">b", v))
    elif k == SHORT:
        out.write(struct.pack(">h", v))
    elif k == INT:
        out.write(struct.pack(">i", v))
    elif k == LONG:
        out.write(struct.pack(">q", v))
    elif k == FLOAT:
        out.write(struct.pack(">f", v))
    elif k == DOUBLE:
        out.write(struct.pack(">d", v))
    elif k == BYTE_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(v)
    elif k == STRING:
        _write_string(out, v)
    elif k == LIST:
        item_kind, items = v
        out.write(struct.pack(">bi", item_kind if items else END, len(items)))
        for item in items:
            _write_payload(out, item)
    elif k == COMPOUND:
        for name, child in v.items():
            out.write(struct.pack(">b", child.kind))
            _write_string(out, name)
            _write_payload(out, child)
        out.write(b"\x00")
    elif k == INT_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}i", *v))
    elif k == LONG_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}q", *v))
    else:
        raise ValueError(k)


def nbt_bytes(root: Tag, name: str = "") -> bytes:
    out = io.BytesIO()
    out.write(struct.pack(">b", COMPOUND))
    _write_string(out, name)
    _write_payload(out, root)
    return out.getvalue()


def gzipped(data: bytes) -> bytes:
    out = io.BytesIO()
    with gzip.GzipFile(fileobj=out, mode="wb", mtime=0) as g:
        g.write(data)
    return out.getvalue()


# --------------------------------------------------------------------------------------------
# Model
# --------------------------------------------------------------------------------------------


def state_string(block_id: str, props: dict[str, str] | None = None) -> str:
    if not props:
        return block_id
    return block_id + "[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"


def parse_state(state: str) -> tuple[str, dict[str, str]]:
    if "[" not in state:
        return state, {}
    block_id, rest = state.split("[", 1)
    props = dict(p.split("=", 1) for p in rest.rstrip("]").split(",") if p)
    return block_id, props


@dataclass
class Model:
    width: int
    height: int
    length: int
    blocks: dict[tuple[int, int, int], str] = field(default_factory=dict)
    block_entities: dict[tuple[int, int, int], dict] = field(default_factory=dict)
    default: str = AIR

    def state(self, x, y, z) -> str:
        return self.blocks.get((x, y, z), self.default)

    def positions(self):
        """Canonical order: x fastest, then z, then y."""
        for y in range(self.height):
            for z in range(self.length):
                for x in range(self.width):
                    yield x, y, z


def expected_for(model: Model, fmt: str, data_version: int | None, skipped_entities: int = 0) -> dict:
    digest = hashlib.sha256()
    counts: dict[str, int] = {}
    for pos in model.positions():
        state = model.state(*pos)
        digest.update(state.encode("utf-8") + b"\n")
        block_id = parse_state(state)[0]
        if block_id not in AIR_BLOCKS:
            counts[block_id] = counts.get(block_id, 0) + 1
    return {
        "format": fmt,
        "size": [model.width, model.height, model.length],
        "dataVersion": data_version,
        "paletteDigest": digest.hexdigest(),
        "blockTotal": sum(counts.values()),
        "blockCounts": dict(sorted(counts.items())),
        "blockEntities": sorted(
            ({"pos": list(pos), "id": be["id"]} for pos, be in model.block_entities.items()),
            key=lambda e: (e["pos"][1], e["pos"][2], e["pos"][0])),
        "skippedEntities": skipped_entities,
    }


def house() -> Model:
    """A small mixed structure used by the cross-format fixtures."""
    m = Model(5, 4, 6)
    for x in range(5):
        for z in range(6):
            m.blocks[(x, 0, z)] = "minecraft:stone"
    for y in (1, 2):
        for x in range(5):
            for z in range(6):
                if x in (0, 4) or z in (0, 5):
                    m.blocks[(x, y, z)] = "minecraft:oak_planks"
    m.blocks[(2, 2, 0)] = "minecraft:glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]"
    m.blocks[(2, 1, 0)] = "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]"
    for x in range(5):
        m.blocks[(x, 3, 0)] = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
        m.blocks[(x, 3, 5)] = "minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"
    m.blocks[(1, 1, 1)] = "minecraft:chest[facing=south,type=single,waterlogged=false]"
    m.block_entities[(1, 1, 1)] = {"id": "minecraft:chest", "Items": []}
    m.blocks[(3, 1, 4)] = "minecraft:cave_air"
    return m


# --------------------------------------------------------------------------------------------
# Writers
# --------------------------------------------------------------------------------------------


def block_state_tag(state: str) -> Tag:
    block_id, props = parse_state(state)
    items = {"Name": string(block_id)}
    if props:
        items["Properties"] = compound_of({k: string(v) for k, v in props.items()})
    return compound_of(items)


def be_payload(be: dict) -> dict[str, Tag]:
    items = {"id": string(be["id"])}
    if "Items" in be:
        items["Items"] = list_(COMPOUND, [])
    return items


def write_structure(model: Model, *, data_version: int | None, extra_palettes: int = 0, entities: int = 0,
                    omit_voids: bool = False) -> bytes:
    """Vanilla structure-block format: size, palette(s), blocks, entities."""
    palette: list[str] = []
    index: dict[str, int] = {}
    blocks = []
    for pos in model.positions():
        state = model.state(*pos)
        if omit_voids and state == STRUCTURE_VOID:
            continue
        if state not in index:
            index[state] = len(palette)
            palette.append(state)
        entry = {"state": int_(index[state]), "pos": list_(INT, [int_(c) for c in pos])}
        if pos in model.block_entities:
            entry["nbt"] = compound_of(be_payload(model.block_entities[pos]))
        blocks.append(compound_of(entry))
    root = {}
    if data_version is not None:
        root["DataVersion"] = int_(data_version)
    root["size"] = list_(INT, [int_(model.width), int_(model.height), int_(model.length)])
    palette_tags = [block_state_tag(s) for s in palette]
    if extra_palettes:
        # Later palettes swap every block for gold, so reading anything but the first palette is visible.
        others = [[block_state_tag("minecraft:gold_block") for _ in palette] for _ in range(extra_palettes)]
        root["palettes"] = list_(LIST, [list_(COMPOUND, palette_tags)] + [list_(COMPOUND, o) for o in others])
    else:
        root["palette"] = list_(COMPOUND, palette_tags)
    root["blocks"] = list_(COMPOUND, blocks)
    root["entities"] = list_(COMPOUND, [
        compound(pos=list_(DOUBLE, [double(0.5), double(1.0), double(0.5 + i)]),
                 blockPos=list_(INT, [int_(0), int_(1), int_(i)]),
                 nbt=compound(id=string("minecraft:armor_stand")))
        for i in range(entities)])
    return gzipped(nbt_bytes(compound_of(root)))


def varints(values) -> bytes:
    out = bytearray()
    for v in values:
        while True:
            b = v & 0x7F
            v >>= 7
            if v:
                out.append(b | 0x80)
            else:
                out.append(b)
                break
    return bytes(out)


def sponge_palette(model: Model) -> tuple[dict[str, int], list[int]]:
    index: dict[str, int] = {}
    data = []
    for pos in model.positions():
        state = model.state(*pos)
        if state not in index:
            index[state] = len(index)
        data.append(index[state])
    return index, data


def write_sponge_v2(model: Model, *, data_version: int | None, entities: int = 0,
                    data_override: bytes | None = None, palette_override: dict[str, int] | None = None,
                    dims_override: tuple[int, int, int] | None = None) -> bytes:
    index, data = sponge_palette(model)
    if palette_override is not None:
        index = palette_override
    w, h, l = dims_override or (model.width, model.height, model.length)
    root = {"Version": int_(2)}
    if data_version is not None:
        root["DataVersion"] = int_(data_version)
    root.update({
        "Width": short(w), "Height": short(h), "Length": short(l),
        "Offset": int_array([0, 0, 0]),
        "PaletteMax": int_(len(index)),
        "Palette": compound_of({s: int_(i) for s, i in index.items()}),
        "BlockData": byte_array(data_override if data_override is not None else varints(data)),
        "BlockEntities": list_(COMPOUND, [
            compound_of({"Pos": int_array(pos), "Id": string(be["id"]), **{k: v for k, v in be_payload(be).items() if k != "id"}})
            for pos, be in sorted(model.block_entities.items())]),
        "Entities": list_(COMPOUND, [
            compound(Pos=list_(DOUBLE, [double(0.5), double(1.0), double(0.5)]), Id=string("minecraft:armor_stand"))
            for _ in range(entities)]),
    })
    return gzipped(nbt_bytes(compound_of(root), name="Schematic"))


def write_sponge_v3(model: Model, *, data_version: int | None, entities: int = 0,
                    dims_override: tuple[int, int, int] | None = None, omit_block_data: bool = False) -> bytes:
    index, data = sponge_palette(model)
    w, h, l = dims_override or (model.width, model.height, model.length)
    blocks = {
        "Palette": compound_of({s: int_(i) for s, i in index.items()}),
        "BlockEntities": list_(COMPOUND, [
            compound(Pos=int_array(pos), Id=string(be["id"]),
                     Data=compound_of({k: v for k, v in be_payload(be).items() if k != "id"}))
            for pos, be in sorted(model.block_entities.items())]),
    }
    if not omit_block_data:
        blocks["Data"] = byte_array(varints(data))
    schematic = {"Version": int_(3)}
    if data_version is not None:
        schematic["DataVersion"] = int_(data_version)
    schematic.update({
        "Width": short(w), "Height": short(h), "Length": short(l),
        "Offset": int_array([0, 0, 0]),
        "Blocks": compound_of(blocks),
        "Entities": list_(COMPOUND, [
            compound(Pos=list_(DOUBLE, [double(0.5), double(1.0), double(0.5)]), Id=string("minecraft:armor_stand"))
            for _ in range(entities)]),
    })
    return gzipped(nbt_bytes(compound(Schematic=compound_of(schematic))))


def litematica_bits(palette_size: int) -> int:
    return max(2, (palette_size - 1).bit_length())


def pack_litematica(values: list[int], bits: int) -> list[int]:
    """Litematica's LitematicaBitArray: entries are packed back to back and may span two longs."""
    count = -(-len(values) * bits // 64)
    longs = [0] * count
    mask = (1 << bits) - 1
    for i, v in enumerate(values):
        start = i * bits
        start_index = start >> 6
        end_index = ((i + 1) * bits - 1) >> 6
        offset = start & 63
        longs[start_index] |= (v & mask) << offset
        if start_index != end_index:
            longs[end_index] |= (v & mask) >> (64 - offset)
    # Store as signed 64-bit, like Java's long[].
    return [((x & 0xFFFFFFFFFFFFFFFF) ^ 0x8000000000000000) - 0x8000000000000000 for x in longs]


@dataclass
class Region:
    name: str
    position: tuple[int, int, int]
    size: tuple[int, int, int]  # signed, like Litematica stores it
    blocks: dict[tuple[int, int, int], str]  # relative to the region's minimum corner
    block_entities: dict[tuple[int, int, int], dict] = field(default_factory=dict)
    entities: int = 0

    def min_corner(self):
        return tuple(p + (s + 1 if s < 0 else 0) for p, s in zip(self.position, self.size))

    def abs_size(self):
        return tuple(abs(s) for s in self.size)


def write_litematic(regions: list[Region], *, version: int, data_version: int | None,
                    palette_override: dict[str, list[str]] | None = None,
                    states_override: dict[str, list[int]] | None = None) -> bytes:
    region_tags = {}
    total_blocks = 0
    for r in regions:
        sx, sy, sz = r.abs_size()
        palette = [AIR]
        values = []
        for y in range(sy):
            for z in range(sz):
                for x in range(sx):
                    state = r.blocks.get((x, y, z), AIR)
                    if state not in palette:
                        palette.append(state)
                    values.append(palette.index(state))
                    if state != AIR:
                        total_blocks += 1
        if palette_override and r.name in palette_override:
            palette = palette_override[r.name]
        bits = litematica_bits(len(palette))
        longs = states_override[r.name] if states_override and r.name in states_override else pack_litematica(values, bits)
        region_tags[r.name] = compound(
            Position=compound(x=int_(r.position[0]), y=int_(r.position[1]), z=int_(r.position[2])),
            Size=compound(x=int_(r.size[0]), y=int_(r.size[1]), z=int_(r.size[2])),
            BlockStatePalette=list_(COMPOUND, [block_state_tag(s) for s in palette]),
            BlockStates=long_array(longs),
            TileEntities=list_(COMPOUND, [
                compound_of({"x": int_(p[0]), "y": int_(p[1]), "z": int_(p[2]), **be_payload(be)})
                for p, be in sorted(r.block_entities.items())]),
            Entities=list_(COMPOUND, [
                compound(Pos=list_(DOUBLE, [double(0.5), double(0.0), double(0.5)]), id=string("minecraft:armor_stand"))
                for _ in range(r.entities)]),
            PendingBlockTicks=list_(COMPOUND, []),
            PendingFluidTicks=list_(COMPOUND, []),
        )
    mins = [r.min_corner() for r in regions]
    maxs = [tuple(m + s - 1 for m, s in zip(r.min_corner(), r.abs_size())) for r in regions]
    enclosing = [max(mx[i] for mx in maxs) - min(mn[i] for mn in mins) + 1 for i in range(3)]
    root = {}
    if data_version is not None:
        root["MinecraftDataVersion"] = int_(data_version)
    root["Version"] = int_(version)
    if version >= 7:
        root["SubVersion"] = int_(1)
    root["Metadata"] = compound(
        Name=string("Conformance"), Author=string("Heavymatica"), Description=string(""),
        RegionCount=int_(len(regions)), TotalBlocks=int_(total_blocks),
        TotalVolume=int_(enclosing[0] * enclosing[1] * enclosing[2]),
        EnclosingSize=compound(x=int_(enclosing[0]), y=int_(enclosing[1]), z=int_(enclosing[2])),
        TimeCreated=long_(1760000000000), TimeModified=long_(1760000000000))
    root["Regions"] = compound_of(region_tags)
    return gzipped(nbt_bytes(compound_of(root)))


def model_from_regions(regions: list[Region]) -> Model:
    """What a reader must produce: the enclosing box, regions in file order, later regions win."""
    mins = [r.min_corner() for r in regions]
    origin = [min(m[i] for m in mins) for i in range(3)]
    maxs = [tuple(m + s - 1 for m, s in zip(r.min_corner(), r.abs_size())) for r in regions]
    size = [max(mx[i] for mx in maxs) - origin[i] + 1 for i in range(3)]
    model = Model(*size)
    for r in regions:
        base = [r.min_corner()[i] - origin[i] for i in range(3)]
        sx, sy, sz = r.abs_size()
        for y in range(sy):
            for z in range(sz):
                for x in range(sx):
                    pos = (base[0] + x, base[1] + y, base[2] + z)
                    model.blocks[pos] = r.blocks.get((x, y, z), AIR)
                    model.block_entities.pop(pos, None)
        for p, be in r.block_entities.items():
            model.block_entities[(base[0] + p[0], base[1] + p[1], base[2] + p[2])] = be
    return model


def region_of(model: Model, name="Main", position=(0, 0, 0)) -> Region:
    return Region(name, position, (model.width, model.height, model.length),
                  {p: s for p, s in model.blocks.items()}, dict(model.block_entities))


# --------------------------------------------------------------------------------------------
# Fixtures
# --------------------------------------------------------------------------------------------

fixtures: dict[str, tuple[bytes, dict]] = {}
notes: dict[str, str] = {}


def add(name: str, data: bytes, expected: dict, note: str):
    fixtures[name] = (data, expected)
    notes[name] = note


def reject(code: str) -> dict:
    return {"reject": code}


def build():
    h = house()

    # The same structure in every format: all must read identically.
    add("house.nbt", write_structure(h, data_version=DATA_VERSION),
        expected_for(h, "structure", DATA_VERSION), "Vanilla structure, one palette, air saved explicitly.")
    add("house.v2.schem", write_sponge_v2(h, data_version=DATA_VERSION),
        expected_for(h, "sponge", DATA_VERSION), "Sponge v2 (root named Schematic, BlockData varints).")
    add("house.v3.schem", write_sponge_v3(h, data_version=DATA_VERSION),
        expected_for(h, "sponge", DATA_VERSION), "Sponge v3 (Schematic compound, Blocks.Data varints).")
    for version, dv in ((5, OLD_DATA_VERSION), (6, OLD_DATA_VERSION), (7, DATA_VERSION)):
        add(f"house.litematica-v{version}.litematic", write_litematic([region_of(h)], version=version, data_version=dv),
            expected_for(h, "litematica", dv), f"Litematica format version {version}, one region.")

    # Format detection by content, not file name.
    add("sponge-named-as.nbt", write_sponge_v2(h, data_version=DATA_VERSION),
        expected_for(h, "sponge", DATA_VERSION), "A Sponge v2 file saved under a .nbt name.")

    # Uncompressed NBT is accepted as well.
    add("house-uncompressed.nbt", gzip.decompress(write_structure(h, data_version=DATA_VERSION)),
        expected_for(h, "structure", DATA_VERSION), "Vanilla structure without gzip compression.")

    # Litematica: two non-overlapping regions, gap in between is air.
    a = Model(3, 2, 3)
    for p in a.positions():
        a.blocks[p] = "minecraft:stone"
    b = Model(2, 3, 2)
    for p in b.positions():
        b.blocks[p] = "minecraft:bricks"
    regions = [Region("A", (0, 0, 0), (3, 2, 3), dict(a.blocks)), Region("B", (5, 0, 4), (2, 3, 2), dict(b.blocks))]
    add("two-regions.litematic", write_litematic(regions, version=6, data_version=DATA_VERSION),
        expected_for(model_from_regions(regions), "litematica", DATA_VERSION),
        "Two regions; the model covers their bounding box and the gap is air.")

    # Litematica: overlapping regions, the later region wins.
    lower = Region("First", (0, 0, 0), (4, 1, 4), {(x, 0, z): "minecraft:dirt" for x in range(4) for z in range(4)})
    upper = Region("Second", (2, 0, 2), (3, 1, 3), {(x, 0, z): "minecraft:diamond_block" for x in range(3) for z in range(3)})
    add("overlapping-regions.litematic", write_litematic([lower, upper], version=6, data_version=DATA_VERSION),
        expected_for(model_from_regions([lower, upper]), "litematica", DATA_VERSION),
        "Overlapping regions; where they overlap the region listed later wins.")

    # Litematica: negative region size moves the origin.
    neg_blocks = {(x, y, z): ("minecraft:red_wool" if x == 0 else "minecraft:blue_wool")
                  for x in range(4) for y in range(2) for z in range(3)}
    neg = Region("Negative", (3, 1, 2), (-4, 2, -3), neg_blocks)
    add("negative-size.litematic", write_litematic([neg], version=6, data_version=DATA_VERSION),
        expected_for(model_from_regions([neg]), "litematica", DATA_VERSION),
        "Region size is negative on x and z; blocks are stored from the minimum corner.")

    # Litematica: bit packing where entries span two longs (5 bits per entry).
    wide = Model(7, 3, 5)
    colors = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
              "cyan", "purple", "blue", "brown", "green", "red", "black", "white_terracotta", "orange_terracotta"]
    for i, p in enumerate(wide.positions()):
        wide.blocks[p] = f"minecraft:{colors[i % len(colors)]}_wool" if "terracotta" not in colors[i % len(colors)] \
            else f"minecraft:{colors[i % len(colors)]}"
    add("bits-span-longs.litematic", write_litematic([region_of(wide)], version=6, data_version=DATA_VERSION),
        expected_for(wide, "litematica", DATA_VERSION), "19 palette entries, 5 bits each, so entries cross long boundaries.")

    # Vanilla structure with several palettes: only the first one is used.
    add("several-palettes.nbt", write_structure(h, data_version=DATA_VERSION, extra_palettes=2),
        expected_for(h, "structure", DATA_VERSION), "Three palettes; the blocks come from the first.")

    # Vanilla structure with omitted positions (structure voids are not saved).
    voids = Model(3, 1, 3)
    voids.blocks[(1, 0, 1)] = "minecraft:stone"
    voids_expected = Model(3, 1, 3, default=STRUCTURE_VOID)
    voids_expected.blocks[(1, 0, 1)] = "minecraft:stone"
    for p in voids.positions():
        if p != (1, 0, 1):
            voids.blocks[p] = STRUCTURE_VOID
    add("structure-voids.nbt", write_structure(voids, data_version=DATA_VERSION, omit_voids=True),
        expected_for(voids_expected, "structure", DATA_VERSION),
        "Positions missing from the blocks list read as minecraft:structure_void.")

    # Modded blocks are kept as written.
    modded = Model(2, 1, 1)
    modded.blocks[(0, 0, 0)] = "servus_foundation:utility_duct[north=true]"
    modded.blocks[(1, 0, 0)] = "somemod:unknown_thing"
    add("modded.v3.schem", write_sponge_v3(modded, data_version=DATA_VERSION),
        expected_for(modded, "sponge", DATA_VERSION), "Blocks from other namespaces, kept with their properties.")

    # Entities are skipped and counted.
    add("entities.nbt", write_structure(h, data_version=DATA_VERSION, entities=3),
        expected_for(h, "structure", DATA_VERSION, skipped_entities=3), "Three entities, left out of the model.")
    add("entities.litematic", write_litematic([Region("Main", (0, 0, 0), (5, 4, 6), dict(h.blocks), dict(h.block_entities), entities=2)],
                                              version=6, data_version=DATA_VERSION),
        expected_for(h, "litematica", DATA_VERSION, skipped_entities=2), "Two entities in a Litematica region.")

    # Missing data version.
    add("no-data-version.litematic", write_litematic([region_of(h)], version=5, data_version=None),
        expected_for(h, "litematica", None), "No MinecraftDataVersion; reported as unknown.")
    add("no-data-version.nbt", write_structure(h, data_version=None),
        expected_for(h, "structure", None), "No DataVersion; reported as unknown.")

    # Stairs facing different ways are counted together.
    stairs = Model(10, 1, 2)
    for x in range(4):
        stairs.blocks[(x, 0, 0)] = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]"
    for x in range(6):
        stairs.blocks[(x, 0, 1)] = "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"
    add("stairs.v2.schem", write_sponge_v2(stairs, data_version=DATA_VERSION),
        expected_for(stairs, "sponge", DATA_VERSION), "4 stairs facing north and 6 facing east count as 10 oak_stairs.")

    # Invalid files ------------------------------------------------------------------------------

    add("unsupported-text.txt", b"This is not a schematic.\n", reject("unsupported_format"), "Plain text.")
    add("unsupported-png.png", bytes.fromhex("89504e470d0a1a0a") + b"\x00" * 24, reject("unsupported_format"), "A PNG header.")
    add("unsupported-level.dat", gzipped(nbt_bytes(compound(Data=compound(LevelName=string("World"))))),
        reject("unsupported_format"), "Valid NBT of an unknown shape (a level.dat).")

    full = write_litematic([region_of(h)], version=6, data_version=DATA_VERSION)
    add("truncated.litematic", full[: len(full) // 2], reject("malformed_nbt"), "The first half of a valid file.")
    raw = gzip.decompress(write_structure(h, data_version=DATA_VERSION))
    add("truncated-inside.nbt", gzipped(raw[: len(raw) - 40]), reject("malformed_nbt"),
        "Valid gzip whose NBT ends in the middle of a tag.")
    deep = compound()
    for _ in range(600):
        deep = compound(child=deep)
    add("too-deep.nbt", gzipped(nbt_bytes(deep)), reject("malformed_nbt"), "Compounds nested 600 deep (limit 512).")

    small = Model(3, 1, 4)
    for p in small.positions():
        small.blocks[p] = "minecraft:stone"
    palette12 = {f"minecraft:block_{i}": i for i in range(12)}
    add("palette-index-out-of-range.v2.schem",
        write_sponge_v2(small, data_version=DATA_VERSION, palette_override=palette12, data_override=varints([40] + [0] * 11)),
        reject("palette_index_out_of_range"), "Block data refers to index 40 of a 12-entry palette.")
    lit_palette = [AIR] + [f"minecraft:block_{i}" for i in range(11)]  # 12 entries, 4 bits
    add("palette-index-out-of-range.litematic",
        write_litematic([Region("Main", (0, 0, 0), (3, 1, 4), {})], version=6, data_version=DATA_VERSION,
                        palette_override={"Main": lit_palette}, states_override={"Main": pack_litematica([14] + [0] * 11, 4)}),
        reject("palette_index_out_of_range"), "Packed value 14 with a 12-entry palette.")

    add("size-mismatch.v2.schem", write_sponge_v2(small, data_version=DATA_VERSION, data_override=varints([0] * 5)),
        reject("size_mismatch"), "BlockData holds 5 entries for a 12-block volume.")
    add("size-mismatch.litematic",
        write_litematic([Region("Main", (0, 0, 0), (3, 1, 4), {})], version=6, data_version=DATA_VERSION,
                        states_override={"Main": [0, 0, 0]}),
        reject("size_mismatch"), "BlockStates has 3 longs where 1 is expected.")

    add("dimensions-too-large.v3.schem",
        write_sponge_v3(Model(1, 1, 1), data_version=DATA_VERSION, dims_override=(2000, 1, 1), omit_block_data=True),
        reject("dimensions_too_large"), "Declares a width of 2000; must be rejected without reading block data.")
    add("volume-too-large.v3.schem",
        write_sponge_v3(Model(1, 1, 1), data_version=DATA_VERSION, dims_override=(1000, 20, 1000), omit_block_data=True),
        reject("volume_too_large"), "1000 x 20 x 1000 = 20,000,000 blocks, over the 16,777,216 limit.")


def write_bomb(path: Path):
    """A small gzip file that decompresses to a 200 MiB byte array."""
    size = 200 * 1024 * 1024
    with open(path, "wb") as f, gzip.GzipFile(fileobj=f, mode="wb", mtime=0, compresslevel=9) as g:
        g.write(struct.pack(">b", COMPOUND))
        g.write(struct.pack(">H", 0))
        g.write(struct.pack(">b", BYTE_ARRAY))
        g.write(struct.pack(">H", 4) + b"Bomb")
        g.write(struct.pack(">i", size))
        chunk = b"\x00" * (1024 * 1024)
        for _ in range(size // len(chunk)):
            g.write(chunk)
        g.write(b"\x00")


def main():
    FILES.mkdir(parents=True, exist_ok=True)
    EXPECTED.mkdir(parents=True, exist_ok=True)
    for old in FILES.glob("*"):
        if old.is_file():
            old.unlink()
    for old in EXPECTED.glob("*.json"):
        old.unlink()

    build()
    for name, (data, expected) in fixtures.items():
        (FILES / name).write_bytes(data)
        (EXPECTED / f"{name}.json").write_text(json.dumps({"note": notes[name], **expected}, indent=2) + "\n", encoding="utf-8")

    write_bomb(FILES / "decompression-bomb.nbt")
    (EXPECTED / "decompression-bomb.nbt.json").write_text(json.dumps(
        {"note": "1 MiB-scale gzip that inflates to 200 MiB; reading must stop at 128 MiB.", "reject": "decompressed_too_large"},
        indent=2) + "\n", encoding="utf-8")

    # Zeros compress to almost nothing in git; the size check must happen before any parsing.
    (FILES / "file-too-large.bin").write_bytes(b"\x00" * (16 * 1024 * 1024 + 1))
    (EXPECTED / "file-too-large.bin.json").write_text(json.dumps(
        {"note": "16 MiB + 1 byte; rejected on size alone.", "reject": "file_too_large"}, indent=2) + "\n", encoding="utf-8")

    print(f"wrote {len(fixtures) + 2} fixtures to {FILES}")


if __name__ == "__main__":
    main()
