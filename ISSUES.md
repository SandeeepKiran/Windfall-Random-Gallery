# Feedback Log — Sep 1, 2026 (after 1 month of use)

## Bugs
1. **Heart refreshes gallery** — tapping favourite on the gallery screen causes the whole gallery to refresh. Expected: heart toggles in place, no reshuffle/reload.
2. **Backward swipe on first page broken** — on page 1, swiping backwards shows a different page (up to 2 pages off) instead of generating a continuous random page (should mirror forward-swipe behavior, i.e. infinite random in both directions).
3. **Viewer vertical swipes do nothing** — in fullscreen view mode, swipe down should go to PREVIOUS media, swipe up should go to NEXT media (in addition to horizontal swipes).
4. **GIFs don't animate** — GIFs render as static images everywhere.
5. **Export favourites: no location shown** — export says "exported" but never shows where the file went, and gives no picker to choose destination (should use SAF/document picker).

## Feature requests
6. **Export selected favourites** — ability to export only a chosen subset of favourites, not all.
7. **Bump app version number** — every release.
8. **Heart icon: black border** — add a black outline/border around the heart so it stands out on top of thumbnails.
9. **Heart favourite-state legibility in greyscale** — pink (favourited) vs grey (not) is indistinguishable in phone greyscale mode. Suggestion: unfavourited = outline only / no fill instead of grey fill.
10. **Slideshow always starts from the very first media** — slideshow button must start at page 1, media 1, not the first media of the current page.

