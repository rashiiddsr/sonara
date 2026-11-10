# SONARA design system

## References

The handoff names Abita and LNK as references, but no links or screenshots were provided (§0 still
holds the placeholder). Nothing from those apps was studied or copied. The system below follows the
handoff's §9 direction directly: modern, clean, artwork-centric, dark-first, Material 3 based.

## Identity

SONARA is named after sonar: a pulse sent into the dark that comes back with something found.
That is what the app does with music, so the whole identity is built on it.

- **Mark**: a rounded play button sending out two echo arcs, sonar teal `#2EE6C5`, on a dark
  rounded square in the app and on the night `#0A0B0D` as the launcher icon. One geometry
  (72-unit grid) is generated into every size by `scripts/sonara_mark.py`: launcher foreground and
  background, monochrome, notification, in-app mark and splash.
- **Splash** (Android 12+): the night background; the play button appears, then its two echoes go
  out one after another (`splash_pulse.xml`, about 0.7 s). The window background is the same night
  color, so there is no flash before the first frame.
- **Themes**: two fixed palettes, picked in Settings > Appearance (follows the phone by default).
  **Sonar Night** (dark): neutral near-black from `#0A0B0D`, teal `#2EE6C5` with dark text on it.
  **Sonar Day** (light): a bright aqua page `#E7F3F2`, never plain white, that deepens to teal at the
  top with a blue-teal light from the top corner (`ui/theme/Backdrop.kt`; Sonar Night gets a faint
  teal glow there). Soft-white surfaces `#F9FDFC` sit on it (chips, cards, search field), ink
  `#0A1B1A`, and the teal a shade deeper and bluer, `#007C85`, with white text on it. Screens, the
  scaffold and the top bar are transparent over the backdrop.
- **Mini player**: tinted with the cover's colour (a pastel of it by day, a deep shade at night).
- **Type**: Plus Jakarta Sans (SIL OFL 1.1, by Tokotype of Jakarta; five static weights cut from its
  variable font, licence in `assets/licenses/PlusJakartaSans-OFL.txt`). Headlines and section titles
  ExtraBold with slightly tight tracking, list titles Bold.
- **Icons**: Material Symbols Rounded at weight 500, filled; outlined only where the outline means
  something (an inactive tab, a heart not yet liked).
- **Shapes**: corners 10dp (thumbnails), 16dp (cards, mini player), 22dp (player cover).
- **Navigation**: our own tab icons (a rounded house with a slot, a heavy magnifier, a stack of
  cards with a play button), outlined at rest and filled when current; the current tab sits in a
  teal pill. The bar is see-through: the page scrolls on under it and fades into it. Switching
  tabs is instant (no fade), so no empty frame ever shows through the bar; pages opened inside a
  tab crossfade with a slight slide, with no gap between the two.
- **Player**: the cover as a rounded card with a shadow, on a glow in the cover's colour from the
  top; between the cover and the title, the line being sung, alone, as a preview (synced lyrics
  only; the full lyrics open from the Lyrics button); a teal play button that glows a little.
- **Tagline**: "Find your sound." / "Temukan suaramu."

## Principles

1. **Artwork first.** Album art is the most colorful thing on screen; chrome stays neutral so the
   art carries the color. The only brand color is the teal accent, used sparingly for the
   active state (play button, progress, active lyric line).
2. **Two themes, one brand.** Sonar Night and Sonar Day use the same roles and the same teal. Both are fixed palettes, not wallpaper-derived, so the identity stays consistent.
3. **Calm motion.** One transition spec for all screen navigation, springs without overshoot for
   anything the finger drives, no animated backgrounds.
4. **Density over decoration.** Lists are 64dp rows with 48dp art, 8pt grid spacing, no cards
   around list rows, minimal elevation (tonal surfaces instead of shadows).

## Tokens (`ui/theme/`)

| File | Content |
| --- | --- |
| `Color.kt` | `ErColors` dark and light palettes, mapped onto a Material 3 `ColorScheme`. |
| `Type.kt` | `ErTypography`: display font (BBH Bartle, bundled) for display/headline roles, system sans for title/body/label. |
| `Shape.kt` | `ErShapes`: 8dp (small: thumbnails, chips), 16dp (medium: cards, sheets, dialogs), 24dp (large: player artwork, bottom sheet top). |
| `Spacing.kt` | `Spacing`: 4 / 8 / 12 / 16 / 24 / 32 / 48dp, plus component sizes (row height, thumbnail sizes, mini player height). |
| `Motion.kt` | `Motion`: durations (short 150, medium 250, long 400ms), the navigation transition, sheet spring, artwork crossfade. |

