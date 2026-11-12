"""Placeholder 16x16 item textures for the K-D lore items. Run: _tools/venv/bin/python gen_textures.py"""
from PIL import Image, ImageDraw
import os
O = os.path.join(os.path.dirname(__file__), "../src/main/resources/assets/elsewhere/textures/item/")
PAR, EDGE, INK, GOLD = (226, 205, 150, 255), (150, 112, 60, 255), (70, 50, 30, 255), (230, 190, 60, 255)

def img(): return Image.new("RGBA", (16, 16), (0, 0, 0, 0))
def save(i, n): i.save(O + n + ".png")

def scroll():
    i = img(); d = ImageDraw.Draw(i)
    d.rectangle([3, 2, 12, 13], fill=PAR, outline=EDGE)
    d.rectangle([2, 1, 13, 2], fill=EDGE); d.rectangle([2, 13, 13, 14], fill=EDGE)
    for y in (5, 7, 9): d.line([5, y, 10, y], fill=INK)
    d.ellipse([9, 10, 12, 13], fill=(170, 30, 40, 255))
    save(i, "lore_royal_proclamation")

def fragment():
    i = img(); d = ImageDraw.Draw(i)
    d.polygon([(2, 3), (12, 2), (13, 9), (9, 8), (8, 13), (3, 12)], fill=PAR, outline=EDGE)
    d.line([4, 5, 9, 5], fill=INK); d.line([4, 7, 7, 7], fill=INK); d.point((10, 5), fill=(170, 30, 40, 255))
    save(i, "lore_survey_fragment")

def foldedmap():
    i = img(); d = ImageDraw.Draw(i)
    d.rectangle([2, 3, 13, 12], fill=PAR, outline=EDGE)
    d.line([6, 3, 6, 12], fill=EDGE); d.line([10, 3, 10, 12], fill=EDGE)
    d.line([4, 6, 5, 9], fill=INK); d.line([8, 5, 8, 10], fill=INK)
    d.rectangle([11, 6, 12, 7], fill=(170, 30, 40, 255))
    d.ellipse([6, 8, 9, 11], fill=GOLD)
    save(i, "lore_royal_survey_map")

def seal(name, col):
    i = img(); d = ImageDraw.Draw(i)
    dark = tuple(max(0, c - 60) for c in col[:3]) + (255,)
    d.ellipse([2, 2, 13, 13], fill=col, outline=dark)
    d.ellipse([5, 5, 10, 10], outline=dark)
    d.point((7, 7), fill=(255, 255, 255, 255)); d.point((8, 8), fill=(255, 255, 255, 255))
    d.rectangle([6, 13, 9, 14], fill=dark)
    save(i, "lore_seal_" + name)

scroll(); fragment(); foldedmap()
for n, c in [("marshal", (170, 40, 40, 255)), ("castellan", (110, 110, 125, 255)), ("master_at_arms", (190, 190, 200, 255)), ("huntsman", (60, 130, 60, 255)), ("chancellor", (110, 60, 160, 255))]:
    seal(n, c)
