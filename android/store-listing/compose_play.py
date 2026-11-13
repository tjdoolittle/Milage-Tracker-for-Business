"""Google Play listing graphics from raw emulator screenshots.

Usage:  python3 android/store-listing/compose_play.py <raw-dir> android/store-listing
Writes screenshots/1-...png to 5-...png (1080 x 1920, 9:16, the shape Play wants)
and graphics/feature-graphic-1024x500.png. The raw captures come from the
ci-android-shots branch ("Android screenshots" workflow). Needs Pillow.
"""
import sys, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
RAW, OUT = sys.argv[1], sys.argv[2]
FONTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "font") + os.sep
SIGN = FONTS + "overpass.ttf"          # variable font; ExtraBold is set below
BODY = FONTS + "atkinson_regular.ttf"
GREEN, DARK, SOFT, WHITE = (10, 106, 74), (14, 19, 17), (207, 231, 220), (255, 255, 255)

SLIDES = [   # upload order: the first three tell the core story
    ("1-drive-detected", "Logs drives on its own", "Automatic detection measures each trip with your phone in your pocket.", GREEN),
    ("2-review", "Business or personal? One tap.", "GPS miles and addresses fill in. Pick a purpose and save.", GREEN),
    ("3-home", "Every business mile, added up", "Your miles and IRS deduction for the year, at a glance.", GREEN),
    ("6-report", "A tax report, ready to hand over", "Totals, Schedule C vehicle miles, parking and tolls, and every trip, as a PDF.", GREEN),
    ("4-settings", "Set it up once", "Turn on detection, save home and work, and you're ready to drive.", GREEN),
    ("5-dark", "Private, and easy on the eyes", "Dark mode, no account, and your trips never leave your phone.", DARK),
]

def sign_font(size):
    f = ImageFont.truetype(SIGN, size)
    try: f.set_variation_by_axes([800])
    except Exception: pass
    return f

def greedy(draw, words, font, width):
    lines, cur = [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=font) <= width: cur = t
        else: lines.append(cur); cur = w
    lines.append(cur); return lines

def wrap(draw, text, font, width):
    words = text.split(); n = len(greedy(draw, words, font, width)); lo, hi = 50, width
    while hi - lo > 4:
        mid = (lo + hi) // 2
        if len(greedy(draw, words, font, mid)) <= n: hi = mid
        else: lo = mid
    return greedy(draw, words, font, hi)

def phone(shot, width):
    s = shot.convert("RGB"); h = round(s.height * width / s.width); s = s.resize((width, h), Image.LANCZOS)
    b = round(width * 0.03); R = round(width * 0.07)
    ph = Image.new("RGBA", (width + 2 * b, h + 2 * b), (0, 0, 0, 0)); d = ImageDraw.Draw(ph)
    d.rounded_rectangle([0, 0, ph.width - 1, ph.height - 1], radius=R + b, fill=(24, 26, 25, 255))
    d.rounded_rectangle([3, 3, ph.width - 4, ph.height - 4], radius=R + b - 3, outline=(82, 86, 84, 255), width=4)
    m = Image.new("L", (width, h), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, width - 1, h - 1], radius=R, fill=255)
    ph.paste(s, (b, b), m); return ph

def drop(img, p, x, y, blur=24, alpha=110):
    sh = Image.new("RGBA", (p.width + 4 * blur, p.height + 4 * blur), (0, 0, 0, 0))
    sh.paste((0, 0, 0, alpha), (2 * blur, 2 * blur), p.split()[3]); sh = sh.filter(ImageFilter.GaussianBlur(blur))
    img.paste(sh, (x - 2 * blur + blur // 3, y - 2 * blur + blur), sh); img.paste(p, (x, y), p)

def screenshots():
    os.makedirs(os.path.join(OUT, "screenshots"), exist_ok=True)
    W, H = 1080, 1920
    for order, (name, head, sub, bg) in enumerate(SLIDES, 1):
        src = os.path.join(RAW, name + ".png")
        if not os.path.exists(src): print("missing", src); continue
        img = Image.new("RGB", (W, H), bg); d = ImageDraw.Draw(img)
        d.rounded_rectangle([28, 28, W - 29, H + 200], radius=48, outline=WHITE, width=7)
        d.text((W / 2, 110), "T A X   M I L E A G E   T R A C K E R", font=sign_font(32), fill=SOFT, anchor="mm")
        hf = sign_font(82); y = 170
        for line in wrap(d, head, hf, W - 170):
            d.text((W / 2, y), line, font=hf, fill=WHITE, anchor="mt"); y += 94
        sf = ImageFont.truetype(BODY, 38); y += 12
        for line in wrap(d, sub, sf, W - 220):
            d.text((W / 2, y), line, font=sf, fill=SOFT, anchor="mt"); y += 50
        p = phone(Image.open(src), 760)
        drop(img, p, (W - p.width) // 2, max(y + 50, 520))
        img.save(os.path.join(OUT, "screenshots", f"{order}-{name.split('-', 1)[1]}.png"), optimize=True)
        print("wrote", name, img.size)

def feature_graphic():
    os.makedirs(os.path.join(OUT, "graphics"), exist_ok=True)
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), GREEN); d = ImageDraw.Draw(img)
    d.rounded_rectangle([14, 14, W - 15, H - 15], radius=26, outline=WHITE, width=4)
    x = 64
    d.text((x, 120), "T A X   M I L E A G E   T R A C K E R", font=sign_font(20), fill=SOFT)
    hf = sign_font(52); y = 160
    for line in ["Every business mile,", "logged for you"]:
        d.text((x, y), line, font=hf, fill=WHITE); y += 60
    sf = ImageFont.truetype(BODY, 22)
    d.text((x, y + 14), "Automatic GPS drive detection", font=sf, fill=SOFT)
    d.text((x, y + 44), "and IRS deductions.", font=sf, fill=SOFT)
    # Two phones on the right, cropped by the bottom edge like a hand-held view.
    a = phone(Image.open(os.path.join(RAW, "3-home.png")), 190)
    b = phone(Image.open(os.path.join(RAW, "1-drive-detected.png")), 210)
    drop(img, a, 590, 110, blur=12); drop(img, b, 745, 60, blur=12)
    # Re-draw the border so the phones sit inside it.
    mask = Image.new("L", (W, H), 0); ImageDraw.Draw(mask).rounded_rectangle([18, 18, W - 19, H - 19], radius=22, fill=255)
    bg = Image.new("RGB", (W, H), GREEN); bg.paste(img, (0, 0), mask); img = bg; d = ImageDraw.Draw(img)
    d.rounded_rectangle([14, 14, W - 15, H - 15], radius=26, outline=WHITE, width=4)
    img.save(os.path.join(OUT, "graphics", "feature-graphic-1024x500.png"), optimize=True)
    print("wrote feature graphic", img.size)

screenshots()
feature_graphic()
