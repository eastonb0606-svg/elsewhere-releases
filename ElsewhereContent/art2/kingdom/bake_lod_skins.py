#!/usr/bin/env python3
"""Baked LOD skins for kingdom soldiers (ARMIES.md 3.7), s-lod lane, 3.0.0.

One 64x64 player-layout texture per unit type: the unit's skin with its armour (leather dyed with the unit's
armor_dye, chainmail/iron/gold and mod equipment assets), the unit's first trim pattern in its trim material and the
faction's tabard colour painted into the skin's OUTER layer (hat, jacket, sleeves, pants), which the player model draws
slightly inflated, like armour. At 48-96 blocks the client draws a soldier with this one texture and no armour, trim,
tabard or held-item passes (banners and spears excepted). The rank impostors (ARMIES.md 3.6) draw one layer only, so each
unit also gets a FLAT copy (outer layer composited onto the inner layer, outer layer cleared); the flat copies also go into one
1024x1024 atlas (16 x 16 slots of 64x64).

Usage (from anywhere; reads the module sources and the loom client jar, writes into the kingdom module):
    python3 -I art2/kingdom/bake_lod_skins.py [--preview DIR]
Outputs:
    kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/lod/<unit>.png   (66)
    kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/lod/flat/<unit>.png  (66, one layer, for ranks)
    kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/lod/atlas.png    (1024x1024, the flat copies)
    kingdom/src/main/resources/assets/elsewhere/lod/lod_skins.json                       (index the client reads)
Re-run whenever a unit's skin, armour, dye, trim or faction tabard colour changes.
"""
import glob
import io
import json
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
CONTENT = os.path.dirname(os.path.dirname(HERE))  # ElsewhereContent
OUT_TEX = os.path.join(CONTENT, "kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/lod")
OUT_IDX = os.path.join(CONTENT, "kingdom/src/main/resources/assets/elsewhere/lod/lod_skins.json")
CLIENT_JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")
ATLAS_COLS = 16
DEFAULT_TABARD = 0x2E4C9E
FREE_COMPANY_TABARD = 0x808080

_zip = zipfile.ZipFile(CLIENT_JAR)
_cache = {}


def asset(ns_path, kind):
    """Loads 'ns:path' as textures/<kind>/<path>.png from the module sources (elsewhere) or the client jar (minecraft)."""
    ns, path = ns_path.split(":", 1) if ":" in ns_path else ("minecraft", ns_path)
    rel = f"assets/{ns}/textures/{kind}/{path}.png"
    if rel in _cache:
        return _cache[rel]
    img = None
    if ns == "minecraft":
        try:
            img = Image.open(io.BytesIO(_zip.read(rel))).convert("RGBA")
        except KeyError:
            img = None
    else:
        hits = sorted(p for p in glob.glob(os.path.join(CONTENT, "*/src/main/resources", rel)) if "/build/" not in p)
        if hits:
            img = Image.open(hits[0]).convert("RGBA")
    _cache[rel] = img
    return img


def tex(id_):
    ns, path = id_.split(":", 1)
    hits = sorted(p for p in glob.glob(os.path.join(CONTENT, f"*/src/main/resources/assets/{ns}/{path}")) if "/build/" not in p)
    if not hits:
        raise SystemExit(f"missing texture {id_}")
    return Image.open(hits[0]).convert("RGBA")


