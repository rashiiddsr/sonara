# SONARA (C) 2026, derived from Metrolist
# Licensed under GPL-3.0 | See git history for contributors
"""
Puts Sonara.app together away from a Mac, as a .zip a Mac opens as it is.

On a Mac, `./gradlew -p desktop packageDmg` makes the proper .dmg; jpackage can only package for
the system it runs on. This does by hand what it does: the app's jars (from
`./gradlew -p desktop stageAppJars -Psonara.natives=macosx-arm64`), a Mac Java runtime (a Temurin
JRE .tar.gz for the same architecture), the icon and an Info.plist, in the usual layout:

    Sonara.app/Contents/{Info.plist, PkgInfo, MacOS/Sonara, app/*.jar, runtime/Contents/Home, Resources/Sonara.icns}

The runtime goes from the archive straight into the zip, so its executable bits and symbolic links
survive Windows. The app is not signed: the first time, macOS asks for it to be allowed.

    python make_app.py <jars dir> <jre.tar.gz> <icon.icns> <version> <out.zip>
"""

import sys
import tarfile
import time
import zipfile

APP = "Sonara.app/Contents/"
BUNDLE_ID = "app.sonara.desktop"
MAIN_CLASS = "com.ermusic.desktop.MainKt"
STAMP = time.localtime()[:6]

LAUNCHER = """#!/bin/sh
# Starts Sonara on the Java runtime inside the app.
CONTENTS="$(cd "$(dirname "$0")/.." && pwd)"
exec "$CONTENTS/runtime/Contents/Home/bin/java" \\
  -Djpackage.app-version={version} -Dcompose.application.configure.swing.globals=true \\
  -Xmx512m -Dfile.encoding=UTF-8 \\
  -Dapple.awt.application.appearance=system -Dapple.awt.application.name=Sonara \\
  -Xdock:name=Sonara -Xdock:icon="$CONTENTS/Resources/Sonara.icns" \\
  -Djpackage.app-path="$CONTENTS/MacOS/Sonara" \\
  -cp "$CONTENTS/app/*" {main} "$@"
"""

PLIST = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key><string>en</string>
    <key>CFBundleExecutable</key><string>Sonara</string>
    <key>CFBundleIconFile</key><string>Sonara.icns</string>
    <key>CFBundleIdentifier</key><string>{bundle_id}</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>CFBundleName</key><string>Sonara</string>
    <key>CFBundleDisplayName</key><string>Sonara</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>{version}</string>
    <key>CFBundleVersion</key><string>{version}</string>
    <key>LSApplicationCategoryType</key><string>public.app-category.music</string>
    <key>LSMinimumSystemVersion</key><string>12.0</string>
    <key>NSHighResolutionCapable</key><true/>
    <key>NSSupportsAutomaticGraphicsSwitching</key><true/>
</dict>
</plist>
"""


def entry(name, mode, kind=0o100000):
    info = zipfile.ZipInfo(name, STAMP)
    info.create_system = 3  # Unix, so the Mac reads the mode below
    info.external_attr = ((kind | mode) & 0xFFFF) << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    return info


def add_runtime(out, jre):
    count = 0
    with tarfile.open(jre, "r:gz") as tar:
        for member in tar:
            parts = member.name.split("/", 2)
            # jdk-…-jre/Contents/<rest>
            if len(parts) < 3 or parts[1] != "Contents":
                continue
            name = APP + "runtime/Contents/" + parts[2]
            if member.isdir():
                out.writestr(entry(name.rstrip("/") + "/", member.mode or 0o755, 0o040000), b"")
            elif member.issym():
                out.writestr(entry(name, 0o755, 0o120000), member.linkname.encode())
            elif member.islnk():
                target = tar.extractfile(tar.getmember(member.linkname))
                out.writestr(entry(name, member.mode or 0o644), target.read())
            elif member.isfile():
                out.writestr(entry(name, member.mode or 0o644), tar.extractfile(member).read())
            count += 1
    return count


def main(jars_dir, jre, icon, version, target):
    import os

    jars = sorted(f for f in os.listdir(jars_dir) if f.endswith(".jar"))
    assert jars, "no jars in " + jars_dir
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as out:
        out.writestr(entry(APP + "Info.plist", 0o644), PLIST.format(bundle_id=BUNDLE_ID, version=version))
        out.writestr(entry(APP + "PkgInfo", 0o644), "APPL????")
        out.writestr(entry(APP + "MacOS/Sonara", 0o755), LAUNCHER.replace("{main}", MAIN_CLASS).replace("{version}", version))
        with open(icon, "rb") as source:
            out.writestr(entry(APP + "Resources/Sonara.icns", 0o644), source.read())
        for jar in jars:
            with open(os.path.join(jars_dir, jar), "rb") as source:
                out.writestr(entry(APP + "app/" + jar, 0o644), source.read())
        runtime = add_runtime(out, jre)
    print(f"{target}: {len(jars)} jars, {runtime} runtime entries")


if __name__ == "__main__":
    if len(sys.argv) != 6:
        sys.exit(__doc__)
    main(*sys.argv[1:])
