#!/usr/bin/env python3
"""Generate dark, theme-specific raster wallpapers for EDA.

Run from android-app/: python3 -m pip install Pillow && python3 generate_theme_backgrounds.py
The GitHub Actions build runs this before Gradle, so the raster assets are packaged into both APKs.
"""
from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageChops

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
W, H = 420, 900

# id: background, main glow, accent, secondary, motif
THEMES = [
    ("dark_queen",  (8, 4, 14),   (105, 18, 76), (230, 75, 132), (255, 163, 206), "crown"),
    ("hellhound",   (12, 3, 4),   (113, 17, 12), (235, 62, 48),  (255, 147, 89),  "wolf_fire"),
    ("obsidian",    (3, 7, 13),   (49, 67, 86),  (166, 183, 203),(229, 103, 111),"crystal"),
    ("pentagram",   (8, 3, 13),   (80, 18, 93),  (202, 69, 123),(222, 142, 255),"pentagram"),
    ("wolf",        (2, 8, 17),   (20, 64, 103), (109, 168, 224),(198, 90, 105), "wolf_moon"),
    ("eclipse",     (5, 3, 17),   (66, 32, 103), (153, 102, 222),(234, 160, 247),"eclipse"),
    ("throne",      (13, 2, 3),   (99, 14, 14),  (204, 65, 65), (235, 171, 157),"throne"),
    ("black_sun",   (12, 6, 1),   (107, 49, 8),  (225, 125, 51),(255, 197, 109),"sun"),
    ("dark_angel",  (2, 6, 19),   (28, 52, 88),  (177, 150, 255),(87, 232, 218),"wings_up"),
    ("dragon_core", (12, 1, 3),   (115, 11, 19),  (255, 58, 49), (255, 160, 81), "dragon"),
    ("fallen_angel",(3, 7, 21),   (35, 51, 96),  (150, 138, 255),(184, 213, 255),"wings_down"),
    ("night_queen", (13, 2, 13),  (94, 14, 75),  (229, 76, 166),(255, 166, 222),"moon_crown"),
    ("space_gate",  (2, 6, 21),   (13, 50, 107), (117, 111, 255),(85, 220, 248),"portal"),
    ("neon_abyss",  (0, 12, 11),  (7, 76, 65),   (47, 222, 190),(132, 255, 234),"abyss"),
]

def mix(a, b, t):
    return tuple(int(a[i] * (1.0 - t) + b[i] * t) for i in range(3))

def add_alpha(color, alpha):
    return (color[0], color[1], color[2], int(alpha))