# ---- box UV helpers -------------------------------------------------------------------------------------------------------
def faces(u, v, w, h, d):
    """Vanilla cube UV layout: name -> (x0, y0, x1, y1)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "east": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "west": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "south": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# part -> (armor-texture UV origin (64x32 humanoid layout), box w h d, mirrored in the armour model, skin OUTER-layer UV origin)
PARTS = {
    "head": ((0, 0), (8, 8, 8), False, (32, 0)),
    "body": ((16, 16), (8, 12, 4), False, (16, 32)),
    "right_arm": ((40, 16), (4, 12, 4), False, (40, 32)),
    "left_arm": ((40, 16), (4, 12, 4), True, (48, 48)),
    "right_leg": ((0, 16), (4, 12, 4), False, (0, 32)),
    "left_leg": ((0, 16), (4, 12, 4), True, (0, 48)),
}
SLOT_PARTS = {"head": ["head"], "chest": ["body", "right_arm", "left_arm"], "legs": ["body", "right_leg", "left_leg"],
              "feet": ["right_leg", "left_leg"]}


def paste_part(dst, src, part, also_hat=False):
    """Composites src's part (armour UV layout) onto dst's outer-layer box for that part (alpha over)."""
    (su, sv), (w, h, d), mirror, (du, dv) = PARTS[part]
    sf, df = faces(su, sv, w, h, d), faces(du, dv, w, h, d)
    for name, box in df.items():
        srcname = name
        if mirror and name in ("east", "west"):
            srcname = "west" if name == "east" else "east"
        piece = src.crop(sf[srcname])
        if mirror:
            piece = piece.transpose(Image.FLIP_LEFT_RIGHT)
        region = dst.crop(box)
        region.alpha_composite(piece)
        dst.paste(region, box[:2])
    if also_hat and part == "head":
        # the armour model's hat box (32,0) carries helmet overlays (e.g. crests): same outer-layer slot
        piece = src.crop((32, 0, 64, 16))
        region = dst.crop((32, 0, 64, 16))
        region.alpha_composite(piece)
        dst.paste(region, (32, 0))


def tint(img, rgb):
    r0, g0, b0 = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    out = img.copy()
    px = out.load()
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = (r * r0 // 255, g * g0 // 255, b * b0 // 255, a)
    return out


_TRIM_BASE = None


def trim_layer(pattern_id, material_id, armor_material, leggings):
    global _TRIM_BASE
    if _TRIM_BASE is None:
        _TRIM_BASE = Image.open(io.BytesIO(_zip.read("assets/minecraft/textures/palettes/trim_base.png"))).convert("RGBA")
    mat = material_id.split(":", 1)[-1]
    if mat == armor_material:
        dark = f"assets/minecraft/textures/palettes/trim/{mat}_darker.png"
        if dark in _zip.namelist():
            mat = mat + "_darker"
    pal = Image.open(io.BytesIO(_zip.read(f"assets/minecraft/textures/palettes/trim/{mat}.png"))).convert("RGBA")
    key = {_TRIM_BASE.getpixel((i, 0))[:3]: pal.getpixel((i, 0)) for i in range(_TRIM_BASE.size[0])}
    pat = asset(pattern_id, "trims/entity/" + ("humanoid_leggings" if leggings else "humanoid"))
    if pat is None:
        return None
    out = Image.new("RGBA", pat.size, (0, 0, 0, 0))
    src, px = pat.load(), out.load()
    for y in range(pat.size[1]):
        for x in range(pat.size[0]):
            c = src[x, y]
            if c[3] and c[:3] in key:
                k = key[c[:3]]
                px[x, y] = (k[0], k[1], k[2], c[3])
    return out


ARMOR_MATERIAL = {"leather": "leather", "chainmail": "chainmail", "iron": "iron", "golden": "gold", "diamond": "diamond",
                  "netherite": "netherite", "copper": "copper"}


def equipment_asset(item_id):
    """Item id -> (equipment asset id 'ns:name', vanilla trim material name or None). Mod items read their item JSON?
    Mod armour uses Item.Properties().humanoidArmor(<asset>, ...); the asset key is taken from assets/<ns>/equipment/*.json
    whose name prefixes the item path (azmar_jackal_helm -> azmar_jackal)."""
    ns, path = item_id.split(":", 1)
    if ns == "minecraft":
        mat = path.rsplit("_", 1)[0]
        return f"minecraft:{ARMOR_MATERIAL.get(mat, mat)}", ARMOR_MATERIAL.get(mat)
    best = None
    for f in glob.glob(os.path.join(CONTENT, f"*/src/main/resources/assets/{ns}/equipment/*.json")):
        name = os.path.splitext(os.path.basename(f))[0]
        if path.startswith(name) and (best is None or len(name) > len(best)):
            best = name
    return (f"{ns}:{best}" if best else None), None


def equipment_layers(asset_id, leggings):
    """Reads the equipment asset JSON (humanoid / humanoid_leggings layers) -> list of (texture id, dyeable)."""
    ns, name = asset_id.split(":", 1)
    kind = "humanoid_leggings" if leggings else "humanoid"
    if ns == "minecraft":
        rel = f"assets/minecraft/equipment/{name}.json"
        try:
            data = json.loads(_zip.read(rel))
        except KeyError:
            data = None
    else:
        hits = glob.glob(os.path.join(CONTENT, f"*/src/main/resources/assets/{ns}/equipment/{name}.json"))
        data = json.load(open(hits[0])) if hits else None
    if data is None:
        return [(f"{ns}:{name}", False)]
    out = []
    for layer in data.get("layers", {}).get(kind, []):
        tid = layer["texture"]
        out.append((tid if ":" in tid else "minecraft:" + tid, "dyeable" in layer))
    return out


