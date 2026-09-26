# My Random Gallery — Complete Feature Specification

This is the source-of-truth checklist for the app, derived from the interactive HTML prototype
(`My Random Gallery.dc.html`). Every item below is a feature that exists in the prototype and
should exist in the built app. Use it to audit the build.

Suggested stack: **Kotlin + Jetpack Compose, Material 3 (Material You dynamic color),
Media3/ExoPlayer for video, Coil for image loading, MediaStore + SAF for file access.**

---

## 0. Global / App-wide

- [ ] App is named **"My Random Gallery"** everywhere (title in More/Settings, about text, etc.). No leftover "Random Gallery".
- [ ] App **always opens on the Gallery tab**.
- [ ] Material 3 / Material You visual language throughout: rounded surfaces, tonal containers, pill-shaped nav indicators, M3 dialogs, snackbars.
- [ ] **Bottom navigation bar** (M3 style): each item is icon + label, with a pill "indicator" behind the active icon and filled icon when active.
- [ ] **Favourite heart icon is always a solid/filled heart in the favourite color** (coral/red `#F38282`), never just an outline — consistent across grid tiles, favourites, recents, albums, and the viewer.
- [ ] Snackbar pattern for transient confirmations (export, share, zip, etc.), some with an action button (e.g. Undo).

---

## 1. Gallery tab (core / default)