def draw_glow(canvas, box, color, alpha=100, blur=55):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(box, fill=add_alpha(color, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(layer)

def polyline(draw, points, color, width=2):
    draw.line(points, fill=color, width=width, joint="curve")

def build(theme):
    theme_id, bg, glow, accent, secondary, motif = theme
    rng = random.Random("EDA:" + theme_id)
    # Smooth vertical base gradient with a subtle color wash.
    base = Image.new("RGB", (W, H))
    px = base.load()
    for y in range(H):
        t = y / max(1, H - 1)
        row = mix(bg, mix(bg, glow, 0.20), math.sin(t * math.pi) ** 1.35)
        for x in range(W):
            xoff = abs(x - W * (0.42 + 0.05 * math.sin(t * 5.0))) / (W * 0.7)
            factor = max(0.0, 1.0 - xoff) * 0.20
            px[x, y] = mix(row, glow, factor)

    art = base.convert("RGBA")
    # Layered, blurred nebula clouds; deterministic for reproducible builds.
    for i in range(8):
        x = rng.randint(-150, W - 20)
        y = rng.randint(80, H - 80)
        rw = rng.randint(90, 300)
        rh = rng.randint(100, 330)
        c = accent if i % 3 == 0 else (glow if i % 2 else secondary)
        draw_glow(art, (x, y, x + rw, y + rh), c, rng.randint(18, 45), rng.randint(38, 100))

    # Distant planets and a large atmospheric focal halo.
    cx, cy = W // 2, int(H * 0.43)
    draw_glow(art, (cx - 200, cy - 210, cx + 200, cy + 190), accent, 62, 48)
    d = ImageDraw.Draw(art, "RGBA")
    planet_r = 95 if motif in ("eclipse", "sun", "portal", "moon_crown", "wolf_moon") else 72
    d.ellipse((cx - planet_r, cy - planet_r, cx + planet_r, cy + planet_r),
              fill=add_alpha(mix(bg, glow, 0.56), 210),
              outline=add_alpha(accent, 145), width=3)
    d.arc((cx - planet_r - 26, cy - planet_r - 26, cx + planet_r + 26, cy + planet_r + 26),
          190, 350, fill=add_alpha(secondary, 170), width=3)
    d.arc((cx - planet_r - 46, cy - planet_r - 46, cx + planet_r + 46, cy + planet_r + 46),
          15, 165, fill=add_alpha(accent, 95), width=2)

    # Theme-specific emblem silhouettes, built as a dramatic wallpaper illustration.
    line = add_alpha(secondary, 210)
    soft = add_alpha(accent, 125)
    dark_shape = add_alpha(mix(bg, glow, 0.25), 232)
    if motif == "crown":
        points = [(cx-78,cy-4),(cx-91,cy-60),(cx-42,cy-28),(cx-18,cy-87),
                  (cx+15,cy-31),(cx+60,cy-68),(cx+70,cy-5),(cx+48,cy+24),
                  (cx-49,cy+24)]
        d.polygon(points, fill=dark_shape, outline=line)
        d.line(points + [points[0]], fill=soft, width=2)
        for dx in (-60, 0, 52):
            d.ellipse((cx+dx-5,cy-44-abs(dx)//3,cx+dx+5,cy-34-abs(dx)//3), fill=line)
    elif motif in ("wolf_fire", "wolf_moon"):
        points = [(cx-75,cy+30),(cx-104,cy-47),(cx-54,cy-25),(cx-25,cy-72),
                  (cx,cy-48),(cx+25,cy-72),(cx+55,cy-25),(cx+105,cy-47),
                  (cx+76,cy+32),(cx+40,cy+64),(cx,cy+80),(cx-42,cy+60)]
        d.polygon(points, fill=dark_shape, outline=line)
        d.polygon([(cx-44,cy+3),(cx-15,cy+20),(cx-3,cy+45),(cx-26,cy+34)], fill=soft)
        d.polygon([(cx+44,cy+3),(cx+15,cy+20),(cx+3,cy+45),(cx+26,cy+34)], fill=soft)
        eye = (255, 119, 67, 230) if motif == "wolf_fire" else add_alpha(secondary, 220)
        d.ellipse((cx-42,cy+4,cx-20,cy+14), fill=eye)
        d.ellipse((cx+20,cy+4,cx+42,cy+14), fill=eye)
    elif motif == "crystal":
        d.polygon([(cx,cy-120),(cx+60,cy-42),(cx+44,cy+65),(cx,cy+120),
                   (cx-49,cy+54),(cx-62,cy-43)], fill=dark_shape, outline=line)
        d.line([(cx,cy-120),(cx+3,cy+4),(cx+44,cy+65)], fill=soft, width=3)
        d.line([(cx,cy+4),(cx-49,cy+54),(cx-62,cy-43)], fill=line, width=2)
    elif motif == "pentagram":
        pts = []
        for i in range(5):
            a = -math.pi/2 + i * 2*math.pi/5
            pts.append((cx+105*math.cos(a), cy+105*math.sin(a)))
        star_order = [0,2,4,1,3,0]
        d.line([pts[i] for i in star_order], fill=add_alpha(secondary, 195), width=3)
        d.ellipse((cx-145,cy-145,cx+145,cy+145), outline=soft, width=3)
    elif motif == "eclipse":
        d.ellipse((cx-66,cy-86,cx+66,cy+46), fill=add_alpha((5,3,14),245))
        d.arc((cx-108,cy-108,cx+108,cy+108), 205, 325, fill=line, width=5)
        d.arc((cx-125,cy-125,cx+125,cy+125), 20, 160, fill=soft, width=2)
    elif motif == "throne":
        d.arc((cx-120,cy-170,cx+120,cy+115),180,360,fill=line,width=7)
        d.polygon([(cx-85,cy+92),(cx-72,cy-20),(cx-35,cy+18),(cx,cy-25),
                   (cx+35,cy+18),(cx+72,cy-20),(cx+85,cy+92)], fill=dark_shape, outline=soft)
        d.rectangle((cx-58,cy+78,cx+58,cy+126), fill=dark_shape, outline=line, width=3)
        d.line([(cx-115,cy+127),(cx+115,cy+127)], fill=line, width=4)
    elif motif == "sun":
        d.ellipse((cx-82,cy-82,cx+82,cy+82), fill=add_alpha(mix(glow, accent, .5),190), outline=line, width=3)
        for i in range(48):
            a = math.tau*i/48
            r1, r2 = 96, rng.randint(122,165)
            d.line([(cx+r1*math.cos(a),cy+r1*math.sin(a)),
                    (cx+r2*math.cos(a),cy+r2*math.sin(a))], fill=add_alpha(secondary, rng.randint(55,140)), width=2)
    elif motif in ("wings_up", "wings_down"):
        sign = -1 if motif == "wings_up" else 1
        for side in (-1,1):
            for n in range(7):
                x0 = cx + side*(20+n*4)
                y0 = cy + sign*(-10+n*7)
                x1 = cx + side*(58+n*14)
                y1 = cy + sign*(-75+n*27)
                x2 = cx + side*(102+n*18)
                y2 = cy + sign*(8+n*23)
                d.line([(x0,y0),(x1,y1),(x2,y2)], fill=add_alpha(secondary, 75+n*14), width=3)
        d.polygon([(cx,cy-60),(cx-22,cy+22),(cx,cy+63),(cx+22,cy+22)], fill=dark_shape, outline=line)
    elif motif == "dragon":
        pts = []
        for i in range(42):
            a = i * 0.30
            r = 20 + i*2.8
            pts.append((cx + r*math.cos(a), cy + r*math.sin(a)*0.62))
        d.line(pts, fill=add_alpha(accent, 150), width=18, joint="curve")
        d.line(pts, fill=add_alpha(secondary, 210), width=3, joint="curve")
        d.polygon([(cx+90,cy-25),(cx+146,cy-46),(cx+125,cy-2),(cx+156,cy+14),
                   (cx+92,cy+21)], fill=dark_shape, outline=line)
    elif motif == "moon_crown":
        d.ellipse((cx-65,cy-90,cx+65,cy+40), fill=add_alpha(secondary,200))
        d.ellipse((cx-26,cy-108,cx+92,cy+12), fill=add_alpha(mix(bg,glow,.4),245))
        d.polygon([(cx-56,cy+78),(cx-65,cy+38),(cx-29,cy+58),(cx,cy+16),
                   (cx+25,cy+58),(cx+60,cy+38),(cx+55,cy+78)], fill=dark_shape, outline=line)
    elif motif == "portal":
        for r, a in [(116,160),(137,105),(158,65)]:
            d.ellipse((cx-r,cy-r,cx+r,cy+r), outline=add_alpha(secondary,a), width=4)
        d.polygon([(cx,cy-78),(cx+54,cy),(cx,cy+78),(cx-54,cy)], outline=line, fill=dark_shape)
        d.line([(cx-185,cy+185),(cx-120,cy+120),(cx-70,cy+190)], fill=soft, width=3)
    elif motif == "abyss":
        for i in range(13):
            x = rng.randint(cx-145,cx+145)
            y0 = cy + rng.randint(30,150)
            pts = [(x,y0)]
            for j in range(1,7):
                pts.append((x+int(math.sin(j*0.85+i)*18),y0+j*29))
            d.line(pts, fill=add_alpha(secondary, rng.randint(55,170)), width=rng.randint(2,5))
            d.ellipse((x-4,y0-5,x+4,y0+3), fill=add_alpha(secondary,210))

    # Ambient orbit lines and tiny stars/embers.
    d = ImageDraw.Draw(art, "RGBA")
    for idx in range(5):
        rx = 175 + idx*23
        ry = 150 + idx*26
        d.arc((cx-rx, cy-ry, cx+rx, cy+ry), 190+idx*8, 345-idx*5,
              fill=add_alpha(accent if idx%2 else secondary, 30+idx*8), width=2)
    for _ in range(145):
        x, y = rng.randrange(W), rng.randrange(H)
        rad = rng.choice([1,1,1,2,3])
        color = secondary if rng.random() > 0.35 else accent
        alpha = rng.randint(30,145)
        d.ellipse((x-rad,y-rad,x+rad,y+rad), fill=add_alpha(color, alpha))
        if rad >= 3:
            d.line((x-6,y,x+6,y), fill=add_alpha(color, alpha//2), width=1)
            d.line((x,y-6,x,y+6), fill=add_alpha(color, alpha//2), width=1)

    # Vignette and subtle grain retain contrast under UI cards.
    mask = Image.radial_gradient("L").resize((W,H), Image.Resampling.LANCZOS)
    mask = mask.point(lambda p: int((255-p)*0.58))
    vignette = Image.new("RGBA", (W,H), (0,0,0,0))
    vignette.putalpha(mask)
    art = Image.alpha_composite(art, vignette)
    grain = Image.new("RGBA",(W,H),(0,0,0,0))
    gd = ImageDraw.Draw(grain)
    for _ in range(5200):
        x,y=rng.randrange(W),rng.randrange(H)
        shade=rng.choice([70,120,190,235])
        gd.point((x,y), fill=(shade,shade,shade,rng.randint(3,12)))
    art = Image.alpha_composite(art, grain)
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"bg_theme_{theme_id}.webp"
    art.convert("RGB").save(target, "WEBP", quality=86, method=6)
    print(f"{target.relative_to(ROOT)}: {target.stat().st_size // 1024} KiB")

def main():
    for theme in THEMES:
        build(theme)
    print(f"Generated {len(THEMES)} raster theme wallpapers in {OUT}")

if __name__ == "__main__":
    main()
