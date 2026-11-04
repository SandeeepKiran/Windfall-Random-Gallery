package com.mousy.windfall.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.mousy.windfall.ui.components.ThumbSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mousy.windfall.data.model.AppTab
import com.mousy.windfall.ui.components.CHROME_ANIM_IN_MS
import com.mousy.windfall.ui.components.CHROME_ANIM_OUT_MS

/** Icon-only bar: every dp saved here is another row of thumbnails. */
object GalleryTabBar {
    val Height = 56.dp

    /** Width of the side rail that large screens use instead of the bottom bar. */
    val RailWidth = 72.dp

    /**
     * Tablets, Chromebooks and large desktop windows show the tabs on a side rail: a bottom bar
     * across a wide screen is far from the hand and wastes a full row of thumbnails.
     */
    @Composable
    @ReadOnlyComposable
    fun useSideRail(): Boolean = ThumbSpec.isLargeScreen(LocalConfiguration.current.smallestScreenWidthDp)
}

/**
 * App shell: full-bleed content with a compact icon-only tab bar floating over the bottom.
 *
 * The bar overlays rather than reserving layout space, so the fullscreen viewer stays edge to
 * edge and media never re-lays-out when chrome fades. Screens that scroll get bottom padding
 * instead (see [contentBottomPadding]).
 */
@Composable
fun MainScaffold(
    currentTab: AppTab,
    visibleTabs: List<AppTab>,
    viewerOpen: Boolean,
    viewerSlideshowMode: Boolean = false,
    selectMode: Boolean,
    snackbarHost: @Composable () -> Unit,
    selectionBar: @Composable () -> Unit,
    bottomBarVisible: Boolean = true,
    onTabSelected: (AppTab) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        snackbarHost = snackbarHost,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()
            }

            if (selectMode) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    selectionBar()
                }
            }

            val isSelected: (AppTab) -> Boolean = { tab ->
                if (viewerOpen && viewerSlideshowMode) tab == AppTab.SLIDESHOW
                else tab == currentTab
            }
            if (GalleryTabBar.useSideRail()) {
                SideTabRail(
                    visible = bottomBarVisible,
                    tabs = visibleTabs,
                    isSelected = isSelected,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            } else {
                AnimatedVisibility(
                    visible = bottomBarVisible,
                    // Matches CHROME_ANIM_* in FullscreenViewer so the tab bar and the viewer's
                    // control bar leave the screen as one motion, not at two speeds.
                    enter = fadeIn(tween(CHROME_ANIM_IN_MS)) +
                        slideInVertically(tween(CHROME_ANIM_IN_MS)) { it },
                    exit = fadeOut(tween(CHROME_ANIM_OUT_MS)) +
                        slideOutVertically(tween(CHROME_ANIM_OUT_MS)) { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    CompactTabBar(
                        tabs = visibleTabs,
                        isSelected = isSelected,
                        onTabSelected = onTabSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTabBar(
    tabs: List<AppTab>,
    isSelected: (AppTab) -> Boolean,
    onTabSelected: (AppTab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(GalleryTabBar.Height),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                TabIcon(
                    tab = tab,
                    selected = isSelected(tab),
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/** One tab: its icon, on a pill when selected. Shared by the bottom bar and the side rail. */
@Composable
private fun TabIcon(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else androidx.compose.ui.graphics.Color.Transparent,
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Large-screen tabs: a short rail at the left edge, centred top to bottom. It is only as tall as
 * its icons, so it does not cover the viewer's top bar or its video controls.
 */
@Composable
private fun SideTabRail(
    visible: Boolean,
    tabs: List<AppTab>,
    isSelected: (AppTab) -> Boolean,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(CHROME_ANIM_IN_MS)) +
            slideInHorizontally(tween(CHROME_ANIM_IN_MS)) { -it },
        exit = fadeOut(tween(CHROME_ANIM_OUT_MS)) +
            slideOutHorizontally(tween(CHROME_ANIM_OUT_MS)) { -it },
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
            // A side navigation bar or a display cutout can sit at the left edge in landscape.
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Start),
            ),
        ) {
            Column(
                modifier = Modifier
                    .width(GalleryTabBar.RailWidth)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tabs.forEach { tab ->
                    TabIcon(
                        tab = tab,
                        selected = isSelected(tab),
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.size(width = GalleryTabBar.RailWidth, height = GalleryTabBar.Height),
                    )
                }
            }
        }
    }
}
