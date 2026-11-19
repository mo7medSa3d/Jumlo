package com.jumlo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.model.OutputRecord
import com.jumlo.app.ui.theme.QuietTheme

/**
 * Shared output file details dialog used by Files and Downloads screens so metadata
 * presentation, RTL BiDi handling, and shape tokens stay consistent in one place.
 */
@Composable
fun OutputDetailsDialog(
    output: OutputRecord,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = QuietTheme.shapes.heroCard,
        title = {
            Text(
                text = output.displayName,
                style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = output.mimeType,
                    style = QuietTheme.typography.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatOutputSizeSummary(output),
                    style = QuietTheme.typography.bodyMedium
                )
                output.metadata.pageCount?.let { count ->
                    Text(
                        text = stringResource(R.string.output_pages_count, count),
                        style = QuietTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.output_sha256_label, output.sha256.take(16) + "…"),
                    style = QuietTheme.typography.metadata.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val savedDescription = if (output.publicUri.isNullOrBlank()) {
                    stringResource(R.string.output_saved_privately)
                } else {
                    val folder = buildString {
                        append("Jumlo / ").append(StoragePaths.category(output.mimeType))
                        output.metadata.subfolder?.takeIf { it.isNotBlank() }?.let { append(" / ").append(it) }
                    }
                    stringResource(R.string.output_saved_location, folder)
                }
                Text(
                    text = savedDescription,
                    style = QuietTheme.typography.metadata.copy(textDirection = TextDirection.Content),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.account_done))
            }
        }
    )
}
