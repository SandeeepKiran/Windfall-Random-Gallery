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
- [ ] 3. REOPENED (build 1.1.0: "not working at all"). Diagnosis: nav reuses the surfaces' end-of-gesture pan detection — the same path as the old flick-to-delete, which likely never fired on this device either. Videos additionally swallow slow vertical drags as brightness/volume and require <300ms flicks. NEXT SESSION plan: move detection to a FullscreenViewer-level `pointerInput` on the HorizontalPager (PointerEventPass.Final; accumulate `positionChange()` for dy so child-consumed zoom-pans/brightness drags contribute zero, plus `positionChangeIgnoreConsumed()` for raw dx to guard against pager-consumed horizontal swipes; skip multi-touch; skip quick up-flick when swipe-delete enabled — that stays delete). Then remove the per-surface onSwipeVertical branches. The `onSwipeVertical` lambda by the pager state is the hook to call.
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

## Status after Sep 27 session — builds, unit tests pass; NOT yet verified on the phone.
- [ ] Verify on the Redmi: swipe back never changes a seen page; slideshow start; scroll back
      shows thumbnails instantly (`adb logcat | Select-String "RealImageLoader"` → MEMORY_CACHE).
- [ ] Item 3 of Sep 1 (viewer vertical swipes) is still REOPENED — untouched this session.
- [x] Decided (Sandeep): Delete now moves files to Android's trash (MediaStore trash request,
      Android's own confirmation, Undo restores). Tested on the emulator: single, multi-select,
      Undo after 5 s. Files MediaStore doesn't know are still only hidden for the session.
- [x] Decided (Sandeep): keep the page-1 back-swipe wrap to the unseen tail.
- [x] Debug builds are now "Windfall Debug" (`com.mousy.windfall.debug`), installed beside the
      real app, so phone tests can use test photos only.
