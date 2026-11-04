# Crop real store screenshots into pieces for the video. Source files are only read.
from PIL import Image
import os, sys
src = sys.argv[1]; out = sys.argv[2]
S = lambda n: os.path.join(src, n)
os.makedirs(out, exist_ok=True)
s1 = Image.open(S("phoneScreenshots/1.jpg")).convert("RGB")
s2 = Image.open(S("phoneScreenshots/2.jpg")).convert("RGB")
s3 = Image.open(S("phoneScreenshots/3.jpg")).convert("RGB")
s4 = Image.open(S("phoneScreenshots/4.jpg")).convert("RGB")
# Phone screens (display coords x1.08).
phone = lambda im, box: im.crop(tuple(round(v*1.08) for v in box))
phone(s1, (158, 435, 842, 1955)).save(os.path.join(out, "phone-gallery.jpg"), quality=95)
phone(s2, (158, 435, 842, 1955)).save(os.path.join(out, "phone-viewer.jpg"), quality=95)
phone(s4, (158, 435, 842, 1955)).save(os.path.join(out, "phone-fav.jpg"), quality=95)
# Grid tiles from the gallery screenshot: 3 cols x 5 rows, 228 display px each.
n = 0
for r in range(5):
    for c in range(3):
        x0 = 158 + c*228; y0 = 646 + r*228
        phone(s1, (x0+2, y0+2, x0+226, y0+226)).save(os.path.join(out, f"tile{n:02d}.jpg"), quality=95); n += 1
# Multi-video quadrants.
for i, (x0, y0) in enumerate([(197, 242), (1000, 242), (197, 605), (1000, 605)]):
    phone(s3, (x0+2, y0+2, x0+800, y0+358)).save(os.path.join(out, f"wall{i}.jpg"), quality=95)
Image.open(S("icon.png")).save(os.path.join(out, "icon.png"))
print("ok", n)
