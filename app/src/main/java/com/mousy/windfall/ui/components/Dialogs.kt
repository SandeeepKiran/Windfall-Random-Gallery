package com.mousy.windfall.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mousy.windfall.data.model.MediaItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DialogButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)

@Composable
fun ResetSettingsConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset all settings?") },
        text = {
            Text("Appearance, folders, filters, and safety toggles return to defaults. Favourites are kept. You can Undo from the snackbar.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                contentPadding = DialogButtonPadding,
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(
                    "Yes, reset",
                    color = Color(0xFFE53935),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = DialogButtonPadding,
                modifier = Modifier.heightIn(min = 52.dp),
            ) {
                Text(
                    "No, keep settings",
                    color = Color(0xFF43A047),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    )
}

@Composable
fun DetailsDialog(
    item: MediaItem?,
    onDismiss: () -> Unit,
) {
    if (item == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column {
                DetailRow("Name", item.displayName)
                DetailRow("Type", "${item.mediaType.name} · ${item.extension}")
                DetailRow("Resolution", "${item.width} × ${item.height}")
                DetailRow("Size", formatBytes(item.sizeBytes))
                DetailRow(
                    "Date taken",
                    formatDetailDate(if (item.dateTakenMs > 0) item.dateTakenMs else item.dateAddedMs),
                )
                DetailRow("Folder", item.folderPath)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = DialogButtonPadding,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Close", style = MaterialTheme.typography.titleMedium) }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text("$label: ", modifier = Modifier.weight(0.4f))
        Text(value, modifier = Modifier.weight(0.6f))
    }
}

@Composable
fun CustomSpeedDialog(
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var seconds by remember(initialSeconds) { mutableStateOf(initialSeconds.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom slideshow speed") },
        text = {
            OutlinedTextField(
                value = seconds,
                onValueChange = { seconds = it.filter { ch -> ch.isDigit() } },
                label = { Text("Seconds") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(seconds.toIntOrNull()?.coerceAtLeast(1) ?: 1)
                },
                contentPadding = DialogButtonPadding,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("OK", style = MaterialTheme.typography.titleMedium) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = DialogButtonPadding,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Cancel", style = MaterialTheme.typography.titleMedium) }
        },
    )
}

@Composable
fun HiddenFoldersDialog(
    folders: Map<String, Boolean>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden Folders") },
        text = {
            Column {
                folders.forEach { (name, included) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(name) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = included, onCheckedChange = { onToggle(name) })
                        Text(name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = DialogButtonPadding,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Done", style = MaterialTheme.typography.titleMedium) }
        },
    )
}

@Composable
fun VideoPickerDialog(
    videos: List<MediaItem>,
    onSelect: (MediaItem?) -> Unit,
    onPickGallery: () -> Unit = {},
    onPickFiles: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose media") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(null) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text("None (clear)")
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPickGallery)
                            .padding(vertical = 10.dp),
                    ) {
                        Text("System gallery / photos…")
                    }
                }
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPickFiles)
                            .padding(vertical = 10.dp),
                    ) {
                        Text("File explorer…")
                    }
                }
                items(videos, key = { it.stableKey }) { video ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(video) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            buildString {
                                append(video.displayName)
                                if (video.mediaType.name == "AUDIO") append("  ♪")
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = DialogButtonPadding,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Close", style = MaterialTheme.typography.titleMedium) }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

private fun formatDetailDate(ms: Long): String {
    if (ms <= 0) return "—"
    return SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(ms))
}
