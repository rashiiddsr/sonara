# SONARA performance log

Performance is only judged on the `benchmark` build type (R8 + resource shrinking, debug-signed,
`profileable`), never on debug builds.

## Harness

| Module | Purpose |
| --- | --- |
| `:macrobenchmark` | `ErMusicBenchmarks`: startup and frame-timing journeys against `:app` `fossBenchmark`. |
| `:baselineprofile` | `BaselineProfileGenerator`: walks the same journeys to generate the Baseline Profile bundled into `:app`. |

Journeys (`Journeys.kt`, selectors are test tags exposed with `testTagsAsResourceId`):

| Test | Metric | What it does |
| --- | --- | --- |
| `coldStartToHome` | `StartupTimingMetric` (COLD) | Launch until `home_feed` is visible. |
| `homeScroll` | `FrameTimingMetric` | 3 flings down and 3 up on `home_feed`. |
| `playerOpenClose` | `FrameTimingMetric` | Starts playback via a `music.youtube.com/watch` deep link, then opens the player from `mini_player` and closes it with Back, 3 times. |
| `librarySongsScroll` | `FrameTimingMetric` | Library → Songs, 3 flings down and 3 up on `library_songs_list`. |
| `openAlbum` | `FrameTimingMetric` | Opens album `MPREb_BOEC90a78us` via deep link and goes back. |

Run:

```bash
./gradlew :macrobenchmark:connectedFossBenchmarkAndroidTest
# compare against no AOT compilation:
./gradlew :macrobenchmark:connectedFossBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.compilation=none
# regenerate the baseline profile:
./gradlew :app:generateFossReleaseBaselineProfile
```

Results land in `macrobenchmark/build/outputs/connected_android_test_additional_output/`.
Targets (§10 of the handoff): < 5% janky frames and P90 frame time ≤ 16.7ms for Home scroll,
Library Songs scroll and player open/close; cold start no worse than the baseline.

Network-dependent journeys (Home feed, playback, album) need internet on the device. The Library
Songs journey needs songs in the library (log in, or add songs to the library first).

## TASK-02 baseline (before any UI change, commit `3e05f68` + test tags)

**Frame timing and startup: not measured.** The only device is the `Medium_Phone_API_37.0`
emulator (x86_64), and it cannot start on this machine: `x86_64 emulation currently requires
hardware acceleration! ... Android Emulator hypervisor driver is not installed`. Enabling
WHPX/the hypervisor driver is a system setting I did not change. No physical device was attached
(`adb devices` empty). No numbers were invented.

To produce the baseline later, check out the TASK-02 commit, then build and run the benchmarks
with a device attached (commands above).

What was measured:

| Item | Value |
| --- | --- |
| `app-foss-benchmark.apk` (R8, universal ABIs) | 26,801,240 bytes (25.6 MiB) |
| `app-foss-debug.apk` | 50,841,092 bytes (48.5 MiB) |
| Clean `assembleFossDebug` on the dev machine (8 cores, 7.8 GB RAM) | 16 min 10 s |
| `assembleFossBenchmark` (incl. R8) | 22 min 6 s |

## TASK-08 after (commit after the redesign)

**Frame timing and startup: not measured**, for the same reason as the baseline (no device and
no emulator acceleration). The §10 targets (< 5% janky frames, P90 ≤ 16.7ms, cold start no
worse than baseline) are therefore **unverified**.

**Baseline Profile: not generated.** `:app:generateFossReleaseBaselineProfile` needs a connected
device. The plugin and `profileinstaller` are wired, so the next run on a device will produce
`app/src/fossRelease/generated/baselineProfiles/` and bundle it.

What was measured:

| Item | Before (TASK-02) | After | Change |
| --- | --- | --- | --- |
| `app-foss-benchmark.apk` (R8, universal) | 26,801,240 B (25.6 MiB) | 10,830,588 B (10.3 MiB) | −59.6% |
| `app-foss-debug.apk` | 50,841,092 B (48.5 MiB) | 36,155,619 B (34.5 MiB) | −28.9% |
| Kotlin source under `app/src/main` | ~118.6k lines | ~71k lines | removed features + screens |

The size drop comes mostly from removed dependencies (Cast/GMS, protobuf, kuromoji's
dictionary, tinypinyin) and removed features. The R8 benchmark build compiles and minifies
cleanly. Runtime R8 correctness (InnerTube, Ktor, kotlinx.serialization) is **not tested**,
because it needs a device.

### Compose compiler report (`-PenableComposeCompilerReports=true`)

Strong skipping is on. 1,860 of 2,313 composables are skippable. Every composable on the hot
paths is `restartable skippable`: `BottomSheetPlayer`, `PlayerContent`, `MiniPlayer`,
`TimeLabels`, `TransportControls`, `ErSlider`, `ProgressLine`, `ArtworkBackground`,
`QueuePage`, `SongListItem`, `MediaMetadataListItem`, `YouTubeGridItem`, `ErChip`.

The only unstable list-item parameters were InnerTube's `YTItem`/`SongItem`. These are
immutable data classes in a module without a Compose dependency. They are now declared stable
in `app/compose_stability.conf`; the effect is not yet confirmed by a fresh report, because
Gradle considered the compile task up to date.

### Structural changes that target the §10 journeys

- **Player open/close:** the sheet's drag progress is only read inside `graphicsLayer {}`. The
  player root no longer reads progress or playback position during composition. The old player
  read `state.progress` at its root (`backgroundAlpha`), recomposing the whole player every
  frame of a drag. The animated mesh background (`AppleMeshBackground`) is gone: the blur is
  computed once per track on a 96px bitmap off the main thread.
- **Playback position:** a 200ms ticker writes one `PlaybackProgress` state. It is read only in
  the slider's draw phase and the mini player's progress line. Time labels use `derivedStateOf`
  at second granularity.
- **Lyrics:** the per-line `RenderEffect` blur (one GPU layer per visible line, animated) is removed.
- **Home:** every `LazyColumn`/`LazyRow`/grid item has a `key` and `contentType`; images are
  requested at display size in the new player/mini player.

## 2026-10-31: on-device frame stats, debug build (for reference only)

The user reported stutter while scrolling Home, the queue and other lists. `dumpsys gfxinfo` for
`com.ermusic.android.debug` on the user's phone (Xiaomi HyperOS, 1220 x 2712, running at 120 Hz,
so the frame budget is 8.3 ms), after normal use by the user:

| Item | Value |
| --- | --- |
| Total frames | 32,912 |
| 50th / 90th / 95th / 99th percentile | 11 / 17 / 23 / 101 ms |
| Janky frames (legacy) | 8,584 (26.08%) |
| Slow UI thread | 109 |

This is a **debug** build (no R8, debuggable), which is known to be much slower for Compose, so it
is not a verdict on the app. The median frame already misses the 120 Hz budget. Next step: the
same measurement on the `benchmark` build. Macrobenchmark was not run: HyperOS refuses input
injection from adb (`INJECT_EVENTS`) unless "USB debugging (Security settings)" is enabled.
