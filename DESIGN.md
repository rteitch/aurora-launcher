# DESIGN.md - Aurora Launcher Visual Direction

Reading this as: native Android home launcher for everyday phone and tablet users who want calm spatial organization without ads or tracking, in a crisp atmospheric-nordic language, dial ENERGY 2 / RHYTHM 2 / MOTION 2.

## 1. Identity and Character

- **Product name**: Aurora Launcher
- **Core promise**: Fast, quiet, privacy-first home screen inspired by modern spatial clarity, built natively for Android with original assets and zero Apple trademarks.
- **Identity motif**: The **22.4% continuous superellipse (squircle)** paired with a **1 dp top-edge atmospheric hairline** (`alpha 0.35` on dark surfaces, `alpha 0.55` on light surfaces) echoing horizon light across northern skies.
- **Liveliness dials**:
  - `ENERGY 2` (Balanced): Crisp atmospheric wallpapers and one deliberate Borealis sky accent against calm obsidian and warm ivory neutrals.
  - `RHYTHM 2` (Consistent with deliberate breaks): Uniform spatial rhythm on the Home icon grid so muscle memory never breaks, contrasted with asymmetric 2x2 category clusters in App Library and tiered list groupings in Settings.
  - `MOTION 2` (Spring transitions, zero perpetual loops): Physics spring transitions (`dampingRatio = 0.8f`, `stiffness = 320f`) for opening folders, overlays, and edit mode, with jiggle active only during explicit user-triggered Edit Mode and disabled immediately when system Reduce Motion is active.

## 2. Color Palette (2 Core Neutrals + 1 Deliberate Accent)

All pairings were verified with `contrast-check.py` against WCAG AA (4.5:1 normal text, 3.0:1 non-text boundaries and focus indicators).

### Light Mode (Warm Ivory Daylight)
- `background`: `#F6F5F0` (Warm Ivory, avoids sterile pure-white glare on large launcher surfaces)
- `surface`: `#FFFFFF` (Elevated cards in App Library and Settings)
- `surfaceVariant`: `#EAE8E0` (Grouped setting rows and search field fills)
- `onBackground` / `onSurface`: `#11161F` (`16.61:1` on `#F6F5F0`, `18.13:1` on `#FFFFFF`)
- `onSurfaceSecondary`: `#434E5E` (`7.73:1` on `#F6F5F0`, `8.43:1` on `#FFFFFF`)
- `accent` (Borealis Sky): `#0B5C8A` (`6.61:1` on `#F6F5F0`, `7.21:1` with `#FFFFFF` button label)
- `outline` (3:1 control boundary): `#6E7B8B` (`3.95:1` on `#F6F5F0`)
- `error`: `#B42318` (`6.02:1` on `#F6F5F0`)
- `glassTint`: `#FFFFFF` at `alpha 0.22` + `1 dp` `#FFFFFF` top hairline (`alpha 0.55`)

### Dark Mode (Deep Obsidian Night)
- `background`: `#0C1017` (Deep Obsidian, tuned for OLED power savings and northern-sky contrast)
- `surface`: `#161D29` (Elevated cards in App Library and Settings)
- `surfaceVariant`: `#202938` (Search input fill and secondary chips)
- `onBackground` / `onSurface`: `#F2F4F8` (`17.30:1` on `#0C1017`, `15.36:1` on `#161D29`)
- `onSurfaceSecondary`: `#A8B3C4` (`8.99:1` on `#0C1017`, `7.98:1` on `#161D29`)
- `accent` (Borealis Cyan): `#5CC8FF` (`10.13:1` on `#0C1017`, `9.95:1` with `#08131E` button label)
- `outline` (3:1 control boundary): `#5B677A` (`3.33:1` on `#0C1017`)
- `error`: `#FF8A80` (`8.35:1` on `#0C1017`)
- `glassTint`: `#080B10` at `alpha 0.28` + `1 dp` `#FFFFFF` top hairline (`alpha 0.35`)

### Wallpaper Icon Label Contrast Guarantee
- Home screen icon labels (`11.5 sp`, single-line ellipsis) render `#F8FAFC` over an adaptive `#141A24` (`alpha 0.72`) micro-pill scrim (`16.68:1` contrast at full opacity, `>= 5.2:1` worst-case over `#FFFFFF` bright wallpaper spots) so labels never fail WCAG AA over bright user photos.

## 3. Typography, Spacing, and Shape Tokens

- **Typeface**: System variable `sans-serif` configured to Android `Roboto Flex` metrics (`FontFamily.SansSerif` with explicit optical weights and tight tabular spacing), keeping APK size lean while preserving legibility at `11.5 sp` icon labels and `200%` accessibility font scale.
- **Spacing scale**: `xs = 4 dp`, `sm = 8 dp`, `md = 16 dp`, `lg = 24 dp`, `xl = 32 dp`, workspace horizontal margin `20 dp`.
- **Shape radii** (deliberately varied by hierarchy, never uniform pills):
  - App icon & mini-icon: `22.4%` continuous superellipse (`SquircleShape`)
  - Folder container & overlay: `24 dp` continuous rounded rectangle
  - Dock container: `28 dp` floating bar
  - Settings & App Library cards: `18 dp`
  - Search field & primary action button: `14 dp`
  - Notification count badge: `CircleShape` (`>= 18 dp` height)

## 4. Glassmorphism Dose Cap (R-10) and Hardware Tiering

- **Dose cap**: At most 1 to 2 glass elements appear simultaneously on any screen.
  - Home Workspace: `Dock` only (1 element). Folder grid tiles use subtle solid translucent tint without backdrop blur.
  - Modal Overlays (`Folder`, `Spotlight`, `ControlPanel`): modal backdrop + active sheet (2 elements max).
  - `AppLibrary` and `Settings`: zero glass surfaces; crisp solid `surface` cards on `background` for fast 60/120 Hz scrolling.
- **Hardware tiers**:
  - `Tier A` (API 33+, non-low-RAM, battery saver off): AGSL subtle edge highlight shader + `RenderEffect` blur.
  - `Tier B` (API 31 to 32): `RenderEffect` blur + tint + hairline.
  - `Tier C` (API < 31, low-RAM device, battery saver on, or live wallpaper active): solid translucent surface (`alpha 0.90`) + hairline.

## 5. R-31 Decision Log (One-Line Reasons)

- **Why Obsidian and Warm Ivory with Borealis Sky?** Provides calm day-and-night contrast behind colorful user app icons while reserving one unmistakable focal accent (`#0B5C8A` / `#5CC8FF`) for primary actions and active indicators.
- **Why uniform Home grid + varied App Library?** Muscle memory demands fixed cell coordinates on Home, while visual scanning by category benefits from 2x2 grouped clusters in App Library.
- **Why Roboto Flex metrics over bundled custom fonts?** Variable optical legibility keeps `11.5 sp` single-line app labels sharp across densities while adding zero font-binary megabytes to the 15 MB APK budget.
- **Why varied radii (`22.4%` squircle, `28 dp` dock, `24 dp` folder, `18 dp` card, `14 dp` input)?** Distinguishes launchable apps, persistent chrome, containers, and form inputs at a glance instead of flattening everything into pills.
- **Why procedural atmospheric wallpapers?** Delivers original zero-copyright daylight and twilight backdrops that Strategy A can sample in-memory for real dock blur without bundling heavy raster PNGs.
- **Why 3-tier isometric spatial prism launcher icon?** Represents the workspace grid deck, floating glass chrome, and Borealis daylight/twilight crown inside the `22.4%` superellipse silhouette without referencing any third-party brand mark.
