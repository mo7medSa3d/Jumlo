package com.jumlo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jumlo.app.R

/** Local-only first-use guide. No fake accounts, premium status or cloud promises. */
@Composable
fun OnboardingGuide(
    onImport: () -> Unit,
    onScan: () -> Unit,
    onExplore: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().testTag("onboarding.guide")
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.quick_start_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    stringResource(R.string.quick_start_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                OnboardingAction(
                    R.string.quick_start_import,
                    R.string.quick_start_import_desc,
                    onImport,
                    "onboarding.import"
                ) { Icon(Icons.Rounded.FolderOpen, null) }
                OnboardingAction(
                    R.string.quick_start_scan,
                    R.string.quick_start_scan_desc,
                    onScan,
                    "onboarding.scan"
                ) { Icon(Icons.Rounded.DocumentScanner, null) }
                OnboardingAction(
                    R.string.quick_start_explore,
                    R.string.quick_start_explore_desc,
                    onExplore,
                    "onboarding.explore"
                ) { Icon(Icons.Rounded.GridView, null) }
                Text(
                    stringResource(R.string.quick_start_privacy),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                        .testTag("onboarding.dismiss")
                ) { Text(stringResource(R.string.quick_start_skip)) }
            }
        }
    }
}

@Composable
private fun OnboardingAction(
    title: Int,
    description: Int,
    onClick: () -> Unit,
    tag: String,
    icon: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        icon()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
