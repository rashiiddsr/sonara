# SONARA: notes for contributors and AI agents

SONARA is a lean YouTube Music player for Android (Kotlin, Jetpack Compose, Material 3, Media3).
It is GPL-3.0 and derives from Metrolist <- InnerTune/OuterTune; the owner credits Metrolist as
its upstream (README, About). Keep the existing license headers intact.

## Build and verify

Requires JDK 21 and the Android SDK (compile SDK 37).

```bash
./gradlew :app:assembleFossDebug          # must stay green
./gradlew :app:testFossDebugUnitTest      # unit tests
./gradlew :app:lintFossDebug              # lint
./gradlew :app:assembleFossBenchmark      # R8 build used for performance work
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest   # needs a device or emulator
./gradlew :app:generateFossReleaseBaselineProfile         # regenerate the baseline profile
```

Judge performance only on the `benchmark` or `release` build type, never on debug. Record numbers
in `docs/perf.md`; never write numbers that were not measured.

## Conventions

- Commits: `type(scope): description`, e.g. `feat(player): add queue page`, `fix(download): retry failed items`.
- Strings: English in `app/src/main/res/values/`, Indonesian in `values-in/`. Other locales are
  legacy and not maintained.
- UI: use the tokens in `ui/theme/` (colors, type, shapes, spacing, motion) and the shared
  components in `ui/component/`. No magic numbers scattered across screens.
- Lazy lists: always pass `key` and `contentType`; list-item parameters must be stable.
- Scope: the app intentionally has no social/companion integrations (Discord, Last.fm, Listen
  Together, Spotify, alarms, updater). Do not re-add them.
- Experiment, requested by the owner and needing Google Play services: Google Cast
  (`playback/SonaraCast.kt`, Default Media Receiver). Keep it isolated so it can be removed.
- Experiment, requested by the owner: song recognition through Shazam's unofficial endpoint
  (`recognition/`, `com.metrolist.shazamkit`, `ui/screens/search/SongRecognitionSheet.kt`). It
  names recordings playing nearby, not sung or hummed tunes, and may stop working if Shazam
  blocks it. Keep it isolated so it can be removed; no history table, widget or tile.
- Experiment, requested by the owner: AI translator for lyrics (`lyrics/LyricsTranslator.kt`,
  `ui/player/LyricsPage.kt`, Settings > Lyrics). Off by default; when on, lyrics are translated
  as soon as they are fetched, into the chosen language (English by default). The model gets
  the whole song numbered and answers only for the lines it translated, so mixed-language songs
  and "ooh" lines are handled. The owner's key is built in, sealed with AES-GCM in
  `secrets/translator.sealed` (opened by `SealedKey.kt` in both apps), so every build has it with
  nothing to set up and no app holds a readable copy. To change it, put the new `SONARA_AI_KEY` in
  `local.properties` and run `./gradlew :app:sealTranslatorKey --no-configuration-cache`; optional
  `SONARA_AI_BASE_URL`, `SONARA_AI_MODEL`, `SONARA_AI_FALLBACK_MODELS` (an OpenAI-compatible
  endpoint, Gemini by default). Users are never asked for a key. AI only: when every model fails,
  lyrics show without a translation. Stored in `lyrics.translatedLyrics`.
- The app always speaks English, whatever the phone's language, with no setting to change it
  (owner's choice, `utils/AppLanguage.kt`). The Indonesian strings stay in `values-in/` for now.
- Sign-in is required (`WelcomeScreen`): one Google sign-in page in a sheet, inside the app, because
  YouTube Music's web API only accepts the session cookies from that flow: an OAuth token from
  the native account chooser was tested and rejected by WEB_REMIX (HTTP 400).

## Desktop app (`desktop/`): Windows, macOS, Linux

A Compose Desktop build of its own (`./gradlew -p desktop run`, `./gradlew -p desktop packageInstaller`),
kept apart so it cannot disturb the Android build. It compiles `innertube/` from source and uses
the desktop artifact of InnerTubeX. FFmpeg decodes (songs are fetched whole into a local cache,
because YouTube refuses the range requests a seek on a streamed URL makes). Sign-in opens Google's
page in a window of the default Chromium browser (Edge otherwise) on a profile of its own and reads
the cookies over the local debugging port; the session is stored encrypted with DPAPI. The AI key
is the built-in sealed one, into generated code under `build/`. `window/WindowChrome.kt` hands the
title bar's space to the app over JNA and keeps the native frame (resize, snap). Bump
`appVersion` for every installer. The Android app's lyric sources are compiled as they are
(`fromAndroid` in the build file, with small stand-ins under `src/main/kotlin/android/`). `SONARA_GUEST=1` skips sign-in for development.

The same app runs on macOS, with nothing left out; `Os.kt` says where it runs, and each Windows piece
has a Mac counterpart: the app drawn under a see-through title bar with the traffic lights kept
(`WindowChrome`), the session sealed with a key in the login keychain (`auth/MacKeychain.kt`), media
keys and Control Center through Now Playing (`window/MacNowPlaying.kt`), Quit, Settings and the Dock
icon (`window/MacApp.kt`), a launch agent for opening at login (`window/StartAtLogin.kt`), Command
for Ctrl, data in `~/Library/Application Support/Sonara`. On a Mac, `./gradlew -p desktop packageDmg`
makes the .dmg. From Windows, `stageAppJars -Psonara.natives=macosx-arm64` and `mac/make_app.py`
with a Temurin JRE put an unsigned `Sonara.app` in a .zip. GitHub Actions (`mac.yml`) builds the
.dmg for Apple Silicon and for Intel on tags `v*`.

It runs on Linux too, with nothing left out: the native title bar stays (each desktop draws its
own), media controls and keys through MPRIS on the session bus (`window/LinuxMpris.kt`, tested on a
bus of its own over TCP), the session key in the keyring through `secret-tool` or else a file only
the user can read (`auth/LinuxKeyring.kt`), sign-in through an installed Chromium browser, Snap ones
with a profile under `~/snap` (`auth/EdgeSignIn.kt`), an autostart entry, data in
`$XDG_DATA_HOME/Sonara`, and a window class equal to the menu entry `sonara-Sonara`
(`window/LinuxDesktop.kt`), so the dock shows the right icon. The tray setting only shows where there
is a tray (GNOME has none). `./gradlew -p desktop packageDeb` and `linux/make_appimage.sh` package
it; GitHub Actions (`linux.yml`) builds both on Ubuntu 22.04 and starts the app once to check it.

## Database

Never change the Room schema without a proper `Migration` (or `AutoMigration` with a spec) and an
exported schema in `app/schemas/`. `fallbackToDestructiveMigration` is forbidden. When unsure,
leave the schema alone.
