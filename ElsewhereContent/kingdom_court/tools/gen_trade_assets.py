#!/usr/bin/env python3
"""Generates the 2.1 trade assets of the court: 4 trader NPC skins (hue-shifted from the diplomat skins) and the 4 trade item icons. Run from kingdom_court/."""
import colorsys
from PIL import Image, ImageDraw

RES = 'src/main/resources/assets/elsewhere/textures/'

def shift(src, dst, hue, sat=1.0, val=1.0):
    im = Image.open(RES + 'entity/kingdom/' + src).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s < 0.12:  # skin-neutral / grey stays
                r2, g2, b2 = colorsys.hsv_to_rgb(h, s, min(1, v * val))
            else:
                r2, g2, b2 = colorsys.hsv_to_rgb((h + hue) % 1.0, min(1, s * sat), min(1, v * val))
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    im.save(RES + 'entity/kingdom/' + dst)

# base skins: lawspeaker = brown/tan, royal_envoy = slate blue, clerk = green
shift('diplomat_lawspeaker.png', 'trader_smith.png', -0.02, 0.5, 0.72)       # soot-brown leather
shift('diplomat_royal_envoy.png', 'trader_market_inspector.png', 0.5, 1.0, 1.25)  # Azmar sand and gold
shift('diplomat_clerk.png', 'trader_auctioneer.png', 0.52, 1.1, 0.95)        # purple coat
shift('diplomat_lawspeaker.png', 'trader_caravan_master.png', 0.04, 1.3, 1.1)  # desert orange

def icon(name, draw):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    draw(d)
    im.save(RES + 'item/' + name + '.png')

def ring(d):
    o, i = (150, 100, 40, 255), (230, 190, 80, 255)
    d.ellipse((2, 4, 13, 14), outline=o, width=2)
    d.ellipse((3, 5, 12, 13), outline=i, width=1)
    d.rectangle((6, 2, 9, 4), fill=(200, 60, 40, 255), outline=o)  # a red stone set in the ring
    d.point((7, 3), fill=(255, 150, 120, 255))
    d.point((5, 12), fill=(255, 235, 150, 255))

def paper(d, band, seal):
    d.rectangle((3, 1, 12, 14), fill=(232, 220, 188, 255), outline=(120, 96, 60, 255))
    for y in (4, 6, 8): d.line((5, y, 10, y), fill=(120, 96, 60, 255))
    d.rectangle((3, 11, 12, 12), fill=band)
    d.ellipse((9, 9, 12, 12), fill=seal, outline=(60, 30, 20, 255))

icon('hrafnholt_arm_ring', ring)
icon('azmar_commission_slip', lambda d: paper(d, (212, 160, 40, 255), (200, 40, 40, 255)))
icon('azmar_letter_of_credit', lambda d: (paper(d, (60, 120, 200, 255), (230, 190, 60, 255)), d.line((5, 10, 8, 10), fill=(120, 96, 60, 255))))
icon('mirewick_loan_note', lambda d: paper(d, (40, 130, 90, 255), (140, 40, 160, 255)))
print('ok')
