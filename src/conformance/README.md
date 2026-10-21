# Conformance set

Sample schematic files and the result every Heavymatica reader must produce for them. The cloud service
(`Heavymatica.Cloud.Formats`) and the mod (`formats` package) both run every file here in their test suites, so
the two readers cannot drift apart. A fix to a reader starts with a new fixture.

```
files/            sample files (generated, see below)
files/real/       files exported by the real tools (Litematica, WorldEdit, structure blocks)
expected/         one <path-under-files>.json per file
generator/        generate.py, which writes files/ and expected/
```

## Expected results

Each `expected/<name>.json` holds a `note` saying what the file covers, and then either a rejection:

```json
{ "reject": "palette_index_out_of_range" }
```

using a reason code from the contract's `ProblemCode`, or the result of reading it:

| Field | Meaning |
|---|---|
| `format` | `litematica`, `sponge` or `structure` |
| `size` | `[width, height, length]` |
| `dataVersion` | Minecraft data version, or `null` when the file records none |
| `paletteDigest` | see below |
| `blockTotal` | number of positions that are not air or structure void |
| `blockCounts` | block ID (without properties) to count, air and structure void excluded |
| `blockEntities` | `{pos, id}` of every block entity, ordered by y, then z, then x |
| `skippedEntities` | number of entities left out of the model |

## paletteDigest

The SHA-256, as lower-case hex, of the canonical block-state string of every position, each followed by `\n`
(UTF-8), visiting positions with x fastest, then z, then y:

```
for y in 0..height-1, for z in 0..length-1, for x in 0..width-1:
    update(canonical_state(x, y, z) + "\n")
```

A canonical block state is the block ID, followed by its properties in `[key=value,...]` with keys sorted
by ordinal string comparison, or the bare block ID when it has no properties:
`minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]`.

Reading rules that the digest makes visible:

- Litematica positions inside the enclosing box but outside every region are `minecraft:air`; where regions
  overlap, the region listed later in the file wins.
- Vanilla structure positions missing from the `blocks` list are `minecraft:structure_void` (structure blocks
  do not save voids).
- Only the first palette of a structure with several `palettes` is used.

## Regenerating

```
python -I src/conformance/generator/generate.py
```

The generator uses only the Python standard library and its own NBT writer, so it shares no code with either
reader, and its output is reproducible byte for byte. It rewrites `files/*` and `expected/*.json` but leaves
`files/real/` alone.

## Real files

Put files exported by the real tools into `files/real/` and add a matching
`expected/real/<name>.json`. These complement the generated files with whatever the tools actually write.
