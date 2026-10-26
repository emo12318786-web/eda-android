#!/usr/bin/env python3
"""Download and process real photographic wallpapers for EDA.

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

# Real photographic sources from Unsplash. Downloaded at build time and bundled
# into the APK, so wallpapers work offline after installation.
PHOTO_URLS = {
    "dark_queen": "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "hellhound": "https://images.unsplash.com/photo-1511497584788-876760111969?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "obsidian": "https://images.unsplash.com/photo-1497250681960-ef046c08a56e?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "pentagram": "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "wolf": "https://images.unsplash.com/photo-1472396961693-142e6e269027?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "eclipse": "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "throne": "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "black_sun": "https://images.unsplash.com/photo-1470252649378-9c29740c9fa8?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "dark_angel": "https://images.unsplash.com/photo-1500534623283-312aade485b7?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "dragon_core": "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "fallen_angel": "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=840&h=1800&fit=crop&crop=entropy&q=88",
    "night_queen": "https://images.unsplash.com/photo-1519608487953-e999c86e7455?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "space_gate": "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
    "neon_abyss": "https://images.unsplash.com/photo-1500375592092-40eb2168fd21?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
}

def build(theme):
    theme_id, bg, glow, accent, secondary, motif = theme
    from urllib.request import Request, urlopen
    from urllib.error import URLError, HTTPError

    url = PHOTO_URLS[theme_id]
    request = Request(url, headers={"User-Agent": "EDA-Android-WallpaperBuilder/1.0"})
    try:
        with urlopen(request, timeout=35) as response:
            raw = response.read()
    except (URLError, HTTPError, TimeoutError) as exc:
        raise RuntimeError(
            f"Cannot download real photo for {theme_id}; refusing to silently "
            f"replace it with generated shapes: {exc}"
        ) from exc

    import io
    with Image.open(io.BytesIO(raw)) as source:
        photo = source.convert("RGB")
    # Crop the real photograph to a tall phone wallpaper without stretching.
    target_ratio = W / H
    source_ratio = photo.width / photo.height
    if source_ratio > target_ratio:
        new_width = max(1, int(photo.height * target_ratio))
        left = (photo.width - new_width) // 2
        photo = photo.crop((left, 0, left + new_width, photo.height))
    else:
        new_height = max(1, int(photo.width / target_ratio))
        top = max(0, (photo.height - new_height) // 2)
        photo = photo.crop((0, top, photo.width, top + new_height))
    photo = photo.resize((W, H), Image.Resampling.LANCZOS).convert("RGBA")

    # Keep the photograph recognisable while darkening it for readable UI text.
    photo = Image.blend(photo, Image.new("RGBA", (W, H), (bg[0], bg[1], bg[2], 255)), 0.43)
    tint = Image.new("RGBA", (W, H), (accent[0], accent[1], accent[2], 0))
    tint.putalpha(Image.new("L", (W, H), 34))
    photo = Image.alpha_composite(photo, tint)

    # Gentle theme-colored glow near the upper/middle area, not a vector emblem.
    glow_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow_layer)
    gd.ellipse((W*0.08, H*0.10, W*0.92, H*0.68),
               fill=(glow[0], glow[1], glow[2], 38))
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(95))
    photo = Image.alpha_composite(photo, glow_layer)

    # Add a distinct, high-contrast dark-fantasy sigil to each wallpaper.
    # This makes each theme recognizable even when the photographic source is subtle.
    emblem = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ed = ImageDraw.Draw(emblem)
    cx, cy = W // 2, int(H * 0.31)
    red = (accent[0], accent[1], accent[2], 225)
    pale = (secondary[0], secondary[1], secondary[2], 175)
    # Shared thin occult ring, like the supplied EDA reference design.
    ed.ellipse((cx-92, cy-92, cx+92, cy+92), outline=red, width=3)
    ed.ellipse((cx-78, cy-78, cx+78, cy+78), outline=(red[0], red[1], red[2], 95), width=2)
    if motif in ("crown", "moon_crown"):
        pts=[(cx-58,cy+26),(cx-66,cy-34),(cx-28,cy-4),(cx,cy-52),(cx+28,cy-4),(cx+66,cy-34),(cx+58,cy+26)]
        ed.line(pts, fill=red, width=5, joint="curve"); ed.line([(cx-58,cy+26),(cx+58,cy+26)],fill=pale,width=4)
        for x,y in [(cx-66,cy-34),(cx,cy-52),(cx+66,cy-34)]: ed.ellipse((x-5,y-5,x+5,y+5),fill=pale)
    elif motif in ("pentagram",):
        points=[]
        for i in range(5):
            a=-math.pi/2+i*4*math.pi/5
            points.append((int(cx+67*math.cos(a)),int(cy+67*math.sin(a))))
        ed.line(points+[points[0]],fill=red,width=4)
    elif motif in ("wolf_fire","wolf_moon"):
        ed.polygon([(cx-58,cy-34),(cx-38,cy-74),(cx-9,cy-48),(cx+9,cy-48),(cx+38,cy-74),(cx+58,cy-34),(cx+43,cy+29),(cx,cy+68),(cx-43,cy+29)],outline=red)
        ed.line([(cx-39,cy-7),(cx-12,cy+3),(cx-27,cy+14)],fill=pale,width=4)
        ed.line([(cx+39,cy-7),(cx+12,cy+3),(cx+27,cy+14)],fill=pale,width=4)
    elif motif in ("dragon","crystal","abyss"):
        ed.polygon([(cx-52,cy-39),(cx-15,cy-65),(cx+5,cy-25),(cx+53,cy-49),(cx+34,cy+4),(cx+12,cy+54),(cx-21,cy+38),(cx-48,cy+7)],outline=red)
        ed.line([(cx-30,cy-8),(cx-4,cy+3),(cx+28,cy-8)],fill=pale,width=4)
    elif motif in ("wings_up","wings_down"):
        direction=-1 if motif=="wings_up" else 1
        ed.line([(cx,cy-12),(cx,cy+58)],fill=pale,width=4)
        for i in range(5):
            y=cy-45+i*22
            ed.line([(cx-5,y),(cx-28-i*5,y+direction*(12+i*3)),(cx-62-i*2,y+direction*(4+i*5))],fill=red,width=4)
            ed.line([(cx+5,y),(cx+28+i*5,y+direction*(12+i*3)),(cx+62+i*2,y+direction*(4+i*5))],fill=red,width=4)
    elif motif in ("eclipse","sun","portal"):
        ed.ellipse((cx-52,cy-52,cx+52,cy+52),outline=red,width=6)
        ed.ellipse((cx-35,cy-35,cx+35,cy+35),outline=pale,width=2)
        for i in range(12):
            a=i*math.pi/6
            ed.line([(int(cx+59*math.cos(a)),int(cy+59*math.sin(a))), (int(cx+76*math.cos(a)),int(cy+76*math.sin(a)))],fill=red,width=3)
    elif motif=="throne":
        ed.line([(cx-50,cy+55),(cx-50,cy-40),(cx-25,cy-12),(cx,cy-52),(cx+25,cy-12),(cx+50,cy-40),(cx+50,cy+55)],fill=red,width=5,joint="curve")
        ed.line([(cx-62,cy+55),(cx+62,cy+55)],fill=pale,width=5)
    photo = Image.alpha_composite(photo, emblem.filter(ImageFilter.GaussianBlur(0.35)))

    # Darken edges and bottom so controls and labels remain legible.
    vignette = Image.new("L", (W, H), 0)
    vd = ImageDraw.Draw(vignette)
    vd.ellipse((-W*0.22, -H*0.10, W*1.22, H*1.10), fill=105)
    vignette = vignette.filter(ImageFilter.GaussianBlur(65))
    shade = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shade.putalpha(vignette.point(lambda p: int(p * 0.55)))
    photo = Image.alpha_composite(photo, shade)

    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / f"bg_theme_{theme_id}.webp"
    final = photo.convert("RGB")
    final.save(target, "WEBP", quality=88, method=6)

    # Theme identity icon is cut from the exact same wallpaper, so selector
    # thumbnails and the dashboard share the same visual language.
    icon_size = 320
    icon_center_y = int(H * 0.31)
    left = max(0, min(W - icon_size, (W - icon_size) // 2))
    top = max(0, min(H - icon_size, icon_center_y - icon_size // 2))
    icon = final.crop((left, top, left + icon_size, top + icon_size))
    icon_target = OUT / f"ic_theme_{theme_id}.webp"
    icon.save(icon_target, "WEBP", quality=90, method=6)
    print(f"{target.relative_to(ROOT)}: {target.stat().st_size // 1024} KiB; icon={icon_target.name}")


def main():
    for theme in THEMES:
        build(theme)
    print(f"Generated {len(THEMES)} raster theme wallpapers in {OUT}")

if __name__ == "__main__":
    main()
