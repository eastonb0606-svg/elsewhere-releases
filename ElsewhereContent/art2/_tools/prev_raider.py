import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
from doll import humanoid, sheet
p = sys.argv[1]; out = sys.argv[2]
t = Image.open(p).convert('RGBA')
ex = [((2, 34, 3, 2), 6, 5), ((11, 33, 1, 3), 6, 2), ((2, 34, 3, 2), 17, 5, True), ((11, 33, 1, 3), 19, 2), ((5, 45, 9, 3), 8, 11), ((37, 45, 5, 4), 4, 11), ((37, 45, 5, 4), 17, 11, True), ((17, 33, 8, 5), 9, 9)]
exb = [((7, 34, 3, 2), 17, 5), ((11, 33, 1, 3), 19, 2), ((7, 34, 3, 2), 6, 5, True), ((11, 33, 1, 3), 6, 2), ((19, 45, 9, 3), 8, 11), ((47, 45, 5, 4), 17, 11), ((47, 45, 5, 4), 4, 11, True)]
sheet(humanoid(t, ex, exb), 12, out, t, 8)
