"""Placeholder textures for gear2: tinted copies of fitting vanilla textures (the art lane replaces them).

Run: <venv python with Pillow> gear2/tools/gen_textures.py <minecraft client jar>
Each texture = vanilla luminance x tint colour, alpha kept, so it is never the missing-texture checkerboard.
"""
import io, os, sys, zipfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures"))
JAR = zipfile.ZipFile(sys.argv[1])

ITEMS = {  # our texture -> (vanilla texture, tint)
    "item/galvanic_coilmail_helmet": ("item/chainmail_helmet", (90, 200, 190)),
    "item/galvanic_coilmail_chestplate": ("item/chainmail_chestplate", (90, 200, 190)),
    "item/galvanic_coilmail_leggings": ("item/chainmail_leggings", (90, 200, 190)),
    "item/galvanic_coilmail_boots": ("item/chainmail_boots", (90, 200, 190)),
    "item/galvanic_upgrade_smithing_template": ("item/netherite_upgrade_smithing_template", (80, 210, 200)),
    "item/ironclad_flail": ("item/mace", (170, 180, 205)),
    "item/ironclad_chain": ("item/iron_chain", (150, 160, 190)),
    "item/gale_rapier": ("item/iron_sword", (190, 245, 215)),
    "item/mirrorbolt_arbalest": ("item/crossbow_standby", (255, 225, 140)),
    "item/mirrorbolt_arbalest_pulling_0": ("item/crossbow_pulling_0", (255, 225, 140)),
    "item/mirrorbolt_arbalest_pulling_1": ("item/crossbow_pulling_1", (255, 225, 140)),
    "item/mirrorbolt_arbalest_pulling_2": ("item/crossbow_pulling_2", (255, 225, 140)),
    "item/mirrorbolt_arbalest_arrow": ("item/crossbow_arrow", (255, 225, 140)),
    "item/heliodor_mirror_core": ("item/heart_of_the_sea", (255, 205, 90)),
    "item/steam_auger": ("item/diamond_pickaxe", (230, 160, 80)),
    "item/steam_auger_bit": ("item/echo_shard", (225, 170, 90)),
    "item/termite_hive_lantern": ("item/lantern", (240, 170, 70)),
    "item/termite_queen": ("item/spider_eye", (200, 140, 70)),
    "item/seers_hourglass": ("item/clock_00", (190, 160, 255)),
    "item/prospectors_pendulum": ("item/compass_00", (235, 160, 110)),
    "item/void_tether_spool": ("item/lead", (170, 110, 230)),
    "entity/equipment/humanoid/galvanic_coilmail": ("entity/equipment/humanoid/chainmail", (90, 200, 190)),
    "entity/equipment/humanoid_leggings/galvanic_coilmail": ("entity/equipment/humanoid_leggings/chainmail", (90, 200, 190)),
    "item/amberstill_helmet": ("item/diamond_helmet", (245, 175, 60)),
    "item/amberstill_chestplate": ("item/diamond_chestplate", (245, 175, 60)),
    "item/amberstill_leggings": ("item/diamond_leggings", (245, 175, 60)),
    "item/amberstill_boots": ("item/diamond_boots", (245, 175, 60)),
    "item/amberstill_upgrade_smithing_template": ("item/netherite_upgrade_smithing_template", (245, 175, 60)),
    "entity/equipment/humanoid/amberstill": ("entity/equipment/humanoid/diamond", (245, 175, 60)),
    "entity/equipment/humanoid_leggings/amberstill": ("entity/equipment/humanoid_leggings/diamond", (245, 175, 60)),
}


def tint(img, rgb):
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            l = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
            l = min(1.0, 0.25 + l * 1.05)
            px[x, y] = (int(rgb[0] * l), int(rgb[1] * l), int(rgb[2] * l), a)
    return img


for ours, (van, rgb) in ITEMS.items():
    src = Image.open(io.BytesIO(JAR.read("assets/minecraft/textures/" + van + ".png")))
    out = os.path.join(TEX, ours + ".png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    tint(src, rgb).save(out)
    print(ours)