### Color roles

| Role | Dark | Light |
| --- | --- | --- |
| background / surface | `#0E0E10` | `#FAFAFB` |
| surfaceContainerLow | `#141417` | `#F4F4F6` |
| surfaceContainer | `#1A1A1E` | `#EEEEF1` |
| surfaceContainerHigh | `#232328` | `#E6E6EA` |
| onSurface | `#F2F2F5` | `#121214` |
| onSurfaceVariant | `#A3A3AD` | `#5A5A64` |
| outlineVariant | `#2E2E34` | `#D9D9DF` |
| primary (ember) | `#FF5A3C` | `#D9441F` |
| onPrimary | `#1A0905` | `#FFFFFF` |
| error | `#FF6B6B` | `#C62828` |

### Type scale

Display font is only used at large sizes (screen titles, player title on large screens), so the
single regular weight of BBH Bartle reads as bold. Body and labels use the platform sans for
legibility at small sizes.

| Role | Font | Size / line height |
| --- | --- | --- |
| displaySmall | display | 36 / 44 |
| headlineMedium | display | 28 / 36 |
| headlineSmall | display | 24 / 32 |
| titleLarge | sans, SemiBold | 20 / 28 |
| titleMedium | sans, SemiBold | 16 / 24 |
| titleSmall | sans, Medium | 14 / 20 |
| bodyLarge / Medium / Small | sans | 16 / 14 / 12 |
| labelLarge / Medium / Small | sans, Medium | 14 / 12 / 11 |

## Navigation

- Three bottom tabs: **Home, Search, Library**, on a solid 56dp bar: icon plus a small label, the
  selected tab shown by the filled icon and full-contrast color, no pill. Settings opens from the
  top bar.
- Downloads is a chip inside Library; Home shows a Downloads shortcut when offline.
- All screen transitions use `Motion.navEnter/navExit` (fade-through with a 1/12-width slide,
  250ms in, 200ms out).

## Player

- Mini player: 64dp, floating 8dp above the nav bar, 16dp corners, artwork 48dp (8dp radius),
  title/artist, play/pause and next. A 2dp progress line sits at its bottom edge.
- Full player: artwork with 24dp corners, title/artist with a like button, a plain slider
  (`ErSlider`: thin track, small thumb that grows while dragging), primary controls, and an
  action row **Lyrics · Queue · Sleep timer**. **More** (the existing track menu: add to playlist,
  download, go to album/artist, equalizer, details) sits in the player's top bar, so the sleep
  timer, a MUST feature, is one tap away. Lyrics and queue are pages inside the player, not
  separate routes; Back returns from a page to the artwork before collapsing the sheet.
- Landscape: page area on the left, title/slider/controls on the right.
- The sheet follows the finger; release uses a critically damped spring (no overshoot). The
  drag progress is only read in `graphicsLayer {}` / `offset {}` lambdas.

### Background

1. When the track changes, the artwork is loaded at 96px on `Dispatchers.Default`, blurred once
   with a stack blur, and the accent color is extracted with `PlayerColorExtractor`.
2. The blurred bitmap is drawn full-bleed and scaled up, with a vertical scrim
   (`background` at 35% → 85% alpha) for legibility.
3. Track changes crossfade the background in `Motion.artworkCrossfade` (400ms).

No per-frame blur, no animated mesh (`AppleMeshBackground` was removed).

## States

Every content screen renders one of: **loading** (shimmer skeleton shaped like the content),
**empty** (icon, title, optional action), **error** (message + Retry), **offline** (message +
"Go to Downloads"). Shared composables live in `ui/component/ErStates.kt`.

## How the redesign reaches every screen

Rather than rewriting all ~40 screens one by one, the system is applied at three levels:

1. **Theme** (`ErMusicTheme`): palette, typography and shapes replace the old dynamic
   color scheme, so every Material component (top bars, dialogs, sheets, switches) changes.
2. **Shared components**: list/grid items (`Items.kt`), thumbnails (8dp corners), chips,
   settings groups, navigation and states are restyled with tokens. Album, Artist, Playlist,
   Library, History and Search lists are built from these.
3. **Rewritten screens**: shell (top bar + 3-tab nav), mini/full player, Queue, Lyrics page,
   Home, Library, Downloads, Settings hub, Account, Appearance, About.

Known gaps: Album/Artist/Playlist headers and the Playback/Storage/Lyrics settings pages keep
their original layouts (now themed). They are the next candidates for a dedicated pass.
