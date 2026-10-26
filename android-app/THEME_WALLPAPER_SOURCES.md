# EDA theme artwork: 14 offline wallpapers

EDA currently creates 14 original, procedural dark-fantasy wallpapers with
`android-app/generate_theme_backgrounds.py`. The art is drawn locally with
Pillow; it does **not** download Unsplash images, use remote URLs, or need network
access at runtime. This is intentionally documented as procedural artwork rather
than photography.

For each theme, the generator creates:
- `bg_theme_<theme-id>.webp`: phone-height wallpaper used behind the dashboard;
- `ic_theme_<theme-id>.webp`: thumbnail shown in the theme picker.

The GitHub Actions workflow installs the pinned Pillow version and runs the
generator before Gradle assembles both APK flavors. To build locally, run:

```bash
cd android-app
python3 -m pip install Pillow==11.0.0
python3 generate_theme_backgrounds.py
gradle testStandardDebugUnitTest assembleStandardDebug assemblePreviewDebug --stacktrace --no-daemon
```

The generated WebP files are not checked into Git; a build that bypasses the
generator can therefore launch without the theme artwork. The app has a safe
resource-ID fallback, but a release candidate must use the workflow-generated APK.
The current procedural art provides distinct emblems, colors and ornamental
backgrounds; it is not a set of photorealistic character illustrations. If
photorealistic character art is needed later, it must be sourced/licensed or
created as separate assets, not described as already present.
