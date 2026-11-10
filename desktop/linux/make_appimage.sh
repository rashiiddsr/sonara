#!/usr/bin/env bash
# Sonara's AppImage, from the app folder `./gradlew -p desktop createDistributable` makes on Linux.
#   make_appimage.sh <app folder> <icon.png> <output .AppImage> [appimagetool]
# The menu entry and the window's class are both sonara-Sonara (window/LinuxDesktop.kt), so the
# dock shows Sonara's icon on the running window.
set -euo pipefail

app="$1"
icon="$2"
out="$3"
tool="${4:-appimagetool}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
dir="$work/Sonara.AppDir"
mkdir -p "$dir/usr"
cp -a "$app" "$dir/usr/sonara"

cat > "$dir/AppRun" <<'RUN'
#!/bin/sh
here="$(dirname "$(readlink -f "$0")")"
exec "$here/usr/sonara/bin/Sonara" "$@"
RUN
chmod +x "$dir/AppRun"

cp "$icon" "$dir/sonara-Sonara.png"
cp "$icon" "$dir/.DirIcon"
cat > "$dir/sonara-Sonara.desktop" <<'ENTRY'
[Desktop Entry]
Type=Application
Name=Sonara
GenericName=Music player
Comment=A lean YouTube Music player
Exec=Sonara
Icon=sonara-Sonara
Categories=AudioVideo;Audio;Player;
StartupWMClass=sonara-Sonara
Terminal=false
ENTRY

ARCH="${ARCH:-x86_64}" "$tool" --no-appstream "$dir" "$out"
