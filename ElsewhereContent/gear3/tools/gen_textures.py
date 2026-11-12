"""gear3 item textures: ROOT/Ideas/overhaul/sprites.json -> 16x16 PNGs in src/main/resources/assets/elsewhere/textures/item/.

Run: ROOT/_tools/venv/bin/python -I gear3/tools/gen_textures.py [--sprites PATH] [--check]
sprites.json: {"<item_id>": {"palette": {"<char>": "#rrggbb"}, "rows": [16 strings of 16 chars]}}, '.' = transparent.
The gear lane writes that file and gear.html draws from the same data, so these textures match the user's sprite page exactly.
Pure stdlib (zlib PNG writer). With --check nothing is written; the run fails if a catalogue item has no sprite or a sprite is malformed.
"""
import json, os, struct, sys, zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # -I drops the script dir; shield3d.py sits next to this file
import shield3d  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOD))
SPRITES = os.path.join(ROOT, "Ideas", "overhaul", "sprites.json")
OUT = os.path.join(MOD, "src", "main", "resources", "assets", "elsewhere", "textures", "item")
CATALOGUE = os.path.join(MOD, "src", "main", "resources", "elsewhere_gear3", "catalogue.json")


def png(width, height, pixels):
    """pixels: list of rows of (r,g,b,a)."""
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def hex_rgb(h):
    h = h.lstrip("#")
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def validate(item_id, spec):
    errs = []
    pal, rows = spec.get("palette"), spec.get("rows")
    if not isinstance(pal, dict) or not isinstance(rows, list):
        return [f"{item_id}: needs palette and rows"]
    if len(rows) != 16:
        errs.append(f"{item_id}: {len(rows)} rows, need 16")
    for i, r in enumerate(rows):
        if len(r) != 16:
            errs.append(f"{item_id}: row {i} has {len(r)} chars, need 16")
        for ch in r:
            if ch != "." and ch not in pal:
                errs.append(f"{item_id}: row {i} uses '{ch}' which is not in the palette")
                break
    return errs


def render(spec):
    pal = {k: hex_rgb(v) for k, v in spec["palette"].items()}
    return [[(*pal[ch], 255) if ch != "." else (0, 0, 0, 0) for ch in row] for row in spec["rows"]]


WORN = os.path.join(MOD, "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "equipment")


def palette_of(spec):
    """(dark, mid, light) from an armor sprite: the sprite's colours ordered by luminance, most frequent first within thirds."""
    pal = {k: hex_rgb(v) for k, v in spec["palette"].items()}
    count = {}
    for r in spec["rows"]:
        for ch in r:
            if ch != ".":
                count[ch] = count.get(ch, 0) + 1
    cols = sorted(count, key=lambda c: -count[c])
    lum = lambda c: 0.3 * pal[c][0] + 0.59 * pal[c][1] + 0.11 * pal[c][2]
    by = sorted(cols, key=lum)
    mid = pal[cols[0]]
    return pal[by[0]], mid, pal[by[-1]]


def worn(spec, leggings):
    """64x32 worn-armor layer drawn in the sprite's own colours (head 0..32x0..16, body 16..40x16..32, arms 40..56x16..32, legs/boots 0..16x16..32)."""
    dark, mid, light = palette_of(spec)
    regions = [(16, 16, 40, 32), (0, 16, 16, 32)] if leggings else [(0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32)]
    px = [[(0, 0, 0, 0)] * 64 for _ in range(32)]
    for x0, y0, x1, y1 in regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = x in (x0, x1 - 1) or y in (y0, y1 - 1) or (x - x0) % 8 == 0 or (y - y0) % 8 == 0
                c = dark if edge else (light if (x + y) % 5 == 0 else mid)
                px[y][x] = (*c, 255)
    return png(64, 32, px)


def main():
    args = sys.argv[1:]
    sprites = SPRITES
    if "--sprites" in args:
        sprites = args[args.index("--sprites") + 1]
    check = "--check" in args
    if not os.path.exists(sprites):
        print(f"gen_textures: {sprites} does not exist yet")
        return 2
    data = json.load(open(sprites, encoding="utf-8"))
    cat = json.load(open(CATALOGUE, encoding="utf-8")) if os.path.exists(CATALOGUE) else {"items": []}
    wanted = [i["id"] for i in cat["items"]]
    errs = []
    for k, v in data.items():
        errs += validate(k, v)
    missing = [i for i in wanted if i not in data]
    for m in missing:
        errs.append(f"{m}: in the catalogue but not in sprites.json")
    extra = [k for k in data if k not in wanted]
    if extra:
        print("gen_textures: sprites without a catalogue item (skipped): " + ", ".join(extra))
    if errs:
        print("gen_textures: ERRORS\n  " + "\n  ".join(errs))
        return 1
    if check:
        print(f"gen_textures: {len(wanted)} sprite(s) OK")
        return 0
    os.makedirs(OUT, exist_ok=True)
    manifest_path = os.path.join(HERE, ".textures_generated.json")
    old = json.load(open(manifest_path)) if os.path.exists(manifest_path) else []
    for i in wanted:
        open(os.path.join(OUT, i + ".png"), "wb").write(png(16, 16, render(data[i])))
    # 3D shields: one 32x32 model texture per shield (tools/shield3d.py)
    for it in cat["items"]:
        if it.get("kind") == "shield":
            open(os.path.join(OUT, it["id"] + "_3d.png"), "wb").write(png(shield3d.TEX, shield3d.TEX, shield3d.texture(data[it["id"]])))
    for stale in old:
        if stale not in wanted and os.path.exists(os.path.join(OUT, stale + ".png")):
            os.remove(os.path.join(OUT, stale + ".png"))
    # worn armor layers: one pair per set, drawn from the set's chestplate-ish sprite (first armor piece with the most pixels)
    sets = {}
    for i in cat["items"]:
        if i.get("kind") == "armor":
            sets.setdefault(i["set"], []).append(i["id"])
    for name, pieces in sets.items():
        best = max(pieces, key=lambda p: sum(ch != "." for r in data[p]["rows"] for ch in r))
        for sub, leg in (("humanoid", False), ("humanoid_leggings", True)):
            d = os.path.join(WORN, sub)
            os.makedirs(d, exist_ok=True)
            open(os.path.join(d, name + ".png"), "wb").write(worn(data[best], leg))
    json.dump(wanted, open(manifest_path, "w"))
    print(f"gen_textures: wrote {len(wanted)} texture(s) to {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
