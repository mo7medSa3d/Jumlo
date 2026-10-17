package com.jumlo.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.BuildConfig
import com.jumlo.app.R
import com.jumlo.app.ui.StorageUsage

@Composable internal fun PreferenceHeading(text: String) {
    Text(text, Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable fun PreferenceRow(title: String, summary: String?, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Text(body, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.account_done)) } })
}

@Composable fun PrivacyDialog(onDismiss: () -> Unit) = InfoDialog(
    stringResource(R.string.account_privacy), stringResource(R.string.account_privacy_body), onDismiss)

@Composable fun AboutDialog(onDismiss: () -> Unit) = InfoDialog(
    stringResource(R.string.account_help_about), stringResource(R.string.account_about_body, BuildConfig.VERSION_NAME), onDismiss)

@Composable fun StorageOverviewDialog(usage: StorageUsage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items = listOf(R.string.storage_imports to usage.sources, R.string.storage_results to usage.outputs,
        R.string.storage_transfers to usage.partials, R.string.storage_temporary to usage.temporary, R.string.storage_trash to usage.trash)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.account_storage)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { (label, bytes) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Formatter.formatFileSize(context, bytes), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                }
            }
            Text(stringResource(R.string.storage_scope), style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.account_done)) } })
}
