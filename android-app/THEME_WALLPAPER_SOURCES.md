# EDA photographic theme wallpapers

EDA downloads 14 distinct photographic source images from Unsplash at build time,
then crops, darkens and color-grades them for readable text and controls. The
processed WebP files are packaged in the APK; the installed app does not need a
network connection to display them.

Source image URLs are listed in `generate_theme_backgrounds.py` under
`PHOTO_URLS`. The original photographs remain subject to the Unsplash License:
https://unsplash.com/license

The build intentionally fails if a source image cannot be downloaded or decoded.
This prevents a successful-looking build from silently shipping synthetic fallback
art instead of the requested photographic wallpapers.

The source images are not stored as binaries in Git; they are fetched and bundled
by the GitHub Actions workflow before Gradle compilation.


Last source validation update: replaced the broken dragon wallpaper URL after GitHub Actions reported HTTP 404. The next build must pass the wallpaper-generation step before an APK is considered ready.
