package com.mousy.windfall.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mousy.windfall.data.model.SnackMessage

@Composable
fun GallerySnackbarHost(
    snackbarHostState: SnackbarHostState,
    message: SnackMessage?,
    onDismiss: () -> Unit,
    /** Gets the message whose action was tapped, which may no longer be the current one. */
    onAction: (SnackMessage) -> Unit,
) {
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = m.text,
            actionLabel = m.actionLabel,
            withDismissAction = m.actionLabel == null,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onAction(m)
            SnackbarResult.Dismissed -> onDismiss()
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(12.dp),
    ) { data ->
        Snackbar(
            action = {
                data.visuals.actionLabel?.let { label ->
                    TextButton(onClick = { data.performAction() }) {
                        Text(label)
                    }
                }
            },
        ) {
            Text(data.visuals.message)
        }
    }
}

@Composable
fun SelectionBar(
    count: Int,
    deleteEnabled: Boolean,
    onExit: () -> Unit,
    onFavourite: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        TextButton(onClick = onExit) { Text("Close") }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        TextButton(onClick = onFavourite) { Text("Fav") }
        TextButton(onClick = onExport) { Text("Export") }
        TextButton(onClick = onDelete, enabled = deleteEnabled) { Text("Delete") }
    }
}
