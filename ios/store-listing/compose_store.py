"""Compose captioned App Store screenshots (1320 x 2868) from raw simulator captures.

Usage:  python3 ios/store-listing/compose_store.py <raw-dir> ios/store-listing/screenshots
The raw captures come from the `store/` folder on the ci-ios branch (made by the
"iOS build and screenshots" workflow on the 6.9-inch simulator). Needs Pillow.
Edit SLIDES below to change the captions.
"""
import sys, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
RAW, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
FONTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "MileageLog", "Fonts") + os.sep
SIGN = FONTS + "Overpass-ExtraBold.ttf"
BODY = FONTS + "AtkinsonHyperlegible-Regular.ttf"
W, H = 1320, 2868
GREEN, INK_DARK = (10, 106, 74), (14, 19, 17)
SOFT = (207, 231, 220)

SLIDES = [
    ("1-home", "Every business mile, added up", "Your miles and IRS deduction for the year, at a glance.", GREEN),
    ("2-drive-detected", "Logs drives on its own", "Automatic detection measures each trip with your phone in your pocket.", GREEN),
    ("3-review", "Business or personal? One tap.", "GPS miles and addresses fill in. Add the purpose and save.", GREEN),
    ("4-settings", "Midyear rate changes, handled", "Each trip uses the IRS rate for the day you drove: 72.5¢, then 76¢.", GREEN),
    ("5-dark", "Private, and easy on the eyes", "Dark mode, no account, and your trips never leave your phone.", INK_DARK),
]

def greedy(draw, words, font, width):
    lines, cur = [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=font) <= width: cur = t
        else: lines.append(cur); cur = w
    lines.append(cur)
    return lines

def wrap(draw, text, font, width):
    """Balanced wrap: same number of lines as a greedy wrap, but the narrowest width that still fits."""
    words = text.split()
    n = len(greedy(draw, words, font, width))
    lo, hi = 50, width
    while hi - lo > 4:
        mid = (lo + hi) // 2
        if len(greedy(draw, words, font, mid)) <= n: hi = mid
        else: lo = mid
    return greedy(draw, words, font, hi)

def phone(shot, width):
    """Screenshot inside a simple dark bezel with rounded corners."""
    s = shot.convert("RGB"); h = round(s.height * width / s.width); s = s.resize((width, h), Image.LANCZOS)
    b = round(width * 0.035); R = round(width * 0.135)
    ph = Image.new("RGBA", (width + 2 * b, h + 2 * b), (0, 0, 0, 0)); d = ImageDraw.Draw(ph)
    d.rounded_rectangle([0, 0, ph.width - 1, ph.height - 1], radius=R + b, fill=(24, 26, 25, 255))
    d.rounded_rectangle([3, 3, ph.width - 4, ph.height - 4], radius=R + b - 3, outline=(82, 86, 84, 255), width=4)
    m = Image.new("L", (width, h), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, width - 1, h - 1], radius=R, fill=255)
    ph.paste(s, (b, b), m)
    return ph

for name, head, sub, bg in SLIDES:
    src = os.path.join(RAW, name + ".png")
    if not os.path.exists(src): print("missing", src); continue
    img = Image.new("RGB", (W, H), bg); d = ImageDraw.Draw(img)
    # Highway-sign inset border along the top and sides.
    d.rounded_rectangle([34, 34, W - 35, H + 200], radius=58, outline=(255, 255, 255), width=8)
    eyebrow = ImageFont.truetype(SIGN, 40)
    d.text((W / 2, 150), "M I L E A G E   L O G", font=eyebrow, fill=SOFT, anchor="mm")
    hf = ImageFont.truetype(SIGN, 104); y = 230
    for line in wrap(d, head, hf, W - 200):
        d.text((W / 2, y), line, font=hf, fill=(255, 255, 255), anchor="mt"); y += 118
    sf = ImageFont.truetype(BODY, 46); y += 18
    for line in wrap(d, sub, sf, W - 260):
        d.text((W / 2, y), line, font=sf, fill=SOFT, anchor="mt"); y += 62
    p = phone(Image.open(src), 1000)
    top = max(y + 70, 700)
    shadow = Image.new("RGBA", (p.width + 160, p.height + 160), (0, 0, 0, 0))
    shadow.paste((0, 0, 0, 120), (80, 80), p.split()[3]); shadow = shadow.filter(ImageFilter.GaussianBlur(30))
    img.paste(shadow, ((W - p.width) // 2 - 80 + 10, top - 80 + 28), shadow)
    img.paste(p, ((W - p.width) // 2, top), p)
    img.save(os.path.join(OUT, f"mileage-log-{name}.png"), optimize=True)
    print("wrote", name, img.size)
