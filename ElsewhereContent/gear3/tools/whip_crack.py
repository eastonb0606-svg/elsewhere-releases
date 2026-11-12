"""s-whips (3.0 swarm): the cracked-lash pose of each gear3 whip, item/<id>_crack.png (16x16).

Run: ROOT/_tools/venv/bin/python -I ElsewhereContent/gear3/tools/whip_crack.py [--check]
Drawn in each whip's OWN palette from ROOT/Ideas/overhaul/sprites.json (the sprite page): same handle as the idle sprite
(o outline, h handle, g grip), the lash (m) snapped straight out toward the upper right with a travelling kink, and the
crack spark at the tip (a). The item definition shows it while the crack cooldown runs (gen_resources.py, "cooldown_model").
Uses gen_textures.png() so the PNGs are written exactly like every other gear3 texture.
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)  # -I drops the script dir; gen_textures.py sits next to this file
import gen_textures  # noqa: E402

WHIPS = ["thornlash", "tidechain_lash", "azmar_sunscourge", "echo_chord"]
CRACK = [
    "............a..a",
    ".............aa.",
    "............maa.",
    "...........m..a.",
    "..........m.....",
    "..........m.....",
    ".........m......",
    "........m.......",
    ".......om.......",
    "......ohho......",
    ".....ohho.......",
    "....ohho........",
    "...ogho.........",
    "..ogo...........",
    "..oo............",
    "................",
]


def main():
    sprites = json.load(open(gen_textures.SPRITES, encoding="utf-8"))
    errs = []
    for w in WHIPS:
        spec = {"palette": sprites[w]["palette"], "rows": CRACK}
        errs += gen_textures.validate(w + "_crack", spec)
    if errs:
        print("whip_crack: ERRORS\n  " + "\n  ".join(errs))
        return 1
    if "--check" in sys.argv:
        bad = [w for w in WHIPS if not os.path.exists(os.path.join(gen_textures.OUT, w + "_crack.png"))]
        print(f"whip_crack: {len(WHIPS)} sprite(s), {len(bad)} missing" + (": " + ", ".join(bad) if bad else ""))
        return 1 if bad else 0
    for w in WHIPS:
        spec = {"palette": sprites[w]["palette"], "rows": CRACK}
        open(os.path.join(gen_textures.OUT, w + "_crack.png"), "wb").write(gen_textures.png(16, 16, gen_textures.render(spec)))
    print(f"whip_crack: wrote {len(WHIPS)} cracked-lash texture(s) to {gen_textures.OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
