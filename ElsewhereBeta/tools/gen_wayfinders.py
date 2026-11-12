import json, os, sys, colorsys
from PIL import Image
B = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CORE = os.path.join(os.path.dirname(B), "Elsewhere", "src", "main", "resources", "assets", "elsewhere")
OUT = os.path.join(B, "overlay_late", "src", "main", "resources", "assets", "elsewhere")
DIMS = {"petalwind": ("Petalwind Isles", 0xF4A6C8), "glasswaste": ("The Glasswaste", 0xF2C14E), "verdigris_sea": ("Verdigris Sea", 0x3FB8A0),
        "ironpeak": ("Ironpeak", 0x9FB3C8), "greenhaze": ("The Greenhaze", 0x7CCB5A), "the_hush": ("The Hush", 0x2E3A6B)}
src = Image.open(os.path.join(CORE, "textures", "item", "wayfinder_pale_hollow.png")).convert("RGBA")
base = Image.open(os.path.join(CORE, "textures", "item", "wayfinder.png")).convert("RGBA")
os.makedirs(os.path.join(OUT, "textures", "item"), exist_ok=True)
os.makedirs(os.path.join(OUT, "models", "item"), exist_ok=True)
os.makedirs(os.path.join(OUT, "items"), exist_ok=True)
os.makedirs(os.path.join(OUT, "lang"), exist_ok=True)
lang = {}
for d, (name, col) in DIMS.items():
    r, g, b = (col >> 16) & 255, (col >> 8) & 255, col & 255
    th, ts, tv = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    im = src.copy(); px = im.load(); bp = base.load()
    for y in range(im.height):
        for x in range(im.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            q = bp[x, y] if x < base.width and y < base.height else (0, 0, 0, 0)
            if q[:3] != p[:3]:  # pixels the attunement recolours: hue-shift to the dimension colour
                h, s, v = colorsys.rgb_to_hsv(p[0] / 255, p[1] / 255, p[2] / 255)
                nr, ng, nb = colorsys.hsv_to_rgb(th, max(ts * 0.9, 0.25 if ts > 0.1 else ts), min(1.0, v * 0.5 + tv * 0.5))
                px[x, y] = (int(nr * 255), int(ng * 255), int(nb * 255), p[3])
    im.save(os.path.join(OUT, "textures", "item", f"wayfinder_{d}.png"))
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"elsewhere:item/wayfinder_{d}"}}, open(os.path.join(OUT, "models", "item", f"wayfinder_{d}.json"), "w"), indent=2)
    json.dump({"model": {"type": "minecraft:model", "model": f"elsewhere:item/wayfinder_{d}"}}, open(os.path.join(OUT, "items", f"wayfinder_{d}.json"), "w"), indent=2)
    lang[f"item.elsewhere.wayfinder_{d}"] = f"Wayfinder: {name}"
json.dump(lang, open(os.path.join(OUT, "lang", "en_us.json"), "w"), indent=2)
print("ok")
