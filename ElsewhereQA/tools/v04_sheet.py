"""Contact sheet: v04_sheet.py <out.png> <cols> <width> <img>...  (labels = file names). Optional crop: CROP=x0,y0,x1,y1 env."""
import os, sys
from PIL import Image, ImageDraw
out, cols, w = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
files = sys.argv[4:]
crop = os.environ.get("CROP")
ims = []
for f in files:
    im = Image.open(f).convert("RGB")
    if crop:
        im = im.crop(tuple(int(v) for v in crop.split(",")))
    h = int(im.height * w / im.width)
    ims.append((os.path.basename(f).replace("qa_", "").replace(".png", ""), im.resize((w, h))))
rows = (len(ims) + cols - 1) // cols
ch = max(i.height for _, i in ims) + 16
sheet = Image.new("RGB", (cols * w, rows * ch), (20, 20, 20))
d = ImageDraw.Draw(sheet)
for k, (n, im) in enumerate(ims):
    x, y = (k % cols) * w, (k // cols) * ch
    sheet.paste(im, (x, y + 16))
    d.text((x + 3, y + 2), n, fill=(255, 255, 0))
sheet.save(out)
print(out, sheet.size)
