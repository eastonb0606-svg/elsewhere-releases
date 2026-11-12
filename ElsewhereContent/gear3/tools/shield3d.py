"""3D shield models for gear3 shields (catalogue kind "shield"). Pure stdlib; imported by gen_textures.py and gen_resources.py.

Why element models: vanilla's shield item uses the "minecraft:shield" special model, whose codec takes no texture (it always
draws Sheets.SHIELD_BASE), so a custom shield can't reuse it with its own art. Instead each gear3 shield gets a block-model
("elements") shield built in the SAME space as the vanilla ShieldModel after the vanilla item transformation (scale 1,-1,-1):
plate front at Z 2, back at Z 1, handle Z -5..1 at the centre, centre at X 0 / Y 0. The display transforms are vanilla's
shield.json / shield_blocking.json verbatim, so the shield sits, is held and blocks exactly where a vanilla shield does;
items/<id>.json switches to the blocking model on minecraft:using_item like vanilla.

The face is a stepped disc (template "shield": radius 7, "buckler": radius 5) with a boss, drawn from the item sprite's palette
(o rim, d dark, m main, l light, a accent, b boss) into one 32x32 texture item/<id>_3d.png:
  px  0..15 x 0..15  front     px 16..31 x 0..15  back
  px  0..7  x 16..23 rim/edge  px  8..15 x 16..23 boss      px 16..31 x 16..31 handle
"""

RADIUS = {"shield": 7, "buckler": 5}
TEX = 32  # texture size in px; model UVs are in 16ths of it


def radius_of(spec):
    return RADIUS.get(spec.get("template", "shield"), 7)


def half_width(r, k):
    """Half width of the disc in the 1-px band |Y| in [k, k+1] (bands merge into a few boxes)."""
    return int((r * r - k * k) ** 0.5 + 0.5) if k < r else 0


def bands(r):
    """Disc boxes (x1, x2, y1, y2), non-overlapping, merged from 1-px bands."""
    out = []
    k = 0
    while k < r:
        w = half_width(r, k)
        k2 = k
        while k2 + 1 < r and half_width(r, k2 + 1) == w:
            k2 += 1
        if k == 0:
            out.append((-w, w, -(k2 + 1), k2 + 1))
        else:
            out.append((-w, w, k, k2 + 1))
            out.append((-w, w, -(k2 + 1), -k))
        k = k2 + 1
    return out


def inside(r, xc, yc):
    k = int(abs(yc))
    return k < r and abs(xc) < half_width(r, k)


def hex_rgb(h):
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def texture(spec):
    """32x32 RGBA rows for the 3D shield of this sprite spec."""
    pal = {k: hex_rgb(v) for k, v in spec["palette"].items()}
    get = lambda k, alt="m": (*pal.get(k, pal.get(alt, (128, 128, 128))), 255)
    r = radius_of(spec)
    wood = spec.get("template", "shield") == "shield"
    img = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]

    def face(ox, mirror):
        for v in range(16):
            for u in range(16):
                xc = (u - 7.5) * (-1 if mirror else 1)
                yc = 7.5 - v
                if not inside(r, xc, yc):
                    continue
                edge = any(not inside(r, xc + dx, yc + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                d2 = (xc * xc + yc * yc) ** 0.5
                if edge:
                    c = get("o")
                elif not mirror and abs(xc) < 1 and abs(yc) < 1:
                    c = get("b")
                elif not mirror and (2.0 <= d2 < 2.9 if not wood else 1.5 <= abs(xc) + abs(yc) < 3.6):
                    c = get("a")
                elif mirror:
                    c = get("d") if (wood and int(u) % 3 == 0) else get("m" if wood else "d")
                elif wood and int(u) % 3 == 0:
                    c = get("d")
                elif not inside(r, xc + 1.5, yc) and xc > 0:
                    c = get("l")
                elif not inside(r, xc - 1.5, yc) and xc < 0:
                    c = get("d")
                else:
                    c = get("m")
                img[v][ox + u] = c

    face(0, False)
    face(16, True)
    for v in range(16, 24):
        for u in range(0, 8):
            img[v][u] = get("o")
        for u in range(8, 16):
            img[v][u] = get("l", "b") if (u == 8 or v == 16) else get("b")
    leather = (75, 48, 32, 255) if wood else (58, 52, 50, 255)
    for v in range(16, 32):
        for u in range(16, 32):
            img[v][u] = leather
    return img


def uv(px1, py1, px2, py2):
    s = 16.0 / TEX
    return [px1 * s, py1 * s, px2 * s, py2 * s]


RIM = uv(0, 16, 8, 24)
BOSS = uv(8, 16, 16, 24)
HANDLE = uv(16, 16, 32, 32)


def elements(r):
    els = []
    for x1, x2, y1, y2 in bands(r):
        els.append({"from": [x1, y1, 1], "to": [x2, y2, 2], "faces": {
            "south": {"uv": uv(x1 + 8, 8 - y2, x2 + 8, 8 - y1), "texture": "#shield"},          # front (design)
            "north": {"uv": uv(16 + 8 - x2, 8 - y2, 16 + 8 - x1, 8 - y1), "texture": "#shield"},  # back
            "east": {"uv": RIM, "texture": "#shield"}, "west": {"uv": RIM, "texture": "#shield"},
            "up": {"uv": RIM, "texture": "#shield"}, "down": {"uv": RIM, "texture": "#shield"}}})
    els.append({"from": [-1, -1, 2], "to": [1, 1, 3], "faces": {
        f: {"uv": BOSS, "texture": "#shield"} for f in ("south", "east", "west", "up", "down")}})
    els.append({"from": [-1, -3, -5], "to": [1, 3, 1], "faces": {
        f: {"uv": HANDLE, "texture": "#shield"} for f in ("north", "east", "west", "up", "down")}})
    return els


# vanilla 26.3 assets/minecraft/models/item/shield.json and shield_blocking.json display blocks, verbatim
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 1.75, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
    "on_shelf": {"rotation": [0, 0, 0], "translation": [11, 18.5, 8.7], "scale": [1.4, 1.4, 1.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
}
DISPLAY_BLOCKING = {
    "thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.49, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.51, 7, 2.5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 3.25, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
}


def models(item_id, template):
    """(relative path -> json object) for the item definition and both models of one shield."""
    r = RADIUS.get(template, 7)
    tex = {"particle": f"elsewhere:item/{item_id}", "shield": f"elsewhere:item/{item_id}_3d"}
    base = {"gui_light": "front", "textures": tex, "elements": elements(r), "display": DISPLAY}
    blocking = {"gui_light": "front", "textures": tex, "elements": elements(r), "display": DISPLAY_BLOCKING}
    item = {"model": {"type": "minecraft:condition", "property": "minecraft:using_item",
                      "on_false": {"type": "minecraft:model", "model": f"elsewhere:item/{item_id}"},
                      "on_true": {"type": "minecraft:model", "model": f"elsewhere:item/{item_id}_blocking"}}}
    return {
        f"assets/elsewhere/models/item/{item_id}.json": base,
        f"assets/elsewhere/models/item/{item_id}_blocking.json": blocking,
        f"assets/elsewhere/items/{item_id}.json": item,
    }
