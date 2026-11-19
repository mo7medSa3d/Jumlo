package com.jumlo.app.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.jumlo.app.R
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Provider names are resolved on IO; changing a saved tree updates both Files and Settings. */
@Composable fun rememberSaveFolderLabel(destination: String): String {
    val context = LocalContext.current
    val fallback = if (destination.startsWith("content://")) stringResource(R.string.modern_chosen_folder) else destination
    val label by produceState(fallback, destination, fallback) {
        value = if (!destination.startsWith("content://")) destination else withContext(Dispatchers.IO) {
            runCatching { DocumentFile.fromTreeUri(context, Uri.parse(destination))?.name }
                .getOrNull()?.takeIf(String::isNotBlank) ?: fallback
        }
    }
    return label
}

@Composable fun SaveFolderCard(destination: String, onChoose: () -> Unit, onOpen: () -> Unit,
    modifier: Modifier = Modifier) {
    val folder = rememberSaveFolderLabel(destination)
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    Surface(
        modifier.fillMaxWidth().testTag("saveFolder.card"),
        shape = QuietTheme.shapes.card,
        color = pillars.cardSurface,
        border = BorderStroke(1.dp, pillars.cardBorder),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = com.jumlo.app.ui.theme.IconTileShape,
                    color = colors.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_prism_folder),
                            null,
                            Modifier.size(21.dp),
                            tint = colors.onPrimaryContainer
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.pref_save_location),
                        style = QuietTheme.typography.metadata,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        folder,
                        style = QuietTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("saveFolder.name")
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = QuietTheme.shapes.button,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        stringResource(R.string.redesign_open_folder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onChoose,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("saveFolder.change"),
                    shape = QuietTheme.shapes.button,
                    border = BorderStroke(1.dp, pillars.cardBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        stringResource(R.string.modern_change_folder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
