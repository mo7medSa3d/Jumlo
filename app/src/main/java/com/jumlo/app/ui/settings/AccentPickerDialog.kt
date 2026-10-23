package com.jumlo.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Check
import com.jumlo.app.ui.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R

/** A directly tappable, fully labelled accent preview. No numeric color inputs. */
@Composable
internal fun AccentPickerDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accents = listOf(
        Triple("BLUE", R.string.redesign_accent_blue, Color(0xFF2F46C6)),
        Triple("VIOLET", R.string.redesign_accent_violet, Color(0xFF6942B0)),
        Triple("TEAL", R.string.redesign_accent_teal, Color(0xFF086F6B)),
        Triple("CORAL", R.string.redesign_accent_coral, Color(0xFFB63E54)),
        Triple("DYNAMIC", R.string.redesign_accent_dynamic, colors.primary)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.redesign_accent)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                accents.forEach { (id, label, preview) ->
                    val active = id == selected
                    Surface(
                        onClick = { onSelect(id) },
                        color = if (active) colors.primaryContainer else colors.surfaceContainerLow,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).background(preview, CircleShape),
                                contentAlignment = Alignment.Center) {
                                if (id == "DYNAMIC") {
                                    Icon(Icons.Rounded.Palette, contentDescription = null,
                                        modifier = Modifier.size(18.dp), tint = colors.onPrimary)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(stringResource(label), Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (active) colors.onPrimaryContainer else colors.onSurface)
                            if (active) Icon(Icons.Rounded.Check, contentDescription = null,
                                tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.account_done)) }
        }
    )
}
