import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngio import Image, load
def sheet(files, out, scale=4, cols=4, bg=(70, 70, 80)):
    ims = [load(f) for f in files]
    cw = max(i.w for i in ims) * scale + 6; ch = max(i.h for i in ims) * scale + 6
    rows = (len(ims) + cols - 1) // cols
    o = Image(cw * cols, ch * rows, bg + (255,))
    for n, im in enumerate(ims):
        ox, oy = (n % cols) * cw + 3, (n // cols) * ch + 3
        for y in range(im.h):
            for x in range(im.w):
                p = im.get(x, y)
                if p[3] == 0: continue
                for dy in range(scale):
                    for dx in range(scale): o.put(ox + x * scale + dx, oy + y * scale + dy, p)
    o.save(out)
if __name__ == "__main__":
    out = sys.argv[1]; scale = int(sys.argv[2]); cols = int(sys.argv[3]); sheet(sys.argv[4:], out, scale, cols)
