# Windfall — Random Gallery

A native Android gallery for **random discovery** of your own photos and videos. Pick the folders you care about, shuffle through media, favourite what you love, run slideshows, and optionally play several videos at once.

Built with **Kotlin**, **Jetpack Compose**, and **Material 3**. Runs on **Android 11+** (API 30); compiled and targeted for **API 36** (Android 16).

| | |
|---|---|
| **Author** | Sandeep Kiran (Mousy) |
| **Package** | `com.mousy.windfall` |
| **Min SDK** | 30 (Android 11) |
| **Target / Compile SDK** | 36 (Android 16) |
| **UI** | Jetpack Compose + Material 3 |
| **License** | [MIT](LICENSE) |

> **Design source of truth:** the interactive wireframe in [`Design_Files/`](Design_Files/) defines screens, navigation, gestures, settings, and visual tokens. This app ports that spec to native Android.

---

## Table of contents

1. [Screenshots](#screenshots)
2. [Features](#features)
3. [Quick start (PowerShell)](#quick-start-powershell)
4. [Build from source](#build-from-source)
5. [First-run guide](#first-run-guide)
6. [Architecture](#architecture)
7. [Performance on large libraries](#performance-on-large-libraries)
8. [Android 16 vs 11–15](#android-16-vs-1115)
9. [Permissions & privacy](#permissions--privacy)
10. [DEVICE-ONLY features](#device-only-features)
11. [Design files](#design-files)
12. [Project structure](#project-structure)
13. [Troubleshooting](#troubleshooting)
14. [Publishing to GitHub](#publishing-to-github)
15. [Contributing](#contributing)
16. [License & author](#license--author)

---

## Screenshots

| Gallery | Favourites | Slideshow | Settings |
|---------|------------|-----------|----------|
| _Add device screenshots here_ | _Add device screenshots here_ | _Add device screenshots here_ | _Add device screenshots here_ |

Tip: capture on a real phone after granting folder access — empty states are intentional until you pick sources.

---

## Features

### Tabs (bottom navigation)

Bottom nav is a compact **icon-only** bar (48dp) that overlays the content, so almost all of the
screen goes to thumbnails.

| Tab | Description | Default |
|-----|-------------|---------|
| **Gallery** | Random shuffled grid of your whole library; the order holds still until you shuffle | Always on; app opens here |
| **Favourites** | Favourited items with type + time filters | On |
| **Recent** | Recently added files (7–365 day windows) | On |
| **Slideshow** | Opens fullscreen viewer / autoplay | On |
| **Videos (Multi-Video)** | Play 1 / 2 / 4 videos or audio files at once | Off (enable in Settings) |
| **Albums** | Browse selected folders as albums | Off (enable in Settings) |
| **More** | Settings, appearance, storage tools | Always on |

Default order: Favourites → Recent → Gallery → Slideshow → More. Tab order and visibility are configurable in **More → Tabs & Layout** (Gallery and More cannot be hidden).

The swipe/scroll grid style is chosen on the Gallery tab and applies to Favourites and Recent too,
so swiping re-deals those tabs as well. In scroll mode Recent stays newest-first; in swipe mode it
becomes a random set drawn from its date window.

### Gestures (from wireframe)

| Gesture | Where | Action |
|---------|-------|--------|
| Double-tap | Grid tile | Toggle favourite |
| Long-press (~2.5s) | Grid tile | Enter multi-select |
| Swipe L/R or U/D | Gallery (swipe mode) | New random set (up/left) or history back (down/right) |
| Pinch | Grid | Change columns (1–6), continuously — one pinch can cross several steps |
| Tap | Fullscreen viewer / slideshow | Toggle chrome (top bar **and** bottom tabs) |
| Swipe L/R | Viewer | Previous / next item |
| Swipe up | Viewer photos | Delete after confirmation (if safety toggles allow) |

### Appearance

- Light / dark mode
- **AMOLED black** surfaces in dark mode
- Six accent palettes: **Sand** (default), **Rose**, **Lavender**, **Mint**, **Peach**, **Sky**
- Material You **dynamic color** on Android 12+ (blended with accent)
- Status- and navigation-bar icons follow the app theme, not the system one

### Playback & safety

- Slideshow speeds: 1s → 5min, Custom, or Off (videos always play full duration)
- Don’t loop at end of list
- Disable swipe-up-to-delete, or disable every delete action app-wide
- Haptic feedback for confirmed actions and padded/edge-to-edge grid tiles
- Viewer media controls are separate from slideshow playback; video and audio autoplay when opened
- Fullscreen is genuinely fullscreen: the status and navigation bars hide while the viewer is open (swipe from an edge to reveal them)
- Rotating the phone **keeps video playing** at the same position, in both the viewer and Multi-Video
- Audio files show embedded album art in the viewer, with a music-note card when a track has none

### Storage & data

- Favourites are mixed into the normal Gallery at roughly a **1-in-25** chance per tile, so the
  things you liked resurface far more often than their share of the library would give them
- **Favourites from all folders** toggle (Settings, and the folder icon on the Favourites tab)
  shows favourited media even when its folder isn't a selected source
- Source folders via **SAF** (Storage Access Framework) + MediaStore discovery
- File-type filters detected from selected folders, with cached per-extension counts and an
  on-demand **Refresh counts** action (labelled with the time of the last scan). Counting reads
  the MediaStore **Files** collection so stray non-media extensions show up too — note that
  scoped storage only exposes non-media files inside folders you granted via SAF
- Optional favourites folder sync (copy on favourite / remove copy on unfavourite)
- Hidden folders dialog
- Export / import settings
- Reset all settings while keeping favourites, and share a captured crash log
- Download favourites as **`.zip`** (copies originals — never moves them)

---

## Quick start (PowerShell)

You must **rebuild after code changes**. Editing Kotlin/Compose/resources does nothing on the phone until you run `assembleDebug` again and reinstall (or re-run from Android Studio).

### Prerequisites

- JDK 17+ (Android Studio’s embedded JBR is fine)
- Android SDK with Platform **API 36**, Build-Tools, and **Platform-Tools** (`adb`)
- Phone or emulator on **Android 11+** with **USB debugging** enabled

### Build and install (from project root)

In **Windows PowerShell**:

```powershell
cd Windfall-Random-Gallery
.\gradlew.bat assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

**PowerShell note:** use `.\gradlew.bat` (with `.\`). Bare `gradlew.bat` is rejected because the current directory is not on PowerShell’s command search path.

**APK output:**

```text
app\build\outputs\apk\debug\app-debug.apk
```

### Optional: confirm the device

```powershell
adb devices
```

You should see your phone listed as `device` (not `unauthorized`). If `adb` is not recognized, it lives under the Android SDK **platform-tools** folder, for example:

```text
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
```

Add that folder to your PATH, or call `adb.exe` with the full path.

### After install

Open **Windfall** → allow media access when prompted → go to **More** → **Add folder (SAF)** and pick at least one folder.

> Debug APKs use the Android debug keystore. For a signed release build, see [Release APK](#release-apk-signed).

### Launcher icon not updating?

Icon / adaptive-icon changes sometimes stick after a plain reinstall. Uninstall the old app from the phone, then install again (`adb install` without relying on a stale launcher cache), or use `adb uninstall com.mousy.windfall` before `adb install`.

---

## Build from source

### Prerequisites

| Tool | Notes |
|------|--------|
| **Android Studio** | Ladybug (2024.2+) or newer recommended |
| **JDK** | 17 or newer (21 works; project `jvmTarget` is 17) |
| **Android SDK** | Platform **API 36**, Build-Tools 36.x, Platform-Tools |
| **Device / emulator** | API **30+** (Android 11+) |

Create `local.properties` in the project root (gitignored):

```properties
sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

### Android Studio

1. **File → Open** the `Windfall-Random-Gallery` folder
2. Wait for Gradle sync
3. Select a device / emulator (API 30+)
4. Run **app**

Studio rebuilds and installs for you. From the terminal, remember: **rebuild after every code change** before expecting the phone to show updates.

### Command line (PowerShell)

From the project root:

```powershell
.\gradlew.bat assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Clean rebuild:

```powershell
.\gradlew.bat clean assembleDebug
```

**macOS / Linux**

```bash
chmod +x gradlew
./gradlew assembleDebug
```

### Release APK (signed)

1. Create a keystore (once):

```powershell
keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias windfall
```

2. Add signing config to `app/build.gradle.kts` (or use Android Studio **Build → Generate Signed Bundle / APK**). Prefer storing passwords in `keystore.properties` (gitignored) — **never commit keystores or passwords**.

3. Build:

```powershell
.\gradlew.bat assembleRelease
```

Output: `app\build\outputs\apk\release\app-release.apk`

### CI

GitHub Actions workflow: [`.github/workflows/android.yml`](.github/workflows/android.yml) runs the unit tests and `assembleDebug` on push/PR. Run the tests locally with:

```powershell
.\gradlew.bat testDebugUnitTest
```

Release builds run **R8** (`isMinifyEnabled` + `isShrinkResources`), which takes the APK from
roughly 70 MB debug to about 8 MB. Lint is clean:

```powershell
.\gradlew.bat :app:lintDebug
```

---

## First-run guide

1. **Grant media permission** when Android asks (full or limited — both work; SAF folders also work independently).
2. Open **More** (settings).
3. Tap **Add folder (SAF)** and select folders that contain photos/videos (e.g. `DCIM`, `Pictures`, `Download`).
4. Optionally enable **Multi-Video** / **Album** tabs under **Tabs & Layout**.
5. Return to **Gallery** — media appears in a shuffled grid.
6. **Double-tap** to favourite · **long-press** to multi-select · **pinch** to change density.

If the grid is empty: no folders selected, or no matching file types — adjust **Source Folders** and **File Types** in Settings.

---

## Architecture

```text
SettingsRepository (DataStore)      MediaRepository (MediaStore + SAF)
        │                                    │
        └──────────────┬─────────────────────┘
                       ▼
              GalleryViewModel
                       │
        ┌──────────────┴───────────────┐
        ▼                              ▼
  libraryState                   viewer / shell / transient
  (filters, sampling, sorting)   (tabs, chrome, menus, dialogs)
  on Dispatchers.Default         cheap, main thread
        └──────────────┬───────────────┘
                       ▼
                 GalleryUiState
                       ▼
   Compose UI (tabs, grids, viewer, dialogs)
```

The ViewModel deliberately keeps its state in **two halves**. `libraryState` holds everything
derived from the media library and only recomputes when the library, the filters, or the random
draw change — off the main thread. The UI-toggle flows (viewer index, chrome, menus, selection,
snackbars) are grouped into three small state objects that combine into `GalleryUiState` with
nothing more than object construction. Tapping a button therefore never re-filters the library.

| Layer | Responsibility |
|-------|----------------|
| **UI** | Compose screens matching wireframe layouts & Material 3 |
| **ViewModel** | Port of wireframe state machine (tabs, gestures, slideshow, multi-video) |
| **Preferences** | Persistent settings via DataStore |
| **Media** | DEVICE-ONLY scanning, playback, copy/zip |
| **Version gate** | API 30–36 branching for permissions & privacy APIs |

**Stack:** Compose · Material 3 · ViewModel · DataStore · Media3 1.10 (`ContentFrame`; multi-video uses per-cell `LifecycleStartEffect` — `PlayerPool` not in published 1.10.1 AARs yet) · Coil 3.5 · DocumentFile / SAF

Tabs use ViewModel + `AnimatedContent` (navigation-compose removed as unused). Immersive viewer overlays the bottom bar so media does not re-layout when chrome fades.

### Baseline Profiles

Checked-in rules live in `app/src/main/baseline-prof.txt` (installed via ProfileInstaller). The `:baselineprofile` Macrobenchmark module can regenerate them on a device/emulator:

```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile
```

CI runs `assembleDebug` only and does not require an emulator for profile generation.

---

## Performance on large libraries

The app is built to stay responsive with folders holding **5,000–10,000+ files**. Four ideas do
most of the work.

### 1. One seeded deal, held still

The Gallery deals the **whole** filtered library once per shuffle from a **single `Long` seed**
(a seeded Fisher–Yates; dealing 10,000 items takes well under a millisecond). A page swipe only
moves a cursor through that order, so swiping back always shows exactly the page you saw.

The deal is then **held still** by `ShuffleDeck`, because the library changes under you: the
cold-start scan finds today's photos, Undo brings a hidden file back, a folder or file type gets
switched on. A plain seeded shuffle re-deals *every* position when even one item is added or
removed, which made swipe-back show pages you'd never seen. Instead:

- items that leave the library are skipped but keep their place, so Undo puts them back where
  they were;
- new items are slotted in at random positions **after the furthest point you've looked at**, so
  nothing already seen moves.

Only the Shuffle button (or re-tapping Gallery) and a fresh app launch change the order.
Favourites and Recent (in swipe mode) are held still the same way. `app/src/test` has the rules
as unit tests.

The seed of the *next launch* is chosen during this session, which is what lets its first page be
decoded in advance, so a cold start lands on thumbnails rather than an empty grid.

### 2. Nothing heavy on the main thread

List derivation runs on `Dispatchers.Default` and is decoupled from UI toggles (see
[Architecture](#architecture)). Scans cooperate with cancellation, so changing folders mid-scan
stops the old one instead of racing it.

### 3. Cheap scans

| Work | Approach |
|------|----------|
| Folder counts for Settings | Two-column cursor tallied into a map — no `MediaItem` per file |
| Extension counts | Same, and moved off the gallery load path entirely |
| SAF tree walk | One cursor per directory instead of a separate query per file attribute |
| Viewer images | Decoded at 1.5× screen size (never `Size.ORIGINAL`), neighbours prefetched, grid thumbnail reused as an instant placeholder |
| Item keys / timestamps | Precomputed once per item rather than rebuilt on every read |
| Folder matching | Selections pre-normalised and lowercased once per scan, not once per row |
| Folder discovery | Only re-walked when the hidden-folder rules change, not when you tick a source |

### 4. Sizing the thumbnail cache

Coil's memory cache gets **3% of the phone's RAM** (at least 45% of the app's heap limit, at most
512 MB; phones under 3 GB keep 25% of the heap limit). Bitmaps live in native memory, not on the
Java heap, so a heap-based share was too small on phones with a 192 MB heap limit: about 60 tiles,
and scrolling back a few screens refetched them all. Coil empties the cache when the app goes to
the background. Each neighbouring random set prefetches only about a screenful: prefetching too
eagerly evicts the pages you're about to swipe back to.

Grid thumbnails come from Android's own thumbnail cache (`MediaStoreThumbFetcher`). Android hands
them back at whatever size it likes (often with the short side *below* the tile), and Coil
refuses to reuse a cached image that looks too small, so every tile that scrolled back into view
was fetched again. The fetcher now rescales each one to exactly cover its tile before it is
cached. That makes it reusable, and 2–3× smaller, so the cache holds far more. Tiles and prefetch
build the very same request (`gridThumbRequest`), a tile that scrolls away cancels its thumbnail
call instead of letting it finish, and scroll mode only looks ahead once the list comes to rest.

Debug builds attach Coil's `DebugLogger`, so the split is measurable:

```powershell
adb logcat -d | Select-String "RealImageLoader:.*Successful"
```

Visible tiles should report `MEMORY_CACHE`; `DISK` lines are the background prefetch.

---

## Android 16 vs 11–15

The app **targets Android 16 (API 36)** and uses newer privacy APIs when available. On older devices it switches to compatible paths.

| Concern | Android 11–12 (30–32) | Android 13–15 (33–35) | Android 16+ (36) |
|---------|----------------------|------------------------|------------------|
| Storage permission | `READ_EXTERNAL_STORAGE` | `READ_MEDIA_IMAGES` / `VIDEO` (+ audio on 13) | Granular media + `READ_MEDIA_VISUAL_USER_SELECTED` |
| Partial photo access | N/A | API 34+ partial / user-selected | Preferred privacy path + Photo Picker |
| Dynamic color | API 31+ | Yes | Yes |
| Predictive back | Legacy | API 34+ | Ready |
| Folder picking | SAF `OpenDocumentTree` | Same | Same |
| Video | Media3 ExoPlayer | Same | Same |

Implementation: [`AndroidVersionGate.kt`](app/src/main/java/com/mousy/windfall/util/AndroidVersionGate.kt) and [`MediaPermissions.kt`](app/src/main/java/com/mousy/windfall/data/media/MediaPermissions.kt).

You do **not** need Android 16 on your phone — **Android 11 is enough**. API 36 is the compile/target baseline so modern privacy features activate when present.

---

## Permissions & privacy

| Permission | Max / when | Why |
|------------|------------|-----|
| `READ_EXTERNAL_STORAGE` | maxSdk 32 | Legacy media read on Android 11–12 |
| `READ_MEDIA_IMAGES` | 33+ | Images via MediaStore |
| `READ_MEDIA_VIDEO` | 33+ | Videos via MediaStore |
| `READ_MEDIA_AUDIO` | 33+ path | Audio types if enabled in filters |
| `READ_MEDIA_VISUAL_USER_SELECTED` | 34+ | Limited / selected photo access |

- **No internet permission** — the app does not phone home.
- **No analytics / ads** in this codebase.
- Favourites and settings stay on-device unless **you** export them: Android backup and
  device-to-device transfer are both switched off for this app.
- Zip export **copies** favourites; it never moves or deletes originals for that feature.
- Favourites-folder sync only ever removes copies **Windfall made itself** (it remembers their
  addresses). Your own files in that folder are never deleted, and nothing is removed while
  **Disable all delete options** is on.
- **Delete** hides a file in the app for the current session; it does not delete the file.

---

## DEVICE-ONLY features

These need a real device (or emulator with storage) and are marked `DEVICE-ONLY` in source:

| Feature | APIs | Primary files |
|---------|------|----------------|
| Folder access | SAF `OpenDocumentTree`, persistable URI permissions, MediaStore | `MediaRepository.kt`, `GalleryApp.kt` |
| Video/audio playback | Media3 `ExoPlayer` | `FullscreenViewer.kt`, `MultiVideoScreen.kt` |
| Landscape multi-video | `ActivityInfo` orientation lock | `MainActivity.kt` |
| Pinch columns | `detectTransformGestures` | `MediaGrid.kt` |
| Favourites zip | `ZipOutputStream`, `FileProvider` | `FavouritesExporter.kt` |
| Favourites folder sync | SAF copy/delete | `FavouritesFolderSync.kt` |

Full details: [`docs/DEVICE_ONLY.md`](docs/DEVICE_ONLY.md).

---

## Design files

| Asset | Path |
|-------|------|
| Main interactive wireframe | [`Design_Files/My Random Gallery app Wireframe/My Random Gallery.dc.html`](Design_Files/My%20Random%20Gallery%20app%20Wireframe/My%20Random%20Gallery.dc.html) |
| Empty folders state | [`Design_Files/My Random Gallery app Wireframe/Empty.dc.html`](Design_Files/My%20Random%20Gallery%20app%20Wireframe/Empty.dc.html) |
| Bundled branding page | [`Design_Files/My Random Gallery.html`](Design_Files/My%20Random%20Gallery.html) |

When behavior or UI conflicts with code, **prefer the wireframe** unless a platform limitation is documented under DEVICE-ONLY.

---

## Project structure

```text
Windfall-Random-Gallery/
├── baselineprofile/              # Macrobenchmark Baseline Profile generator
├── Design_Files/                 # Interactive HTML wireframe (source of truth)
├── docs/
│   └── DEVICE_ONLY.md            # Device API notes
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mousy/windfall/
│       │   ├── MainActivity.kt
│       │   ├── data/             # models, DataStore, MediaStore/SAF
│       │   ├── viewmodel/        # GalleryViewModel
│       │   ├── ui/               # Compose screens & theme
│       │   └── util/             # AndroidVersionGate
│       └── res/
├── gradle/libs.versions.toml
├── .github/workflows/android.yml
├── README.md
└── LICENSE
```

---

## Troubleshooting

| Problem | What to try |
|---------|-------------|
| Empty Gallery | Add at least one SAF folder in **More**; check **File Types** |
| New photos don't show up straight away | Intentional: they join the part of the shuffle you haven't reached yet, so pages you've seen never change. Tap **Shuffle** to deal everything afresh |
| Grid tiles have gaps | Turn off **Padded thumbnails** in More → Playback & Safety for an edge-to-edge grid (the default for new installs) |
| File-type counts look stale | They're cached from the last scan; tap **Refresh counts** in **More → File Types** |
| Accent is Rose, not Sand | Sand is the default for new installs; an existing install keeps its stored choice. Pick Sand, or use **Reset all settings** |
| Permission denied | Re-open app → system settings → allow Photos/Videos; or rely on SAF folders |
| Videos won’t play | Confirm codec support; try another file; check Multi-Video cell has a selection |
| Gradle sync fails | Install SDK Platform 36 + Build-Tools; set `sdk.dir` in `local.properties` |
| `jdk` errors | Use JDK 17+; Android Studio’s embedded JBR is fine |
| `gradlew.bat` not found / not runnable | From project root use `.\gradlew.bat` in PowerShell |
| `adb` not found | Install Platform-Tools; add `%LOCALAPPDATA%\Android\Sdk\platform-tools` to PATH |
| Phone not listed | Enable USB debugging; run `adb devices`; accept the RSA prompt on the phone |
| Code changes missing on phone | Rebuild (`.\gradlew.bat assembleDebug`) then `adb install -r ...` |
| Launcher icon stale | Uninstall the app, then install again |
| Install blocked | Enable unknown sources / use `adb install` |
| Delete disabled | Check **Disable all delete options** in More → Playback & Safety |

---

## Getting the code

```bash
git clone https://github.com/SandeeepKiran/Windfall-Random-Gallery.git
cd Windfall-Random-Gallery
```

**Do not commit:**

- `local.properties`
- `*.jks` / `*.keystore` / `keystore.properties`
- `app/build/` or `.gradle/`

These are already covered by [`.gitignore`](.gitignore).

### Tag a GitHub Release (debug APK)

CI already uploads `app-debug.apk` as an Actions artifact on every push. To also attach it to a **GitHub Release**, push a version tag:

```powershell
git tag v1.0.1
git push origin v1.0.1
```

That triggers [`.github/workflows/release.yml`](.github/workflows/release.yml), which builds the debug APK and attaches it to the release for tag `v*`.

Suggested repo topics: `android`, `kotlin`, `jetpack-compose`, `material3`, `gallery`, `mediastore`.

---

## Contributing

1. Fork the repository
2. Create a branch: `git checkout -b feature/my-change`
3. Match existing Kotlin/Compose style and wireframe behavior
4. Keep DEVICE-ONLY comments when touching storage, video, pinch, or zip/copy
5. On Windows PowerShell: `.\gradlew.bat assembleDebug` (rebuild after changes)
6. Open a PR with summary + test plan (device API level, folders used)

Issues and ideas welcome via GitHub Issues.

---

## License & author

**MIT** — see [LICENSE](LICENSE).

**Sandeep Kiran (Mousy)** — Made with ❤️ for random media discovery.
