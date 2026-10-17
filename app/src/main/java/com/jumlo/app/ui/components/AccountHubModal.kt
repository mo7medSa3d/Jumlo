package com.jumlo.app.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.AppSettingsState
import com.jumlo.app.ui.StorageUsage
import com.jumlo.app.ui.settings.*

/** Local data hub; no invented identity, membership tier or connected account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountHubModal(settings: AppSettingsState, storage: StorageUsage, outputsCount: Int,
    onDismiss: () -> Unit, onOpenSettings: () -> Unit, onOpenFiles: () -> Unit) {
    var panel by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val total = storage.sources + storage.outputs + storage.partials + storage.temporary + storage.trash
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("account.hub")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(stringResource(R.string.account_data_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.account_local_workspace), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.account_local_description), Modifier.padding(top = 6.dp, bottom = 20.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceRow(stringResource(R.string.account_my_files), stringResource(R.string.account_result_count, outputsCount),
                Icons.Rounded.FolderOpen, { onDismiss(); onOpenFiles() }, Modifier.testTag("account.files"))
            PreferenceRow(stringResource(R.string.account_storage), Formatter.formatFileSize(context, total),
                Icons.Rounded.Storage, { panel = "storage" }, Modifier.testTag("account.storage"))
            PreferenceRow(stringResource(R.string.settings_title),
                stringResource(if (settings.language.isRtl) R.string.account_language_ar else R.string.account_language_en),
                Icons.Rounded.Settings, { onDismiss(); onOpenSettings() }, Modifier.testTag("account.settings"))
            PreferenceRow(stringResource(R.string.account_privacy), null, Icons.Rounded.PrivacyTip,
                { panel = "privacy" }, Modifier.testTag("account.privacy"))
            PreferenceRow(stringResource(R.string.account_help_about), null, Icons.Rounded.HelpOutline,
                { panel = "about" }, Modifier.testTag("account.about"))
        }
    }
    when (panel) {
        "storage" -> StorageOverviewDialog(usage = storage, onDismiss = { panel = null })
        "privacy" -> PrivacyDialog { panel = null }
        "about" -> AboutDialog { panel = null }
    }
}
