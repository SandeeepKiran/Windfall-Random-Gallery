package com.mousy.windfall.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mousy.windfall.R
import com.mousy.windfall.data.model.AppTab
import com.mousy.windfall.data.model.MediaItem
import com.mousy.windfall.data.model.MediaType
import com.mousy.windfall.ui.components.CustomSpeedDialog
import com.mousy.windfall.ui.components.DetailsDialog
import com.mousy.windfall.ui.components.FullscreenViewer
import com.mousy.windfall.ui.components.GallerySnackbarHost
import com.mousy.windfall.ui.components.HiddenFoldersDialog
import com.mousy.windfall.ui.components.ResetSettingsConfirmDialog
import com.mousy.windfall.multivideo.MultiVideoSetupScreen
import com.mousy.windfall.multivideo.MultiVideoViewModel
import com.mousy.windfall.multivideo.MultiVideoWall
import com.mousy.windfall.multivideo.VideoPicker
import com.mousy.windfall.multivideo.WallVideo
import com.mousy.windfall.multivideo.activityOrientation
import com.mousy.windfall.ui.components.SelectionBar
import com.mousy.windfall.ui.navigation.GalleryTabBar
import com.mousy.windfall.ui.navigation.MainScaffold
import com.mousy.windfall.ui.screens.AlbumsScreen
import com.mousy.windfall.ui.screens.FavouritesScreen
import com.mousy.windfall.ui.screens.GalleryScreen
import com.mousy.windfall.ui.screens.RecentScreen
import com.mousy.windfall.ui.screens.SettingsScreen
import com.mousy.windfall.ui.components.CHROME_ANIM_OUT_MS
import com.mousy.windfall.ui.components.ThumbSpec
import com.mousy.windfall.util.AppIconSwitcher
import com.mousy.windfall.util.GalleryHaptics
import com.mousy.windfall.util.LogCapture
import com.mousy.windfall.viewmodel.GalleryViewModel

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun GalleryApp(
    viewModel: GalleryViewModel = viewModel(),
    /** Multi-Video keeps its own ViewModel; the gallery's knows nothing about it. */
    multiVideo: MultiVideoViewModel = viewModel(),
    onRequestOrientation: (Int) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val wall by multiVideo.state.collectAsStateWithLifecycle()
    val videoPicker by multiVideo.picker.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current
    // Launcher-icon choice lives in PackageManager component state; mirrored here for the UI.
    var appIcon by remember { mutableStateOf(AppIconSwitcher.current(context)) }
    // Read in composition rather than from LocalContext inside the click handlers, so the
    // values track configuration changes.
    val githubUrl = stringResource(R.string.github_url)
    val playStoreUrl = stringResource(R.string.play_store_url)
    val playStoreWebUrl = stringResource(R.string.play_store_web_url)
    val appVersion = remember {
        runCatching {
            val pm = context.packageManager
            val pkg = context.packageName
            val info = pm.getPackageInfo(pkg, 0)
            val name = info.versionName ?: "1.0.0"
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull()
                ?: runCatching {
                    @Suppress("DEPRECATION")
                    context.applicationInfo.nativeLibraryDir?.substringAfterLast('/')
                }.getOrNull()
                ?: ""
            if (abi.isNotBlank()) "v$name ($abi)" else "v$name"
        }.getOrDefault("v1.0.0")
    }

    val backEnabled = state.viewerOpen ||
        state.albumOpen != null ||
        state.selectMode ||
        state.detailsOpen ||
        state.customSpeedOpen ||
        state.confirmResetSettings ||
        state.hiddenFoldersDialog ||
        state.viewerMenuOpen ||
        state.speedMenuOpen

    BackHandler(enabled = backEnabled) {
        viewModel.handleSystemBack()
    }

    // Fullscreen means fullscreen: drop the status and navigation bars while the viewer or the
    // video wall is up, and put them back on the way out. Swiping from an edge still reveals
    // them temporarily.
    val immersive = state.viewerOpen || wall.wallOpen
    DisposableEffect(immersive, view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            if (immersive) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val gridThumbBucketPx = remember(state.settings.columns, landscape, density) {
        ThumbSpec.gridBucket(state.settings.columns, landscape, density)
    }

    // Source folders are only ever read, so only read access is kept.
    val safFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onSuccess {
            viewModel.addSafTreeUri(uri.toString())
        }.onFailure {
            viewModel.showSnack("Couldn't get lasting access to that folder")
        }
    }

    // The Favourites folder is written to (copies go in and out), so it needs write access.
    val favFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.onSuccess {
            viewModel.setCopyFavFolder(uri.toString(), uri.lastPathSegment ?: uri.toString())
        }.onFailure {
            viewModel.showSnack("Couldn't get lasting access to that folder")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importSettingsOrFavourites(uri)
    }

    // Delete moves files to Android's trash. Android shows its own confirmation for that, so
    // the ViewModel hands over a prompt and gets told what the user chose.
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onTrashPromptResult(confirmed = result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(state.trashPrompt) {
        val prompt = state.trashPrompt ?: return@LaunchedEffect
        viewModel.onTrashPromptLaunched()
        trashLauncher.launch(IntentSenderRequest.Builder(prompt.intentSender).build())
    }

    // Export settings goes through a real save-as picker: the user chooses WHERE, and the
    // snack only fires once the bytes are actually on disk.
    var pendingSettingsJson by remember { mutableStateOf<String?>(null) }
    val exportSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingSettingsJson
        pendingSettingsJson = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open destination")
        }.onSuccess {
            viewModel.showSnack("Settings saved")
        }.onFailure {
            viewModel.showSnack(it.message ?: "Save failed")
        }
    }

    val shareZip: (android.net.Uri) -> Unit = { zipUri ->
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, zipUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Save or share zip"))
    }

    // The video wall faces the way it is set to; otherwise a video in the viewer may turn with
    // the phone, and everything else follows the phone's own setting.
    LaunchedEffect(
        wall.wallOpen,
        wall.rotation,
        state.viewerOpen,
        state.viewerItem?.mediaType,
    ) {
        val orientation = when {
            wall.wallOpen -> wall.rotation.activityOrientation()
            state.viewerOpen && state.viewerItem?.mediaType == MediaType.VIDEO ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onRequestOrientation(orientation)
    }

    fun handleItemClick(item: MediaItem, list: List<MediaItem>, sourceTab: AppTab) {
        if (state.selectMode) {
            viewModel.toggleSelect(item.stableKey)
            return
        }
        viewModel.openViewer(
            keys = list.map { it.stableKey },
            index = list.indexOfFirst { it.stableKey == item.stableKey }.coerceAtLeast(0),
            slideshowMode = false,
            sourceTab = sourceTab,
        )
    }

    val hideBottomBar = (state.viewerOpen && !state.viewerChrome) || wall.wallOpen
    // Animated so the viewer's control bar GLIDES down with the departing tab bar instead of
    // snapping by the bar's height the instant chrome hides. Matches CHROME_ANIM_OUT_MS.
    val viewerControlsPadding by animateDpAsState(
        targetValue = if (hideBottomBar) 0.dp else GalleryTabBar.Height,
        animationSpec = tween(CHROME_ANIM_OUT_MS),
        label = "viewerControlsPad",
    )

    Box(Modifier.fillMaxSize()) {
        MainScaffold(
            currentTab = state.currentTab,
            visibleTabs = state.visibleTabs,
            viewerOpen = state.viewerOpen,
            viewerSlideshowMode = state.viewerSlideshowMode,
            selectMode = state.selectMode,
            bottomBarVisible = !hideBottomBar,
            selectionBar = {
                SelectionBar(
                    count = state.selectedKeys.size,
                    deleteEnabled = !state.settings.deletesDisabled,
                    onExit = viewModel::exitSelectMode,
                    onFavourite = {
                        GalleryHaptics.confirm(view, state.settings.hapticsEnabled)
                        viewModel.favouriteSelected()
                    },
                    onExport = {
                        viewModel.downloadSelectedZip { uri -> uri?.let(shareZip) }
                    },
                    onDelete = viewModel::deleteSelected,
                )
            },
            snackbarHost = {
                GallerySnackbarHost(
                    snackbarHostState = snackbarHostState,
                    message = state.snack,
                    onDismiss = viewModel::dismissSnack,
                    onAction = viewModel::runSnackAction,
                )
            },
            onTabSelected = viewModel::selectTab,
        ) {
            // Status-bar inset lives here rather than around the whole scaffold so the fullscreen
            // viewer (a sibling below) stays edge-to-edge without shifting the screen behind it.
            // The tab bar overlays the bottom, so screens reserve room for it here.
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = if (hideBottomBar) 0.dp else GalleryTabBar.Height),
            ) {
                AnimatedContent(
                    targetState = state.currentTab,
                    transitionSpec = {
                        (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            scaleIn(
                                initialScale = 0.98f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            )) togetherWith
                            (fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                                scaleOut(
                                    targetScale = 0.98f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                ))
                    },
                    label = "mainTab",
                    modifier = Modifier.fillMaxSize(),
                ) { tab ->
                    when (tab) {
                    AppTab.GALLERY -> GalleryScreen(
                        items = state.gallery,
                        totalCount = state.galleryTotal,
                        columns = state.settings.columns,
                        gridMode = state.settings.gridMode,
                        noFolders = state.noFolders,
                        favouriteKeys = state.settings.favIds,
                        selectedKeys = state.selectedKeys,
                        thumbnailPadding = state.settings.thumbnailPadding,
                        hapticsEnabled = state.settings.hapticsEnabled,
                        onToggleGridMode = viewModel::toggleGridMode,
                        onCycleColumns = viewModel::cycleColumns,
                        onShuffle = { viewModel.shuffleGrid() },
                        onItemClick = { handleItemClick(it, state.gallery, AppTab.GALLERY) },
                        onItemDoubleTap = {
                            GalleryHaptics.confirm(view, state.settings.hapticsEnabled)
                            viewModel.toggleFavourite(it.stableKey)
                        },
                        onItemLongPress = { viewModel.enterSelectMode(it.stableKey) },
                        onSwipeShuffle = viewModel::onGridSwipe,
                        onSetColumns = viewModel::setColumns,
                        pageStart = state.pageCursors[AppTab.GALLERY] ?: 0,
                        onPageGeometryChanged = viewModel::onPageGeometryChanged,
                        onScrolled = { top, last, settled ->
                            viewModel.onGridScrolled(AppTab.GALLERY, top, last, settled)
                        },
                        onGoSettings = { viewModel.selectTab(AppTab.SETTINGS) },
                    )
                    AppTab.FAV -> FavouritesScreen(
                        items = state.favourites,
                        columns = state.settings.columns,
                        noFolders = state.noFolders,
                        favouriteKeys = state.settings.favIds + state.favourites.map { it.stableKey }.toSet(),
                        selectedKeys = state.selectedKeys,
                        favTypes = state.settings.favTypes,
                        favWindow = state.settings.favWindow,
                        favTypeMenuOpen = state.favTypeMenuOpen,
                        thumbnailPadding = state.settings.thumbnailPadding,
                        gridMode = state.settings.gridMode,
                        onSwipeShuffle = viewModel::onGridSwipe,
                        pageStart = state.pageCursors[AppTab.FAV] ?: 0,
                        onPageGeometryChanged = viewModel::onPageGeometryChanged,
                        onScrolled = { top, last, settled ->
                            viewModel.onGridScrolled(AppTab.FAV, top, last, settled)
                        },
                        hapticsEnabled = state.settings.hapticsEnabled,
                        showAllFolders = state.settings.showAllFavourites,
                        onToggleAllFolders = viewModel::toggleShowAllFavourites,
                        onToggleFavTypeMenu = viewModel::toggleFavTypeMenu,
                        onToggleFavType = viewModel::toggleFavType,
                        onSelectFavWindow = viewModel::setFavWindow,
                        onItemClick = { handleItemClick(it, state.favourites, AppTab.FAV) },
                        onItemDoubleTap = {
                            GalleryHaptics.confirm(view, state.settings.hapticsEnabled)
                            viewModel.toggleFavourite(it.stableKey)
                        },
                        onItemLongPress = { viewModel.enterSelectMode(it.stableKey) },
                        onSetColumns = viewModel::setColumns,
                        onGoSettings = { viewModel.selectTab(AppTab.SETTINGS) },
                    )
                    AppTab.RECENT -> RecentScreen(
                        items = state.recent,
                        columns = state.settings.columns,
                        noFolders = state.noFolders,
                        favouriteKeys = state.settings.favIds,
                        selectedKeys = state.selectedKeys,
                        listTypes = state.settings.recentTypes,
                        listWindow = state.settings.recentWindow,
                        typeMenuOpen = state.recentTypeMenuOpen,
                        thumbnailPadding = state.settings.thumbnailPadding,
                        gridMode = state.settings.gridMode,
                        onSwipeShuffle = viewModel::onGridSwipe,
                        pageStart = state.pageCursors[AppTab.RECENT] ?: 0,
                        onPageGeometryChanged = viewModel::onPageGeometryChanged,
                        onScrolled = { top, last, settled ->
                            viewModel.onGridScrolled(AppTab.RECENT, top, last, settled)
                        },
                        hapticsEnabled = state.settings.hapticsEnabled,
                        onToggleTypeMenu = viewModel::toggleRecentTypeMenu,
                        onToggleType = viewModel::toggleRecentType,
                        onSelectWindow = viewModel::setRecentWindow,
                        onItemClick = { handleItemClick(it, state.recent, AppTab.RECENT) },
                        onItemDoubleTap = {
                            GalleryHaptics.confirm(view, state.settings.hapticsEnabled)
                            viewModel.toggleFavourite(it.stableKey)
                        },
                        onItemLongPress = { viewModel.enterSelectMode(it.stableKey) },
                        onSetColumns = viewModel::setColumns,
                        onGoSettings = { viewModel.selectTab(AppTab.SETTINGS) },
                    )
                    // Works without any folders chosen: videos can come from Android's picker too.
                    AppTab.MULTIVIDEO -> MultiVideoSetupScreen(viewModel = multiVideo)
                    AppTab.ALBUM -> AlbumsScreen(
                        albums = state.albums,
                        albumOpen = state.albumOpen,
                        albumItems = state.albumDetail,
                        columns = state.settings.columns,
                        noFolders = state.noFolders,
                        favouriteKeys = state.settings.favIds,
                        selectedKeys = state.selectedKeys,
                        thumbnailPadding = state.settings.thumbnailPadding,
                        onOpenAlbum = viewModel::openAlbum,
                        onCloseAlbum = viewModel::closeAlbum,
                        onShuffle = viewModel::shuffleAlbum,
                        pageStart = state.pageCursors[AppTab.ALBUM] ?: 0,
                        onScrolled = { top, last, settled ->
                            viewModel.onGridScrolled(AppTab.ALBUM, top, last, settled)
                        },
                        onItemClick = { handleItemClick(it, state.albumDetail, AppTab.ALBUM) },
                        onItemDoubleTap = { viewModel.toggleFavourite(it.stableKey) },
                        onItemLongPress = { viewModel.enterSelectMode(it.stableKey) },
                        onSetColumns = viewModel::setColumns,
                        onGoSettings = { viewModel.selectTab(AppTab.SETTINGS) },
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        settings = state.settings,
                        discoveredFolders = state.discoveredFolders,
                        collapsedGroups = state.collapsedGroups,
                        appVersion = appVersion,
                        countsRefreshing = state.countsRefreshing,
                        onRefreshFileTypeCounts = viewModel::refreshFileTypeCounts,
                        onToggleDark = viewModel::toggleTheme,
                        onToggleAmoled = viewModel::toggleAmoled,
                        onSetAccent = viewModel::setAccent,
                        appIcon = appIcon,
                        onSetAppIcon = { chosen ->
                            AppIconSwitcher.set(context, chosen)
                            appIcon = chosen
                        },
                        onMoveTab = viewModel::moveTab,
                        onToggleTabVisibility = viewModel::toggleTabVisibility,
                        onToggleFolder = viewModel::toggleFolder,
                        onToggleGroup = viewModel::toggleGroupCollapsed,
                        onToggleFileType = viewModel::toggleFileType,
                        onToggleBehaviour = viewModel::toggleBehaviour,
                        onToggleCopyFavs = viewModel::toggleCopyFavs,
                        onToggleShowAllFavourites = viewModel::toggleShowAllFavourites,
                        onChooseFavFolder = { favFolderLauncher.launch(null) },
                        onOpenHiddenFolders = viewModel::openHiddenFoldersDialog,
                        onExportSettings = {
                            viewModel.exportSettings { json ->
                                pendingSettingsJson = json
                                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                    .format(java.util.Date())
                                exportSettingsLauncher.launch("windfall_settings_$stamp.json")
                            }
                        },
                        onDownloadFavs = {
                            viewModel.downloadFavouritesZip { uri -> uri?.let(shareZip) }
                        },
                        onImportSettings = {
                            importLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/*",
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        onAddSafFolder = { safFolderLauncher.launch(null) },
                        onResetSettings = viewModel::requestResetSettings,
                        onShareLogs = {
                            viewModel.captureLogs { file ->
                                context.startActivity(
                                    Intent.createChooser(LogCapture.shareIntent(context, file), "Share log"),
                                )
                            }
                        },
                        onOpenGithub = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)),
                                )
                            }
                        },
                        onOpenRate = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playStoreUrl)))
                            }.onFailure {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(playStoreWebUrl)))
                                }
                            }
                        },
                    )
                    AppTab.SLIDESHOW -> Unit
                    }
                }

                // Slim and non-blocking: rescans happen on ordinary actions like ticking a folder,
                // and a centred spinner made those feel like the app had stalled.
                if (state.loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                    )
                }
            }

            AnimatedVisibility(
                visible = state.viewerOpen,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    scaleIn(
                        initialScale = 0.94f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ),
                exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleOut(
                        targetScale = 0.94f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    ),
            ) {
                FullscreenViewer(
                    item = state.viewerItem,
                    index = state.viewerIndex,
                    count = state.viewerCount,
                    itemAt = viewModel::viewerItemAt,
                    isPlaying = state.viewerPlaying,
                    chromeVisible = state.viewerChrome,
                    menuOpen = state.viewerMenuOpen,
                    speedMenuOpen = state.speedMenuOpen,
                    speedIndex = state.settings.speedIdx,
                    customMs = state.settings.customMs,
                    isFavourite = state.viewerItem?.let { viewModel.isFavouriteItem(it) } == true,
                    disableSwipeDelete = state.settings.disableSwipeDelete || state.settings.deletesDisabled,
                    deleteEnabled = !state.settings.deletesDisabled,
                    slideshowMode = state.viewerSlideshowMode,
                    muted = state.viewerMuted,
                    loopVideos = !state.settings.dontLoop,
                    hapticsEnabled = state.settings.hapticsEnabled,
                    onClose = viewModel::closeViewer,
                    onToggleChrome = viewModel::toggleViewerChrome,
                    onJumpTo = viewModel::viewerJumpTo,
                    onSwipeUpDelete = viewModel::viewerSwipeUpDelete,
                    onTogglePlay = viewModel::togglePlayPause,
                    onToggleMenu = viewModel::toggleViewerMenu,
                    onToggleSpeedMenu = viewModel::toggleSpeedMenu,
                    onSpeedSelected = viewModel::setSpeedIndex,
                    onToggleFavourite = {
                        GalleryHaptics.confirm(view, state.settings.hapticsEnabled)
                        state.viewerItem?.stableKey?.let(viewModel::toggleFavourite)
                    },
                    onShare = {
                        viewModel.shareCurrentItem()?.let { intent ->
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        }
                    },
                    onDelete = viewModel::requestDeleteCurrent,
                    onDetails = viewModel::openDetails,
                    onToggleMute = viewModel::toggleViewerMute,
                    onVideoEnded = viewModel::onViewerVideoEnded,
                    onUserInteracted = viewModel::noteViewerInteraction,
                    chromeAutoHideNonce = state.viewerChromeNonce,
                    prefetch = state.viewerPrefetch,
                    farPrefetch = state.viewerFarPrefetch,
                    gridThumbBucketPx = gridThumbBucketPx,
                    // Keep the scrubber and play controls clear of the overlaid tab bar.
                    controlsBottomPadding = viewerControlsPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (state.confirmResetSettings) {
                ResetSettingsConfirmDialog(
                    onConfirm = viewModel::confirmResetSettings,
                    onDismiss = viewModel::cancelResetSettings,
                )
            }

            if (state.detailsOpen) {
                DetailsDialog(item = state.viewerItem, onDismiss = viewModel::closeDetails)
            }

            if (state.customSpeedOpen) {
                CustomSpeedDialog(
                    initialSeconds = state.customSpeedSeconds,
                    onConfirm = viewModel::confirmCustomSpeed,
                    onDismiss = viewModel::dismissCustomSpeed,
                )
            }

            if (state.hiddenFoldersDialog) {
                HiddenFoldersDialog(
                    folders = state.settings.hiddenFolders,
                    onToggle = viewModel::toggleHiddenFolder,
                    onDismiss = viewModel::closeHiddenFoldersDialog,
                )
            }
        }

        // Above the whole shell, tab bar included: the wall is videos and nothing else, and the
        // picker needs the full screen for its thumbnails.
        if (wall.wallOpen) {
            MultiVideoWall(viewModel = multiVideo)
        }
        videoPicker?.let { target ->
            VideoPicker(
                target = target,
                libraryVideos = state.videos,
                onChooseLibrary = { items ->
                    multiVideo.choose(items.map { WallVideo(uri = it.uri.toString(), name = it.displayName) })
                },
                onChooseUris = multiVideo::chooseUris,
                onDismiss = multiVideo::closePicker,
            )
        }
    }
}