def paint_armor(skin, unit):
    armor = unit.get("armor") or {}
    dye = unit.get("armor_dye")
    trim = unit.get("trim")
    for slot in ("legs", "feet", "chest", "head"):  # inner to outer
        item = armor.get(slot)
        if not item or "banner" in item:
            continue
        asset_id, armor_mat = equipment_asset(item)
        if asset_id is None:
            continue
        leggings = slot == "legs"
        kind = "entity/equipment/" + ("humanoid_leggings" if leggings else "humanoid")
        layer_imgs = []
        for tid, dyeable in equipment_layers(asset_id, leggings):
            img = asset(tid, kind)
            if img is None:
                continue
            if dyeable:
                img = tint(img, dye if isinstance(dye, int) else 0xA06540)
            layer_imgs.append(img)
        if trim and trim.get("patterns"):
            t = trim_layer(trim["patterns"][0], trim.get("material", "minecraft:iron"), armor_mat, leggings)
            if t is not None:
                layer_imgs.append(t)
        for img in layer_imgs:
            for part in SLOT_PARTS[slot]:
                paste_part(skin, img, part, also_hat=(slot == "head"))
    return skin


def paint_tabard(skin, color):
    tab = tint(tex("elsewhere:textures/entity/kingdom/tabard.png"), color)
    for part in ("body", "right_leg", "left_leg"):
        paste_part(skin, tab, part)
    return skin


def faction_colors():
    unit_faction, colors = {}, {}
    for f in glob.glob(os.path.join(CONTENT, "*/src/main/resources/data/elsewhere/kingdom_faction/*.json")):
        if "/build/" in f:
            continue
        d = json.load(open(f))
        fid = os.path.splitext(os.path.basename(f))[0]
        c = (d.get("tabard") or {}).get("color", DEFAULT_TABARD)
        colors[fid] = int(c.lstrip("#"), 16) if isinstance(c, str) else int(c)

        def walk(o):
            if isinstance(o, dict):
                for k, v in o.items():
                    if k in ("unit", "units", "leader", "members"):
                        for x in (v if isinstance(v, list) else [v]):
                            if isinstance(x, str):
                                unit_faction.setdefault(x.split(":", 1)[-1], fid)
                            else:
                                walk(x)
                    else:
                        walk(v)
            elif isinstance(o, list):
                for x in o:
                    walk(x)
        walk(d.get("roster", {}))
        walk(d.get("army", {}))
    return unit_faction, colors


# inner-layer UV origins in the 64x64 player skin, matching PARTS' outer-layer origins
INNER = {"head": (0, 0), "body": (16, 16), "right_arm": (40, 16), "left_arm": (32, 48), "right_leg": (0, 16), "left_leg": (16, 48)}


