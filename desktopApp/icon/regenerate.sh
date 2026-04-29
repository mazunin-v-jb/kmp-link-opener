#!/usr/bin/env bash
# Rasterize the app's source SVG into bundle icons for all three desktop
# OSes — macOS `.icns`, Windows `.ico`, Linux `.png`.
#
# Inputs:
#   shared/src/commonMain/composeResources/drawable/app_logo_v2.svg
#
# Outputs (committed, consumed by build.gradle.kts):
#   desktopApp/icon/AppIcon.icns  — macOS multi-resolution container
#   desktopApp/icon/AppIcon.ico   — Windows multi-resolution container
#   desktopApp/icon/AppIcon.png   — Linux 512×512 PNG
#
# Why a script and not a Gradle task: regen is rare (only when the logo
# changes) and ImageMagick isn't a JVM dependency, so we keep the build
# graph dep-free and shell out only when a human asks. Run from anywhere,
# no args needed.
#
# Requires: ImageMagick 7+ (`brew install imagemagick`). On macOS the
# `.icns` output also needs the built-in `iconutil`; on Linux/Windows
# hosts, run only on a mac for the `.icns` step or skip it.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SVG_SRC="$REPO_ROOT/shared/src/commonMain/composeResources/drawable/app_logo_v2.svg"
OUT_DIR="$REPO_ROOT/desktopApp/icon"
OUT_ICNS="$OUT_DIR/AppIcon.icns"
OUT_ICO="$OUT_DIR/AppIcon.ico"
OUT_PNG="$OUT_DIR/AppIcon.png"

if ! command -v magick >/dev/null; then
    echo "error: ImageMagick (magick) not on PATH — install via 'brew install imagemagick'." >&2
    exit 1
fi
if [ ! -f "$SVG_SRC" ]; then
    echo "error: source SVG not found at $SVG_SRC" >&2
    exit 1
fi

# `iconutil` is macOS-only; we still want the script to do *something* on
# other hosts (the .ico and .png outputs), so don't hard-fail here.
HAVE_ICONUTIL=true
if ! command -v iconutil >/dev/null; then
    HAVE_ICONUTIL=false
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

ICONSET="$WORK/AppIcon.iconset"
mkdir -p "$ICONSET"

# `stroke="null"` is a malformed export artefact present in the source SVG.
# Skia tolerates it at runtime, but ImageMagick's parser rejects it. Patch
# in a tmp copy so we don't touch the designer-owned source.
SVG_FIXED="$WORK/app_logo_v2.svg"
sed 's/stroke="null"/stroke="none"/g' "$SVG_SRC" > "$SVG_FIXED"

# Apple's iconset wants 7 unique pixel sizes — each then surfaces under one
# or two filenames (NxN and (N/2)x(N/2)@2x).
for size in 16 32 64 128 256 512 1024; do
    magick -background none -density 1024 "$SVG_FIXED" \
        -resize "${size}x${size}" \
        "$WORK/icon_${size}.png"
done

cp "$WORK/icon_16.png"   "$ICONSET/icon_16x16.png"
cp "$WORK/icon_32.png"   "$ICONSET/icon_16x16@2x.png"
cp "$WORK/icon_32.png"   "$ICONSET/icon_32x32.png"
cp "$WORK/icon_64.png"   "$ICONSET/icon_32x32@2x.png"
cp "$WORK/icon_128.png"  "$ICONSET/icon_128x128.png"
cp "$WORK/icon_256.png"  "$ICONSET/icon_128x128@2x.png"
cp "$WORK/icon_256.png"  "$ICONSET/icon_256x256.png"
cp "$WORK/icon_512.png"  "$ICONSET/icon_256x256@2x.png"
cp "$WORK/icon_512.png"  "$ICONSET/icon_512x512.png"
cp "$WORK/icon_1024.png" "$ICONSET/icon_512x512@2x.png"

# macOS: pack iconset into .icns
if $HAVE_ICONUTIL; then
    iconutil -c icns "$ICONSET" -o "$OUT_ICNS"
    echo "wrote $OUT_ICNS ($(wc -c < "$OUT_ICNS") bytes)"
else
    echo "skipping $OUT_ICNS — iconutil not on PATH (macOS-only)" >&2
fi

# Windows: .ico is itself a multi-resolution container. ImageMagick happily
# stuffs multiple PNGs into one .ico file when given several inputs, picking
# up to 256×256 entries (Windows Explorer caps display there). Order
# largest-first so older Windows shells pick the high-res first.
magick \
    "$WORK/icon_256.png" \
    "$WORK/icon_128.png" \
    "$WORK/icon_64.png" \
    "$WORK/icon_32.png" \
    "$WORK/icon_16.png" \
    "$OUT_ICO"
echo "wrote $OUT_ICO ($(wc -c < "$OUT_ICO") bytes)"

# Linux: jpackage's DEB target wants a single PNG. 512×512 is what most
# desktop environments render at; they downscale as needed.
cp "$WORK/icon_512.png" "$OUT_PNG"
echo "wrote $OUT_PNG ($(wc -c < "$OUT_PNG") bytes)"
