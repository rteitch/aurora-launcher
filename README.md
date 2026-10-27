<div align="center">
  <img src="docs/assets/logo.png" width="112" alt="Aurora Launcher isometric prism logo" />
  <h1>Aurora Launcher</h1>
  <p>A fast, zero-telemetry native Android home launcher built with Kotlin and Jetpack Compose.</p>
  <p>
    <a href="https://github.com/rteitch/aurora-launcher/releases/download/v1.1.6/aurora-launcher-v1.1.6.apk"><strong>Download APK v1.1.6 (GitHub Release)</strong></a>
    &nbsp;&middot;&nbsp;
    <a href="releases/aurora-launcher-v1.1.6.apk"><strong>Repository APK Mirror</strong></a>
    &nbsp;&middot;&nbsp;
    <a href="DESIGN.md"><strong>Design System</strong></a>
    &nbsp;&middot;&nbsp;
    <a href="docs/architecture-rationale.md"><strong>Architecture</strong></a>
  </p>
</div>

---

## Overview

Aurora Launcher organizes installed Android apps into a calm spatial grid with continuous `22.4%` superellipse icons, adaptive glass chrome, procedural iOS-inspired wallpapers, continuous auto-reflow, unified vertical gestures, an automatic App Library, and instant local search. Every feature runs offline on the device. The merged release manifest requests zero `INTERNET` and zero `QUERY_ALL_PACKAGES` permissions (measured).

## Release Package

Download and install the signed `v1.1.6` (`versionCode = 7`) release build on any device running Android 8.0 (`API 26`) through Android 17 (`API 36+`). Both APKs are signed with a dedicated RSA-4096 release certificate using APK Signature Scheme `v2` and `v3` (measured):

