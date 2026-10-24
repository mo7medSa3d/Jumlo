package com.jumlo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jumlo.app.model.PillarCategory
import com.jumlo.app.ui.theme.QuietTheme

/** A single action button on a tool result screen. */
data class ToolResultAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

/** Shared shell for every tool page: slim top bar, scrollable content area and a
 *  sticky bottom action bar (navigation-bar insets applied). The caller supplies
 *  the drop/pick zone, editor and preview as [content], and the primary actions
 *  as [bottomActions]. */
@Composable
fun ToolScaffold(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    pillar: PillarCategory = PillarCategory.PDF,
    toolId: String? = null,
    onBack: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    bottomActions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        QuietWorkspaceTopBar(
            title = title, subtitle = subtitle, badgeText = badgeText, pillar = pillar,
            onBack = onBack, onMoreClick = onMoreClick, trailingContent = trailingContent, toolId = toolId
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                content()
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomActions()
            }
        }
    }
}

/** Progress overlay shown while a tool runs. A ModalBottomSheet keeps it visible
 *  above the preview without blocking the whole screen. */
@Composable
fun ToolProgressSheet(visible: Boolean, title: String, message: String?, onDismiss: () -> Unit) {
    if (!visible) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text(title, style = QuietTheme.typography.sectionTitle)
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = QuietTheme.typography.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** Result screen shown after a tool completes. Primary actions (Open / Share /
 *  Save / Print / Use in another tool) are supplied as [actions]; a Close button
 *  returns to the previous screen. */
@Composable
fun ToolResultScreen(
    title: String,
    message: String? = null,
    onBack: () -> Unit,
    actions: List<ToolResultAction>
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(title, style = QuietTheme.typography.sectionTitle, fontWeight = FontWeight.SemiBold)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = QuietTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        if (actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    Button(onClick = action.onClick, modifier = Modifier.weight(1f)) {
                        Icon(action.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(action.label, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_close)) }
    }
}