- [ ] Randomized grid of media thumbnails from the selected source folders.
- [ ] Header actions: **swipe/scroll mode toggle**, **column density button**, **shuffle button** (filled primary container).
- [ ] **Tile overlays:** filled favourite heart top-**right**; media-type badge bottom-**right** (`play_circle` for video, `gif` for GIFs; photos have no badge).
- [ ] **Pinch-to-zoom changes column count** — zoom in = fewer columns (down to 1 single media), zoom out = more columns (up to 6). One column added/removed per zoom step. (Density button cycles the same range as a fallback.)
- [ ] **Swipe left/right = randomize** to a new set (like paging). **Swiping back shows the previous set** (history is consistent, not a fresh random each time).
- [ ] **Swipe vs Scroll mode toggle** — swipe mode = swipe for new random sets; scroll mode = normal vertical scrolling for more.
- [ ] **Tap a tile** → opens fullscreen viewer.
- [ ] **Double-tap a tile** → add/remove from favourites.
- [ ] **Long-press (hold ~2.5 seconds)** → enters multi-select mode. (Sensitivity deliberately low so it doesn't trigger accidentally.)
- [ ] Empty states: no folder selected → "no folder selected" logo/empty design (routes to Settings); folder selected but no matching media → "no media of selected file types" message.

## 2. Multi-select mode (grid)

- [ ] Activated by hold-2.5s on any tile.
- [ ] Selection top bar (secondary-container color): close (X), "N selected" count, favourite action, delete action.
- [ ] Selected tiles show a check-circle badge + primary-color ring.
- [ ] **Favourite selected** → marks all selected as favourites.
- [ ] **Delete selected** → delete-confirm dialog → snackbar with Undo. (Disabled/greyed when "Disable editing & deleting" is on.)

---

## 3. Favourites tab

- [ ] Grid of favourited media only (all filled hearts).
- [ ] **Filetype multi-select dropdown** (top-right) — Photos / Videos / GIFs, default = all types. Label reads "All types" or "N types".
- [ ] **Day-window filter** (like Recent) — cycles All time → 7 → 14 → 30 → 60 → 90 → 365 days, based on when media was taken/downloaded.
- [ ] Empty state ("Nothing here yet" + hint to double-tap or loosen filters).

## 4. Recent tab

- [ ] Grid of recently added/taken media within the chosen day window, sorted newest-first.
- [ ] **Day-window chip** cycles **7 → 14 → 30 → 60 → 90 → 365 days**, then loops back.
- [ ] Empty state ("No recently added files found in the selected paths").

---

## 5. Slideshow (fullscreen viewer)

- [ ] Launched from a grid tile OR from the Slideshow bottom-nav tab.
- [ ] **Image fills the screen at full resolution** (zoomable); videos play; **video rotates to landscape** when phone is turned sideways for easy watching.
- [ ] **Tap anywhere toggles the top & bottom chrome** (immersive fullscreen). Tap again brings it back.
- [ ] **Top bar:** back, date, play/pause, speed control, favourite (filled heart in fav color), overflow (⋮) menu.
- [ ] **Bottom of viewer = the app's bottom nav tabs** (NOT a "1/30 · swipe up to delete" counter — that old counter line is removed).
- [ ] **Speed presets:** 1s, 2s, 5s, 10s, 15s, 30s, 1min, 5min, **Custom**, **Off (None — doesn't change)**.
- [ ] **Custom speed opens a dialog** to type any number of seconds.
- [ ] **Video-aware timing:** for videos the slideshow **waits for the whole video to finish** before advancing — the fixed interval only applies to photos/GIFs.
- [ ] **"Don't loop videos" setting** respected (stops at end instead of looping the set).
- [ ] **Swipe left/right** = previous/next media.
- [ ] **Swipe up = delete** (only when "Disable swipe-up-to-delete" is OFF and editing/deleting is allowed) → confirm dialog → Undo snackbar.
- [ ] **Overflow (⋮) menu contains only: Share, Delete, Details.** (Hide and "Set as Background" were removed — they served no purpose.)
- [ ] **Delete** → confirmation dialog ("Delete this file?") → on confirm, snackbar with **Undo**.
- [ ] **Details** → dialog with full metadata: name, type + extension, resolution, size, date taken, folder/path.

### Slideshow source-order (IMPORTANT)
- [ ] The slideshow follows the **order of the tab the user came from**:
  - From **Favourites** → plays the favourites order.
  - From **Gallery** → plays gallery's current order.
  - From an **opened Album** (specific folder) → plays only that folder's media.
  - From **Settings / Multi-Video / Album main screen** → falls back to the **Gallery** order.

---

## 6. Multi-Video tab (optional, toggled on in settings)

- [ ] Plays **multiple videos at the same time** in a mini-grid.
- [ ] **Grid count selector: 1, 2, 4.** (4 lays out as 2×2.)
- [ ] **Each cell starts empty ("Choose video"), no video selected by default.**
- [ ] **Per cell, choose a specific video from the device** (from the selected folders) via a picker dialog, including a **"None (clear)"** option.
- [ ] **Per-cell independent controls:** play/pause, mute, and its own **progress bar** — adjustable individually.
- [ ] Global controls: **Play all, Pause all, Mute all.**
- [ ] **Padding/gap between the video cells** (they must not touch).
- [ ] **Overlays auto-hide after a few seconds** (name chip, big play icon, control bar) so you see just the video; **tap the cell to bring them back**.
- [ ] **Works sideways / landscape:**
  - A **button to force landscape** (for users locked to portrait in device settings who want landscape only for this feature).
  - If the device auto-rotate is enabled, the app **follows real orientation**.
  - In landscape, the **grid fills the whole screen**.
  - Landscape has an always-reachable **Exit/back overlay button** to return to normal portrait (must never be stuck sideways with no way out).

---

## 7. Album tab (optional, toggled on in settings)

- [ ] Album/grid view like a normal gallery — shows source folders as **album cards** (thumbnail, name, path, item count).
- [ ] **Tapping an album opens it and behaves like the main Gallery tab** (grid, tap-to-view, shuffle, multi-select, etc.).
- [ ] Album detail has a back button, title + count, and shuffle.
- [ ] Opening a slideshow from inside an album uses **that album's order only** (see slideshow source-order).

---

## 8. Settings ("More" tab)

### 8a. Appearance
- [ ] **Enable Dark Mode** toggle.
- [ ] **AMOLED Black** toggle — pure-black surfaces in dark mode.
- [ ] **Accent color picker — pastel themes**: Rose, Lavender, Mint, Peach, Sky, Sand (selected one shows a check + ring). Drives Material You accent across the app.

### 8b. Tabs & Layout
- [ ] **Toggle to add/remove the Multi-Video tab** (appears after Slideshow).
- [ ] **Toggle to add/remove the Album tab** (appears after Multi-Video / Slideshow).
- [ ] **Reorder the bottom tabs** (move up/down) and **show/hide** each tab.
- [ ] **Gallery is default and locked** ("always", greyed — can't be hidden). Settings/More also always present.
- [ ] App always opens on Gallery regardless of order.

### 8c. Source Folders
- [ ] Lists **main folders** (bold headers, known to the user — e.g. Pictures, MyFiles) with their **subfolders** below.
- [ ] **"Other (found)"** group = stray/one-off folders discovered to contain media, so the user can identify them.
- [ ] Each folder is a **checkbox** (multi-select which folders to include).
- [ ] Each main folder header has an **expand/collapse (up/down chevron)** to minimize/maximize its subfolder list (so long lists aren't tiring to scroll).

### 8d. File Types
- [ ] A settings category that lists **file types actually found in the selected folders** (not all possible types) — e.g. gif, png, jpg, mp4, mp3, webp, etc.
- [ ] **Multi-select which types to show.**
- [ ] **Unsupported/non-media types (e.g. pdf) are shown greyed out** with a "not media" tag — visible for transparency (to show the app scanned them) but not selectable.

### 8e. Playback & Safety
- [ ] **Don't loop videos** toggle.
- [ ] **Disable 'Swipe up to Delete'** toggle.
- [ ] **Disable editing & deleting media entirely** toggle — protects files from accidental permanent modification/deletion. (Zoom/normal viewing still works — only edit/delete is blocked; delete actions become greyed/disabled everywhere.)

### 8f. Storage & Data
- [ ] **Favourites folder** toggle — when ON, favourites are **copied from their original location to a chosen folder**, and the Favourites tab uses that folder (faster loading). When OFF, the app just remembers which are favourites and shows them normally. Shows the chosen path + edit when enabled.
- [ ] **Hidden Folders** → opens a dialog to choose device folders that Android normally keeps hidden but that contain media to index.
- [ ] **Export settings** — exports current settings including other tabs' settings (recents time window, slideshow custom speed, number of columns/rows, etc.).
- [ ] **Download favourites (.zip)** — exports a **copy** of all favourite media into a zip (copies originals, never moves them). (Also acceptable: copy to a chosen folder.)
- [ ] **Import settings / favourites** — import button for the above two exports.

### 8g. Footer
- [ ] Growth/help blurb text.
- [ ] **GitHub and Rate buttons side by side** (two buttons in a row, with appropriate logos/icons — not two stacked full-width buttons).
- [ ] **"Made with ❤️ by Sandeep Kiran (Mousy)"** at the very bottom (red heart, not purple).

---

## 9. Cross-cutting behaviors to verify

- [ ] Every gesture actually works in the built app (swipe-to-randomize + back history, pinch-to-zoom columns, double-tap favourite, hold-2.5s multi-select, tap-to-toggle chrome, swipe-up delete).
- [ ] Delete anywhere → confirm dialog → Undo snackbar (never a silent permanent delete).
- [ ] "Disable editing & deleting" gates ALL delete/edit entry points (viewer menu, multi-select, swipe-up).
- [ ] Filetype and day-window filters apply consistently across Gallery, Favourites, Recent, and Albums.

---

## 10. Things the prototype could only simulate — wire to real Android APIs
- Real folder access & indexing (MediaStore + Storage Access Framework), including hidden folders.
- Actual image loading + full-resolution zoom, and real video playback (Media3/ExoPlayer).
- True multi-touch pinch gesture and device orientation sensor for landscape auto-rotate.
- Real file operations: copy favourites to folder, zip export, settings export/import.
- Dynamic color from wallpaper (Material You) if you want system-driven accents in addition to the pastel presets.