def flatten(skin):
    """One-layer copy for the rank impostors: each part's outer box composited over its inner box, then the outer layer cleared."""
    flat = skin.copy()
    for part, (iu, iv) in INNER.items():
        _, (w, h, d), _, (ou, ov) = PARTS[part]
        inner, outer = faces(iu, iv, w, h, d), faces(ou, ov, w, h, d)
        for name, box in inner.items():
            region = flat.crop(box)
            region.alpha_composite(skin.crop(outer[name]))
            flat.paste(region, box[:2])
    clear = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for part in INNER:
        _, (w, h, d), _, (ou, ov) = PARTS[part]
        for box in faces(ou, ov, w, h, d).values():
            flat.paste(clear.crop((0, 0, box[2] - box[0], box[3] - box[1])), box[:2])
    return flat


def main():
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(preview, exist_ok=True)
    unit_faction, colors = faction_colors()
    units = sorted(p for p in glob.glob(os.path.join(CONTENT, "*/src/main/resources/data/elsewhere/kingdom_unit/*.json")) if "/build/" not in p)
    os.makedirs(os.path.join(OUT_TEX, "flat"), exist_ok=True)
    os.makedirs(os.path.dirname(OUT_IDX), exist_ok=True)
    atlas = Image.new("RGBA", (64 * ATLAS_COLS, 64 * ATLAS_COLS), (0, 0, 0, 0))
    index = []
    for slot, f in enumerate(sorted(units, key=lambda p: os.path.basename(p))):
        name = os.path.splitext(os.path.basename(f))[0]
        unit = json.load(open(f))
        skin = tex(unit["skin"])
        if skin.size == (64, 32):  # legacy layout: copy right limbs to the left slots first
            s = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            s.paste(skin, (0, 0))
            skin = s
        skin = skin.copy()
        fac = unit_faction.get(name)
        if name.startswith("free_company") or "_fc_" in name:
            fac = "free_company"  # sellsword companies (FreeCompanies.FACTION, grey tabard), also the Mirewick hire
        elif fac is None:
            fac = next((k for k in colors if name.startswith(k + "_")), None)  # e.g. azmar_jackal_guard -> azmar
        color = FREE_COMPANY_TABARD if fac == "free_company" else colors.get(fac, DEFAULT_TABARD)
        paint_armor(skin, unit)
        paint_tabard(skin, color)
        out = os.path.join(OUT_TEX, f"{name}.png")
        skin.save(out)
        flat = flatten(skin)
        flat.save(os.path.join(OUT_TEX, "flat", f"{name}.png"))
        atlas.paste(flat, ((slot % ATLAS_COLS) * 64, (slot // ATLAS_COLS) * 64))
        armor = unit.get("armor") or {}
        index.append({"unit": f"elsewhere:{name}", "skin": unit["skin"], "baked": f"elsewhere:textures/entity/kingdom/lod/{name}.png",
                      "flat": f"elsewhere:textures/entity/kingdom/lod/flat/{name}.png",
                      "slot": slot, "faction": f"elsewhere:{fac}" if fac else None, "tabard": f"#{color:06X}",
                      "armor": [armor.get("head"), armor.get("chest"), armor.get("legs"), armor.get("feet")]})
        if preview:
            skin.resize((256, 256), Image.NEAREST).save(os.path.join(preview, f"{name}.png"))
            flat.resize((256, 256), Image.NEAREST).save(os.path.join(preview, f"{name}_flat.png"))
    atlas.save(os.path.join(OUT_TEX, "atlas.png"))
    json.dump({"atlas": "elsewhere:textures/entity/kingdom/lod/atlas.png", "atlas_size": 64 * ATLAS_COLS, "slot_size": 64,
               "columns": ATLAS_COLS, "skins": index}, open(OUT_IDX, "w"), indent=1)
    print(f"baked {len(index)} LOD skins -> {OUT_TEX}, index {OUT_IDX}")


if __name__ == "__main__":
    main()
