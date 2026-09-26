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
import com.mousy.windfall.data.model.SamplingDefaults
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
import com.mousy.windfall.data.media.warmSystemThumbnail
import com.mousy.windfall.ui.components.gridThumbRequest
import com.mousy.windfall.util.AppVisibility
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val mediaRepo = MediaRepository(application)
    private val indexCache = MediaIndexCache(application)
    private val favExporter = FavouritesExporter(application)
    private val favSync = FavouritesFolderSync(application)

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

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

    /** How many items of the library the gallery prepares; grows as the user keeps browsing. */
    private val _sampleLimit = MutableStateFlow(SamplingDefaults.MIN_SAMPLE)

    private val _albumOpen = MutableStateFlow<String?>(null)
    private val _viewerUi = MutableStateFlow(ViewerUi())
    private val _shellUi = MutableStateFlow(ShellUi())
    private val _transient = MutableStateFlow(TransientUi())

    private var pendingUndoSettings: AppSettings? = null

    private var slideshowJob: Job? = null
    private var snackJob: Job? = null
    private var mvOverlayJob: Job? = null
    private var refreshJob: Job? = null
    private var countsJob: Job? = null
    private var farmJob: Job? = null

    private var lastMediaScanKey: String? = null
    private var lastFolderDiscoveryKey: String? = null
    private var settingsRestored = false
    private var refreshToken = 0

    /** Distinct items opened this app session — feeds the adaptive sample size. */
    private val sessionViewedKeys = HashSet<String>()
    private var sessionBaselineAvg = SamplingDefaults.INITIAL_AVG_VIEWED

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
        combine(_shuffleSeed, _sampleLimit, _albumOpen, _boostFavIds) { seed, limit, album, boost ->
            SampleInputs(seed, limit, album, boost)
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
            settingsRepo.settingsFlow.collect { raw ->
                val s = raw.sanitized()
                _settings.value = s
                if (!settingsRestored) {
                    settingsRestored = true
                    sessionBaselineAvg = s.avgViewedPerSession
                    initShuffleSeed(s)
                }
                val key = mediaScanKey(s)
                if (key != lastMediaScanKey) {
                    lastMediaScanKey = key
                    refreshMedia()
                }
            }
        }
    }

    fun onPermissionsGranted() = refreshMedia()

    fun refreshMedia() {
        refreshJob?.cancel()
        // A cancelled scan must not clear the spinner belonging to the scan that replaced it.
        val token = ++refreshToken
        refreshJob = viewModelScope.launch {
            delay(180)
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
                        _sampleLimit.value =
                            SamplingDefaults.sampleSizeFor(s.avgViewedPerSession, cached.size)
                        _transient.update { it.copy(loading = false) }
                    }
                }
                val media = mediaRepo.scanMedia(
                    selectedFolders = s.selectedFolders,
                    safTreeUris = s.safTreeUris,
                    hiddenFolders = s.hiddenFolders,
                    fileTypeFilters = effectiveFileTypes(s.fileTypes),
                )
                _allMedia.value = media
                _sampleLimit.value = SamplingDefaults.sampleSizeFor(s.avgViewedPerSession, media.size)
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
        slideshowJob?.cancel()
        when (tab) {
            AppTab.SLIDESHOW -> {
                val source = _shellUi.value.tab
                val list = tabSourceList(source, _albumOpen.value)
                if (list.isNotEmpty()) {
                    // Slideshow ALWAYS starts at the very first media of the order, so land the
                    // source grid on page 1 too — index 0 below is page 1, media 1.
                    setCursor(source, 0)
                    openViewer(
                        keys = list,
                        index = 0,
                        autoPlay = true,
                        slideshowMode = true,
                        fromGallery = source != AppTab.FAV && source != AppTab.RECENT && source != AppTab.ALBUM,
                    )
                }
            }
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

    private fun closeViewerState() {
        _viewerUi.update {
            it.copy(open = false, playing = false, menuOpen = false, speedMenuOpen = false, detailsOpen = false)
        }
    }

    fun toggleGridMode() = persistSettings {
        it.copy(gridMode = if (it.gridMode == GridMode.SWIPE) GridMode.SCROLL else GridMode.SWIPE)
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

    /** Shuffle button (or bag exhausted): deal a brand-new random order from page one. */
    fun shuffleGrid() {
        _boostFavIds.value = _settings.value.favIds
        _shuffleSeed.value = newShuffleSeed()
        setCursor(AppTab.GALLERY, 0)
    }

    fun onGridSwipe(direction: Int) {
        if (_settings.value.gridMode == GridMode.SWIPE) swipePage(direction)
    }

    /**
     * CONTINUITY CORE. One seeded shuffle per session = one fixed random ordering; a page
     * swipe just slides a cursor window through it. Back re-shows exactly what you saw (still
     * cached, so it's instant); forward reveals the next unseen slice and keeps the sample a
     * page ahead. Only when every playable item has been dealt does a fresh shuffle start —
     * the classic shuffle-bag: full randomness, zero repeats within a cycle.
     */
    private fun swipePage(direction: Int) {
        val tab = _shellUi.value.tab
        val library = libraryState.value
        val listSize = when (tab) {
            AppTab.FAV -> library.favourites.size
            AppTab.RECENT -> library.recent.size
            else -> library.gallery.size
        }
        if (listSize == 0) return
        val capacity = pageCapacity.coerceAtLeast(1)
        val cursor = cursorFor(tab)
        if (direction < 0 && cursor > 0) {
            setCursor(tab, (cursor - capacity).coerceAtLeast(0))
            return
        }
        if (direction < 0) {
            // Back swipe on the FIRST page: wrap to the tail window of the list as it stands,
            // so backwards is as continuous (and as random) as forwards — the tail of the
            // seeded sample is unseen content. The grid parks this same window on the back
            // side (see SwipePagedGrid), so the settle handoff stays invisible. Dealing
            // forward here (the old behaviour) made the next forward swipe look like it
            // skipped up to two pages. Swiping forward from the tail keeps extending the
            // sample as usual, so travel stays continuous in both directions.
            val size = if (tab == AppTab.GALLERY) library.gallery.size else listSize
            // NO sample extension here: the list must stay untouched through the settle
            // animation, or the parked window changes content mid-flight (the "double
            // refresh"). The grid parks this same window (backWrapStart) on the back side.
            if (size > capacity) setCursor(tab, (size - capacity).coerceAtLeast(0))
            return
        }
        val next = cursor + capacity
        if (tab == AppTab.GALLERY) {
            if (next >= library.gallery.size) {
                // Off the end of the materialised list: wrap to page 1 (the grid already
                // parks the first window there). A surprise reshuffle here redealt EVERYTHING
                // including page 1; fresh orders still come from each app launch and from
                // re-tapping the Gallery tab.
                setCursor(tab, 0)
                return
            }
            // Keep the page after next prepared; the window clamps while the sample grows.
            extendSampleIfNeeded(next + capacity * 2)
            setCursor(tab, next)
        } else {
            // Favourites / Recents are complete lists — wrap around at the end.
            setCursor(tab, if (next >= listSize) 0 else next)
        }
    }

    private fun cursorFor(tab: AppTab): Int = _shellUi.value.pageCursors[tab] ?: 0

    private fun setCursor(tab: AppTab, value: Int) {
        _shellUi.update { it.copy(pageCursors = it.pageCursors + (tab to value.coerceAtLeast(0))) }
    }

    /**
     * Widens the prepared slice once the user approaches the end of it. Draws from the same
     * seeded order, so the extra items are new — never repeats of what's already shown.
     */
    fun extendSampleIfNeeded(reachedIndex: Int) {
        val total = libraryState.value.playableCount
        val limit = _sampleLimit.value
        if (limit >= total) return
        if (reachedIndex < (limit * SamplingDefaults.EXTEND_AT_FRACTION).toInt()) return
        val batch = SamplingDefaults.sampleSizeFor(_settings.value.avgViewedPerSession, total)
        _sampleLimit.value = (limit + batch).coerceAtMost(total)
    }

    fun toggleFavourite(key: String) {
        viewModelScope.launch {
            val s = _settings.value
            val folderItem = _folderFavourites.value.find { it.stableKey == key }
            val libraryItem = _allMedia.value.find { it.stableKey == key }

            // Unfavourite a Favourites-folder copy: remove file + matching library favIds.
            if (folderItem != null && usesFavouritesFolder(s) && libraryItem == null) {
                favSync.syncFavouriteRemoved(s.copyFavTreeUri, folderItem.displayName)
                val name = folderItem.displayName
                persistSettings { cur ->
                    cur.copy(
                        favIds = cur.favIds.filter { id ->
                            _allMedia.value.find { it.stableKey == id }?.displayName != name
                        }.toSet(),
                    )
                }
                refreshFolderFavourites()
                return@launch
            }

            val item = libraryItem ?: folderItem ?: return@launch
            val newFavs = s.favIds.toMutableSet()
            // Library items: toggle favIds; folder-mode hearts on gallery still use favIds.
            val isFav = isFavouriteItem(item)
            if (isFav) {
                newFavs.remove(key)
                _allMedia.value.filter { it.displayName == item.displayName }
                    .forEach { newFavs.remove(it.stableKey) }
                persistSettings { it.copy(favIds = newFavs) }
                if (usesFavouritesFolder(s)) {
                    favSync.syncFavouriteRemoved(s.copyFavTreeUri, item.displayName)
                    refreshFolderFavourites()
                }
            } else {
                newFavs.add(key)
                persistSettings { it.copy(favIds = newFavs) }
                if (usesFavouritesFolder(s)) {
                    favSync.syncFavouriteAdded(s.copyFavTreeUri, item)
                    refreshFolderFavourites()
                }
            }
        }
    }

    fun isFavouriteItem(item: MediaItem): Boolean {
        val s = _settings.value
        if (item.stableKey in s.favIds) return true
        if (!usesFavouritesFolder(s)) return false
        return _folderFavourites.value.any {
            it.stableKey == item.stableKey || it.displayName == item.displayName
        }
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
        viewModelScope.launch {
            val keys = _shellUi.value.selectedKeys
            persistSettings { it.copy(favIds = it.favIds + keys) }
            val s = _settings.value
            if (usesFavouritesFolder(s)) {
                keys.forEach { key ->
                    mediaByKey(key)?.let { favSync.syncFavouriteAdded(s.copyFavTreeUri, it) }
                }
                refreshFolderFavourites()
            }
            exitSelectMode()
        }
    }

    fun deleteSelected() {
        if (_settings.value.deletesDisabled) {
            showDeleteDisabledPrompt()
            return
        }
        _shellUi.update { it.copy(confirmDeleteKeys = it.selectedKeys.toList()) }
    }

    fun openViewer(
        keys: List<String>,
        index: Int,
        autoPlay: Boolean = false,
        slideshowMode: Boolean = autoPlay,
        fromGallery: Boolean = false,
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
                fromGallery = fromGallery,
            )
        }
        // View mode keeps the source tab selected; slideshow mode selects Slideshow.
        if (slideshowMode) _shellUi.update { it.copy(tab = AppTab.SLIDESHOW) }
        noteViewed(_viewerUi.value.currentKey())
        if (_viewerUi.value.playing) scheduleSlideshow() else slideshowJob?.cancel()
    }

    fun closeViewer() {
        slideshowJob?.cancel()
        val v = _viewerUi.value
        val returnTab = v.returnTab
        // Land the grid on the page containing what was just on screen — viewer and grid walk
        // the SAME list, so closing continues exactly where the eyes were. Only move when the
        // item actually LEFT the visible page: pageCapacity can differ while the viewer is open
        // (the hidden tab bar makes the grid taller), so unconditionally re-deriving the cursor
        // from it shifted the window by a few rows on a plain open→close.
        if (v.open && v.fromGallery && _settings.value.gridMode == GridMode.SWIPE) {
            val capacity = pageCapacity.coerceAtLeast(1)
            val cursor = cursorFor(AppTab.GALLERY)
            if (v.index < cursor || v.index >= cursor + capacity) {
                setCursor(AppTab.GALLERY, (v.index / capacity) * capacity)
            }
        }
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
        persistViewingHabit()
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
            shell.confirmDeleteKeys != null -> cancelDelete()
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
        noteViewed(_viewerUi.value.currentKey())
        if (_viewerUi.value.fromGallery) {
            extendSampleIfNeeded(_viewerUi.value.index)
            adoptExtendedGallery()
        }
        // Keep chrome vanished if it was vanished — do not force-show on advance.
        if (_viewerUi.value.playing) scheduleSlideshow()
    }

    /**
     * Picks up items added by [extendSampleIfNeeded] while the viewer is open. The seeded draw
     * only ever appends, so the viewer's existing keys and index stay valid.
     */
    private fun adoptExtendedGallery() {
        val v = _viewerUi.value
        if (!v.open || !v.fromGallery) return
        val gallery = libraryState.value.gallery
        if (gallery.size <= v.keys.size) return
        val deleted = _deletedKeys.value
        val extra = gallery.asSequence()
            .drop(v.keys.size)
            .map { it.stableKey }
            .filter { it !in deleted }
            .toList()
        if (extra.isEmpty()) return
        _viewerUi.update { it.copy(keys = it.keys + extra) }
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
        noteViewed(_viewerUi.value.currentKey())
        if (_viewerUi.value.playing) scheduleSlideshow()
    }

    fun viewerSwipeUpDelete() {
        val s = _settings.value
        if (s.disableSwipeDelete) {
            showSnack("Swipe-up delete is off. Enable it in More → Playback & Safety.")
            return
        }
        if (s.deletesDisabled) {
            showDeleteDisabledPrompt()
            return
        }
        val key = _viewerUi.value.currentKey() ?: return
        _shellUi.update { it.copy(confirmDeleteKeys = listOf(key)) }
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
        if (_settings.value.deletesDisabled) {
            showDeleteDisabledPrompt()
            return
        }
        _viewerUi.value.currentKey()?.let { key ->
            _shellUi.update { it.copy(confirmDeleteKeys = listOf(key)) }
        }
    }

    fun cancelDelete() = _shellUi.update { it.copy(confirmDeleteKeys = null) }

    fun confirmDelete() {
        val keys = _shellUi.value.confirmDeleteKeys ?: return
        _shellUi.update {
            it.copy(confirmDeleteKeys = null, selectedKeys = emptySet(), selectMode = false)
        }
        val deleted = _deletedKeys.value + keys
        _deletedKeys.value = deleted

        val live = _viewerUi.value.keys.filter { it !in deleted }
        if (_viewerUi.value.open && live.isEmpty()) {
            closeViewer()
        } else if (_viewerUi.value.open) {
            _viewerUi.update {
                it.copy(keys = live, index = it.index.coerceAtMost(live.lastIndex.coerceAtLeast(0)))
            }
        }
        showSnack(
            if (keys.size > 1) "${keys.size} files deleted" else "File deleted",
            "Undo",
        ) {
            _deletedKeys.value = _deletedKeys.value - keys.toSet()
        }
    }

    private fun showDeleteDisabledPrompt() {
        showSnack("Delete is disabled. Turn it back on in More → Playback & Safety.")
    }

    fun requestResetSettings() = _shellUi.update { it.copy(confirmResetSettings = true) }
    fun cancelResetSettings() = _shellUi.update { it.copy(confirmResetSettings = false) }

    fun confirmResetSettings() {
        _shellUi.update { it.copy(confirmResetSettings = false) }
        viewModelScope.launch {
            val previous = _settings.value
            pendingUndoSettings = previous
            val defaults = settingsRepo.resetToDefaults(keepFavourites = true)
            _settings.value = defaults
            showSnack("Settings reset", "Undo") {
                pendingUndoSettings?.let { snap ->
                    viewModelScope.launch {
                        settingsRepo.update { snap }
                        _settings.value = snap
                        pendingUndoSettings = null
                        refreshMedia()
                    }
                }
            }
            refreshMedia()
        }
    }

    fun cycleRecentWindow() {
        setRecentWindow(FavWindow.cycle(_settings.value.recentWindow))
    }

    fun cycleFavWindow() = setFavWindow(FavWindow.cycle(_settings.value.favWindow))

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

    /** @deprecated shared setter — prefer [setFavWindow] / [setRecentWindow]. */
    fun setListWindow(window: FavWindow) = setFavWindow(window)

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
        viewModelScope.launch {
            val s = _settings.value
            if (usesFavouritesFolder(s)) {
                val fromIds = _allMedia.value.filter { it.stableKey in s.favIds }
                favSync.syncAll(s.copyFavTreeUri, fromIds, previousNames = emptySet())
            }
            refreshFolderFavourites()
        }
    }

    fun setCopyFavFolder(uri: String, path: String) {
        persistSettings { it.copy(copyFavTreeUri = uri, copyFavPath = path) }
        viewModelScope.launch {
            val s = _settings.value.copy(copyFavTreeUri = uri, copyFavPath = path)
            if (s.copyFavs) {
                val fromIds = _allMedia.value.filter { it.stableKey in s.favIds }
                favSync.syncAll(uri, fromIds, previousNames = emptySet())
            }
            refreshFolderFavourites(s.copy(copyFavs = _settings.value.copyFavs))
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
            if (!enabling && _shellUi.value.tab == tab) {
                _shellUi.update { it.copy(tab = AppTab.GALLERY) }
            }
            s.copy(tabHidden = hidden, tabOrder = order, tabFeatures = features)
        }
    }

    fun toggleMultiVideoFeature() = toggleTabVisibility(AppTab.MULTIVIDEO)

    fun toggleAlbumFeature() = toggleTabVisibility(AppTab.ALBUM)

    fun openAlbum(path: String) {
        _albumOpen.value = path
        _shellUi.update { it.copy(tab = AppTab.ALBUM) }
    }

    fun closeAlbum() { _albumOpen.value = null }

    fun exportSettings(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = settingsRepo.exportJson(_settings.value)
            // The UI hands this to a save-as picker and snacks once it's actually written.
            onResult(json)
        }
    }

    fun importSettings(json: String) {
        viewModelScope.launch {
            settingsRepo.importJson(json)
            showSnack("Settings imported")
            refreshMedia()
        }
    }

    /** DEVICE-ONLY: Import settings JSON and/or a favourites zip from the document picker. */
    fun importSettingsOrFavourites(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val name = runCatching {
                resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
            }.getOrNull().orEmpty()
            val mime = resolver.getType(uri).orEmpty()
            val isZip = mime.contains("zip", ignoreCase = true) ||
                name.endsWith(".zip", ignoreCase = true)

            if (isZip) {
                val s = _settings.value
                favExporter.importFavouritesZip(
                    zipUri = uri,
                    favTreeUri = s.copyFavTreeUri.takeIf { s.copyFavs && it.isNotBlank() },
                ).onSuccess { result ->
                    result.settingsJson?.let { settingsRepo.importJson(it) }
                    if (result.displayNames.isNotEmpty()) {
                        val wanted = result.displayNames.toSet()
                        val matched = _allMedia.value
                            .filter { it.displayName in wanted }
                            .map { it.stableKey }
                            .toSet()
                        if (matched.isNotEmpty()) {
                            persistSettings { it.copy(favIds = it.favIds + matched) }
                        }
                    }
                    refreshMedia()
                    val parts = buildList {
                        if (result.mediaCount > 0) add("${result.mediaCount} media")
                        if (result.settingsJson != null) add("settings")
                    }
                    showSnack(
                        if (parts.isEmpty()) "Nothing to import"
                        else "Imported ${parts.joinToString(" + ")}",
                    )
                }.onFailure {
                    showSnack(it.message ?: "Import failed")
                }
            } else {
                val json = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (json.isNullOrBlank()) {
                    showSnack("Unable to read file")
                    return@launch
                }
                settingsRepo.importJson(json)
                showSnack("Settings imported")
                refreshMedia()
            }
        }
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
        _transient.update { it.copy(snack = SnackMessage(text, actionLabel, action)) }
        snackJob = viewModelScope.launch {
            delay(4_000)
            _transient.update { it.copy(snack = null) }
        }
    }

    fun dismissSnack() = _transient.update { it.copy(snack = null) }

    fun runSnackAction() {
        _transient.value.snack?.action?.invoke()
        _transient.update { it.copy(snack = null) }
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
            noteViewed(_viewerUi.value.currentKey())
            if (_viewerUi.value.fromGallery) {
                extendSampleIfNeeded(next)
                adoptExtendedGallery()
            }
            scheduleSlideshow()
        }
    }

    private fun persistSettings(block: (AppSettings) -> AppSettings) {
        val updated = block(_settings.value.sanitized()).sanitized()
        _settings.value = updated
        viewModelScope.launch {
            settingsRepo.update { updated }
        }
    }

    private fun noteViewed(key: String?) {
        if (key != null) sessionViewedKeys.add(key)
    }

    /**
     * Folds this session's viewing count into the stored moving average. Always measured
     * against the average captured at launch, so calling it repeatedly during one session
     * converges instead of drifting.
     */
    private fun persistViewingHabit() {
        val seen = sessionViewedKeys.size
        if (seen < SamplingDefaults.MIN_SESSION_FOR_AVERAGE) return
        val updated = SamplingDefaults.updatedAverage(sessionBaselineAvg, seen)
        if (kotlin.math.abs(updated - _settings.value.avgViewedPerSession) < 1f) return
        persistSettings { it.copy(avgViewedPerSession = updated) }
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

    private fun tabSourceList(tab: AppTab, albumPath: String?): List<String> {
        val library = libraryState.value
        return when (tab) {
            AppTab.FAV -> library.favourites.map { it.stableKey }
            AppTab.RECENT -> library.recent.map { it.stableKey }
            AppTab.ALBUM -> {
                if (albumPath != null && library.albumDetail.isNotEmpty()) {
                    library.albumDetail.map { it.stableKey }
                } else {
                    library.gallery.map { it.stableKey }
                }
            }
            // Settings / Multi-Video / Gallery / Slideshow fallback → gallery order
            else -> library.gallery.map { it.stableKey }
        }
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

        // Favourites are drawn from a separate pool so they land far more often than their
        // share of the library would give them.
        val favIds = inputs.favIds
        // Gallery draw uses the shuffle-time snapshot (see _boostFavIds) so heart taps don't
        // reshuffle the visible page; the Favourites tab below always uses the live set.
        val boostIds = sample.boostIds
        val boosted = ArrayList<MediaItem>()
        val regular = ArrayList<MediaItem>(playable.size)
        for (item in playable) {
            if (item.stableKey in boostIds) boosted += item else regular += item
        }
        val liveFavourites =
            if (boostIds == favIds) boosted else playable.filter { it.stableKey in favIds }

        val limit = sample.limit.coerceAtLeast(SamplingDefaults.MIN_SAMPLE)
        val gallery = seededMixedSample(
            regular = regular,
            boosted = boosted,
            seed = sample.seed,
            count = limit,
            boostedRate = SamplingDefaults.FAVOURITE_RATE,
        )
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
        // Favourites have no natural order, so they follow the gallery's random draw and
        // re-deal on the same swipe gesture.
        val shuffledFavourites = seededSample(favourites, sample.seed, favourites.size)

        val recentWindow = inputs.recentWindow
        val recent = try {
            val matching = playable.filter { item ->
                passFavType(item, inputs.recentTypes) && recentWindow.matches(item.ageDays(now))
            }
            // Swipe mode is about random sets; scroll mode is about browsing, where newest-first
            // is what "Recent" should mean.
            if (inputs.gridMode == GridMode.SWIPE) {
                seededSample(matching, sample.seed, matching.size)
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
            playable.filter { MediaRepository.folderMatchesSelection(it.folderPath, albumNorm) }
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
        confirmDeleteKeys = shell.confirmDeleteKeys,
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
        val limit: Int,
        val albumOpen: String?,
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
        /** Gallery lists grow as the sample widens; other tabs are already complete. */
        val fromGallery: Boolean = false,
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
        val confirmDeleteKeys: List<String>? = null,
        val confirmResetSettings: Boolean = false,
        val hiddenFoldersDialog: Boolean = false,
        val favTypeMenuOpen: Boolean = false,
        val recentTypeMenuOpen: Boolean = false,
        val collapsedGroups: Set<String> = emptySet(),
    )

    private data class TransientUi(
        val multiVideo: MultiVideoState = MultiVideoState(),
        val snack: SnackMessage? = null,
        val loading: Boolean = false,
        val countsRefreshing: Boolean = false,
    )

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
    val confirmDeleteKeys: List<String>? = null,
    val confirmResetSettings: Boolean = false,
    val hiddenFoldersDialog: Boolean = false,
    val favTypeMenuOpen: Boolean = false,
    val recentTypeMenuOpen: Boolean = false,
    val collapsedGroups: Set<String> = emptySet(),
    val multiVideo: MultiVideoState = MultiVideoState(),
    val snack: SnackMessage? = null,
    val mediaByKey: Map<String, MediaItem> = emptyMap(),
)
