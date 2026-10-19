# AGENTS.md — Windfall Gallery

Rules and map for AI coding agents in this repo. Read this file first. It overrides general habits.

## 1. What this is

- **Windfall Gallery**: a native Android gallery for random browsing of the user's own photos,
  videos, GIFs and audio. Local only: MediaStore + SAF folders. No internet permission, no accounts,
  no analytics.
- Kotlin + Jetpack Compose + Material 3. Single module app `:app` plus `:baselineprofile`.
- Package / applicationId: `com.mousy.windfall`. Debug builds: `com.mousy.windfall.debug`
  ("Windfall Gallery Debug").
- Current version: **1.4.0 (versionCode 6)**, in `app/build.gradle.kts`.
- minSdk 30 (Android 11), compile/target SDK 36, JVM target 17. Versions: `gradle/libs.versions.toml`.
- Public repo, MIT license, single contributor.

## 2. Read first

| File | Read it for |
|---|---|
| `AGENTS.md` (this file) | Rules, commands, map, release process |
| `ISSUES.md` | Feedback logs and fix history per version: root causes, decisions, what was verified on device |
| `README.md` | Features, gestures, architecture diagram, performance design, permissions, troubleshooting |
| `docs/DEVICE_ONLY.md` | Features that need a real device or emulator (SAF, ExoPlayer, picker, zip) |
| `Design_Files/` | Original wireframe and feature spec. README says: prefer the wireframe when UI conflicts with code, unless `ISSUES.md` records a later decision |
| `fastlane/metadata/android/en-US/` | Store texts, changelogs, icon, feature graphic, screenshots |

- Read `ISSUES.md` before you change a behaviour. Many behaviours were decided on purpose and reversed once already.
- Do not re-derive the architecture from scratch. Use the map in section 4.

## 3. Build, test, install (PowerShell, from the repo root)

| Task | Command |
|---|---|
| Unit tests (JVM, no device) | `.\gradlew.bat testDebugUnitTest` |
| Debug build | `.\gradlew.bat assembleDebug` → `app\build\outputs\apk\debug\app-debug.apk` |
| Clean debug build | `.\gradlew.bat clean assembleDebug` |
| Lint | `.\gradlew.bat :app:lintDebug` |
| Detekt (Kotlin + Compose static analysis) | `.\gradlew.bat :app:detekt` → `appuildeports\detekt\detekt.html` |
| Release build | `.\gradlew.bat assembleRelease` → `app\build\outputs\apk\release\app-release.apk` |
| Build + install debug (one device attached) | `.\gradlew.bat installDebug` |
| Install an APK | `adb -s <serial> install -r <apk>` |
| List devices | `adb devices` |
| Regenerate baseline profile (needs device) | `.\gradlew.bat :app:generateReleaseBaselineProfile` |

- Use `.\gradlew.bat`, not bare `gradlew.bat`. PowerShell does not search the current folder.
- Use the Gradle wrapper (Gradle 8.14.5). Do not use a global Gradle.
- If more than one device is connected, pass `-s <serial>` to every `adb` command.
- `adb` lives in the SDK `platform-tools` folder if it is not on PATH.
- Unit tests: **78** JUnit 4 tests in `app/src/test/java/com/mousy/windfall/` (8 files; 25 in
  `multivideo/WallModelsTest.kt`). Keep them green. Add a test when you change shuffling, paging,
  thumbnail sizing, settings import/export or Multi-Video rules.
- Without the signing properties, `assembleRelease` produces an **unsigned** APK. That is expected locally.
- R8 minify and resource shrink are on for release. After a change to release config or ProGuard
  rules, build the release APK, install it and launch it once.
- Debug builds attach Coil's `DebugLogger`. Check cache hits with
  `adb logcat -d | Select-String "RealImageLoader:.*Successful"`.
