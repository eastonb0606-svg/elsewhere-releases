import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from boxpaint import Tex, hx
A2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
t = Tex(64, 64, os.path.join(A2, 'vanilla/panda_lazy.png'))
lum = lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
SAGE = ['#6f8a57', '#83a066', '#97b277', '#a9c189', '#bccf9c', '#cbdbae']
MOSS = ['#1b2414', '#25321b', '#314224', '#3e532d', '#4c6537']
cols = {tuple(int(v) for v in t.a[y, x, :3]) for y in range(64) for x in range(64) if t.a[y, x, 3]}
light = sorted([c for c in cols if lum(c) >= 110], key=lum)
dark = sorted([c for c in cols if lum(c) < 110], key=lum)
m = {}
for group, ramp in ((light, SAGE), (dark, MOSS)):
    for i, c in enumerate(group):
        m[c] = hx(ramp[round(i * (len(ramp) - 1) / max(1, len(group) - 1))])
for y in range(64):
    for x in range(64):
        if t.a[y, x, 3]:
            t.a[y, x, :3] = m[tuple(int(v) for v in t.a[y, x, :3])][:3]
t.save(os.path.join(A2, 'greenhaze_hush/src/main/resources/assets/elsewhere/textures/entity/lazy_gorger.png'))
print(len(light), len(dark))
