"""Landscape creative assets (product page header / search results art) for App Store Connect.

Usage:  python3 ios/store-listing/compose_header.py <raw-dir> ios/store-listing/creative-assets
Writes 5244 x 2950 and 3840 x 1646.

The search results card shows only a 3:2 slice of this art: measured from App Store
Connect's preview, x 29%-71% of the width and y 14%-64% of the height. Everything
that matters (headline and phone) is placed inside that slice with a margin; the
rest of the canvas is plain background, so any crop looks intentional. Needs Pillow.
"""
import sys, os
from PIL import Image, ImageDraw, ImageFont, ImageFilter
RAW, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)
FONTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "MileageLog", "Fonts") + os.sep
SIGN, BODY = FONTS + "Overpass-ExtraBold.ttf", FONTS + "AtkinsonHyperlegible-Regular.ttf"
GREEN, SOFT, WHITE = (10, 106, 74), (207, 231, 220), (255, 255, 255)
SAFE_X = (0.29, 0.71)      # search crop, fraction of width
SAFE_TOP = 0.139           # search crop top, fraction of height (crop is 3:2)
INSET = 0.07               # keep content this far inside the crop

HEAD = ["Every", "business mile,", "logged for you"]
SUB = ["Automatic GPS drive detection", "and IRS deductions."]
SHOT = "2-drive-detected"

def phone(shot, width):
    s = shot.convert("RGB"); h = round(s.height * width / s.width); s = s.resize((width, h), Image.LANCZOS)
    b = round(width * 0.035); R = round(width * 0.135)
    ph = Image.new("RGBA", (width + 2 * b, h + 2 * b), (0, 0, 0, 0)); d = ImageDraw.Draw(ph)
    d.rounded_rectangle([0, 0, ph.width - 1, ph.height - 1], radius=R + b, fill=(24, 26, 25, 255))
    d.rounded_rectangle([3, 3, ph.width - 4, ph.height - 4], radius=R + b - 3, outline=(82, 86, 84, 255), width=max(3, width // 200))
    m = Image.new("L", (width, h), 0); ImageDraw.Draw(m).rounded_rectangle([0, 0, width - 1, h - 1], radius=R, fill=255)
    ph.paste(s, (b, b), m)
    return ph

def make(W, H, name):
    img = Image.new("RGB", (W, H), GREEN); d = ImageDraw.Draw(img)
    # Crop box (3:2), then the content box inside it.
    cx0, cx1 = W * SAFE_X[0], W * SAFE_X[1]; cw = cx1 - cx0; ch = cw / 1.5
    cy0 = min(H * SAFE_TOP, H - ch); cy1 = cy0 + ch
    x0, x1 = cx0 + cw * INSET, cx1 - cw * INSET
    y0, y1 = cy0 + ch * INSET, cy1 - ch * INSET
    bw, bh = x1 - x0, y1 - y0
    u = bw / 1000                                   # content unit

    # Sign border around the whole canvas (outside the crop, so search never shows it cut).
    m = round(H * 0.025)
    d.rounded_rectangle([m, m, W - m - 1, H - m - 1], radius=round(H * 0.045), outline=WHITE, width=max(4, round(H * 0.004)))

    # Phone on the right, full height of the content box.
    shot = Image.open(os.path.join(RAW, SHOT + ".png"))
    ph_h = bh
    screen_w = round(ph_h / (shot.height / shot.width + 0.07))
    p = phone(shot, screen_w)
    if p.height > bh: p = p.resize((round(p.width * bh / p.height), round(bh)), Image.LANCZOS)
    px, py = round(x1 - p.width), round(y0 + (bh - p.height) / 2)
    blur = max(8, round(14 * u))
    sh = Image.new("RGBA", (p.width + 4 * blur, p.height + 4 * blur), (0, 0, 0, 0))
    sh.paste((0, 0, 0, 120), (2 * blur, 2 * blur), p.split()[3]); sh = sh.filter(ImageFilter.GaussianBlur(blur))
    img.paste(sh, (px - 2 * blur + blur // 3, py - 2 * blur + blur), sh)
    img.paste(p, (px, py), p)

    # Side phones fill the wide header but sit wholly outside the search crop, so search never cuts them.
    side_w = round(W * (SAFE_X[0] - 0.08))          # room between the border and the crop
    for side_shot, left in (("3-review", True), ("1-home", False)):
        sp = phone(Image.open(os.path.join(RAW, side_shot + ".png")), round(side_w * 0.80))
        limit = p.height * 0.92                     # a touch smaller than the main phone
        if sp.height > limit: sp = sp.resize((round(sp.width * limit / sp.height), round(limit)), Image.LANCZOS)
        gap = W * 0.025
        sx = round(cx0 - gap - sp.width) if left else round(cx1 + gap)
        sy = round(py + p.height * 0.18)            # lower than the main phone, still inside the border
        sy = min(sy, round(H - m - H * 0.03 - sp.height))
        sb = max(8, round(sp.width * 0.03))
        shd = Image.new("RGBA", (sp.width + 4 * sb, sp.height + 4 * sb), (0, 0, 0, 0))
        shd.paste((0, 0, 0, 110), (2 * sb, 2 * sb), sp.split()[3]); shd = shd.filter(ImageFilter.GaussianBlur(sb))
        img.paste(shd, (sx - 2 * sb + sb // 3, sy - 2 * sb + sb), shd)
        img.paste(sp, (sx, sy), sp)

    # Text column on the left, vertically centered next to the phone.
    col_w = px - x0 - 50 * u
    def fit(lines, path, size):
        while True:
            f = ImageFont.truetype(path, round(size))
            if max(d.textlength(l, font=f) for l in lines) <= col_w: return f
            size *= 0.96
    eb = fit(["M I L E A G E   L O G"], SIGN, 30 * u)
    hf = fit(HEAD, SIGN, 104 * u)
    sf = fit(SUB, BODY, 40 * u)
    hs, ss = hf.size, sf.size
    block = eb.size * 1.2 + 30 * u + len(HEAD) * hs * 1.08 + 30 * u + len(SUB) * ss * 1.35
    y = y0 + (bh - block) / 2
    d.text((x0, y), "M I L E A G E   L O G", font=eb, fill=SOFT); y += eb.size * 1.2 + 30 * u
    for l in HEAD: d.text((x0, y), l, font=hf, fill=WHITE); y += hs * 1.08
    y += 30 * u
    for l in SUB: d.text((x0, y), l, font=sf, fill=SOFT); y += ss * 1.35

    img.save(os.path.join(OUT, name), optimize=True)
    print("wrote", name, img.size, "crop box", [round(cx0), round(cy0), round(cx1), round(cy1)])
    return img, (cx0, cy0, cx1, cy1)

out = {}
out["5244"] = make(5244, 2950, "mileage-log-header-5244x2950.png")
out["3840"] = make(3840, 1646, "mileage-log-header-3840x1646.png")
if len(sys.argv) > 3:   # optional: write what the search card would show
    for k, (img, box) in out.items():
        img.crop(tuple(round(v) for v in box)).resize((900, 600), Image.LANCZOS).save(os.path.join(sys.argv[3], f"search-crop-{k}.png"))