## Status after Sep 1, 2026 session — ALL 10 addressed in code; NOT yet built/tested (no Android SDK in sandbox). Build in Android Studio + verify on device before release.
- [x] 1. Heart refresh fix — gallery draw now uses favIds snapshot frozen at shuffle time (`_boostFavIds` in GalleryViewModel); Favourites tab still live.
- [x] 2. Backward swipe on first page — wraps to the tail window of the current sample (fresh unseen content), and the swipe strip now parks that same window on the back side, so the settle handoff stays pixel-identical. Sample pre-extends on wrap, so swiping forward from the tail continues seamlessly too. (Old fall-through dealt forward, which made the next forward swipe look 2 pages off. First version of this fix materialised the full cycle and mismatched the strip's parked window — both reworked.)
- [x] 3. FIXED in 1.2.0 (see "Version 1.2.0" at the end). Was REOPENED (build 1.1.0: "not working at all"). Diagnosis: nav reuses the surfaces' end-of-gesture pan detection — the same path as the old flick-to-delete, which likely never fired on this device either. Videos additionally swallow slow vertical drags as brightness/volume and require <300ms flicks. NEXT SESSION plan: move detection to a FullscreenViewer-level `pointerInput` on the HorizontalPager (PointerEventPass.Final; accumulate `positionChange()` for dy so child-consumed zoom-pans/brightness drags contribute zero, plus `positionChangeIgnoreConsumed()` for raw dx to guard against pager-consumed horizontal swipes; skip multi-touch; skip quick up-flick when swipe-delete enabled — that stays delete). Then remove the per-surface onSwipeVertical branches. The `onSwipeVertical` lambda by the pager state is the hook to call.
- [x] 3-old. Viewer vertical swipe nav (first attempt, superseded) — swipe down = previous, swipe up = next (same pager animation). Root cause of "does nothing": swipe-up was the flick-to-delete gesture, which you have disabled. If swipe-delete is ENABLED in settings, up-flick still deletes (down still = previous).
- [x] 4. GIF playback — added coil-gif + `AnimatedImageDecoder.Factory()` in GalleryApplication (minSdk 30). Grid thumbs stay static (thumb fetcher), viewer animates.
- [x] 5. Export settings — root cause: it built the JSON and snacked "exported" without ever writing a file. Now opens a save-as picker (CreateDocument), you choose location + name, snack fires only after the write succeeds.
- [x] 6. Export selected — new "Export" button in the select-mode bar zips exactly the selected items and opens the save/share sheet (works for any selection, not just favourites). Favourites zip export also goes through the same sheet.
- [x] 7. Version bump → versionCode 2, versionName 1.1.0.
- [x] 8. Heart black border — layered black heart under pink one in MediaGrid.
- [x] 9. Greyscale-safe heart — viewer toggle now uses outline (FavoriteBorder) when not favourited; grid's black ring also helps.
- [x] 10. Slideshow — code already opened viewer at index 0 (first media of the random order); also now resets the source tab's page cursor to page 1. If it still misbehaves on device, re-check `selectTab(SLIDESHOW)` path — the installed 1.0.0 build may have differed.

---

# Feedback Log — Sep 27, 2026 (full code review + 3 bugs)

## Bugs reported
1. **Swiping back (right) flashes and re-shuffles the whole stack.**
   Root cause: the gallery order was recomputed from (seed + the whole pool of media), and a
   seeded shuffle of a *different* pool is a different shuffle. Any pool change re-dealt every
   page: the cold-start scan finding new photos, hiding a file with Delete, Undo, a new file type
   being switched on. Also, SAF-folder files got ids from a counter, so their keys shifted too.
   Fix: `ShuffleDeck` deals once per shuffle and then only reconciles (gone items keep their slot,
   new items go into the part not yet seen). SAF ids now come from the document address.
   The swipe strip also pre-builds the back-wrap page from page 1, instead of building it
   mid-drag (the flash).
2. **Slideshow starts at page 1 every time.** This REVERSES item 10 of Sep 1, at Sandeep's
   request. It now starts at the top-left item of the page on screen (or top row in scroll
   mode), or at the photo open in the viewer.
3. **Scroll mode lags; thumbnails re-render after scrolling back.**
   Root cause (verified in Android + Coil source): `loadThumbnail()` returns the OS thumbnail
   shrunk to FIT the box, so the short side is below the tile; Coil then rejects that cached
   copy as too small and fetches it again every time the tile reappears. Prefetch requests used
   Coil's defaults (FIT/EXACT) and rejected cached thumbs too, 30 at a time per row scrolled.
   Fix: thumbnails rescaled to exactly cover the tile before caching, one shared request for
   tiles and prefetch, real cancellation of off-screen thumbnail calls, idle-only look-ahead.

## Found in review and fixed
- **Favourites-folder sync could delete ORIGINAL photos** (hearting matched folder files by
  name). It now removes only copies it made itself, tracked by address, and respects
  "Disable all delete options".
- **Import settings wiped folders, file types, hidden folders, tabs** (and favourites if the
  file had none). Import now merges, refuses non-settings files, caps size, offers Undo.
- Zip import had no limits and could fill the phone; it extracted into a hidden folder.
- Backups sent favourites, folder names and the media index off the phone, contrary to the
  README. Backup and device transfer are now off.
- Settings could lose a change (the farmer's background save raced taps; stale DataStore
  echoes overwrote newer settings). One atomic update path plus a single writer.
- Album "Shuffle" re-dealt the main Gallery instead of the album.
- Brightness set in a video stayed on the whole app after closing the viewer.
- Android 14+ never asked for the audio permission, so music never appeared there.
- Log sharing ran logcat on the main thread and shared folder names; now background + masked.
- Multi-Video file picks took permanent permissions that could evict the source folders'.
- Scroll position was lost on tab switch and after a slideshow; now kept per tab.
- Dead code and 4 unused dependencies removed; unused manifest `<queries>` removed; FileProvider
  narrowed to `cache/exports` and `cache/logs`; `*.jks` git-ignored; CI runs tests, has a
  read-only token, and deletes the keystore before the third-party release step.
- First unit tests: 53 JVM tests in `app/src/test` (deck, sampling, paging, thumbnails, settings).

## Status after Sep 27 session — builds, unit tests pass, verified on the phone (see below).
- [x] Verified on the Redmi K20 Pro (Android 11, "Windfall Debug" pointed at test photos only):
      swiping 2 pages forward and back showed identical pages; the slideshow started at page 2's
      top-left and moved on every ~5.2 s in page order; scrolling back up 5 screens in scroll mode
      made 111 thumbnail requests, all 111 from memory (none fetched again).
- [x] Item 3 of Sep 1 (viewer vertical swipes): fixed in 1.2.0, see the section at the end.
- [x] Decided (Sandeep): Delete now moves files to Android's trash (MediaStore trash request,
      Android's own confirmation, Undo restores). Tested on the emulator: single, multi-select,
      Undo after 5 s. Files MediaStore doesn't know are still only hidden for the session.
- [x] Decided (Sandeep): keep the page-1 back-swipe wrap to the unseen tail.
- [x] Debug builds are now "Windfall Debug" (`com.mousy.windfall.debug`), installed beside the
      real app, so phone tests can use test photos only.

---

# Multi-Video rebuilt — Sep 27, 2026

## What Sandeep asked for
Watch 4 videos at once in landscape, tiled like windows with no gaps and nothing else on screen
(top-left 1st, top-right 2nd, bottom-left 3rd, bottom-right 4th). Also 2 videos (portrait, or
landscape for vertical videos), maybe more than 4, fewer than the tiles allowed. Play all /
restart all / pause all / mute, plus normal per-video controls. A way out (the old screen had no
Back and no way to rotate back: he got stuck). Choose videos by their pictures, from the gallery
or another app, not by random download file names. Controls that hide after about 3 seconds.

## What was wrong with the old one (checked in the old code)
- Stuck in landscape: entering it hid the Exit button until a tile was tapped (and tapping an
  empty tile opened the picker instead), and the phone's Back closed the whole app.
- The picker was a list of file names (audio files included).
- Only 1, 2 or 4 videos; outside landscape the videos sat in padded cards, not tiles.
- Per-video controls were play/pause and mute only; the progress bar could not be dragged.
- Every playing tile wrote its progress into the gallery's shared state 4 times a second, so
  with 4 videos the whole app's UI state was rebuilt about 16 times a second.

## What it is now (package `multivideo/`, its own ViewModel)
- Layouts 1 / 2 / 3 / 4 / 6. 2 and 3 sit side by side in landscape and stack in portrait. Six is
  the cap: each playing video holds a hardware decoder.
- Screen direction: Landscape (default), Portrait, Auto (follows the sensor even with rotation
  lock on). Changeable on the wall.
- Picker: Windfall's own videos (thumbnails, lengths, order numbers), Android's photo picker
  (ordered selection), or "Other apps…" (chooser over `ACTION_GET_CONTENT`).
- Wall: only videos until tapped. Controls fade 3 s after the last touch: Exit, Play all, Pause
  all, Restart all, Mute all / Sound on, Layout, Fit/Fill, direction. The tapped video gets a
  bar: play/pause, ±10 s, seek, sound, Change, Remove. Double-tap sides skips 10 s. Back exits.
- Sound: first video only at the start; the wall holds Android's sound focus only while a video
  with sound plays (verified: focus released on Mute all, taken again on Sound on).
- Players exist only while the wall is visible; Home frees every decoder, return resumes.
- Tiles are TextureViews in one keyed grid: layout and rotation changes move tiles instead of
  rebuilding them. With SurfaceViews, the emulator showed a stretched, cut-off picture after a
  playing video moved to a new tile.

## Status — builds, 74 unit tests pass (21 new), tested on the emulator with generated videos
- [x] 4 videos in the right corners; 6 videos; 2 side by side and stacked; Fit and Fill.
- [x] Per-video sound, pause, ±10 s (exact), double-tap skip; Mute all; Restart; Layout menu.
- [x] Exit and Back leave the wall (players 0, focus released, rotation back to normal).
- [x] Home frees all players; return resumes positions; reopening plays everything again.
- [x] Photo picker (ordered) and the in-app picker; the signed release build too.
- [x] On the Redmi K20 Pro (Android 11): 4 and then 6 full-HD (1080p30) videos at once, all in
      real time and in step, all on the hardware decoder (`OMX.qcom.video.decoder.avc`, no
      fallback, no errors). Home freed all 6 players; return resumed them; Back left the wall.
- [x] "Other apps…" and "Photo picker" on the Redmi: checked by Sandeep himself (they show his
      own media): both work as expected.
- [x] Remembered across app restarts (Sandeep: "yes, remember"): how many videos, the screen
      direction and Fit/Fill, in a small file of Multi-Video's own. The chosen videos are still
      not kept (picked videos can only be read while the app runs).

---

# Version 1.2.0 — Sep 27, 2026 (evening)

- [x] **Sep 1 item 3 fixed: swipe up/down in the viewer** (up = next, down = previous). Root cause:
      a real swipe is never perfectly vertical, so the sideways pager claimed it once the finger
      drifted past its touch slop, then snapped back and cancelled the page change. A one-finger
      drag is now sorted by its first movement (0.6 × touch slop, before the pager's threshold):
      mostly up/down is kept from the pager. On videos a swipe under 0.3 s changes item and a slower
      drag stays brightness/volume. Checked on the emulator: slanted swipes up and down, a steep
      30° swipe, sideways paging still works.
- [x] **Shuffle button on the Recent tab** (swipe mode; scroll mode lists Recent newest first).
      Recent now has its own order: its Shuffle deals Recent again, the Gallery's leaves it alone.
- [x] **Version bump on every build that leaves the laptop:** 1.2.0 (versionCode 3).
- [x] Viewer buttons had no names for screen readers (Close, favourite, More options, slideshow
      play/pause): added.
- [x] Store listing: `fastlane/metadata/android/en-US` (title, descriptions, changelog, icon,
      feature graphic, 7 screenshots) and a launch video made with /brag-slim, all on the emulator
      with generated demo media. The release workflow now uses the changelog as release notes.
- [ ] Promo video on the Play Store and F-Droid needs a YouTube link (both only take a URL):
      Sandeep uploads `docs/media/windfall-launch.mp4`, then add the URL to
      `fastlane/metadata/android/en-US/video.txt` and the Play Console.
