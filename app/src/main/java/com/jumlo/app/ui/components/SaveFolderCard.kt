package com.jumlo.app.ui.components

import android.net.Uri
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
    Surface(modifier.fillMaxWidth().testTag("saveFolder.card"), shape = QuietTheme.shapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_prism_folder), null, Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pref_save_location), style = QuietTheme.typography.metadata)
                    Text(folder, style = QuietTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("saveFolder.name"))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.redesign_open_folder), maxLines = 1)
                }
                TextButton(onClick = onChoose, modifier = Modifier.weight(1f).testTag("saveFolder.change")) {
                    Text(stringResource(R.string.modern_change_folder), maxLines = 1)
                }
            }
        }
    }
}
