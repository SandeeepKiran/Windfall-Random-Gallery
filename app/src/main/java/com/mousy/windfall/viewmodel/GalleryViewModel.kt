package com.mousy.windfall.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mousy.windfall.data.media.FavouritesExporter
import com.mousy.windfall.data.media.FavouritesFolderSync
import com.mousy.windfall.data.media.MediaRepository
import com.mousy.windfall.data.model.AccentColor
import com.mousy.windfall.data.model.AppSettings
import com.mousy.windfall.data.model.AppTab
import com.mousy.windfall.data.model.FavWindow
import com.mousy.windfall.data.model.FileTypeFilter
import com.mousy.windfall.data.model.GridMode
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.data.model.MediaType
import com.mousy.windfall.data.model.MultiVideoState
import com.mousy.windfall.data.model.PageWindow
import com.mousy.windfall.data.model.SamplingDefaults
import com.mousy.windfall.data.model.ShuffleDeck
import com.mousy.windfall.data.model.SlideshowSpeeds
import com.mousy.windfall.data.model.SnackMessage
import com.mousy.windfall.data.model.ThemeMode
import com.mousy.windfall.data.model.newShuffleSeed
import com.mousy.windfall.data.model.sanitized
import com.mousy.windfall.data.model.seededMixedSample
import com.mousy.windfall.data.model.seededSample
import com.mousy.windfall.data.preferences.SettingsRepository
import coil3.SingletonImageLoader
import com.mousy.windfall.data.media.MediaIndexCache
import com.mousy.windfall.data.media.MediaTrash
import com.mousy.windfall.data.media.warmSystemThumbnail
import com.mousy.windfall.ui.components.gridThumbRequest
import com.mousy.windfall.util.AppVisibility
import com.mousy.windfall.util.LogCapture
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val mediaRepo = MediaRepository(application)
    private val indexCache = MediaIndexCache(application)
    private val favExporter = FavouritesExporter(application)
    private val favSync = FavouritesFolderSync(application)
    private val mediaTrash = MediaTrash(application)

    /** The trash (or restore) request Android is showing now, waiting for its answer. */
    private var pendingTrash: PendingTrash? = null

    /**
     * The one source of truth for settings once they are restored. Changes land here first
     * (atomically, so the background thumbnail farmer and a tap can't overwrite each other) and
     * are written to disk behind it by a single writer (see [settingsVersion]).
     */
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** Completes once settings are loaded from disk; nothing may scan or save before that. */
    private val settingsReady = CompletableDeferred<Unit>()

    /**
     * Bumped on every settings change. One collector saves whatever [_settings] holds at that
     * moment: a burst of changes costs one write, and the newest settings are always what ends
     * up on disk. Handing snapshots to the writer instead could let an older one land last.
     */
    private val settingsVersion = MutableStateFlow(0L)

    /** True once the first real screen can be drawn; the splash screen waits for it. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _folderFavourites = MutableStateFlow<List<MediaItem>>(emptyList())

    /** Favourites resolved from outside the selected folders (opt-in setting). */
    private val _globalFavourites = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _deletedKeys = MutableStateFlow<Set<String>>(emptySet())
    private val _discoveredFolders = MutableStateFlow<List<MediaRepository.FolderInfo>>(emptyList())

    /** Current random draw. One Long reproduces the whole gallery order (see [seededSample]). */
    private val _shuffleSeed = MutableStateFlow(newShuffleSeed())

    /**
     * Favourite ids frozen at shuffle time. The gallery's favourite-boosted draw reads THIS
     * set, not the live one, so tapping a heart never re-deals the page you're looking at —
     * the boost only updates on the next real shuffle.
     */
    private val _boostFavIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Grid geometry reported back by the UI: items per page (drives the swipe stride and the
     * viewer-close snap) and the thumbnail bucket in px (drives cache-warming with keys that
     * actually match what the grid will request).
     */
    @Volatile private var pageCapacity = SamplingDefaults.PREFETCH_PAGE
    @Volatile private var lastThumbBucketPx = 256

    /** Seed the NEXT app launch will use; its first page is pre-decoded during this session. */
    private var nextLaunchSeed = 0L
    private var warmedNextLaunchKey: String? = null

    /**
     * One stable random order per swipe-able list (see [ShuffleDeck]). Only touched from the
     * library pipeline, which runs one computation at a time.
     */
    private val galleryDeck = ShuffleDeck<MediaItem> { it.stableKey }
    private val favouritesDeck = ShuffleDeck<MediaItem> { it.stableKey }
    private val recentDeck = ShuffleDeck<MediaItem> { it.stableKey }

    /**
     * Per list, how many leading items the user may already have seen. New media is only ever
     * slotted in after this point, so no page that has been on screen changes. Written on the
     * main thread, read by the library pipeline.
     */
    @Volatile private var frontiers: Map<AppTab, Int> = emptyMap()

    private val _albumOpen = MutableStateFlow<String?>(null)

    /** Random order for the open album; null shows it newest-first. */
    private val _albumSeed = MutableStateFlow<Long?>(null)
    private val _viewerUi = MutableStateFlow(ViewerUi())
    private val _shellUi = MutableStateFlow(ShellUi())
    private val _transient = MutableStateFlow(TransientUi())

    private var slideshowJob: Job? = null
    private var snackJob: Job? = null
    private var mvOverlayJob: Job? = null
    private var refreshJob: Job? = null
    private var countsJob: Job? = null
    private var farmJob: Job? = null

    private var lastMediaScanKey: String? = null
    private var lastFolderDiscoveryKey: String? = null
    private var refreshToken = 0

    /**
     * The expensive half of the UI state. Kept apart from viewer/chrome/menu toggles so that
     * tapping a button never re-filters or re-sorts a 10k library, and computed on
     * [Dispatchers.Default] so it can never block the frame loop.
     */
    private val libraryState: StateFlow<LibraryState> = combine(
        _settings.map { it.toLibraryInputs() }.distinctUntilChanged(),
        combine(
            _allMedia,
            _folderFavourites,
            _deletedKeys,
            _discoveredFolders,
            _globalFavourites,
        ) { media, favs, deleted, folders, globalFavs ->
            LibrarySources(media, favs, deleted, folders, globalFavs)
        },
        combine(_shuffleSeed, _albumOpen, _albumSeed, _boostFavIds) { seed, album, albumSeed, boost ->
            SampleInputs(seed, album, albumSeed, boost)
        },
    ) { inputs, sources, sample ->
        buildLibraryState(inputs, sources, sample)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    val uiState: StateFlow<GalleryUiState> = combine(
        _settings,
        libraryState,
        _viewerUi,
        _shellUi,
        _transient,
    ) { settings, library, viewer, shell, transient ->
        assembleUiState(settings, library, viewer, shell, transient)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    init {
        viewModelScope.launch {
            // Read once. From here on the in-memory copy leads and disk follows. The old code
            // re-applied every DataStore emission, which could put an older snapshot back on
            // screen in the middle of a burst of changes and then save over the newer one.
            val restored = settingsRepo.settingsFlow.first().sanitized()
            _settings.value = restored
            settingsReady.complete(Unit)
            initShuffleSeed(restored)
            launch {
                settingsVersion.collect {
                    // A failed write (disk full, say) must not kill the writer: the next change
                    // retries with the newest settings.
                    try {
                        settingsRepo.save(_settings.value)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        android.util.Log.e("GalleryVM", "Saving settings failed", t)
                    }
                }
            }
            // Rescan whenever a change affects which files are in the library.
            _settings.collect { s ->
                val key = mediaScanKey(s)
                if (key != lastMediaScanKey) {
                    lastMediaScanKey = key
                    refreshMedia()
                }
            }
        }
        // Never hold the splash screen for long, whatever the library is doing.
        viewModelScope.launch {
            delay(READY_TIMEOUT_MS)
            _ready.value = true
        }
    }

    fun onPermissionsGranted() = refreshMedia()

    fun refreshMedia() {
        refreshJob?.cancel()
        // A cancelled scan must not clear the spinner belonging to the scan that replaced it.
        val token = ++refreshToken
        refreshJob = viewModelScope.launch {
            // Scanning before settings load would scan (and cache) an empty default library.
            settingsReady.await()
            // Debounce bursts such as ticking several folders, but never delay the first load.
            if (_allMedia.value.isNotEmpty()) delay(180)
            _transient.update { it.copy(loading = true) }
            try {
                val s = _settings.value
                val scanKey = mediaScanKey(s)
                // INSTANT COLD START: render the last session's library snapshot right away
                // (its thumbnails already sit in the OS cache), then let the real scan below
                // reconcile. A settings change invalidates the snapshot via its scan key.
                if (_allMedia.value.isEmpty()) {
                    val cached = indexCache.load(scanKey)
                    if (cached.isNotEmpty() && token == refreshToken && _allMedia.value.isEmpty()) {
                        _allMedia.value = cached
                        _transient.update { it.copy(loading = false) }
                        _ready.value = true
                    }
                }
                val media = mediaRepo.scanMedia(
                    selectedFolders = s.selectedFolders,
                    safTreeUris = s.safTreeUris,
                    hiddenFolders = s.hiddenFolders,
                    fileTypeFilters = effectiveFileTypes(s.fileTypes),
                )
                // Files in added (SAF) folders come back with stable keys now; carry favourites
                // saved under the old unstable keys across before the hearts are drawn.
                migrateSafFavouriteKeys(media)
                _allMedia.value = media
                _ready.value = true
                indexCache.save(scanKey, media)
                // Which folders exist on the device doesn't change when you tick one of them,
                // so this device-wide walk only reruns when the hidden-folder rules change.
                val folderKey = s.hiddenFolders.entries
                    .sortedBy { it.key }
                    .joinToString(",") { "${it.key}=${it.value}" }
                if (folderKey != lastFolderDiscoveryKey || _discoveredFolders.value.isEmpty()) {
                    lastFolderDiscoveryKey = folderKey
                    _discoveredFolders.value = mediaRepo.discoverFolders(s.hiddenFolders)
                }
                autoConfigureFileTypes(media, s)
                refreshFolderFavourites(_settings.value)
                refreshGlobalFavourites(_settings.value)
                // Session idle-time work: pre-decode next launch's first page, then start the
                // whole-library thumbnail sweep (see docs on each).
                maybeWarmNextLaunchPage()
                startThumbnailFarmer()
                // File-type counts are a separate, cancellable pass (Settings-only data).
                if (_settings.value.discoveredFileTypeCounts.isEmpty() && media.isNotEmpty()) {
                    refreshFileTypeCounts()
                }
            } finally {
                if (token == refreshToken) _transient.update { it.copy(loading = false) }
            }
        }
    }

    /**
     * Recounts every extension under the selected folders. This walks the whole library, so it
     * runs on demand rather than as part of loading the gallery; Settings shows the previous
     * numbers until it finishes.
     */
    fun refreshFileTypeCounts() {
        if (countsJob?.isActive == true) return
        countsJob = viewModelScope.launch {
            _transient.update { it.copy(countsRefreshing = true) }
            try {
                val s = _settings.value
                val counts = mediaRepo.discoverExtensionCounts(
                    selectedFolders = s.selectedFolders,
                    safTreeUris = s.safTreeUris,
                    hiddenFolders = s.hiddenFolders,
                )
                persistSettings {
                    it.copy(
                        discoveredFileTypeCounts = counts,
                        fileTypeCountsScannedAtMs = System.currentTimeMillis(),
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                android.util.Log.e("GalleryVM", "File-type count scan failed", t)
                showSnack("Could not refresh file counts")
            } finally {
                _transient.update { it.copy(countsRefreshing = false) }
            }
        }
    }

    /** When Favourites-folder mode is on, load media from that SAF tree for the Favourites tab. */
    private suspend fun refreshFolderFavourites(s: AppSettings = _settings.value) {
        if (s.copyFavs && s.copyFavTreeUri.isNotBlank()) {
            _folderFavourites.value = mediaRepo.scanSafTree(
                treeUri = s.copyFavTreeUri,
                fileTypeFilters = effectiveFileTypes(s.fileTypes),
            )
        } else {
            _folderFavourites.value = emptyList()
        }
    }

    /**
     * Resolves favourites that live outside the selected folders. Only the out-of-scope ones need
     * a scan — favourites inside the selection already come from the normal library pass.
     */
    private suspend fun refreshGlobalFavourites(s: AppSettings = _settings.value) {
        if (!s.showAllFavourites || s.favIds.isEmpty()) {
            _globalFavourites.value = emptyList()
            return
        }
        _globalFavourites.value = runCatching {
            mediaRepo.scanFavouritesByKey(s.favIds, s.hiddenFolders)
        }.getOrElse {
            android.util.Log.e("GalleryVM", "All-folder favourites scan failed", it)
            emptyList()
        }
    }

    fun toggleShowAllFavourites() {
        persistSettings { it.copy(showAllFavourites = !it.showAllFavourites) }
        viewModelScope.launch { refreshGlobalFavourites() }
    }

    private fun usesFavouritesFolder(s: AppSettings = _settings.value): Boolean =
        s.copyFavs && s.copyFavTreeUri.isNotBlank()

    private fun mediaScanKey(s: AppSettings): String =
        listOf(
            s.selectedFolders.sorted().joinToString(","),
            s.safTreeUris.sorted().joinToString(","),
            s.copyFavs.toString(),
            s.copyFavTreeUri,
            s.fileTypes.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" },
            s.hiddenFolders.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" },
        ).joinToString("|")

    /**
     * Every app open starts a fresh random order — a reopen should feel new — but the order was
     * already *chosen* by the previous session ([AppSettings.nextLaunchSeed]), which also
     * pre-decoded its first page into the caches. "New every time" and "instant every time"
     * coexist because the randomness is decided before it's needed.
     */
    private fun initShuffleSeed(s: AppSettings) {
        _boostFavIds.value = s.favIds
        _shuffleSeed.value = s.nextLaunchSeed.takeIf { it != 0L } ?: newShuffleSeed()
        // Decide next launch's order right now; its first page is warmed once the scan lands.
        nextLaunchSeed = newShuffleSeed()
        persistSettings { it.copy(nextLaunchSeed = nextLaunchSeed) }
    }

    /** The grid reports its real geometry; swipe stride and cache-warm keys follow it. */
    fun onPageGeometryChanged(capacity: Int, thumbBucketPx: Int) {
        pageCapacity = capacity.coerceAtLeast(1)
        if (thumbBucketPx > 0) lastThumbBucketPx = thumbBucketPx
        maybeWarmNextLaunchPage()
    }

    /**
     * "We KNOW the app will be reopened" optimisation: while this session idles, decode the
     * first page of the NEXT launch's random order, using the exact request + cache keys the
     * grid will ask for on that launch. One-shot per (seed, bucket, library) combination.
     */
    private fun maybeWarmNextLaunchPage() {
        val seed = nextLaunchSeed
        if (seed == 0L) return
        val media = _allMedia.value
        if (media.isEmpty()) return
        val warmKey = "$seed:$lastThumbBucketPx:${media.size}"
        if (warmKey == warmedNextLaunchKey) return
        warmedNextLaunchKey = warmKey
        val bucket = lastThumbBucketPx
        val count = pageCapacity.coerceAtLeast(SamplingDefaults.PREFETCH_PAGE)
        viewModelScope.launch(Dispatchers.Default) {
            val s = _settings.value
            val types = effectiveFileTypes(s.fileTypes)
            val deleted = _deletedKeys.value
            val pool = media.filter { it.stableKey !in deleted && passType(it, types) }
            if (pool.isEmpty()) return@launch
            val boosted = pool.filter { it.stableKey in s.favIds }
            val regular = if (boosted.isEmpty()) pool else pool.filter { it.stableKey !in s.favIds }
            val firstPage = seededMixedSample(
                regular = regular,
                boosted = boosted,
                seed = seed,
                count = count,
                boostedRate = SamplingDefaults.FAVOURITE_RATE,
            )
            val app = getApplication<Application>()
            val loader = SingletonImageLoader.get(app)
            firstPage.forEach { item ->
                if (item.mediaType != MediaType.AUDIO) {
                    loader.enqueue(gridThumbRequest(app, item, bucket))
                }
            }
        }
    }

    /**
     * THE THUMBNAIL FARMER — idle-CPU work while the user just looks at things. A gentle
     * trickle asks the OS to generate (and persist, on disk) a thumbnail for every item in
     * the library. After a few sessions the whole library is permanently "hot": no grid cell
     * ever decodes an original file again. IO-bound, one item per ~150 ms, pauses whenever
     * the app leaves the foreground, touches no app memory cache (kind to low-RAM phones),
     * and resumes across sessions from a checkpoint keyed to the library fingerprint.
     */
    private fun startThumbnailFarmer() {
        farmJob?.cancel()
        val media = _allMedia.value
        if (media.isEmpty()) return
        val fingerprint =
            "${media.size}:${media.firstOrNull()?.stableKey}:${media.lastOrNull()?.stableKey}"
        var position = if (_settings.value.farmFingerprint == fingerprint) {
            _settings.value.farmPosition
        } else {
            0
        }
        if (position >= media.size) return // this library is fully farmed
        val bucket = lastThumbBucketPx
        val app = getApplication<Application>()
        farmJob = viewModelScope.launch(Dispatchers.IO) {
            while (position < media.size && isActive) {
                if (!AppVisibility.visible) {
                    // User left — stand down, keep the checkpoint.
                    delay(2_000)
                    continue
                }
                val item = media[position]
                if (item.mediaType != MediaType.AUDIO) {
                    warmSystemThumbnail(app, item.uri, bucket)
                }
                position++
                if (position % FARM_CHECKPOINT == 0) {
                    val checkpoint = position
                    persistSettings {
                        it.copy(farmFingerprint = fingerprint, farmPosition = checkpoint)
                    }
                }
                delay(FARM_STEP_MS)
            }
            if (isActive) {
                val checkpoint = position
                persistSettings {
                    it.copy(farmFingerprint = fingerprint, farmPosition = checkpoint)
                }
            }
        }
    }

    /**
     * Files in added (SAF) folders used to take their id from a counter in walk order, so after
     * any change to such a folder the same file could come back under a different key and its
     * favourite was silently lost. Keys are stable now; this moves favourites saved under an
     * old key onto the file's current key, matched by the file's document address.
     */
    private fun migrateSafFavouriteKeys(media: List<MediaItem>) {
        if (_settings.value.favIds.isEmpty()) return
        val safByUri = media.filter { it.id < 0 }.associateBy { it.uri.toString() }
        if (safByUri.isEmpty()) return
        fun currentKey(key: String): String =
            MediaItem.uriOfSafKey(key)?.let { safByUri[it]?.stableKey } ?: key
        if (_settings.value.favIds.none { currentKey(it) != it }) return
        persistSettings { s -> s.copy(favIds = s.favIds.mapTo(HashSet()) { currentKey(it) }) }
    }

    private suspend fun autoConfigureFileTypes(media: List<MediaItem>, s: AppSettings) {
        if (media.isEmpty()) return
        val known = s.fileTypes
        val discovered = HashSet<String>()
        for (item in media) discovered.add(item.extension)
        val missing = discovered.filter { it.isNotBlank() && it !in known }
        if (missing.isEmpty()) return
        val updated = known.toMutableMap()
        missing.forEach { ext -> updated[ext] = ext in SlideshowSpeeds.supportedExtensions }
        persistSettings { it.copy(fileTypes = updated) }
        // Adopt the new key so the settings collector doesn't treat this as a fresh rescan.
        lastMediaScanKey = mediaScanKey(_settings.value)
    }

    private fun effectiveFileTypes(fileTypes: Map<String, Boolean>): Map<String, Boolean> {
        if (fileTypes.isNotEmpty()) return fileTypes
        return SlideshowSpeeds.supportedExtensions.associateWith { true }
    }

    fun selectTab(tab: AppTab) {
        if (tab == AppTab.SLIDESHOW) {
            startSlideshow()
            return
        }
        slideshowJob?.cancel()
        when (tab) {
            AppTab.GALLERY -> {
                // Re-tapping Gallery while already on it re-deals; arriving from another tab
                // (or closing the viewer) keeps your place — continuity over surprise.
                val reDeal = _shellUi.value.tab == AppTab.GALLERY && !_viewerUi.value.open
                closeViewerState()
                _albumOpen.value = null
                _shellUi.update {
                    it.copy(tab = AppTab.GALLERY, selectMode = false, selectedKeys = emptySet())
                }
                if (reDeal) shuffleGrid()
            }
            else -> {
                closeViewerState()
                if (tab != AppTab.ALBUM) _albumOpen.value = null
                _shellUi.update { it.copy(tab = tab, selectMode = false, selectedKeys = emptySet()) }
                if (tab == AppTab.MULTIVIDEO) showMultiVideoOverlay()
            }
        }
    }

    /**
     * The Slideshow tab starts where the eyes are, never back at page 1:
     *  - from a grid, at the top-left item of the page (swipe) or top row (scroll) on screen;
     *  - from the fullscreen viewer, at the item being viewed.
     * It then carries on through everything after that point.
     */
    private fun startSlideshow() {
        val viewer = _viewerUi.value
        when {
            viewer.open && viewer.slideshowMode -> Unit // already running; leave it be
            viewer.open -> openViewer(
                keys = viewer.keys,
                index = viewer.index,
                autoPlay = true,
                slideshowMode = true,
                sourceTab = viewer.sourceTab,
            )
            else -> {
                val listTab = listTabFor(_shellUi.value.tab)
                val list = tabSourceList(listTab)
                if (list.isEmpty()) return
                openViewer(
                    keys = list,
                    index = cursorFor(listTab).coerceIn(0, list.lastIndex),
                    autoPlay = true,
                    slideshowMode = true,
                    sourceTab = listTab,
                )
            }
        }
    }

    private fun closeViewerState() {
        _viewerUi.update {
            it.copy(open = false, playing = false, menuOpen = false, speedMenuOpen = false, detailsOpen = false)
        }
    }

    /** Switching modes keeps your place: the top-left item stays in view. */
    fun toggleGridMode() {
        val toSwipe = _settings.value.gridMode == GridMode.SCROLL
        if (toSwipe) {
            // Scroll mode's top row can sit anywhere; snap to the page that contains it.
            val capacity = pageCapacity.coerceAtLeast(1)
            setCursor(AppTab.GALLERY, (cursorFor(AppTab.GALLERY) / capacity) * capacity)
            setCursor(AppTab.FAV, (cursorFor(AppTab.FAV) / capacity) * capacity)
        }
        // Recent changes ORDER between modes (shuffled vs newest-first), so its old position
        // points at unrelated items; start it over.
        setCursor(AppTab.RECENT, 0)
        persistSettings { it.copy(gridMode = if (toSwipe) GridMode.SWIPE else GridMode.SCROLL) }
    }

    fun cycleColumns() = persistSettings {
        it.copy(columns = if (it.columns >= 6) 1 else it.columns + 1)
    }

    /** DEVICE-ONLY: Pinch gesture sets an absolute column count 1–6. */
    fun setColumns(count: Int) {
        val clamped = count.coerceIn(1, 6)
        if (clamped == _settings.value.columns) return
        persistSettings { it.copy(columns = clamped) }
    }

    /**
     * Shuffle button (or re-tapping Gallery): deal a brand-new random order from page one. The
     * only way, besides a fresh app launch, that the gallery's order changes.
     */
    fun shuffleGrid() {
        _boostFavIds.value = _settings.value.favIds
        _shuffleSeed.value = newShuffleSeed()
        // Every deck re-deals on the new seed, so nothing of the new orders has been seen yet.
        frontiers = emptyMap()
        setCursor(AppTab.GALLERY, 0)
    }

    fun onGridSwipe(direction: Int) {
        if (_settings.value.gridMode == GridMode.SWIPE) swipePage(direction)
    }

    /**
     * CONTINUITY CORE. One deal per shuffle = one fixed random order (held still by
     * [ShuffleDeck]); a page swipe just slides a cursor window through it. Back re-shows
     * exactly what you saw (still cached, so it's instant); forward reveals the next page.
     * Back from page 1 wraps to the unseen tail and forward from the last page wraps to page
     * 1, so both directions are endless. [PageWindow] holds the rule, shared with the grid.
     */
    private fun swipePage(direction: Int) {
        val tab = _shellUi.value.tab
        val size = listFor(tab).size
        if (size == 0) return
        val capacity = pageCapacity.coerceAtLeast(1)
        val cursor = cursorFor(tab)
        val target = if (direction < 0) {
            PageWindow.previous(cursor, size, capacity)
        } else {
            PageWindow.next(cursor, size, capacity)
        }
        if (target != null) setCursor(tab, target)
    }

    private fun cursorFor(tab: AppTab): Int = _shellUi.value.pageCursors[tab] ?: 0

    private fun setCursor(tab: AppTab, value: Int) {
        val cursor = value.coerceAtLeast(0)
        _shellUi.update { it.copy(pageCursors = it.pageCursors + (tab to cursor)) }
        // The page at the cursor is on screen and the one after it is parked, decoded.
        noteSeen(tab, cursor + 2 * pageCapacity.coerceAtLeast(1))
    }

    /** Raises [tab]'s frontier: everything before [index] may have been seen. */
    private fun noteSeen(tab: AppTab, index: Int) {
        val current = frontiers[tab] ?: 0
        if (index > current) frontiers = frontiers + (tab to index)
    }

    /**
     * Scroll-mode position. Every row that passes moves the frontier (a plain field, no UI
     * update); only a settled position is stored as the tab's cursor, which the slideshow
     * starts from and a recreated grid scrolls back to.
     */
    fun onGridScrolled(tab: AppTab, topLeft: Int, lastVisible: Int, settled: Boolean) {
        // A screen's worth below the fold is composed and decoding already.
        noteSeen(tab, lastVisible + 1 + pageCapacity.coerceAtLeast(1))
        if (settled && topLeft != cursorFor(tab)) {
            _shellUi.update { it.copy(pageCursors = it.pageCursors + (tab to topLeft.coerceAtLeast(0))) }
        }
    }

    fun toggleFavourite(key: String) {
        viewModelScope.launch {
            val s = _settings.value
            val libraryItem = _allMedia.value.find { it.stableKey == key }
            val folderItem = _folderFavourites.value.find { it.stableKey == key }

            // Un-heart a file shown from the Favourites folder (Favourites tab, folder mode).
            if (folderItem != null && usesFavouritesFolder(s) && libraryItem == null) {
                val name = folderItem.displayName
                val library = libraryState.value.lookup
                // Its original, if one is favourited, stops being a favourite too.
                persistSettings { cur ->
                    cur.copy(favIds = cur.favIds.filterTo(HashSet()) { library[it]?.displayName != name })
                }
                removeFolderCopy(folderItem, explainIfNotOurs = true)
                return@launch
            }

            val item = libraryItem ?: folderItem ?: return@launch
            if (isFavouriteItem(item)) {
                persistSettings { it.copy(favIds = it.favIds - key) }
                // Copies are named after their original, so two favourites with the same file
                // name share one copy; it goes only when neither is a favourite any more.
                val library = libraryState.value.lookup
                val nameStillFavourite = _settings.value.favIds.any {
                    library[it]?.displayName == item.displayName
                }
                if (usesFavouritesFolder(s) && !nameStillFavourite) {
                    _folderFavourites.value
                        .firstOrNull { it.displayName == item.displayName }
                        ?.let { removeFolderCopy(it, explainIfNotOurs = false) }
                }
            } else {
                persistSettings { it.copy(favIds = it.favIds + key) }
                if (usesFavouritesFolder(s)) {
                    copyIntoFavouritesFolder(s.copyFavTreeUri, listOf(item))
                }
            }
        }
    }

    /**
     * A heart means "this key is a favourite", or, in Favourites-folder mode, "this is one of
     * the files in that folder". A library photo is NOT a favourite just because the folder
     * holds a file with the same name: that name match is what turned hearting an original
     * into deleting it.
     */
    fun isFavouriteItem(item: MediaItem): Boolean {
        val s = _settings.value
        if (item.stableKey in s.favIds) return true
        if (!usesFavouritesFolder(s)) return false
        return _folderFavourites.value.any { it.stableKey == item.stableKey }
    }

    /** Copies [items] into the Favourites folder and remembers which copies are ours. */
    private suspend fun copyIntoFavouritesFolder(treeUri: String, items: List<MediaItem>) {
        val made = favSync.copyAll(treeUri, items).map { it.toString() }
        if (made.isNotEmpty()) persistSettings { it.copy(favCopyUris = it.favCopyUris + made) }
        refreshFolderFavourites()
    }

    /**
     * Removes a file from the Favourites folder, but only a copy Windfall made itself, and never
     * while "Disable all delete options" is on. A file the user put there stays.
     */
    private suspend fun removeFolderCopy(file: MediaItem, explainIfNotOurs: Boolean) {
        val s = _settings.value
        val address = file.uri.toString()
        when {
            address !in s.favCopyUris -> if (explainIfNotOurs) {
                showSnack("Kept \"${file.displayName}\": Windfall only removes copies it made.")
            }
            s.deletesDisabled -> Unit // the copy stays; it's still ours to remove later
            else -> favSync.removeCopy(file.uri)
                .onSuccess { persistSettings { it.copy(favCopyUris = it.favCopyUris - address) } }
                .onFailure { android.util.Log.w("GalleryVM", "Could not remove favourite copy", it) }
        }
        refreshFolderFavourites()
    }

    fun toggleSelect(key: String) {
        _shellUi.update {
            val sel = it.selectedKeys.toMutableSet()
            if (!sel.remove(key)) sel.add(key)
            it.copy(selectedKeys = sel, selectMode = sel.isNotEmpty())
        }
    }

    fun enterSelectMode(key: String) {
        _shellUi.update { it.copy(selectMode = true, selectedKeys = it.selectedKeys + key) }
    }

    fun exitSelectMode() {
        _shellUi.update { it.copy(selectMode = false, selectedKeys = emptySet()) }
    }

    fun favouriteSelected() {
        val keys = _shellUi.value.selectedKeys
        persistSettings { it.copy(favIds = it.favIds + keys) }
        // Leave select mode now; copying many files into the Favourites folder can take a while.
        exitSelectMode()
        val s = _settings.value
        if (usesFavouritesFolder(s)) {
            viewModelScope.launch {
                copyIntoFavouritesFolder(s.copyFavTreeUri, keys.mapNotNull(::mediaByKey))
            }
        }
    }

    fun deleteSelected() = requestDelete(_shellUi.value.selectedKeys.toList())

    fun openViewer(
        keys: List<String>,
        index: Int,
        autoPlay: Boolean = false,
        slideshowMode: Boolean = autoPlay,
        /** The tab whose list [keys] is; its grid follows the viewer when it closes. */
        sourceTab: AppTab? = null,
    ) {
        val deleted = _deletedKeys.value
        val live = if (deleted.isEmpty()) keys else keys.filter { it !in deleted }
        if (live.isEmpty()) return
        val currentTab = _shellUi.value.tab
        _viewerUi.update { v ->
            val ret = if (v.open) v.returnTab else {
                if (currentTab == AppTab.SLIDESHOW) AppTab.GALLERY else currentTab
            }
            v.copy(
                open = true,
                keys = live,
                index = index.coerceIn(0, live.lastIndex),
                chrome = true,
                menuOpen = false,
                speedMenuOpen = false,
                detailsOpen = false,
                slideshowMode = slideshowMode,
                playing = slideshowMode && autoPlay,
                returnTab = ret,
                sourceTab = sourceTab,
            )
        }
        // View mode keeps the source tab selected; slideshow mode selects Slideshow.
        if (slideshowMode) _shellUi.update { it.copy(tab = AppTab.SLIDESHOW) }
        noteViewerPosition()
        if (_viewerUi.value.playing) scheduleSlideshow() else slideshowJob?.cancel()
    }

    fun closeViewer() {
        slideshowJob?.cancel()
        val v = _viewerUi.value
        val returnTab = v.returnTab
        if (v.open) landGridOnViewerItem(v)
        _viewerUi.update {
            it.copy(
                open = false,
                playing = false,
                slideshowMode = false,
                menuOpen = false,
                speedMenuOpen = false,
                detailsOpen = false,
            )
        }
        _shellUi.update { it.copy(tab = returnTab) }
    }

    /**
     * Closing the viewer continues exactly where the eyes were: the source grid shows the item
     * that was last on screen. Found by key rather than by index, because the grid's list can
     * have gained new media while the viewer was open.
     */
    private fun landGridOnViewerItem(v: ViewerUi) {
        val tab = v.sourceTab ?: return
        val key = v.currentKey() ?: return
        val position = listFor(tab).indexOfFirst { it.stableKey == key }
        if (position < 0) return
        if (_settings.value.gridMode == GridMode.SWIPE && tab != AppTab.ALBUM) {
            // Only move when the item actually LEFT the visible page: pageCapacity can differ
            // while the viewer is open (the hidden tab bar makes the grid taller), so always
            // re-deriving the cursor from it shifted the window by a few rows on a plain
            // open→close.
            val capacity = pageCapacity.coerceAtLeast(1)
            val cursor = cursorFor(tab)
            if (position < cursor || position >= cursor + capacity) {
                setCursor(tab, (position / capacity) * capacity)
            }
        } else {
            // Scroll grids scroll to it only if it isn't on screen already.
            setCursor(tab, position)
        }
    }

    /** What the viewer shows counts as seen in its source list. */
    private fun noteViewerPosition() {
        val v = _viewerUi.value
        val tab = v.sourceTab ?: return
        // A few items ahead are prefetched in the viewer, so they count as seen as well.
        noteSeen(tab, v.index + VIEWER_LOOKAHEAD)
    }

    /** System back / predictive back — returns true if the event was consumed. */
    fun handleSystemBack(): Boolean {
        val viewer = _viewerUi.value
        val shell = _shellUi.value
        when {
            viewer.menuOpen -> _viewerUi.update { it.copy(menuOpen = false) }
            viewer.speedMenuOpen -> _viewerUi.update { it.copy(speedMenuOpen = false) }
            viewer.detailsOpen -> closeDetails()
            viewer.customSpeedOpen -> dismissCustomSpeed()
            shell.confirmResetSettings -> cancelResetSettings()
            shell.hiddenFoldersDialog -> closeHiddenFoldersDialog()
            _transient.value.multiVideo.pickerIndex != null -> closeMultiVideoPicker()
            viewer.open -> closeViewer()
            _albumOpen.value != null -> closeAlbum()
            shell.selectMode -> exitSelectMode()
            else -> return false
        }
        return true
    }

    fun toggleViewerChrome() {
        _viewerUi.update {
            val visible = !it.chrome
            it.copy(chrome = visible, chromeNonce = if (visible) it.chromeNonce + 1 else it.chromeNonce)
        }
    }

    fun noteViewerInteraction() {
        _viewerUi.update { if (it.chrome) it.copy(chromeNonce = it.chromeNonce + 1) else it }
    }

    fun toggleViewerMute() = _viewerUi.update { it.copy(muted = !it.muted) }

    fun viewerNavigate(delta: Int) {
        _viewerUi.update { v ->
            if (v.keys.isEmpty()) return@update v
            var idx = v.index + delta
            if (idx < 0) idx = v.keys.lastIndex
            if (idx > v.keys.lastIndex) idx = 0
            v.copy(index = idx)
        }
        afterViewerIndexChange()
    }

    /** The viewer's pager settled on an absolute index. No wrap — the pager clamps at ends. */
    fun viewerJumpTo(index: Int) {
        val v = _viewerUi.value
        if (v.keys.isEmpty()) return
        val idx = index.coerceIn(0, v.keys.lastIndex)
        if (idx == v.index) return
        _viewerUi.update { it.copy(index = idx) }
        afterViewerIndexChange()
    }

    /** Random access for the viewer's pager pages. */
    fun viewerItemAt(index: Int): MediaItem? {
        val key = _viewerUi.value.keys.getOrNull(index) ?: return null
        return libraryState.value.lookup[key]
    }

    private fun afterViewerIndexChange() {
        noteViewerPosition()
        // Keep chrome vanished if it was vanished — do not force-show on advance.
        if (_viewerUi.value.playing) scheduleSlideshow()
    }

    /** Called when a video finishes in the viewer (loop disabled). */
    fun onViewerVideoEnded() {
        if (_viewerUi.value.slideshowMode) {
            // Slideshow + no loop → advance to next item
            advanceSlideshowAfterVideo()
        }
        // View mode + no loop → stop (ExoPlayer already stopped)
    }

    private fun advanceSlideshowAfterVideo() {
        val v = _viewerUi.value
        // A paused slideshow shouldn't jump forward just because the video ran out.
        if (!v.playing) return
        if (v.keys.isEmpty()) {
            _viewerUi.update { it.copy(playing = false) }
            return
        }
        var next = v.index + 1
        if (next >= v.keys.size) {
            if (_settings.value.dontLoop) {
                // At end of list and "don't loop" — stop slideshow
                _viewerUi.update { it.copy(playing = false) }
                return
            }
            next = 0
        }
        _viewerUi.update { it.copy(index = next) }
        noteViewerPosition()
        if (_viewerUi.value.playing) scheduleSlideshow()
    }

    fun viewerSwipeUpDelete() {
        val s = _settings.value
        if (s.disableSwipeDelete) {
            showSnack("Swipe-up delete is off. Enable it in More → Playback & Safety.")
            return
        }
        val key = _viewerUi.value.currentKey() ?: return
        requestDelete(listOf(key))
    }

    fun togglePlayPause() {
        _viewerUi.update { it.copy(playing = !it.playing) }
        if (_viewerUi.value.playing) scheduleSlideshow() else slideshowJob?.cancel()
    }

    fun setSpeedIndex(index: Int) {
        if (index == SlideshowSpeeds.CUSTOM_INDEX) {
            val seconds = (_settings.value.customMs / 1000).toInt().coerceAtLeast(1)
            _viewerUi.update {
                it.copy(customSpeedOpen = true, customSpeedSeconds = seconds, speedMenuOpen = false)
            }
            return
        }
        persistSettings { it.copy(speedIdx = index) }
        _viewerUi.update { it.copy(speedMenuOpen = false) }
        if (_viewerUi.value.playing) scheduleSlideshow()
    }

    fun confirmCustomSpeed(seconds: Int) {
        val v = seconds.coerceAtLeast(1)
        persistSettings { it.copy(customMs = v * 1000L, speedIdx = SlideshowSpeeds.CUSTOM_INDEX) }
        _viewerUi.update { it.copy(customSpeedOpen = false) }
        if (_viewerUi.value.playing) scheduleSlideshow()
    }

    fun dismissCustomSpeed() = _viewerUi.update { it.copy(customSpeedOpen = false) }

    fun toggleSpeedMenu() =
        _viewerUi.update { it.copy(speedMenuOpen = !it.speedMenuOpen, menuOpen = false) }

    fun toggleViewerMenu() =
        _viewerUi.update { it.copy(menuOpen = !it.menuOpen, speedMenuOpen = false) }

    fun openDetails() = _viewerUi.update { it.copy(detailsOpen = true, menuOpen = false) }

    fun closeDetails() = _viewerUi.update { it.copy(detailsOpen = false) }

    fun shareCurrentItem(): Intent? {
        val item = currentViewerItem() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun requestDeleteCurrent() {
        _viewerUi.value.currentKey()?.let { key -> requestDelete(listOf(key)) }
    }

    /**
     * Delete = move to Android's trash, where a file stays restorable for about 30 days. Android
     * asks for confirmation itself, so the app shows no dialog of its own. A file MediaStore
     * doesn't know can't go to the trash; that one is only hidden until the app closes.
     */
    private fun requestDelete(keys: List<String>) {
        if (keys.isEmpty()) return
        if (_settings.value.deletesDisabled) {
            showDeleteDisabledPrompt()
            return
        }
        viewModelScope.launch {
            val lookup = libraryState.value.lookup
            val trashable = LinkedHashMap<String, Uri>()
            val hideOnly = ArrayList<String>()
            for (key in keys) {
                val address = (lookup[key] ?: mediaByKey(key))?.let { mediaTrash.trashableUri(it) }
                if (address != null) trashable[key] = address else hideOnly += key
            }
            if (hideOnly.isNotEmpty()) {
                hideItems(hideOnly)
                showSnack(
                    if (hideOnly.size > 1) {
                        "${hideOnly.size} files hidden until you close the app. They can't go to the trash."
                    } else {
                        "Hidden until you close the app. This file can't go to the trash."
                    },
                    "Undo",
                ) { unhideItems(hideOnly) }
            }
            if (trashable.isNotEmpty()) launchTrashPrompt(trashable, restoring = false)
        }
    }

    private fun launchTrashPrompt(files: Map<String, Uri>, restoring: Boolean) {
        val sender = runCatching {
            if (restoring) mediaTrash.restoreRequest(files.values) else mediaTrash.trashRequest(files.values)
        }.getOrElse {
            android.util.Log.e("GalleryVM", "Trash request failed", it)
            showSnack(if (restoring) "Couldn't restore from the trash" else "Couldn't move that to the trash")
            return
        }
        pendingTrash = PendingTrash(files, restoring)
        _transient.update { it.copy(trashPrompt = TrashPrompt(sender)) }
    }

    /** The UI has handed the prompt to Android; it must not launch it a second time. */
    fun onTrashPromptLaunched() = _transient.update { it.copy(trashPrompt = null) }

    /** Android's answer to a trash (or restore) prompt. */
    fun onTrashPromptResult(confirmed: Boolean) {
        val pending = pendingTrash ?: return
        pendingTrash = null
        if (!confirmed) return
        val keys = pending.files.keys
        if (pending.restoring) {
            unhideItems(keys)
            // Trashed files left the library; a scan brings them back, into their old places
            // (the deck kept their slots).
            refreshMedia()
            showSnack(if (keys.size > 1) "${keys.size} files restored" else "Restored")
            return
        }
        hideItems(keys)
        forgetTrashed(keys)
        showSnack(if (keys.size > 1) "${keys.size} files moved to trash" else "Moved to trash", "Undo") {
            launchTrashPrompt(pending.files, restoring = true)
        }
    }

    /** Takes [keys] out of every list and the open viewer right away, without a rescan. */
    private fun hideItems(keys: Collection<String>) {
        _shellUi.update { it.copy(selectedKeys = emptySet(), selectMode = false) }
        val hidden = _deletedKeys.value + keys
        _deletedKeys.value = hidden
        val live = _viewerUi.value.keys.filter { it !in hidden }
        if (_viewerUi.value.open && live.isEmpty()) {
            closeViewer()
        } else if (_viewerUi.value.open) {
            _viewerUi.update {
                it.copy(keys = live, index = it.index.coerceAtMost(live.lastIndex.coerceAtLeast(0)))
            }
        }
    }

    private fun unhideItems(keys: Collection<String>) {
        _deletedKeys.value = _deletedKeys.value - keys.toSet()
    }

    /**
     * Trashed files are gone from MediaStore. Drop them from the library and from the saved
     * index too, or the next cold start would draw them for a moment before the scan.
     */
    private fun forgetTrashed(keys: Set<String>) {
        val remaining = _allMedia.value.filter { it.stableKey !in keys }
        if (remaining.size == _allMedia.value.size) return
        _allMedia.value = remaining
        val scanKey = mediaScanKey(_settings.value)
        viewModelScope.launch { indexCache.save(scanKey, remaining) }
    }

    private fun showDeleteDisabledPrompt() {
        showSnack("Delete is disabled. Turn it back on in More → Playback & Safety.")
    }

    fun requestResetSettings() = _shellUi.update { it.copy(confirmResetSettings = true) }
    fun cancelResetSettings() = _shellUi.update { it.copy(confirmResetSettings = false) }

    /** Back to defaults, keeping favourites (and the record of which folder copies are ours). */
    fun confirmResetSettings() {
        _shellUi.update { it.copy(confirmResetSettings = false) }
        val previous = _settings.value
        persistSettings {
            AppSettings.defaults().copy(favIds = it.favIds, favCopyUris = it.favCopyUris)
        }
        // A rescan follows by itself: the folder selection changed.
        showSnack("Settings reset", "Undo") { persistSettings { previous } }
    }

    /** Favourites date window (independent of Recents). */
    fun setFavWindow(window: FavWindow) = persistSettings { s ->
        s.copy(favWindow = FavWindow.normalize(window))
    }

    /** Recents date window (independent of Favourites). */
    fun setRecentWindow(window: FavWindow) = persistSettings { s ->
        val canonical = FavWindow.normalize(window)
        s.copy(
            recentWindow = canonical,
            recentWindowDays = canonical.asRecentDays() ?: 365,
        )
    }

    fun toggleFavType(key: String) {
        persistSettings { s -> s.copy(favTypes = toggleTypeFilter(s.favTypes, key)) }
    }

    fun toggleRecentType(key: String) {
        persistSettings { s -> s.copy(recentTypes = toggleTypeFilter(s.recentTypes, key)) }
    }

    private fun toggleTypeFilter(ft: FileTypeFilter, key: String): FileTypeFilter {
        val updated = when (key) {
            "photo" -> ft.copy(photo = !ft.photo)
            "video" -> ft.copy(video = !ft.video)
            "gif" -> ft.copy(gif = !ft.gif)
            "audio" -> ft.copy(audio = !ft.audio)
            else -> ft
        }
        val anyOn = updated.photo || updated.video || updated.gif || updated.audio
        return if (anyOn) updated else ft
    }

    fun toggleFavTypeMenu() = _shellUi.update { it.copy(favTypeMenuOpen = !it.favTypeMenuOpen) }
    fun toggleRecentTypeMenu() = _shellUi.update { it.copy(recentTypeMenuOpen = !it.recentTypeMenuOpen) }

    fun toggleTheme() = persistSettings {
        it.copy(themeMode = if (it.themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    fun toggleAmoled() = persistSettings { it.copy(amoled = !it.amoled) }

    fun setAccent(accent: AccentColor) = persistSettings { it.copy(accent = accent) }

    fun toggleFolder(path: String) = persistSettings { s ->
        val sel = s.selectedFolders.toMutableSet()
        val normalized = MediaRepository.normalizeFolderPath(path)
        val existing = sel.find { MediaRepository.normalizeFolderPath(it).equals(normalized, ignoreCase = true) }
        if (existing != null) sel.remove(existing) else sel.add(normalized)
        s.copy(selectedFolders = sel)
    }

    fun addSafTreeUri(uri: String) = persistSettings { s ->
        s.copy(
            safTreeUris = s.safTreeUris + uri,
            selectedFolders = s.selectedFolders + "SAF:${Uri.parse(uri).lastPathSegment}",
        )
    }

    fun toggleFileType(ext: String) = persistSettings { s ->
        val map = s.fileTypes.toMutableMap()
        map[ext] = !(map[ext] ?: true)
        s.copy(fileTypes = map)
    }

    fun toggleBehaviour(key: String) = persistSettings { s ->
        when (key) {
            "dontLoop" -> s.copy(dontLoop = !s.dontLoop)
            "disableSwipeDelete" -> s.copy(disableSwipeDelete = !s.disableSwipeDelete)
            "disableDeleteOptions" -> s.copy(
                disableDeleteOptions = !s.disableDeleteOptions,
                disableEditDelete = !s.disableDeleteOptions,
            )
            "disableEditDelete" -> s.copy(
                disableEditDelete = !s.disableEditDelete,
                disableDeleteOptions = !s.disableEditDelete,
            )
            "hapticsEnabled" -> s.copy(hapticsEnabled = !s.hapticsEnabled)
            "thumbnailPadding" -> s.copy(thumbnailPadding = !s.thumbnailPadding)
            else -> s
        }
    }

    fun toggleCopyFavs() {
        persistSettings { it.copy(copyFavs = !it.copyFavs) }
        syncFavouritesFolder()
    }

    fun setCopyFavFolder(uri: String, path: String) {
        persistSettings { it.copy(copyFavTreeUri = uri, copyFavPath = path) }
        syncFavouritesFolder()
    }

    /** Brings the Favourites folder up to date with the current favourites (copy-in only). */
    private fun syncFavouritesFolder() {
        viewModelScope.launch {
            val s = _settings.value
            if (usesFavouritesFolder(s)) {
                copyIntoFavouritesFolder(s.copyFavTreeUri, _allMedia.value.filter { it.stableKey in s.favIds })
            } else {
                refreshFolderFavourites()
            }
        }
    }

    fun openHiddenFoldersDialog() = _shellUi.update { it.copy(hiddenFoldersDialog = true) }
    fun closeHiddenFoldersDialog() = _shellUi.update { it.copy(hiddenFoldersDialog = false) }

    fun toggleHiddenFolder(key: String) = persistSettings { s ->
        val map = s.hiddenFolders.toMutableMap()
        map[key] = !(map[key] ?: false)
        s.copy(hiddenFolders = map)
    }

    fun toggleGroupCollapsed(name: String) {
        _shellUi.update {
            val c = it.collapsedGroups.toMutableSet()
            if (!c.remove(name)) c.add(name)
            it.copy(collapsedGroups = c)
        }
    }

    fun moveTab(tab: AppTab, direction: Int) = persistSettings { s ->
        val order = s.tabOrder.toMutableList()
        val i = order.indexOf(tab)
        if (i < 0) return@persistSettings s
        val j = i + direction
        if (j !in order.indices) return@persistSettings s
        Collections.swap(order, i, j)
        s.copy(tabOrder = order)
    }

    fun toggleTabVisibility(tab: AppTab) {
        if (tab.locked) return
        persistSettings { s ->
            val hidden = s.tabHidden.toMutableSet()
            val enabling = hidden.contains(tab)
            if (enabling) hidden.remove(tab) else hidden.add(tab)
            val order = s.tabOrder.toMutableList()
            if (enabling && tab !in order) {
                val si = order.indexOf(AppTab.SETTINGS)
                order.add(if (si >= 0) si else order.size, tab)
            }
            val features = when (tab) {
                AppTab.MULTIVIDEO -> s.tabFeatures.copy(multivideo = enabling)
                AppTab.ALBUM -> s.tabFeatures.copy(album = enabling)
                else -> s.tabFeatures
            }
            s.copy(tabHidden = hidden, tabOrder = order, tabFeatures = features)
        }
        // Outside the update block: that block may run more than once under contention.
        if (tab in _settings.value.tabHidden && _shellUi.value.tab == tab) {
            _shellUi.update { it.copy(tab = AppTab.GALLERY) }
        }
    }

    fun openAlbum(path: String) {
        _albumSeed.value = null // each album opens newest-first
        _albumOpen.value = path
        setCursor(AppTab.ALBUM, 0)
        _shellUi.update { it.copy(tab = AppTab.ALBUM) }
    }

    fun closeAlbum() { _albumOpen.value = null }

    /**
     * The Shuffle button inside an album. It used to call the GALLERY shuffle, which re-dealt
     * the main gallery (and sent it back to page 1) while the album itself stayed in date order.
     */
    fun shuffleAlbum() {
        _albumSeed.value = newShuffleSeed()
        setCursor(AppTab.ALBUM, 0)
    }

    fun exportSettings(onResult: (String) -> Unit) {
        // The UI hands this to a save-as picker and snacks once it's actually written.
        onResult(settingsRepo.exportJson(_settings.value))
    }

    /** DEVICE-ONLY: Import settings JSON and/or a favourites zip from the document picker. */
    fun importSettingsOrFavourites(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull().orEmpty()
            }
            val mime = runCatching { resolver.getType(uri) }.getOrNull().orEmpty()
            val isZip = mime.contains("zip", ignoreCase = true) ||
                name.endsWith(".zip", ignoreCase = true)
            if (isZip) importFavouritesZip(uri) else importSettingsFile(uri)
        }
    }

    private suspend fun importSettingsFile(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val json = withContext(Dispatchers.IO) {
            runCatching {
                resolver.openInputStream(uri)?.use { it.readTextCapped(MAX_SETTINGS_FILE_BYTES) }
            }.getOrNull()
        }
        val previous = _settings.value
        when {
            json.isNullOrBlank() -> showSnack("Unable to read that file")
            !applyImportedSettings(json) -> showSnack("That isn't a Windfall settings file")
            else -> showSnack("Settings imported", "Undo") { persistSettings { previous } }
        }
    }

    private suspend fun importFavouritesZip(uri: Uri) {
        val s = _settings.value
        favExporter.importFavouritesZip(
            zipUri = uri,
            favTreeUri = s.copyFavTreeUri.takeIf { usesFavouritesFolder(s) },
        ).onSuccess { result ->
            val settingsApplied = result.settingsJson?.let(::applyImportedSettings) == true
            val wanted = result.displayNames.toSet()
            val matched = _allMedia.value.filter { it.displayName in wanted }.map { it.stableKey }
            if (matched.isNotEmpty()) persistSettings { it.copy(favIds = it.favIds + matched) }
            if (result.filesCopied > 0) refreshFolderFavourites()
            val parts = buildList {
                if (matched.isNotEmpty()) add("${matched.size} favourites")
                if (result.filesCopied > 0) add("${result.filesCopied} files")
                if (settingsApplied) add("settings")
            }
            showSnack(
                when {
                    parts.isNotEmpty() -> "Imported ${parts.joinToString(" + ")}"
                    result.displayNames.isNotEmpty() && !usesFavouritesFolder(s) ->
                        "No matching photos. Turn on a Favourites folder to restore the files."
                    else -> "Nothing to import"
                },
            )
        }.onFailure {
            showSnack(it.message ?: "Import failed")
        }
    }

    /**
     * Merges an exported settings file onto the CURRENT settings: only what the file mentions
     * changes. The old import rebuilt settings from the file alone, which reset folders, file
     * types, hidden folders and tabs to defaults. Returns false if [json] isn't a settings file.
     */
    private fun applyImportedSettings(json: String): Boolean {
        if (settingsRepo.mergeImport(_settings.value, json) == null) return false
        persistSettings { current -> settingsRepo.mergeImport(current, json) ?: current }
        return true
    }

    /** DEVICE-ONLY: Zip only the currently selected items (from select mode). */
    fun downloadSelectedZip(onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val keys = _shellUi.value.selectedKeys
            val items = keys.mapNotNull { key -> libraryState.value.lookup[key] ?: mediaByKey(key) }
            if (items.isEmpty()) {
                showSnack("Nothing selected")
                return@launch
            }
            favExporter.exportFavouritesZip(items).onSuccess { uri ->
                onResult(uri)
                showSnack("Zipping ${items.size} selected…")
                exitSelectMode()
            }.onFailure {
                showSnack(it.message ?: "Export failed")
            }
        }
    }

    fun downloadFavouritesZip(onResult: (Uri?) -> Unit) {
        viewModelScope.launch {
            val favs = libraryState.value.favourites
            favExporter.exportFavouritesZip(favs).onSuccess { uri ->
                onResult(uri)
                showSnack("Zipping ${favs.size} favourites…")
            }.onFailure {
                showSnack(it.message ?: "Export failed")
            }
        }
    }

    fun showSnack(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        snackJob?.cancel()
        val message = SnackMessage(text, actionLabel, action)
        _transient.update { it.copy(snack = message) }
        snackJob = viewModelScope.launch {
            // An Undo needs time to read and reach; a plain notice doesn't.
            delay(if (action != null) ACTION_SNACK_MS else SNACK_MS)
            _transient.update { if (it.snack === message) it.copy(snack = null) else it }
        }
    }

    fun dismissSnack() = _transient.update { it.copy(snack = null) }

    /**
     * Runs the action of the message the user actually tapped. Reading the current message
     * instead lost the tap when the timer cleared it a moment before the snackbar left the
     * screen: a visible Undo that did nothing.
     */
    fun runSnackAction(message: SnackMessage) {
        message.action?.invoke()
        _transient.update { if (it.snack === message) it.copy(snack = null) else it }
    }

    // Multi-video — DEVICE-ONLY playback wiring in UI layer
    fun setMultiVideoCount(count: Int) {
        updateMultiVideo { it.copy(count = count) }
        showMultiVideoOverlay()
    }

    fun toggleMultiVideoLandscape() {
        updateMultiVideo {
            val entering = !it.landscape
            it.copy(
                landscape = entering,
                chromeVisible = if (entering) false else true,
                overlayVisible = !entering,
            )
        }
        if (!_transient.value.multiVideo.landscape) showMultiVideoOverlay()
    }

    fun exitMultiVideoLandscape() {
        updateMultiVideo { it.copy(landscape = false, chromeVisible = true) }
        showMultiVideoOverlay()
    }

    fun onMultiVideoCellTap(index: Int) {
        val mv = _transient.value.multiVideo
        val cell = mv.cells.getOrNull(index) ?: return
        if (cell.uri == null) {
            openMultiVideoPicker(index)
            return
        }
        if (mv.landscape) {
            // Immersive: tap toggles chrome overlay
            updateMultiVideo { it.copy(chromeVisible = !it.chromeVisible, overlayVisible = !it.chromeVisible) }
            return
        }
        if (!mv.overlayVisible) {
            showMultiVideoOverlay()
        } else {
            toggleMultiVideoCellPlay(index)
        }
    }

    fun updateMultiVideoProgress(index: Int, progress: Float) {
        updateMultiVideo { mv ->
            if (index !in mv.cells.indices) return@updateMultiVideo mv
            val c = mv.cells[index]
            if (c.progress == progress) return@updateMultiVideo mv
            val cells = mv.cells.toMutableList()
            cells[index] = c.copy(progress = progress.coerceIn(0f, 1f))
            mv.copy(cells = cells)
        }
    }

    fun multiVideoPlayAll() {
        updateMultiVideo { mv ->
            mv.copy(cells = mv.cells.mapIndexed { i, c -> if (i < mv.count) c.copy(playing = true) else c })
        }
        showMultiVideoOverlay()
    }

    fun multiVideoPauseAll() {
        updateMultiVideo { mv ->
            mv.copy(cells = mv.cells.mapIndexed { i, c -> if (i < mv.count) c.copy(playing = false) else c })
        }
    }

    fun multiVideoMuteAll() {
        updateMultiVideo { mv ->
            val m = !mv.muteAll
            mv.copy(muteAll = m, cells = mv.cells.mapIndexed { i, c ->
                if (i < mv.count) c.copy(muted = m) else c
            })
        }
        showMultiVideoOverlay()
    }

    fun toggleMultiVideoCellPlay(index: Int) {
        updateMultiVideo { mv ->
            val cells = mv.cells.toMutableList()
            cells[index] = cells[index].copy(playing = !cells[index].playing)
            mv.copy(cells = cells)
        }
        showMultiVideoOverlay()
    }

    fun toggleMultiVideoCellMute(index: Int) {
        updateMultiVideo { mv ->
            val cells = mv.cells.toMutableList()
            cells[index] = cells[index].copy(muted = !cells[index].muted)
            mv.copy(cells = cells)
        }
    }

    fun openMultiVideoPicker(index: Int) = updateMultiVideo { it.copy(pickerIndex = index) }

    fun closeMultiVideoPicker() = updateMultiVideo { it.copy(pickerIndex = null) }

    fun assignMultiVideo(index: Int, item: MediaItem?) {
        updateMultiVideo { mv ->
            val cells = mv.cells.toMutableList()
            cells[index] = cells[index].copy(
                mediaId = item?.id,
                uri = item?.uri?.toString(),
                displayName = item?.displayName,
                isAudio = item?.mediaType == MediaType.AUDIO,
                playing = item != null,
                progress = 0f,
            )
            mv.copy(cells = cells, pickerIndex = null)
        }
        showMultiVideoOverlay()
    }

    fun assignMultiVideoUri(index: Int, uri: String, displayName: String?, isAudio: Boolean) {
        updateMultiVideo { mv ->
            val cells = mv.cells.toMutableList()
            cells[index] = cells[index].copy(
                mediaId = null,
                uri = uri,
                displayName = displayName,
                isAudio = isAudio,
                playing = true,
                progress = 0f,
            )
            mv.copy(cells = cells, pickerIndex = null)
        }
        showMultiVideoOverlay()
    }

    fun showMultiVideoOverlay() {
        mvOverlayJob?.cancel()
        updateMultiVideo { it.copy(overlayVisible = true) }
        mvOverlayJob = viewModelScope.launch {
            delay(3_000)
            if (_shellUi.value.tab == AppTab.MULTIVIDEO) {
                updateMultiVideo { it.copy(overlayVisible = false) }
            }
        }
    }

    private inline fun updateMultiVideo(crossinline block: (MultiVideoState) -> MultiVideoState) {
        _transient.update { it.copy(multiVideo = block(it.multiVideo)) }
    }

    private fun scheduleSlideshow() {
        slideshowJob?.cancel()
        val s = _settings.value
        val v = _viewerUi.value
        if (!v.playing || !v.open || !v.slideshowMode) return
        if (s.speedIdx == SlideshowSpeeds.OFF_INDEX) return
        val item = currentViewerItem()
        // Videos: either loop in place (dontLoop=false) or advance on STATE_ENDED.
        // Do not also fire a duration-based timer (double-advance).
        if (item?.mediaType == MediaType.VIDEO || item?.mediaType == MediaType.AUDIO) {
            return
        }
        val delayMs = when {
            s.speedIdx == SlideshowSpeeds.CUSTOM_INDEX -> s.customMs
            else -> SlideshowSpeeds.speeds.getOrNull(s.speedIdx)?.ms ?: 5_000L
        }
        if (delayMs <= 0L) return
        slideshowJob = viewModelScope.launch {
            delay(delayMs)
            val cur = _viewerUi.value
            if (cur.keys.isEmpty()) {
                _viewerUi.update { it.copy(playing = false) }
                return@launch
            }
            var next = cur.index + 1
            if (next >= cur.keys.size) {
                if (s.dontLoop) {
                    _viewerUi.update { it.copy(playing = false) }
                    return@launch
                }
                next = 0
            }
            _viewerUi.update { it.copy(index = next) }
            noteViewerPosition()
            scheduleSlideshow()
        }
    }

    /**
     * The only way settings change. Safe from any thread: the update is atomic (the thumbnail
     * farmer checkpoints from a background thread, and a plain read-then-write here could drop a
     * favourite tapped at the same moment), and saving is left to the single writer. [block]
     * must not have side effects; under contention it can run more than once.
     */
    private fun persistSettings(block: (AppSettings) -> AppSettings) {
        _settings.update { block(it.sanitized()).sanitized() }
        settingsVersion.update { it + 1 }
    }

    /** Captures the app's recent log off the main thread (it runs `logcat`). */
    fun captureLogs(onReady: (File) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LogCapture.captureToCache(getApplication()) }
                .onSuccess(onReady)
                .onFailure { showSnack(it.message ?: "Could not capture log") }
        }
    }

    private fun mediaByKey(key: String): MediaItem? =
        _allMedia.value.find { it.stableKey == key }
            ?: _folderFavourites.value.find { it.stableKey == key }

    private fun passType(item: MediaItem, fileTypes: Map<String, Boolean>): Boolean {
        if (fileTypes[item.extension] == false) return false
        return when (item.mediaType) {
            MediaType.PHOTO, MediaType.VIDEO, MediaType.GIF, MediaType.AUDIO -> true
            MediaType.OTHER -> item.extension in SlideshowSpeeds.supportedExtensions
        }
    }

    private fun passFavType(item: MediaItem, filter: FileTypeFilter): Boolean = when (item.mediaType) {
        MediaType.PHOTO -> filter.photo
        MediaType.VIDEO -> filter.video
        MediaType.GIF -> filter.gif
        MediaType.AUDIO -> filter.audio
        MediaType.OTHER -> false
    }

    /** The tab whose list a slideshow started from [tab] walks. */
    private fun listTabFor(tab: AppTab): AppTab = when (tab) {
        AppTab.FAV, AppTab.RECENT -> tab
        AppTab.ALBUM -> if (_albumOpen.value != null) AppTab.ALBUM else AppTab.GALLERY
        // Settings / Multi-Video / Gallery / Slideshow → the gallery order
        else -> AppTab.GALLERY
    }

    /** The list [tab]'s grid shows, in on-screen order. */
    private fun listFor(tab: AppTab): List<MediaItem> {
        val library = libraryState.value
        return when (tab) {
            AppTab.FAV -> library.favourites
            AppTab.RECENT -> library.recent
            AppTab.ALBUM -> library.albumDetail
            else -> library.gallery
        }
    }

    private fun tabSourceList(tab: AppTab): List<String> = listFor(tab).map { it.stableKey }

    /**
     * How far into [tab]'s list the user may already have looked. Read by the library pipeline
     * (off the main thread): the recorded high-water mark, or at least the page on screen now
     * plus the parked one after it.
     */
    private fun frontierFor(tab: AppTab): Int {
        val capacity = pageCapacity.coerceAtLeast(1)
        val onScreen = (_shellUi.value.pageCursors[tab] ?: 0) + 2 * capacity
        return maxOf(frontiers[tab] ?: 0, onScreen)
    }

    private fun currentViewerItem(): MediaItem? =
        _viewerUi.value.currentKey()?.let { libraryState.value.lookup[it] ?: mediaByKey(it) }

    /**
     * Single pass over the library that produces every list the UI needs. Runs off the main
     * thread and only when the library, filters, or the random draw actually change.
     */
    private fun buildLibraryState(
        inputs: LibraryInputs,
        sources: LibrarySources,
        sample: SampleInputs,
    ): LibraryState {
        val allMedia = sources.media
        val deleted = sources.deleted
        val fileTypes = effectiveFileTypes(inputs.fileTypes)
        val now = System.currentTimeMillis()

        val lookup = HashMap<String, MediaItem>(allMedia.size + sources.folderFavourites.size)
        val playable = ArrayList<MediaItem>(allMedia.size)
        for (item in allMedia) {
            lookup[item.stableKey] = item
            if (item.stableKey in deleted) continue
            if (passType(item, fileTypes)) playable += item
        }
        // Favourites-folder copies win on key collisions, matching the previous lookup order.
        for (item in sources.folderFavourites) lookup[item.stableKey] = item

        val favIds = inputs.favIds
        // The whole library is dealt once per shuffle, then held still: new media only ever
        // joins the unseen part, so swiping back always shows the page you saw (ShuffleDeck).
        // Favourites are dealt from their own pool so they land far more often than their
        // share of the library would give them. That pool is the favourites as of the shuffle
        // (_boostFavIds), so the deal is the same one next launch's warm-up predicted.
        val boostIds = sample.boostIds
        val gallery = galleryDeck.arrange(playable, sample.seed, frontierFor(AppTab.GALLERY)) { pool ->
            val (boosted, regular) = pool.partition { it.stableKey in boostIds }
            seededMixedSample(
                regular = regular,
                boosted = boosted,
                seed = sample.seed,
                count = pool.size,
                boostedRate = SamplingDefaults.FAVOURITE_RATE,
            )
        }
        val liveFavourites = playable.filter { it.stableKey in favIds }
        val favWindow = inputs.favWindow
        val favourites = try {
            if (inputs.copyFavs && inputs.copyFavTreeUri.isNotBlank()) {
                sources.folderFavourites.filter { item ->
                    item.stableKey !in deleted &&
                        passFavType(item, inputs.favTypes) &&
                        favWindow.matches(item.ageDays(now))
                }
            } else {
                // "All folders" adds favourites living outside the current selection.
                val pool = if (inputs.showAllFavourites) {
                    (liveFavourites + sources.globalFavourites).distinctBy { it.stableKey }
                } else {
                    liveFavourites
                }
                pool.filter { item ->
                    item.stableKey !in deleted &&
                        passFavType(item, inputs.favTypes) &&
                        favWindow.matches(item.ageDays(now))
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("GalleryVM", "Favourites filter failed window=$favWindow", t)
            emptyList()
        }
        // Favourites have no natural order, so they follow the gallery's shuffle. Held still
        // the same way, so hearting a photo elsewhere doesn't re-deal the Favourites pages.
        val shuffledFavourites =
            favouritesDeck.arrange(favourites, sample.seed, frontierFor(AppTab.FAV)) { pool ->
                seededSample(pool, sample.seed, pool.size)
            }

        val recentWindow = inputs.recentWindow
        val recent = try {
            val matching = playable.filter { item ->
                passFavType(item, inputs.recentTypes) && recentWindow.matches(item.ageDays(now))
            }
            // Swipe mode is about random sets; scroll mode is about browsing, where newest-first
            // is what "Recent" should mean.
            if (inputs.gridMode == GridMode.SWIPE) {
                recentDeck.arrange(matching, sample.seed, frontierFor(AppTab.RECENT)) { pool ->
                    seededSample(pool, sample.seed, pool.size)
                }
            } else {
                matching.sortedByDescending { it.recencyMs }
            }
        } catch (t: Throwable) {
            android.util.Log.e("GalleryVM", "Recent filter failed window=$recentWindow", t)
            emptyList()
        }

        val videos = playable.filter {
            it.mediaType == MediaType.VIDEO || it.mediaType == MediaType.AUDIO
        }

        val selectedNormalized = MediaRepository.mediaStoreFolderKeys(inputs.selectedFolders)
        val albums = sources.discovered.filter {
            MediaRepository.folderMatchesSelection(it.path, selectedNormalized)
        }
        val albumDetail = if (sample.albumOpen != null) {
            val albumNorm = setOf(MediaRepository.normalizeFolderPath(sample.albumOpen))
            val inAlbum = playable.filter { MediaRepository.folderMatchesSelection(it.folderPath, albumNorm) }
            sample.albumSeed?.let { seed -> seededSample(inAlbum, seed, inAlbum.size) } ?: inAlbum
        } else {
            emptyList()
        }

        // Favourites-folder copies aren't in the library scan, so make them resolvable.
        for (item in sources.globalFavourites) lookup.putIfAbsent(item.stableKey, item)

        return LibraryState(
            gallery = gallery,
            favourites = shuffledFavourites,
            recent = recent,
            videos = videos,
            albums = albums,
            albumDetail = albumDetail,
            albumOpen = sample.albumOpen,
            discoveredFolders = sources.discovered,
            lookup = lookup,
            playableCount = playable.size,
            noFolders = selectedNormalized.isEmpty() && inputs.safTreeUris.isEmpty(),
        )
    }

    private fun assembleUiState(
        settings: AppSettings,
        library: LibraryState,
        viewer: ViewerUi,
        shell: ShellUi,
        transient: TransientUi,
    ): GalleryUiState = GalleryUiState(
        settings = settings,
        currentTab = shell.tab,
        visibleTabs = settings.tabOrder.filter { it !in settings.tabHidden },
        gallery = library.gallery,
        pageCursors = shell.pageCursors,
        favourites = library.favourites,
        recent = library.recent,
        videos = library.videos,
        albums = library.albums,
        discoveredFolders = library.discoveredFolders,
        albumDetail = library.albumDetail,
        albumOpen = library.albumOpen,
        noFolders = library.noFolders,
        loading = transient.loading,
        countsRefreshing = transient.countsRefreshing,
        galleryTotal = library.playableCount,
        viewerOpen = viewer.open,
        viewerItem = viewer.currentKey()?.let { library.lookup[it] },
        viewerPrefetch = viewer.neighbourKeys().mapNotNull { library.lookup[it] },
        viewerFarPrefetch = viewer.farNeighbourKeys().mapNotNull { library.lookup[it] },
        viewerIndex = viewer.index,
        viewerCount = viewer.keys.size,
        viewerPlaying = viewer.playing,
        viewerChrome = viewer.chrome,
        viewerMenuOpen = viewer.menuOpen,
        viewerSlideshowMode = viewer.slideshowMode,
        viewerMuted = viewer.muted,
        viewerChromeNonce = viewer.chromeNonce,
        speedMenuOpen = viewer.speedMenuOpen,
        detailsOpen = viewer.detailsOpen,
        customSpeedOpen = viewer.customSpeedOpen,
        customSpeedSeconds = viewer.customSpeedSeconds,
        selectMode = shell.selectMode,
        selectedKeys = shell.selectedKeys,
        trashPrompt = transient.trashPrompt,
        confirmResetSettings = shell.confirmResetSettings,
        hiddenFoldersDialog = shell.hiddenFoldersDialog,
        favTypeMenuOpen = shell.favTypeMenuOpen,
        recentTypeMenuOpen = shell.recentTypeMenuOpen,
        collapsedGroups = shell.collapsedGroups,
        multiVideo = transient.multiVideo,
        snack = transient.snack,
        mediaByKey = library.lookup,
    )

    private fun AppSettings.toLibraryInputs() = LibraryInputs(
        fileTypes = fileTypes,
        favIds = favIds,
        favWindow = favWindow,
        favTypes = favTypes,
        recentWindow = recentWindow,
        recentTypes = recentTypes,
        copyFavs = copyFavs,
        copyFavTreeUri = copyFavTreeUri,
        showAllFavourites = showAllFavourites,
        gridMode = gridMode,
        selectedFolders = selectedFolders,
        safTreeUris = safTreeUris,
    )

    override fun onCleared() {
        slideshowJob?.cancel()
        snackJob?.cancel()
        mvOverlayJob?.cancel()
        refreshJob?.cancel()
        countsJob?.cancel()
        farmJob?.cancel()
        super.onCleared()
    }

    private companion object {
        /** Farmer pacing: one thumbnail per ~150 ms — gentle on an old phone's IO. */
        const val FARM_STEP_MS = 150L

        /** Persist the farmer checkpoint every N items. */
        const val FARM_CHECKPOINT = 50

        /** Longest the splash screen may wait for the first library content. */
        const val READY_TIMEOUT_MS = 1_200L

        /** Items past the one on screen that the viewer decodes ahead, so they count as seen. */
        const val VIEWER_LOOKAHEAD = 4

        /** A settings export is a few KB; anything this big is not one. */
        const val MAX_SETTINGS_FILE_BYTES = 1_000_000

        /** How long a snackbar stays: a plain notice, and one with an action such as Undo. */
        const val SNACK_MS = 4_000L
        const val ACTION_SNACK_MS = 8_000L
    }

    /** The slice of [AppSettings] that actually changes the media lists. */
    private data class LibraryInputs(
        val fileTypes: Map<String, Boolean> = emptyMap(),
        val favIds: Set<String> = emptySet(),
        val favWindow: FavWindow = FavWindow.ALL,
        val favTypes: FileTypeFilter = FileTypeFilter(),
        val recentWindow: FavWindow = FavWindow.Days(30),
        val recentTypes: FileTypeFilter = FileTypeFilter(),
        val copyFavs: Boolean = false,
        val copyFavTreeUri: String = "",
        val showAllFavourites: Boolean = false,
        val gridMode: GridMode = GridMode.SWIPE,
        val selectedFolders: Set<String> = emptySet(),
        val safTreeUris: Set<String> = emptySet(),
    )

    private data class LibrarySources(
        val media: List<MediaItem>,
        val folderFavourites: List<MediaItem>,
        val deleted: Set<String>,
        val discovered: List<MediaRepository.FolderInfo>,
        val globalFavourites: List<MediaItem>,
    )

    private data class SampleInputs(
        val seed: Long,
        val albumOpen: String?,
        val albumSeed: Long?,
        val boostIds: Set<String> = emptySet(),
    )

    private data class LibraryState(
        val gallery: List<MediaItem> = emptyList(),
        val favourites: List<MediaItem> = emptyList(),
        val recent: List<MediaItem> = emptyList(),
        val videos: List<MediaItem> = emptyList(),
        val albums: List<MediaRepository.FolderInfo> = emptyList(),
        val albumDetail: List<MediaItem> = emptyList(),
        val albumOpen: String? = null,
        val discoveredFolders: List<MediaRepository.FolderInfo> = emptyList(),
        val lookup: Map<String, MediaItem> = emptyMap(),
        val playableCount: Int = 0,
        val noFolders: Boolean = true,
    )

    private data class ViewerUi(
        val open: Boolean = false,
        val keys: List<String> = emptyList(),
        val index: Int = 0,
        val playing: Boolean = false,
        val chrome: Boolean = true,
        val menuOpen: Boolean = false,
        val speedMenuOpen: Boolean = false,
        val detailsOpen: Boolean = false,
        val customSpeedOpen: Boolean = false,
        val customSpeedSeconds: Int = 8,
        val slideshowMode: Boolean = false,
        val muted: Boolean = false,
        val chromeNonce: Int = 0,
        val returnTab: AppTab = AppTab.GALLERY,
        /** The tab whose list [keys] came from; its grid follows the viewer on close. */
        val sourceTab: AppTab? = null,
    ) {
        fun currentKey(): String? = keys.getOrNull(index)

        /** Keys either side of the current one, wrapping like [viewerNavigate] does. */
        fun neighbourKeys(): List<String> {
            if (!open || keys.size < 2) return emptyList()
            val next = if (index + 1 > keys.lastIndex) 0 else index + 1
            val prev = if (index - 1 < 0) keys.lastIndex else index - 1
            return listOf(keys[next], keys[prev]).distinct()
        }

        /** Two and three swipes out, both directions (wrapping) — dwell-time warm targets. */
        fun farNeighbourKeys(): List<String> {
            if (!open || keys.size < 4) return emptyList()
            val n = keys.size
            return listOf(2, 3, -2, -3)
                .map { keys[((index + it) % n + n) % n] }
                .distinct()
                .filter { it != currentKey() }
        }
    }

    private data class ShellUi(
        val tab: AppTab = AppTab.GALLERY,
        /** Per-tab window position into that tab's (session-stable) list. */
        val pageCursors: Map<AppTab, Int> = emptyMap(),
        val selectMode: Boolean = false,
        val selectedKeys: Set<String> = emptySet(),
        val confirmResetSettings: Boolean = false,
        val hiddenFoldersDialog: Boolean = false,
        val favTypeMenuOpen: Boolean = false,
        val recentTypeMenuOpen: Boolean = false,
        val collapsedGroups: Set<String> = emptySet(),
    )

    /** Which files a trash (or restore) prompt is about, keyed by stableKey. */
    private data class PendingTrash(val files: Map<String, Uri>, val restoring: Boolean)

    private data class TransientUi(
        val multiVideo: MultiVideoState = MultiVideoState(),
        val snack: SnackMessage? = null,
        val trashPrompt: TrashPrompt? = null,
        val loading: Boolean = false,
        val countsRefreshing: Boolean = false,
    )

}

/** A confirmation Android shows for moving files to or from its trash; launched by the UI. */
data class TrashPrompt(val intentSender: android.content.IntentSender)

/** Reads the stream as UTF-8 text, or returns null once it passes [maxBytes]. */
private fun java.io.InputStream.readTextCapped(maxBytes: Int): String? {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (out.size() + read > maxBytes) return null
        out.write(buffer, 0, read)
    }
    return out.toString(Charsets.UTF_8.name())
}

data class GalleryUiState(
    val settings: AppSettings = AppSettings(),
    val currentTab: AppTab = AppTab.GALLERY,
    val visibleTabs: List<AppTab> = AppTab.defaultOrder.filter { it !in AppTab.defaultHidden },
    val gallery: List<MediaItem> = emptyList(),
    /**
     * Per-tab page cursor: index of the first visible item within that tab's list. Pages are
     * windows into ONE session-stable shuffled list, which is what gives swipe-back identical
     * items and gives the fullscreen viewer perfect continuity with the grid.
     */
    val pageCursors: Map<AppTab, Int> = emptyMap(),
    val favourites: List<MediaItem> = emptyList(),
    val recent: List<MediaItem> = emptyList(),
    val videos: List<MediaItem> = emptyList(),
    val albums: List<MediaRepository.FolderInfo> = emptyList(),
    val discoveredFolders: List<MediaRepository.FolderInfo> = emptyList(),
    val albumDetail: List<MediaItem> = emptyList(),
    val albumOpen: String? = null,
    val noFolders: Boolean = true,
    val loading: Boolean = false,
    /** True while the Settings file-type tally is being recomputed in the background. */
    val countsRefreshing: Boolean = false,
    /** Everything that passes the current filters, of which [gallery] is a random slice. */
    val galleryTotal: Int = 0,
    val viewerOpen: Boolean = false,
    val viewerItem: MediaItem? = null,
    /** Neighbouring items the viewer should decode ahead of a swipe. */
    val viewerPrefetch: List<MediaItem> = emptyList(),
    /** Items 2-3 swipes away — warmed at thumbnail resolution after a dwell. */
    val viewerFarPrefetch: List<MediaItem> = emptyList(),
    val viewerIndex: Int = 0,
    val viewerCount: Int = 0,
    val viewerPlaying: Boolean = false,
    val viewerChrome: Boolean = true,
    val viewerMenuOpen: Boolean = false,
    val viewerSlideshowMode: Boolean = false,
    val viewerMuted: Boolean = false,
    val viewerChromeNonce: Int = 0,
    val speedMenuOpen: Boolean = false,
    val detailsOpen: Boolean = false,
    val customSpeedOpen: Boolean = false,
    val customSpeedSeconds: Int = 8,
    val selectMode: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    /** Android's own trash (or restore) confirmation, for the UI to launch once. */
    val trashPrompt: TrashPrompt? = null,
    val confirmResetSettings: Boolean = false,
    val hiddenFoldersDialog: Boolean = false,
    val favTypeMenuOpen: Boolean = false,
    val recentTypeMenuOpen: Boolean = false,
    val collapsedGroups: Set<String> = emptySet(),
    val multiVideo: MultiVideoState = MultiVideoState(),
    val snack: SnackMessage? = null,
    val mediaByKey: Map<String, MediaItem> = emptyMap(),
)
