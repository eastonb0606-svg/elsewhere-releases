"""Bow item models for gear3 bows drawn UPRIGHT in sprites.json (template "bow": limbs top to bottom, grip on the left,
string on the right), s-prismbow, 3.0 swarm. Used by gen_resources.py for catalogue items with "model": "bow".

The vanilla bow display transforms assume the vanilla texture, which lies on the diagonal (grip upper left, string lower
right). An upright sprite in them would be held 45 degrees off. So every hand transform gets the same model-space
pre-rotation, Z -45 (the upright sprite turned clockwise onto the vanilla diagonal): ItemTransform applies Z first, so it
is "vanilla Z - 45" in the right-hand contexts, and "vanilla Z + 45" in the left-hand ones (the left hand negates the
specified Y and Z). If the sprite's grip is given, the hand translations also move the bow so that its grip lands where the
vanilla bow's grip is (else the hand would hold the bow a little off its grip). GUI, ground, fixed and head stay vanilla:
the icon is upright in the inventory, like every other gear3 sprite.

Writes, for item <i> (all as vanilla does them, so the pull animation, the first-person draw and the third-person aim pose
are the vanilla ones; the item class must be a BowItem):
  assets/elsewhere/items/<i>.json                        using_item -> use_duration range_dispatch (3 pull stages)
  assets/elsewhere/models/item/<i>.json                  parent minecraft:item/bow, corrected display
  assets/elsewhere/models/item/<i>_pulling_{0,1,2}.json  parent elsewhere:item/<i>, the pull frame textures
The pull frame textures (textures/item/<i>_pulling_N.png) come from the item's own art tool (e.g. tools/prism_bow_art.py).

Catalogue options ("bow": {...}, all optional):
  "draw_ticks": ticks for a full draw (vanilla 20); the range_dispatch scale is 1 / draw_ticks, thresholds 0.65 and 0.9
  "grip": [x, y] the grip's centre in the 16x16 sprite, in pixels (pixel centres are +0.5), for the translation fix
Pure stdlib.
"""
import math

# vanilla minecraft:item/bow display (26.3)
VANILLA = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
VANILLA_GRIP = (6.0, 6.0)  # centre of the vanilla bow texture's grey grip (pixels 5..6 on the diagonal), pixel centres +0.5
TURN = -45.0               # model-space pre-rotation that lays an upright sprite on the vanilla diagonal
THRESHOLDS = (0.65, 0.9)


def _rot(deg, axis):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return [[1, 0, 0], [0, c, -s], [0, s, c]]
    if axis == "y":
        return [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def _xyz(rx, ry, rz):
    """ItemTransform's rotationXYZ: R = Rx * Ry * Rz (Z is applied to the vertices first)."""
    return _mul(_mul(_rot(rx, "x"), _rot(ry, "y")), _rot(rz, "z"))


def _model_offset(px, py):
    """A sprite pixel position as a model-space offset from the item's centre (model y points up)."""
    return [px - 8.0, 8.0 - py, 0.0]


def display(grip=None):
    """The four hand transforms for an upright sprite (see the module doc)."""
    out = {}
    delta = None
    if grip is not None:
        g = _apply(_rot(TURN, "z"), _model_offset(*grip))  # where the grip ends up after the pre-rotation
        v = _model_offset(*VANILLA_GRIP)
        delta = [g[i] - v[i] for i in range(3)]
    for ctx, t in VANILLA.items():
        left = ctx.endswith("lefthand")
        rx, ry, rz = t["rotation"]
        rot = [rx, ry, rz + (-TURN if left else TURN)]
        tr = list(t["translation"])
        if delta is not None:
            # applied transform: T + R * S * v (left hand: R(x, -y, -z), T(-x, y, z)); shift the model by -delta
            r_applied = _xyz(rx, -ry if left else ry, -rz if left else rz)
            shift = _apply(r_applied, [-d * t["scale"][i] for i, d in enumerate(delta)])
            applied = [(-tr[0] if left else tr[0]) + shift[0], tr[1] + shift[1], tr[2] + shift[2]]
            tr = [-applied[0] if left else applied[0], applied[1], applied[2]]
        out[ctx] = {"rotation": [_num(x) for x in rot], "translation": [_num(round(x, 2)) for x in tr], "scale": list(t["scale"])}
    return out


def _num(x):
    return int(x) if float(x).is_integer() else x


def _ref(model):
    return {"type": "minecraft:model", "model": model}


def models(i, opts=None):
    """{relative path under src/main/resources: json object} for bow item i."""
    opts = opts or {}
    draw = float(opts.get("draw_ticks", 20))
    grip = opts.get("grip")
    base = f"elsewhere:item/{i}"
    out = {
        f"assets/elsewhere/models/item/{i}.json": {"parent": "minecraft:item/bow", "textures": {"layer0": base},
                                                   "display": display(tuple(grip) if grip else None)},
        f"assets/elsewhere/items/{i}.json": {"model": {
            "type": "minecraft:condition", "property": "minecraft:using_item",
            "on_false": _ref(base),
            "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": _num(round(1.0 / draw, 6)),
                        "entries": [{"threshold": th, "model": _ref(f"{base}_pulling_{n + 1}")} for n, th in enumerate(THRESHOLDS)],
                        "fallback": _ref(f"{base}_pulling_0")}}},
    }
    for n in range(3):
        out[f"assets/elsewhere/models/item/{i}_pulling_{n}.json"] = {"parent": base, "textures": {"layer0": f"{base}_pulling_{n}"}}
    return out


if __name__ == "__main__":  # print what a bow would get, e.g. python3 -I tools/bow_models.py prism_bow 7.3 7.0
    import json, sys
    a = sys.argv[1:]
    o = {"grip": [float(a[1]), float(a[2])]} if len(a) >= 3 else {}
    for rel, obj in models(a[0] if a else "example_bow", o).items():
        print(rel)
        print(json.dumps(obj, indent=1))
