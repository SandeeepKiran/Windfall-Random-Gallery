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