| Edition | Download Asset | Size and SHA-256 (measured) | Play Protect Sideload Compatibility |
| :--- | :--- | :--- | :--- |
| **Standard (`sideload`)** | [GitHub Release (`aurora-launcher-v1.1.6.apk`)](https://github.com/rteitch/aurora-launcher/releases/download/v1.1.6/aurora-launcher-v1.1.6.apk) / [Repo Mirror](releases/aurora-launcher-v1.1.6.apk) | `2.58 MB` (`2,705,948` bytes)<br />`fff48c112125b62b98d4197f48f74fda491c1cee175bd003e4f29f1abece033e` | Recommended for browser downloads. Declares `0` `BIND_NOTIFICATION_LISTENER_SERVICE` and `0` `READ_CONTACTS` entries in manifest and `classes.dex` to pass Google Play Protect Enhanced Fraud Protection. |
| **Full (`full`)** | [GitHub Release (`aurora-launcher-v1.1.6-full.apk`)](https://github.com/rteitch/aurora-launcher/releases/download/v1.1.6/aurora-launcher-v1.1.6-full.apk) / [Repo Mirror](releases/aurora-launcher-v1.1.6-full.apk) | `2.58 MB` (`2,706,112` bytes)<br />`21ddbe8d71045c819511229a38c5546b164872405957270796fa726cf05f4aec` | Optional build adding opt-in Notification Count Badges and Contacts Search. Install via `adb install -r aurora-launcher-v1.1.6-full.apk` on regions enforcing Enhanced Fraud Protection. |

On first installation from GitHub, Android Play Protect may show a standard unrecognized-developer prompt because the open-source signing key is distributed outside Google Play. Tap **More details** and choose **Install anyway** to finish installing. Devices upgrading from debug-signed `v1.1.0` must uninstall `v1.1.0` once before installing release-signed versions.

## Application Screenshots

All screenshots below were captured from the running application on an Android 17 (`ps16k`, `16,384` byte page size) device image (measured).

### Home Workspace and Procedural Wallpapers

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/screenshots/04_home_default_active.png" width="210" alt="Home screen on Iridescent Strata wallpaper" /><br />
      <sub><strong>Home (Iridescent Strata)</strong><br />iOS-inspired spectral waves, superellipse icons, and floating dock.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/17_aura_flow_wallpaper.png" width="210" alt="Home screen on Aura Flow procedural wallpaper" /><br />
      <sub><strong>Home (Aura Flow)</strong><br />Layered chromatic fluid glass ribbons.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/13_home_light_mode.png" width="210" alt="Home screen in Warm Ivory Mist light theme" /><br />
      <sub><strong>Home (Ivory Mist)</strong><br />Daylight palette with dark status bar icons.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/05_edit_mode.png" width="210" alt="Home screen in Edit Mode with Tidy button" /><br />
      <sub><strong>Edit Mode & Tidy</strong><br />Compact 56dp top bar, Tidy button, and wallpaper cycler.</sub>
    </td>
  </tr>
</table>

### Search, App Library, and System Overlays

<table>
  <tr>
    <td align="center" width="20%">
      <img src="docs/screenshots/07_spotlight_search.png" width="175" alt="Local Spotlight Search" /><br />
      <sub><strong>Spotlight Search</strong><br />Swipe down from anywhere to open prefix & fuzzy search.</sub>
    </td>
    <td align="center" width="20%">
      <img src="docs/screenshots/08_app_library.png" width="175" alt="App Library category clusters" /><br />
      <sub><strong>App Library</strong><br />Swipe up from anywhere to open 2x2 category clusters.</sub>
    </td>
    <td align="center" width="20%">
      <img src="docs/screenshots/09_app_library_az.png" width="175" alt="App Library alphabetical index" /><br />
      <sub><strong>Alphabetical Index</strong><br />Full A to Z catalog drawer.</sub>
    </td>
    <td align="center" width="20%">
      <img src="docs/screenshots/10_control_panel.png" width="175" alt="Quick Control Panel overlay" /><br />
      <sub><strong>Control Panel</strong><br />Wi-Fi, Bluetooth, torch, and brightness shortcuts.</sub>
    </td>
    <td align="center" width="20%">
      <img src="docs/screenshots/06_widget_picker.png" width="175" alt="Native AppWidget picker with previews" /><br />
      <sub><strong>Widget Picker</strong><br />Real visual preview cards and responsive layout.</sub>
    </td>
  </tr>
</table>

### Widgets, Page Management, and Smart Glance Header

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/screenshots/19_smart_header_home.png" width="200" alt="Page 1 Smart Glance Header with clock, date and search" /><br />
      <sub><strong>Smart Glance Header</strong><br />Live clock, date, Google search, and voice mic intent.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/20_widget_picker_2col.png" width="200" alt="Full-height 92% bottom sheet widget gallery in 2 columns" /><br />
      <sub><strong>2-Column Widget Gallery</strong><br />Full 92% bottom sheet with category chips and search.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/21_page_management.png" width="200" alt="Page Management screen with thumbnail cards" /><br />
      <sub><strong>Page Management</strong><br />Thumbnail cards, visibility checkmarks, and empty page deletion.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/22_edit_mode_pages.png" width="200" alt="Edit mode with minus badges, resize controls, and pages button" /><br />
      <sub><strong>Edit Mode Controls</strong><br />Minus badges, resize modal, and Halaman button.</sub>
    </td>
  </tr>
</table>

### Page Management Expansion, Target Page Picker, and Undo/Redo

<table>
  <tr>
    <td align="center" width="33%">
      <img src="docs/screenshots/23_page_management_add_delete.png" width="200" alt="Page Management screen with Add Page card and page cards" /><br />
      <sub><strong>Page Management (Add & Safe Relocation)</strong><br />Add new blank pages, toggle visibility, and delete pages with safe app relocation.</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/24_target_page_picker.png" width="200" alt="Target page picker dialog from App Library" /><br />
      <sub><strong>Target Page Picker</strong><br />Add apps from App Library to any specific screen or a new blank page.</sub>
    </td>
    <td align="center" width="33%">
      <img src="docs/screenshots/25_undo_redo_topbar.png" width="200" alt="Edit Mode top action bar with Undo and Redo buttons" /><br />
      <sub><strong>Undo & Redo Action Bar</strong><br />One-tap undo and redo for item moves, drops, tidying, and page mutations.</sub>
    </td>
  </tr>
</table>

### Settings, Auto-Reflow, and Quick Actions

<table>
  <tr>
    <td align="center" width="25%">
      <img src="docs/screenshots/11_settings_dark.png" width="200" alt="Launcher Settings in dark mode with auto-reflow switch" /><br />
      <sub><strong>Settings (Dark)</strong><br />Wallpapers, grid sizes, and Auto-Fill toggle.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/12_settings_light.png" width="200" alt="Launcher Settings in light mode" /><br />
      <sub><strong>Settings (Light)</strong><br />Full configuration parity in daylight theme.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/16_quick_actions_menu.png" width="200" alt="Long-press Quick Actions popup" /><br />
      <sub><strong>Quick Actions</strong><br />App shortcuts, App Info, Hide, and Biometric Lock.</sub>
    </td>
    <td align="center" width="25%">
      <img src="docs/screenshots/01_onboarding_dark.png" width="200" alt="Onboarding in dark mode" /><br />
      <sub><strong>Onboarding</strong><br />Grid preset, theme, and preview-first launch.</sub>
    </td>
  </tr>
</table>

<div align="center">
  <img src="docs/screenshots/14_tablet_adaptive_home.png" width="520" alt="Adaptive 6x5 tablet workspace layout" /><br />
  <sub><strong>Adaptive Large-Screen Workspace (`>= 600 dp`)</strong><br />Expands automatically to a 6x5 desktop grid with a centered 6-slot dock.</sub>
</div>

---

## Core Capabilities

- **Cross-Page Drag & Drop with Edge Dwell Paging.** Holding a dragged icon within 48dp of the screen edge for 400ms automatically flips to the adjacent page with haptic feedback. Dwelling at the right edge of the last page for 550ms dynamically creates and flips to a brand-new page.
- **Smart Glance Header Deletion & Restoration.** The primary Page 1 Smart Glance Header includes a circular red minus button during Edit Mode to delete it cleanly. The header can be restored at any time as a first-party widget from the widget gallery.
- **Workspace Undo & Redo History.** Edit Mode features dedicated Undo and Redo toolbar buttons. Every drag-drop move, tidy reflow, widget resize, and page mutation can be stepped backward or forward with atomic workspace snapshots.
- **Page Management Expansion (Add Blank Page & Safe Relocation).** Tapping page indicator dots or the toolbar button reveals full page management with a "+ Tambah Halaman" (Add Page) card. Deleting a non-empty page safely relocates all its applications to remaining pages without data loss.
- **Flexible Target Page Picker.** Adding apps to the home screen from App Library presents an intuitive target page selector allowing direct placement onto any existing page or immediate creation of a new blank page.
- **Zero-Lag GPU Wallpaper Rendering.** Isolates `ProceduralWallpaperCanvas` behind `.graphicsLayer()` with cached vector `Path` objects, eliminating swipe stutter and rendering page transitions at a solid 60/120 fps.
- **Smart Glance Header on Page 1.** Seated dynamically on row 0 of Page 1 with a live digital clock, localized date format, and Google search bar pill with voice mic intent launcher. Automatically allocates space above application icons.
- **Full-Height 2-Column Widget Gallery.** Opens in a full 92% modal bottom sheet with rounded drag handle, category filter chips, search query input, and responsive 2-column preview cards.
- **Universal Widget Resize & Borderless Squircles.** Eliminates neon widget border frames for authentic iOS squircle aesthetics. Adds Edit Mode circular minus badges and a dedicated resize modal supporting standard presets (`2x2`, `4x1`, `4x2`, `4x3`, `4x4`) and dimension steppers.
- **Page Management (Edit Pages).** Tapping page indicator dots or the toolbar button in Edit Mode reveals a 2-column thumbnail card overview of all workspace pages with visibility checkmark toggles and empty page deletion.
- **Unified Vertical Gestures.** Swipe down from any workspace area to open Spotlight Search. Swipe up from anywhere to access App Library. Both use a responsive 32dp gesture threshold.
- **Procedural Zero-Bitmap Wallpapers.** Renders GPU-accelerated procedural backgrounds including `AURA_FLOW` (chromatic fluid glass ribbons) and `IRIDESCENT_STRATA` (spectral waves) without static image assets.
- **Continuous Auto-Reflow & Smart Drag Displacement.** Dropping an item onto an occupied slot swaps and displaces items smoothly. The "Rapikan" (Tidy) button and "Auto-Fill Empty Spaces" preference toggle eliminate gaps across all pages in row-major order.
- **120fps Drag Fluidity.** Direct bounding-box tests eliminate per-frame 2D array allocations during drag gestures, preventing micro-stutters.
- **Spatial Home Grid and Dock.** Supports `4x5`, `4x6`, and `5x6` phone grids plus an automatic `6x5` tablet grid with multi-page horizontal paging and a pinned floating dock.
- **Superellipse Icon Pipeline.** Normalizes adaptive, legacy, monochrome, and third-party ADW/Nova `appfilter.xml` icon packs into a uniform `22.4%` continuous superellipse mask.
- **Three-Tier Adaptive Glass.** Selects real `RenderEffect` blur (`API 31+`), downsampled cached backdrop blur (`API 28` to `30`), or low-RAM translucent surfaces automatically based on device capability and battery state.
- **Folders and Native AppWidgets with Visual Previews.** Supports drag-and-drop folder creation, inline folder renaming, `SafeAppWidgetHostView` crash containment, real visual preview bitmaps in the picker, and widget span resizing.
- **Local Spotlight Search.** Combines prefix/fuzzy app matching, launch-frequency weighting, static and dynamic `LauncherApps` shortcuts, deterministic arithmetic evaluation, and unit conversion with zero network calls.
- **Automatic App Library.** Categorizes installed apps across all active user profiles (`Personal`, `Work`, and `Private Space`) using `ApplicationInfo.category` and curated package heuristics.
- **Biometric App Lock and Hidden Apps.** Protects hidden or locked apps behind `BiometricPrompt` (`BIOMETRIC_STRONG` or device credential) before launching activities or revealing search results.
- **Encrypted Layout Snapshots.** Exports and restores `.aurorabackup` archives encrypted with `AES-256-GCM` (`PBKDF2WithHmacSHA256` passphrase derivation) via the Storage Access Framework.

## Privacy and Permissions

Aurora Launcher enforces strict permission boundaries across [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) and [`app/src/full/AndroidManifest.xml`](app/src/full/AndroidManifest.xml):

- **No Network Access.** Both editions declare zero `android.permission.INTERNET` and zero `ACCESS_NETWORK_STATE` permissions (measured).
- **No Broad Package Visibility Permission.** Launcher catalog discovery uses the standard `<queries>` `ACTION_MAIN` + `CATEGORY_LAUNCHER` intent filter and `LauncherApps` APIs instead of `QUERY_ALL_PACKAGES` (measured).
- **Play-Protect-Safe Default Manifest.** The default `sideload` edition declares zero `BIND_NOTIFICATION_LISTENER_SERVICE`, zero `BIND_ACCESSIBILITY_SERVICE`, zero SMS permissions, and zero `READ_CONTACTS` permissions, and R8 strips `AuroraNotificationListenerService` from `classes.dex` (measured via [`scripts/verify-play-protect-compliance.ps1`](scripts/verify-play-protect-compliance.ps1)).
- **Opt-In Full Edition Listeners.** In the optional `full` edition, notification count badges (`NotificationListenerService`) and contact search (`READ_CONTACTS`) stay disabled until explicitly granted by the user in Android settings, and never read or persist notification message text.
- **Sanitized Cloud Backup.** Android Auto Backup includes only a sanitized workspace coordinate file and excludes SQLite usage history, search history, and lock metadata (measured).

## Multi-Module Architecture

The project is organized into 14 Gradle modules under a Shell-Orchestrated Slot Architecture where `:feature:*` modules never depend on one another:

```text
aurora-launcher/
├── app/                  # Single-Activity Compose shell, AppWidgetHost, BiometricPrompt gate
├── core/
│   ├── common/           # AuroraDispatchers, SystemResult, safeSystemCall IPC wrapper
│   ├── model/            # Pure Kotlin domain models, Squircle math, search/math engines
│   ├── designsystem/     # Obsidian & Warm Ivory tokens, SquircleShape, AuroraIsometricLogo
│   ├── data/             # Room v1 database (exported schema 1.json) & Proto/Prefs DataStore
│   ├── platform/         # LauncherApps, WidgetHost, Biometric, RoleManager, Torch, IconPack
│   └── testing/          # Deterministic fakes and coroutine test rules
├── feature/
│   ├── onboarding/       # Preview-first onboarding & RoleManager.ROLE_HOME handoff
│   ├── home/             # Paged grid, Dock, Edit Mode drag controller, Quick Actions
│   ├── folder/           # Paged folder sheet & inline rename
│   ├── spotlight/        # Local Spotlight search, shortcuts, math & unit converter
│   ├── applibrary/       # 2x2 category clusters & alphabetical drawer
│   ├── widgets/          # Native AppWidget picker & span calculator
│   ├── controlpanel/     # Connectivity, torch, audio, and brightness sheet
│   └── settings/         # Appearance, glass tier, gestures, privacy, and AES-GCM backup
├── benchmark/            # Jetpack Macrobenchmark startup & scroll suite
└── baselineprofile/      # Baseline Profile generator module
```

## Measured Runtime Metrics

All figures below were measured on an Android 17 (`ps16k`, `16,384` byte page size) `x86_64` emulator configured with `2,048 MB` RAM:

| Metric | Target Budget | Measured Value | Status |
| :--- | :--- | :--- | :--- |
| Minified Release APK Size | `<= 15.0 MB` | `2.56 MB` (`2,689,564` bytes `sideload`, `2,689,728` bytes `full`) | Pass |
| Cold Start (`am start -W`, median of 5) | `< 700 ms` | `672 ms` | Pass |
| Warm Resume (`KEYCODE_HOME`, median of 5) | `< 150 ms` | `98.5 ms` | Pass |
| Idle Resident Memory (`dumpsys meminfo` Total PSS) | `< 110 MB` | `52.13 MB` | Pass |
| Emulated GPU Frame Time (`dumpsys gfxinfo`) | `< 16.6 ms` physical target | `p50 = 18 ms`, `p95 = 32 ms` (emulated 2 GB AVD) | Hardware Macrobenchmark module included |
| `INTERNET` / `QUERY_ALL_PACKAGES` in Merged Manifest | `0` | `0` | Pass |

## Building from Source

Prerequisites:

- JDK 21 (for example Android Studio bundled JBR).
- Android SDK Platform `37` (`compileSdk = 37`, `targetSdk = 36`, `minSdk = 26`).

Run unit tests, Android Lint, and build both minified release editions from the repository root:

```bash
./gradlew testSideloadDebugUnitTest testFullDebugUnitTest :core:model:test lintSideloadDebug lintFullDebug assembleSideloadRelease assembleFullRelease
```

The release packages are written to `app/build/outputs/apk/sideload/release/app-sideload-release.apk` and `app/build/outputs/apk/full/release/app-full-release.apk`. Verify binary manifest, DEX, and signature compliance with [`scripts/verify-play-protect-compliance.ps1`](scripts/verify-play-protect-compliance.ps1).

## License

Copyright 2026 Aurora Launcher Contributors. Released for open-source distribution and inspection in this repository.