- CI (`.github/workflows/android.yml`) runs `detekt`, `testDebugUnitTest` and `assembleDebug` on pushes and PRs to `main` / `master` only.
- Detekt (1.23.8 + Compose rules) reports only findings that are not in `config/detekt/baseline.xml`. Fix a new finding;
  do not add it to the baseline. Rebuild the baseline (`:app:detektBaseline`) only when the owner agrees.
  Rules: `config/detekt/detekt.yml`. Old baseline findings were checked on Oct 19, 2026: none was a real bug.

### Verify a change

1. Run the unit tests and `detekt`.
2. Run `assembleDebug`. It must build with no errors. Warnings are acceptable.
3. Install the debug build. Test the change on the emulator or the test phone (section 6 rules apply).
4. If you change `GalleryViewModel`, `MediaRepository`, `RandomSample.kt` or `ShuffleDeck.kt`, test with a
   large folder (thousands of generated items). Watch for stutter in Gallery, page swipes and Settings.
5. If you change `FullscreenViewer.kt`, test a photo, a video, a GIF and an audio file; rotate mid-video;
   swipe fast; swipe up to delete and Undo.
6. If you change settings code, change the setting, force-close the app from Recents, reopen, and confirm it persisted.
7. If you change permissions or the manifest, test on a fresh install of the **debug** app only (section 6).

## 4. Architecture map

Source root: `app/src/main/java/com/mousy/windfall/`. Hand-rolled MVVM: `StateFlow` + DataStore.
No Room, no networking, no DI framework, no Navigation library (tabs = `AppTab` state + `AnimatedContent`).

| Path | What it does |
|---|---|
| `MainActivity.kt` | Single activity: splash, media permissions, hosts `GalleryApp`, creates `MultiVideoViewModel` |
| `GalleryApplication.kt` | App-wide Coil `ImageLoader` (cache sizing, `MediaStoreThumbFetcher`, video frames, GIF decoder) |
| `viewmodel/GalleryViewModel.kt` | The app's brain (~2000 lines). Two-tier state: expensive `libraryState` on `Dispatchers.Default`, cheap `uiState` for viewer/shell toggles. `persistSettings` = the only settings write path |
| `data/model/AppModels.kt` | `AppSettings` + defaults, `AppTab`, `AccentColor` (default `SAND`), `GridMode`, `FavWindow` |
| `data/model/RandomSample.kt` | Seeded Fisher–Yates; every random deal goes through it |
| `data/model/ShuffleDeck.kt` | Holds a deal still when media changes (new items only go into the unseen part) |
| `data/model/PageWindow.kt` | The one swipe-paging rule, shared by grid and ViewModel |
| `data/model/MediaItem.kt` | Core media class; keys and ages precomputed once per item on purpose |
| `data/media/MediaRepository.kt` | MediaStore + SAF scans, folder discovery, extension counts, folder matching |
| `data/media/MediaIndexCache.kt` | TSV snapshot of the last scan for instant cold start |
| `data/media/MediaPermissions.kt` | Permission logic per Android version |
| `data/media/MediaStoreThumbFetcher.kt` | Coil fetcher: OS thumbnails rescaled to cover the tile before caching |
| `data/media/MediaTrash.kt` | Delete = MediaStore trash request (Android confirms; Undo restores) |
| `data/media/FavouritesExporter.kt` | Favourites/selection zip export and limited zip import |
| `data/media/FavouritesFolderSync.kt` | Optional favourites folder; deletes only copies it made (`AppSettings.favCopyUris`) |
| `data/preferences/SettingsRepository.kt` | DataStore `windfall_settings`; `SettingsJson` export/import (merges, refuses non-settings files) |
| `multivideo/` | Video wall, self-contained: `WallModels.kt` (pure rules, tested), `MultiVideoViewModel`, `WallPlayers` (one ExoPlayer per tile), `MultiVideoWall`, `MultiVideoSetupScreen`, `VideoPicker`, `MultiVideoPrefs` (DataStore `windfall_multivideo`) |
| `ui/GalleryApp.kt` | Composition root: wires screens to the ViewModel, viewer overlay, dialogs, back, immersive mode |
| `ui/navigation/MainScaffold.kt` | Scaffold + compact icon tab bar |
| `ui/screens/` | `GalleryScreen`, `FavouritesScreen`, `RecentScreen`, `AlbumsScreen`, `SettingsScreen` |
| `ui/components/FullscreenViewer.kt` | Viewer pager, zoom, ExoPlayer, swipe navigation (`isNavigationSwipe`), slideshow mode |
| `ui/components/MediaGrid.kt` | Swipe-paged and scroll grids, tiles, pinch columns |
| `ui/components/ThumbSpec.kt` | Thumbnail bucket math shared by grid and viewer placeholder |
| `ui/theme/` | Material 3 theme, accent palettes |
| `util/AndroidVersionGate.kt` | All `SDK_INT` checks. Add new version gates here, never inline |
| `util/AppIconSwitcher.kt` | Launcher icon aliases (`.IconWindrose`, `.IconClassic`, `.IconMono` in the manifest) |
| `util/PlayerWarmPool.kt` | Pre-buffered players for swipe-to-next; `VideoPositionMemory` |
| `util/LogCapture.kt` | Background logcat capture (masked), keeps the 5 newest logs |

