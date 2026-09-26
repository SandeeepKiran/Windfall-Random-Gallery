package com.mousy.windfall.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mousy.windfall.R
import com.mousy.windfall.data.media.MediaRepository
import com.mousy.windfall.data.model.AccentColor
import com.mousy.windfall.data.model.AppSettings
import com.mousy.windfall.data.model.AppTab
import com.mousy.windfall.data.model.SlideshowSpeeds
import com.mousy.windfall.data.model.ThemeMode
import com.mousy.windfall.util.AppLauncherIcon
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    settings: AppSettings,
    discoveredFolders: List<MediaRepository.FolderInfo>,
    collapsedGroups: Set<String>,
    onToggleDark: () -> Unit,
    onToggleAmoled: () -> Unit,
    onSetAccent: (AccentColor) -> Unit,
    appIcon: AppLauncherIcon,
    onSetAppIcon: (AppLauncherIcon) -> Unit,
    onMoveTab: (AppTab, Int) -> Unit,
    onToggleTabVisibility: (AppTab) -> Unit,
    onToggleFolder: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
    onToggleFileType: (String) -> Unit,
    onToggleBehaviour: (String) -> Unit,
    onToggleCopyFavs: () -> Unit,
    onToggleShowAllFavourites: () -> Unit,
    onChooseFavFolder: () -> Unit,
    onOpenHiddenFolders: () -> Unit,
    onExportSettings: () -> Unit,
    onDownloadFavs: () -> Unit,
    onImportSettings: () -> Unit,
    onAddSafFolder: () -> Unit,
    onOpenGithub: () -> Unit = {},
    onOpenRate: () -> Unit = {},
    onResetSettings: () -> Unit,
    onShareLogs: () -> Unit,
    appVersion: String,
    countsRefreshing: Boolean = false,
    onRefreshFileTypeCounts: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Text(
            "Windfall",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )

        hints.forEach { (icon, text) ->
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text,
                        modifier = Modifier.padding(start = 14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        SectionTitle("Appearance")
        SettingsGroup {
            ToggleRow("Enable Dark Mode", settings.themeMode == ThemeMode.DARK, onToggleDark)
            HorizontalDivider()
            ToggleRow("AMOLED Black", settings.amoled, onToggleAmoled, subtitle = "Pure-black surfaces in dark mode")
            HorizontalDivider()
            Text("Accent color", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AccentColor.entries.forEach { accent ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(accent.pastelHex))
                                .border(
                                    if (settings.accent == accent) 3.dp else 2.dp,
                                    if (settings.accent == accent) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    CircleShape,
                                )
                                .clickable { onSetAccent(accent) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (settings.accent == accent) {
                                Icon(Icons.Default.Check, null, tint = Color.Black.copy(alpha = 0.5f))
                            }
                        }
                        Text(accent.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider()
            Text("App icon", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(16.dp))
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIconChoice(AppLauncherIcon.WINDROSE, R.drawable.ic_fg_windrose_square, Color(0xFF0E3A43), appIcon, onSetAppIcon)
                AppIconChoice(AppLauncherIcon.CLASSIC, R.drawable.ic_fg_gemini_replica, Color(0xFF0E3A43), appIcon, onSetAppIcon)
                AppIconChoice(AppLauncherIcon.MONO, R.drawable.ic_fg_mono_still, Color(0xFFDFE5E0), appIcon, onSetAppIcon)
            }
        }

        SectionTitle("Source Folders")
        Text(
            "Select folders containing media. Use “Add folder” for SAF tree access.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsGroup {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddSafFolder)
                    .padding(16.dp),
            ) {
                Text("Add folder (SAF)", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            val groups = discoveredFolders.groupBy { it.group.ifBlank { "Other" } }
            groups.forEach { (group, folders) ->
                val open = group !in collapsedGroups
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggleGroup(group) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(if (open) "▲" else "▼")
                }
                if (open) {
                    folders.forEach { folder ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggleFolder(folder.path) }
                                .padding(start = 30.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(folder.path, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = settings.selectedFolders.any {
                                    MediaRepository.normalizeFolderPath(it)
                                        .equals(
                                            MediaRepository.normalizeFolderPath(folder.path),
                                            ignoreCase = true,
                                        )
                                },
                                onCheckedChange = { onToggleFolder(folder.path) },
                            )
                        }
                    }
                }
            }
        }

        SectionTitle("File Types")
        SettingsGroup {
            // Counting every file is a full-library walk, so it runs on demand and the list
            // shows the numbers from the previous scan in the meantime.
            FileTypeCountHeader(
                scannedAtMs = settings.fileTypeCountsScannedAtMs,
                refreshing = countsRefreshing,
                onRefresh = onRefreshFileTypeCounts,
            )
            HorizontalDivider()
            // Always show discovered extensions from scan; never fall back to a hardcoded preset
            // when discoveries exist. Empty only when nothing has been scanned yet.
            val discovered = settings.discoveredFileTypeCounts
            val exts = (discovered.keys + settings.fileTypes.keys)
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
            if (exts.isEmpty()) {
                Text(
                    "Select a source folder to detect file types from your library.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                exts.forEach { ext ->
                    val supported = ext in SlideshowSpeeds.supportedExtensions
                    val count = discovered[ext] ?: 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(if (supported) Modifier.clickable { onToggleFileType(ext) } else Modifier)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ".$ext",
                                color = if (supported) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                },
                            )
                            Text(
                                if (supported) {
                                    "$count files"
                                } else {
                                    "not media · $count files"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (supported) 1f else 0.55f,
                                ),
                            )
                        }
                        Checkbox(
                            checked = settings.fileTypes[ext] ?: supported,
                            onCheckedChange = { if (supported) onToggleFileType(ext) },
                            enabled = supported,
                        )
                    }
                }
            }
        }

        SectionTitle("Tabs & Layout")
        Text(
            "Reorder the bottom bar and choose which tabs appear. Gallery and More are always shown. Multi-Video and Albums are off by default.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        SettingsGroup {
            settings.tabOrder.forEachIndexed { index, tab ->
                TabConfigRow(
                    tab = tab,
                    locked = tab.locked,
                    visible = tab !in settings.tabHidden,
                    canMoveUp = index > 0,
                    canMoveDown = index < settings.tabOrder.lastIndex,
                    onMoveUp = { onMoveTab(tab, -1) },
                    onMoveDown = { onMoveTab(tab, 1) },
                    onToggleVisibility = { onToggleTabVisibility(tab) },
                )
                if (index < settings.tabOrder.lastIndex) HorizontalDivider()
            }
        }

        SectionTitle("Playback & Safety")
        SettingsGroup {
            ToggleRow("Don't loop videos", settings.dontLoop, onToggle = { onToggleBehaviour("dontLoop") })
            HorizontalDivider()
            ToggleRow(
                "Disable 'Swipe up to Delete'",
                settings.disableSwipeDelete,
                subtitle = "In fullscreen, swipe up on a photo to delete it after confirmation. When on, that gesture is blocked.",
                onToggle = { onToggleBehaviour("disableSwipeDelete") },
            )
            HorizontalDivider()
            ToggleRow(
                label = "Disable all delete options",
                checked = settings.deletesDisabled,
                subtitle = "Hides delete actions and blocks deletion prompts throughout the app.",
                onToggle = { onToggleBehaviour("disableDeleteOptions") },
            )
            HorizontalDivider()
            ToggleRow(
                label = "Haptic feedback",
                checked = settings.hapticsEnabled,
                subtitle = "Vibrate lightly when a shuffle or delete is confirmed.",
                onToggle = { onToggleBehaviour("hapticsEnabled") },
            )
            HorizontalDivider()
            ToggleRow(
                label = "Padded thumbnails",
                checked = settings.thumbnailPadding,
                subtitle = "On: padded rounded tiles. Off: edge-to-edge grid.",
                onToggle = { onToggleBehaviour("thumbnailPadding") },
            )
        }

        SectionTitle("Storage & Data")
        SettingsGroup {
            ToggleRow(
                label = "Favourites from all folders",
                checked = settings.showAllFavourites,
                subtitle = "Show favourited media even when its folder isn't selected as a source.",
                onToggle = onToggleShowAllFavourites,
            )
            HorizontalDivider()
            ActionRow(Icons.Default.FolderCopy, "Favourites folder", "Auto copy/remove favourites", settings.copyFavs, onToggleCopyFavs)
            if (settings.copyFavs) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChooseFavFolder)
                        .padding(start = 30.dp, end = 18.dp, bottom = 12.dp),
                ) {
                    Text(settings.copyFavPath.ifBlank { "Pick a folder…" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            SimpleActionRow(Icons.Default.FolderSpecial, "Hidden Folders", onOpenHiddenFolders)
            HorizontalDivider()
            SimpleActionRow(Icons.Default.IosShare, "Export settings", onExportSettings)
            HorizontalDivider()
            SimpleActionRow(Icons.Default.Archive, "Download favourites (.zip)", onDownloadFavs)
            HorizontalDivider()
            SimpleActionRow(Icons.Default.FileDownload, "Import settings / favourites", onImportSettings)
            HorizontalDivider()
            SimpleActionRow(Icons.Default.Archive, "Reset all settings", onResetSettings)
        }

        SectionTitle("Diagnostics")
        SettingsGroup {
            SimpleActionRow(Icons.Default.Share, "Share crash log", onShareLogs)
        }

        Text(
            "Help us to grow and to bring new and improved features to Windfall!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedAction(Icons.Default.Code, "GITHUB", Modifier.weight(1f), onClick = onOpenGithub)
            FilledAction(Icons.Default.Star, "RATE", Modifier.weight(1f), onClick = onOpenRate)
        }

        Spacer(Modifier.height(48.dp))
        Text(
            "Made with ❤️ by Sandeep Kiran (Mousy)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )
        Text(
            "App Version: $appVersion",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp),
        )
    }
}

