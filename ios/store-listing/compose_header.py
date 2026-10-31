"""Landscape creative assets (product page header / search art) for App Store Connect.

Usage:  python3 ios/store-listing/compose_header.py <raw-dir> ios/store-listing/creative-assets
Writes 5244 x 2950 and 3840 x 1646. Apple crops these differently per device, so the
headline and phones stay centered with wide margins. Needs Pillow.
"""
import sys, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
RAW, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
FONTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "MileageLog", "Fonts") + os.sep
SIGN, BODY = FONTS + "Overpass-ExtraBold.ttf", FONTS + "AtkinsonHyperlegible-Regular.ttf"
GREEN, SOFT, WHITE = (10, 106, 74), (207, 231, 220), (255, 255, 255)
SHOTS = ["3-review", "2-drive-detected", "1-home"]   # left, center, right
HEAD = "Every business mile, logged for you"
SUB = "Automatic GPS drive detection and IRS deductions, ready for tax time."

def phone(shot, width):
    s = shot.convert("RGB"); h = round(s.height * width / s.width); s = s.resize((width, h), Image.LANCZOS)
    b = round(width * 0.035); R = round(width * 0.135)
    ph = Image.new("RGBA", (width + 2 * b, h + 2 * b), (0, 0, 0, 0)); d = ImageDraw.Draw(ph)
    d.rounded_rectangle([0, 0, ph.width - 1, ph.height - 1], radius=R + b, fill=(24, 26, 25, 255))
    d.rounded_rectangle([3, 3, ph.width - 4, ph.height - 4], radius=R + b - 3, outline=(82, 86, 84, 255), width=max(3, width // 250))
    m = Image.new("L", (width, h), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, width - 1, h - 1], radius=R, fill=255)
    ph.paste(s, (b, b), m)
    return ph

def place(img, p, x, y, blur):
    sh = Image.new("RGBA", (p.width + 4 * blur, p.height + 4 * blur), (0, 0, 0, 0))
    sh.paste((0, 0, 0, 120), (2 * blur, 2 * blur), p.split()[3]); sh = sh.filter(ImageFilter.GaussianBlur(blur))
    img.paste(sh, (x - 2 * blur + blur // 3, y - 2 * blur + blur), sh)
    img.paste(p, (x, y), p)

def make(W, H, name):
    img = Image.new("RGB", (W, H), GREEN); d = ImageDraw.Draw(img)
    u = H / 1000                                  # layout unit
    m = round(26 * u)
    d.rounded_rectangle([m, m, W - m - 1, H + 400], radius=round(46 * u), outline=WHITE, width=max(4, round(6 * u)))
    d.text((W / 2, 95 * u), "M I L E A G E   L O G", font=ImageFont.truetype(SIGN, round(30 * u)), fill=SOFT, anchor="mm")
    d.text((W / 2, 150 * u), HEAD, font=ImageFont.truetype(SIGN, round(82 * u)), fill=WHITE, anchor="mt")
    d.text((W / 2, 262 * u), SUB, font=ImageFont.truetype(BODY, round(36 * u)), fill=SOFT, anchor="mt")
    pw = round(300 * u)                           # phone screen width
    phones = [phone(Image.open(os.path.join(RAW, n + ".png")), pw) for n in SHOTS]
    gap = round(60 * u); total = sum(p.width for p in phones) + 2 * gap
    x = (W - total) // 2; top = round(360 * u)
    for i, p in enumerate(phones):
        y = top + (0 if i == 1 else round(60 * u))   # center phone sits higher
        place(img, p, x, y, max(8, round(18 * u))); x += p.width + gap
    img.save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, img.size)

make(5244, 2950, "mileage-log-header-5244x2950.png")
make(3840, 1646, "mileage-log-header-3840x1646.png")
