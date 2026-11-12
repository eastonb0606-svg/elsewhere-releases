import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
from doll import humanoid, sheet
t = Image.open(sys.argv[1]).convert('RGBA')
exb = [((14, 36, 6, 7), 10, 13), ((26, 34, 2, 3), 11, 10), ((31, 35, 3, 4), 11, 14)]
sheet(humanoid(t, (), exb, larm=(32, 48), lleg=(16, 48)), 12, sys.argv[2], t, 8)