@Composable
private fun FileTypeCountHeader(
    scannedAtMs: Long,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val subtitle = when {
        refreshing -> "Counting files…"
        scannedAtMs <= 0L -> "Not counted yet"
        else -> "Based on last scan · ${formatScanTime(scannedAtMs)}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !refreshing, onClick = onRefresh)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Refresh counts")
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (refreshing) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh counts", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatScanTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        Column { content() }
    }
}

/**
 * One selectable launcher-icon preview. The foreground vector is drawn scaled up over the
 * icon's plate colour, roughly matching how the adaptive icon looks after launcher masking.
 */
@Composable
private fun AppIconChoice(
    icon: AppLauncherIcon,
    @DrawableRes foreground: Int,
    plate: Color,
    selected: AppLauncherIcon,
    onSelect: (AppLauncherIcon) -> Unit,
) {
    val isSelected = selected == icon
    val shape = RoundedCornerShape(18.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(shape)
                .background(plate)
                .border(
                    if (isSelected) 3.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape,
                )
                .clickable { onSelect(icon) },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(foreground),
                contentDescription = icon.label,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.45f),
            )
        }
        Text(
            icon.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    leading: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun TabConfigRow(
    tab: AppTab,
    locked: Boolean,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(tab.label, modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp))
        if (locked) Text("always", style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.ArrowUpward, null)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.ArrowDownward, null)
        }
        Checkbox(checked = visible, onCheckedChange = { if (!locked) onToggleVisibility() }, enabled = !locked)
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier
            .weight(1f)
            .padding(horizontal = 14.dp)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SimpleActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(title, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun OutlinedAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null)
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun FilledAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
            Text(label, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private val hints = listOf(
    Icons.Default.Swipe to "Swipe the grid for new pics",
    Icons.Default.ZoomIn to "Pinch to zoom for more/less photos",
    Icons.Default.TouchApp to "Double tap to add photos to favourites",
    Icons.Default.Share to "Hold to select multiple images",
    Icons.Default.Swipe to "Swipe up in fullscreen mode to delete",
)