### Where to start for common changes

| Change | Start in |
|---|---|
| New setting | `AppSettings` → `SettingsRepository` (key + `SettingsJson`) → `SettingsScreen` → ViewModel setter → `GalleryApp` wiring |
| New tab | `AppTab` in `AppModels.kt` → `when` branch in `GalleryApp.kt` → screen in `ui/screens/` |
| Shuffle behaviour | `RandomSample.kt`, `ShuffleDeck.kt`, `GalleryViewModel.buildLibraryState()` + their tests |
| Scanning | `MediaRepository.kt` |
| Viewer gestures | `FullscreenViewer.kt` |
| Grid / swipe vs scroll | `MediaGrid.kt`, `PageWindow.kt` |
| Multi-Video | `multivideo/WallModels.kt` first (+ `WallModelsTest.kt`), then players/screens |

## 5. Release process

1. Bump **both** `versionCode` (+1) and `versionName` in `app/build.gradle.kts` for **every build that
   leaves the laptop**: phone install, push, or release. Why: the owner tracks which build is on which device by version.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. Start with the versionName line.
   Keep it at 500 characters or less (store limit).
3. Add a dated section for the version to `ISSUES.md` when the change fixes or decides something.
4. Commit. Tag `v<versionName>` (example: `v1.3.1`). Push the commit and the tag. If your tool is not
   allowed to push in the owner's setup, stop after the commit and tell the owner.
5. The tag runs `.github/workflows/release.yml`: unit tests → signed `assembleRelease` →
   GitHub Release "Windfall Gallery v…" with `Windfall-Gallery-v….apk`. Release notes = the changelog
   file for the `versionCode` it reads from `app/build.gradle.kts`, then GitHub's generated notes.

- Signing happens in CI only, from GitHub secrets. The build reads `WINDFALL_KEYSTORE`,
  `WINDFALL_KEYSTORE_PASSWORD`, `WINDFALL_KEY_ALIAS`, `WINDFALL_KEY_PASSWORD` from Gradle properties or env vars.
- ⚠️ Never commit a keystore, `keystore.properties`, passwords or any signing value. `*.jks`,
  `*.keystore` and `keystore.properties` are git-ignored; keep it that way.
- ⚠️ Never put signing values in the project `gradle.properties` (it is tracked). Local signing, if
  ever needed, uses the user-level Gradle properties file.
- Do not change the `release.yml` `versionCode` regex without keeping `versionCode = <n>` on one line in `app/build.gradle.kts`.
- Store media rebuilds (screenshots, feature graphic, launch video) only when the owner asks.

## 6. Device and test-data rules

- ⚠️ Use **generated demo media only** for every test, screenshot and video. Never open, screenshot,
  dump or copy real personal photos from any device.
- ⚠️ Make store screenshots and promo video on the **emulator** with generated demo media. Never on the phone.
- Do phone tests with the **debug** app (`com.mousy.windfall.debug`) pointed at the test folder only.
- ⚠️ The test phone keeps **three** installs. Never uninstall any of them:
  - `com.mousy.myrandomgallery` (old name, leave untouched)
  - `com.mousy.windfall` (release)
  - `com.mousy.windfall.debug` (debug)
