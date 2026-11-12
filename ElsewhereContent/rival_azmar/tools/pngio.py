"""Minimal dependency-free RGBA PNG read/write and a tiny Image class (no Pillow on this machine)."""
import zlib, struct

class Image:
    def __init__(self, w, h, fill=(0, 0, 0, 0)):
        self.w, self.h = w, h
        self.px = [fill] * (w * h)
    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            if len(c) == 3: c = tuple(c) + (255,)
            self.px[y * self.w + x] = tuple(c)
    def get(self, x, y):
        return self.px[y * self.w + x]
    def rect(self, x0, y0, x1, y1, c):
        """Inclusive-exclusive box [x0,x1) x [y0,y1)."""
        for y in range(y0, y1):
            for x in range(x0, x1): self.put(x, y, c)
    def line(self, x0, y0, x1, y1, c):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.put(x0, y0, c)
            if x0 == x1 and y0 == y1: break
            e2 = 2 * err
            if e2 >= dy: err += dy; x0 += sx
            if e2 <= dx: err += dx; y0 += sy
    def save(self, path):
        raw = b"".join(b"\x00" + b"".join(bytes(p) for p in self.px[y * self.w:(y + 1) * self.w]) for y in range(self.h))
        def chunk(t, d):
            c = struct.pack(">I", len(d)) + t + d
            return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
        open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
                               + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))

def load(path):
    d = open(path, "rb").read()
    pos, idat, w = 8, b"", 0
    ct = 6
    while pos < len(d):
        n = struct.unpack(">I", d[pos:pos + 4])[0]; t = d[pos + 4:pos + 8]; body = d[pos + 8:pos + 8 + n]
        if t == b"IHDR": w, h, bd, ct = struct.unpack(">IIBB", body[:10])
        elif t == b"IDAT": idat += body
        elif t == b"PLTE": pal = body
        pos += 12 + n
    ch = {6: 4, 2: 3, 0: 1, 4: 2}[ct]
    raw = zlib.decompress(idat); stride = w * ch
    img = Image(w, h); prev = bytearray(stride); i = 0
    for y in range(h):
        f = raw[i]; line = bytearray(raw[i + 1:i + 1 + stride]); i += 1 + stride
        for x in range(stride):
            a = line[x - ch] if x >= ch else 0; b = prev[x]; c = prev[x - ch] if x >= ch else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c; pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        for x in range(w):
            q = line[x * ch:(x + 1) * ch]
            img.px[y * w + x] = (q[0], q[1], q[2], q[3] if ch == 4 else 255) if ch >= 3 else (q[0], q[0], q[0], q[1] if ch == 2 else 255)
    return img
