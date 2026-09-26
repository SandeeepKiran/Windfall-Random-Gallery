package com.mousy.windfall.ui.navigation

import androidx.compose.animation.AnimatedVisibility
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
                    isSelected = { tab ->
                        if (viewerOpen && viewerSlideshowMode) tab == AppTab.SLIDESHOW
                        else tab == currentTab
                    },
                    onTabSelected = onTabSelected,
                )
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
                val selected = isSelected(tab)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = { onTabSelected(tab) }),
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
        }
    }
}
