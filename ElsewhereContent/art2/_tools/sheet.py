# usage: sheet.py out.png scale cols file1 file2 ...  -> contact sheet, checker bg, 1px grid gaps
import sys
from PIL import Image, ImageDraw
out, scale, cols = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
files = sys.argv[4:]
ims = [Image.open(f).convert('RGBA') for f in files]
cw = max(i.width for i in ims) * scale; ch = max(i.height for i in ims) * scale
rows = (len(ims) + cols - 1) // cols
pad = 8; lab = 14
W = cols * (cw + pad) + pad; H = rows * (ch + pad + lab) + pad
sheet = Image.new('RGBA', (W, H), (60, 60, 64, 255))
d = ImageDraw.Draw(sheet)
for k, (im, f) in enumerate(zip(ims, files)):
    x = pad + (k % cols) * (cw + pad); y = pad + (k // cols) * (ch + pad + lab)
    w, h = im.width * scale, im.height * scale
    bg = Image.new('RGBA', (w, h))
    bd = ImageDraw.Draw(bg)
    for yy in range(0, h, scale):
        for xx in range(0, w, scale):
            c = (140, 140, 146, 255) if ((xx // scale + yy // scale) % 2) else (120, 120, 126, 255)
            bd.rectangle([xx, yy, xx + scale - 1, yy + scale - 1], fill=c)
    bg.alpha_composite(im.resize((w, h), Image.NEAREST))
    sheet.paste(bg, (x, y + lab))
    d.text((x, y), f.split('/')[-1][:max(8, cw // 6)], fill=(255, 255, 255, 255))
sheet.save(out)
print(out, sheet.size)
