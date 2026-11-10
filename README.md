# Sonara

**Find your sound.** A lean YouTube Music player for Android, Windows and macOS.

Sonara signs in with your Google account and plays your YouTube Music: your library, playlists,
history and recommendations, kept in sync with the phone and the web. No ads, no clutter.

## Download

Get the latest version from [Releases](../../releases/latest):

| System | File | Notes |
|---|---|---|
| Android 8.0 or newer | `Sonara-<version>.apk` | Allow installing apps from your browser or file manager when asked. |
| Windows 10 / 11 (64-bit) | `Sonara-<version>.msi` | If SmartScreen warns, choose **More info → Run anyway**. Installs for your user only; a newer version replaces the old one. |
| macOS 12 or newer, Apple Silicon (M1 and later) | `Sonara-<version>-mac-arm64.dmg` | Drag Sonara to Applications. The app is not signed by Apple: the first time, open it, then go to **System Settings → Privacy & Security → Open Anyway**. |

If macOS still refuses ("damaged" or "can't be opened"), run this once in Terminal:

```bash
sudo xattr -dr com.apple.quarantine /Applications/Sonara.app
```

## What it does

**Everywhere**

- Google sign-in; your library, liked songs, playlists and listening history stay in sync with YouTube Music
- Search with suggestions, Home, Browse, album, artist and playlist pages
- Lyrics from eight sources, word by word where the source has it
- AI lyric translator (optional, off by default)
- Downloads for offline listening, a queue, radio, sleep timer, normalized volume and mono audio
- Playlist editing, collaboration and import

**Android** — Google Cast, song recognition, background playback with the system media controls.

**Windows and macOS** — a full-screen player with a turning record and lyrics, mini player,
equalizer, crossfade, media keys, keyboard shortcuts (`Ctrl`/`⌘` + `K` to search), keep playing
from the tray or menu bar, and opening at sign-in.

## AI lyric translator: where the key goes

The translator uses an OpenAI-compatible endpoint (Gemini by default). The key is built into the
apps when they are compiled; users are never asked for one. Without a key, everything else works
and lyrics simply show untranslated.

Put it in `local.properties` at the root of the project (create the file if it isn't there):

```properties
SONARA_AI_KEY=your-api-key
# Optional:
# SONARA_AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai/
# SONARA_AI_MODEL=gemini-flash-lite-latest
# SONARA_AI_FALLBACK_MODELS=model-a,model-b
```

`local.properties` is ignored by git: never commit it. For builds made by GitHub Actions, add the
same key as a repository secret named `SONARA_AI_KEY` (**Settings → Secrets and variables →
Actions → New repository secret**).

## Build

You need JDK 21. The Android app also needs the Android SDK (compile SDK 37).

**Android**

```bash
./gradlew :app:assembleFossDebug        # debug APK
./gradlew :app:assembleFossRelease      # release APK, in app/build/outputs/apk/foss/release/
./gradlew :app:testFossDebugUnitTest :app:lintFossDebug
```

**Windows** (on Windows)

```bash
./gradlew -p desktop run                # try it
./gradlew -p desktop packageInstaller   # desktop/build/compose/binaries/main/msi/Sonara-<version>.msi
./gradlew -p desktop test               # UI tests, headless
```

**macOS** (on a Mac)

```bash
./gradlew -p desktop run
./gradlew -p desktop packageDmg         # desktop/build/compose/binaries/main/dmg/Sonara-<version>.dmg
```

No Mac? The **macOS app** workflow builds the .dmg on GitHub's Macs: open the **Actions** tab,
choose **macOS app → Run workflow**, and download the .dmg from the finished run. It also runs by
itself for every tag that starts with `v`.

Notes for contributors are in [AGENTS.md](AGENTS.md).

## License and credits

Sonara is free software under the [GNU General Public License v3.0](LICENSE).

Sonara is based on [Metrolist](https://github.com/MetrolistGroup/Metrolist). Copyright notices
and license headers are kept in the source files. This does not imply affiliation with or
endorsement by Metrolist.

Sonara is not affiliated with YouTube, YouTube Music or Google. Playback relies on YouTube's
unofficial API and can stop working when YouTube changes it.
