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
    "dragon_core": "https://images.unsplash.com/photo-1475738198235-4b30fc2fcf7b?w=840&h=1800&fit=crop&crop=entropy&auto=format&q=88",
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
    photo.convert("RGB").save(target, "WEBP", quality=88, method=6)
    print(f"{target.relative_to(ROOT)}: {target.stat().st_size // 1024} KiB; source=photo; {photo.size}")


def main():
    for theme in THEMES:
        build(theme)
    print(f"Generated {len(THEMES)} raster theme wallpapers in {OUT}")

if __name__ == "__main__":
    main()