- To update an install, use `adb install -r`. Do not uninstall first, even for a manifest or permission change;
  if a clean first-run is needed, use the emulator or ask the owner.
- ⚠️ Never delete the test media on the phone at `/sdcard/Pictures/WindfallTest`. It is reused.
- Media picker checks that show the device's real gallery ("Photo picker", "Other apps…") are done by the owner, not by agents.
- The test phone runs plain Android 11 (not MIUI): `adb shell input` and `pm grant` work.
- On the test phone `adb logcat -c` fails. Use `adb logcat -T '<MM-DD hh:mm:ss.000>'` to read from a time.
- Test media generator scripts are kept outside this repo. Ask the owner if you need new test media.

## 7. Known gotchas and do-nots

- Do not re-apply old performance fixes (main-thread combine, O(n²) shuffle, triple scans). They are
  done. A new freeze is a new bug: profile first (thumbnail farmer `startThumbnailFarmer()`, Coil cache sizing).
- Do not bring back the adaptive sample size or a per-launch partial deal. The whole library is dealt once per shuffle and held still by `ShuffleDeck`.
- Do not make the slideshow start at page 1. It starts at the top-left of the current page or the open item (decided Sep 27, reversing Sep 1).
- Keep page-1 back-swipe wrapping to the unseen tail (decided).
- Keep Gallery and Recent shuffles independent (separate seeds).
- Keep delete going through Android's trash. Respect "Disable all delete options" in every delete path.
- Favourites-folder sync must only remove copies it made. Never match files by name.
- Keep `allowBackup="false"` and `data_extraction_rules.xml`. Keep FileProvider limited to `cache/exports` and `cache/logs`.
- Do not add the INTERNET permission, analytics or ads.
- Do not rename the DataStore files `windfall_settings` / `windfall_multivideo`. Users would lose saved settings.
- Do not rename the launcher `activity-alias` names. Existing icon choices would break.
- Do not change `applicationId`. It stays `com.mousy.windfall` even after the display-name rename.
- Do not add `navigation-compose`. It was removed on purpose.
- Multi-Video tiles stay TextureViews (SurfaceViews showed a stretched picture after a tile moved). Six videos is the cap (hardware decoders).
- The viewer's vertical slide moves pages in the placement step (`Modifier.layout`), not with `graphicsLayer`. The viewer video
  is a SurfaceView, and a SurfaceView does not follow a `graphicsLayer`: the video after an up/down swipe showed a black
  picture until a tap (fixed in 1.3.2). Keep the SurfaceView (HDR output, less GPU work than a TextureView).
- Viewer swipe direction is decided at 0.6 × touch slop, before the pager's slop. Do not move it later; the pager then steals slanted swipes.
- Keep `DEVICE-ONLY` comments when you touch storage, video, pinch or zip/copy code.
- Do not "fix" comments that explain deliberate design choices.
- Do not accept Android Studio's AGP upgrade prompt inside another change. AGP 8.9.1's R8 warns that it
  cannot read Kotlin 2.3 metadata; this is harmless. Bump AGP only as its own tested change.
- Do not use names, icons or terms from games or other franchises (for example HoYoverse titles) in
  app names, strings, store texts or assets. Why: Play Store IP risk.
- `GalleryViewModel` is one large class. Do not start a big split without asking.
- `fastlane/.../video.txt` is missing until the owner supplies a YouTube URL. Do not invent one.

## 8. Commits and privacy

- Do not add `Co-Authored-By` lines or any AI/tool attribution to commits, tags or PRs. This is a single-contributor repo.
- Write short, imperative commit subjects that match the history (example: "Add a Shuffle button to the Recent tab").
- This repo is **public**. Never commit secrets, personal data, device serials, email addresses or paths to private folders.
- Private notes about this project are kept outside the repo. Do not copy them in.
- Before you commit, check `git status` for build logs, APKs, `local.properties` or keystore files.
