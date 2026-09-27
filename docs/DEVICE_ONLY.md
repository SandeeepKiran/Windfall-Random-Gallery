# Device-only features

Windfall is designed to run on a physical Android device or emulator with real storage access. The following capabilities **require device APIs** and are marked `DEVICE-ONLY` in source code.

## Folder access

| Feature | APIs | Files |
|---------|------|-------|
| MediaStore scan | `ContentResolver`, `MediaStore.Images/Video` | `MediaRepository.kt` |
| SAF tree folders | `OpenDocumentTree`, `DocumentFile` | `MediaRepository.kt`, `GalleryApp.kt` |
| Persistable URI permissions | `takePersistableUriPermission` | `GalleryApp.kt`, `MainActivity.kt` |

## Permissions & privacy (API-dependent)

| API level | Behavior |
|-----------|----------|
| **30–32** | `READ_EXTERNAL_STORAGE` |
| **33–35** | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` |
| **36+** | Granular media + `READ_MEDIA_VISUAL_USER_SELECTED`; Photo Picker preferred for partial access |

See `MediaPermissions.kt` and `AndroidVersionGate.kt`.

## Video playback

| Feature | APIs | Files |
|---------|------|-------|
| Slideshow / viewer video | Media3 `ExoPlayer`, `ContentFrame` | `FullscreenViewer.kt` |
| Multi-Video wall | One ExoPlayer per tile, `ContentFrame` on a TextureView, sound focus via `AudioFocusRequest` | `multivideo/WallPlayers.kt`, `multivideo/MultiVideoWall.kt` |
| Multi-Video picking | Photo picker (`PickMultipleVisualMedia`, ordered), `ACTION_GET_CONTENT` in a chooser | `multivideo/VideoPicker.kt` |
| Screen direction | `ActivityInfo.SCREEN_ORIENTATION_*` | `multivideo/MultiVideoWall.kt`, `MainActivity.kt` |

## Gestures

| Feature | Implementation |
|---------|------------------|
| Pinch column density (1–6) | `detectTransformGestures` → `GalleryViewModel.adjustColumnsFromPinch` |
| Grid swipe shuffle | Horizontal drag in `MediaGrid.kt` |
| Viewer swipe nav / delete | `FullscreenViewer.kt` |

## File copy & export

| Feature | APIs | Files |
|---------|------|-------|
| Favourites zip export | `ZipOutputStream`, `FileProvider` | `FavouritesExporter.kt` |
| Favourites folder sync | SAF copy/delete in tree | `FavouritesFolderSync.kt` |
| Settings export/import | JSON via DataStore + file picker | `SettingsRepository.kt` |

## What works without a device

- UI preview in Android Studio (@Preview composables can be added)
- Unit logic for filters, tab order, slideshow timing math
- Theme/accent rendering

## What does **not** work on desktop alone

- Scanning real photos/videos
- SAF folder picker results
- ExoPlayer hardware decode (emulator dependent)
- Zip export of real files
