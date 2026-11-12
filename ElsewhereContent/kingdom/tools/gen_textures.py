"""Generates the kingdom soldier skins (64x64 player layout), the white tabard (64x32, tinted in game) and spawn eggs."""
import os, sys, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "elsewhere", "textures")
ENT = os.path.join(ROOT, "entity", "kingdom")
ITEM = os.path.join(ROOT, "item")
os.makedirs(ENT, exist_ok=True); os.makedirs(ITEM, exist_ok=True)

def rect(img, box, col, noise=8, seed=0):
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        for y in range(y0, y1):
            n = r.randint(-noise, noise)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in col[:3]) + (255,))

def cube(img, u, v, w, h, d, col, seed):
    # standard box UV: top/bottom row then sides
    rect(img, (u + d, v, u + d + w, v + d), col, seed=seed)            # top
    rect(img, (u + d + w, v, u + d + 2 * w, v + d), col, seed=seed + 1)  # bottom
    rect(img, (u, v + d, u + 2 * d + 2 * w, v + d + h), col, seed=seed + 2)  # sides

def skin(name, hair, beard, tunic, hose, seed):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    tone = (196, 150, 116)
    cube(img, 0, 0, 8, 8, 8, tone, seed)                       # head
    rect(img, (8, 0, 16, 8), hair, seed=seed + 3)              # top of head = hair
    rect(img, (0, 8, 8, 11), hair, seed=seed + 4); rect(img, (16, 8, 32, 11), hair, seed=seed + 5)
    rect(img, (24, 8, 32, 16), hair, seed=seed + 6)            # back of head
    rect(img, (8, 8, 16, 10), hair, seed=seed + 7)             # fringe
    face = [(9, 12, (240, 240, 240)), (10, 12, (60, 45, 35)), (13, 12, (60, 45, 35)), (14, 12, (240, 240, 240)),
            (9, 11, tuple(c - 30 for c in hair)), (10, 11, tuple(c - 30 for c in hair)), (13, 11, tuple(c - 30 for c in hair)), (14, 11, tuple(c - 30 for c in hair)),
            (11, 13, (170, 120, 90)), (12, 13, (170, 120, 90))]
    for x, y, c in face: img.putpixel((x, y), c + (255,))
    if beard:
        rect(img, (8, 14, 16, 16), beard, seed=seed + 8); rect(img, (9, 13, 11, 14), beard, 4, seed + 9); rect(img, (13, 13, 15, 14), beard, 4, seed + 10)
    img.putpixel((11, 14), (120, 70, 60, 255)); img.putpixel((12, 14), (120, 70, 60, 255))
    cube(img, 16, 16, 8, 12, 4, tunic, seed + 11)              # body gambeson
    for yy in range(21, 32, 3):                               # quilting
        for xx in range(20, 28): img.putpixel((xx, yy), tuple(c - 25 for c in tunic) + (255,))
        for xx in range(32, 40): img.putpixel((xx, yy), tuple(c - 25 for c in tunic) + (255,))
    rect(img, (20, 30, 28, 32), (70, 50, 35), 4, seed + 12)    # belt
    img.putpixel((23, 30), (200, 170, 60, 255)); img.putpixel((24, 30), (200, 170, 60, 255))
    for (u, v) in ((40, 16), (32, 48)):                       # arms
        cube(img, u, v, 4, 12, 4, tunic, seed + 13)
        rect(img, (u, v + 4 + 9, u + 16, v + 16), tone, seed=seed + 14)   # hands
        rect(img, (u + 4, v, u + 8, v + 4), tunic, seed=seed + 15)
    for (u, v) in ((0, 16), (16, 48)):                        # legs
        cube(img, u, v, 4, 12, 4, hose, seed + 16)
        rect(img, (u, v + 4 + 8, u + 16, v + 16), (55, 40, 30), seed=seed + 17)   # boots
    img.save(os.path.join(ENT, name + ".png"))

skin("soldier_a", (92, 64, 40), None, (122, 106, 85), (74, 59, 44), 1)
skin("soldier_b", (40, 32, 28), (50, 38, 30), (110, 100, 90), (62, 52, 46), 2)
skin("archer", (150, 110, 60), None, (70, 100, 55), (90, 70, 45), 3)
skin("officer", (150, 150, 150), (165, 165, 165), (60, 70, 120), (50, 45, 60), 4)

# tabard: white surcoat on the body box (front/back/sides/shoulders) + short skirt on the legs; emblem in grey
tab = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
d = ImageDraw.Draw(tab)
d.rectangle((20, 16, 27, 19), fill=(235, 235, 235, 255))      # shoulders (top)
for (x0, x1) in ((20, 27), (32, 39)):                          # front, back
    d.rectangle((x0, 20, x1, 31), fill=(250, 250, 250, 255))
    d.rectangle((x0, 20, x0, 31), fill=(200, 200, 200, 255)); d.rectangle((x1, 20, x1, 31), fill=(200, 200, 200, 255))
    d.rectangle((x0, 31, x1, 31), fill=(190, 190, 190, 255))
    cx = (x0 + x1) // 2
    d.rectangle((cx, 22, cx + 1, 29), fill=(150, 150, 150, 255))  # emblem: cross
    d.rectangle((cx - 2, 24, cx + 3, 25), fill=(150, 150, 150, 255))
d.rectangle((16, 20, 19, 31), fill=(225, 225, 225, 255))      # sides
d.rectangle((28, 20, 31, 31), fill=(225, 225, 225, 255))
d.rectangle((0, 20, 15, 24), fill=(245, 245, 245, 255))       # skirt on legs (all four sides, 5 px)
d.rectangle((0, 24, 15, 24), fill=(190, 190, 190, 255))
tab.save(os.path.join(ENT, "tabard.png"))

eggs = {"man_at_arms": ((140, 140, 150), (46, 76, 158)), "knight": ((200, 200, 210), (46, 76, 158)),
        "lancer": ((120, 90, 60), (46, 76, 158)), "mounted_swordsman": ((170, 140, 100), (46, 76, 158)),
        "archer": ((59, 110, 46), (110, 80, 50)), "crossbowman": ((107, 74, 43), (59, 110, 46)),
        "sergeant": ((46, 76, 158), (220, 180, 60))}
for k, (base, spot) in eggs.items():
    e = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dd = ImageDraw.Draw(e)
    dd.ellipse((3, 1, 12, 14), fill=base + (255,), outline=tuple(c // 2 for c in base) + (255,))
    r = random.Random(k)
    for _ in range(5):
        x, y = r.randint(5, 10), r.randint(3, 11)
        dd.rectangle((x, y, x + 1, y + 1), fill=spot + (255,))
    e.putpixel((5, 3), (255, 255, 255, 200))
    e.save(os.path.join(ITEM, f"kingdom_{k}_spawn_egg.png"))
print("textures written")
