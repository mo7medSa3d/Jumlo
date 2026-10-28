package com.jumlo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.R
import com.jumlo.app.ui.theme.ColorPresets
import com.jumlo.app.ui.theme.M3ColorFactory
import com.jumlo.app.ui.theme.QuietTheme

/** Appearance > App color picker: live preview, 16 presets, HSV sliders and a
 *  hex field. Confirming emits the chosen hex seed. */
@Composable
fun AppearanceColorPicker(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var hexDraft by remember {
        mutableStateOf(
            M3ColorFactory.hexColor(current)?.let { M3ColorFactory.hexString(it) } ?: "#1E88E5"
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pref_app_color), style = QuietTheme.typography.sectionTitle)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
            Spacer(Modifier.height(12.dp))

            val seed = M3ColorFactory.hexColor(hexDraft) ?: Color(0xFF1E88E5)
            val scheme = M3ColorFactory.scheme(seed, dark = false)
            Card(
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.pref_app_color_preview), style = QuietTheme.typography.navigation)
                    Spacer(Modifier.height(10.dp))
                    SwatchRow(scheme.primary to scheme.onPrimary, scheme.primaryContainer to scheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    SwatchRow(scheme.secondary to scheme.onSecondary, scheme.tertiary to scheme.onTertiary)
                }
            }
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.pref_app_color_presets), style = QuietTheme.typography.navigation)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ColorPresets.ALL) { preset ->
                    PresetChip(
                        preset = preset,
                        selected = preset.hex.equals(hexDraft, ignoreCase = true),
                        onClick = {
                            hexDraft = preset.hex
                            onSelect(preset.hex)
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            val (hue, sat, valB) = M3ColorFactory.hsvOf(seed)
            Text(stringResource(R.string.pref_app_color_hsv), style = QuietTheme.typography.navigation)
            Spacer(Modifier.height(6.dp))
            HsvSlider(label = stringResource(R.string.pref_app_color_hue), value = hue,
                range = 0f..360f, onChange = { h ->
                    hexDraft = M3ColorFactory.hexString(M3ColorFactory.fromHsv(h, sat, valB))
                })
            HsvSlider(label = stringResource(R.string.pref_app_color_sat), value = sat,
                range = 0f..1f, onChange = { s ->
                    hexDraft = M3ColorFactory.hexString(M3ColorFactory.fromHsv(hue, s, valB))
                })
            HsvSlider(label = stringResource(R.string.pref_app_color_val), value = valB,
                range = 0f..1f, onChange = { v ->
                    hexDraft = M3ColorFactory.hexString(M3ColorFactory.fromHsv(hue, sat, v))
                })
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = hexDraft,
                onValueChange = {
                    val parsed = M3ColorFactory.hexColor(it)
                    if (parsed != null) hexDraft = M3ColorFactory.hexString(parsed) else hexDraft = it
                },
                label = { Text(stringResource(R.string.pref_app_color_hex)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    hexDraft = M3ColorFactory.hexString(M3ColorFactory.seedFor("BLUE"))
                }) { Text(stringResource(R.string.pref_app_color_reset)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onSelect(hexDraft) }, enabled = M3ColorFactory.hexColor(hexDraft) != null) {
                    Text(stringResource(R.string.action_apply), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SwatchRow(vararg pairs: Pair<Color, Color>) {
    Row(Modifier.fillMaxWidth()) {
        pairs.forEachIndexed { i, (bg, fg) ->
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(bg, RoundedCornerShape(8.dp))
            ) {
                Text(
                    if (i % 2 == 0) "Aa" else "Bb",
                    color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center).padding(4.dp)
                )
            }
            if (i < pairs.lastIndex) Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun PresetChip(preset: com.jumlo.app.ui.theme.ColorPreset, selected: Boolean, onClick: () -> Unit) {
    val isArabic = LocalContext.current.resources.configuration.locale.language.startsWith("ar")
    val displayName = if (isArabic) preset.nameAr else preset.nameEn
    val scheme = M3ColorFactory.scheme(M3ColorFactory.hexColor(preset.hex) ?: Color.Gray, dark = false)
    Column(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .background(
                if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(28.dp).background(
                M3ColorFactory.hexColor(preset.hex) ?: Color.Gray, RoundedCornerShape(6.dp)
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            displayName,
            style = QuietTheme.typography.badge,
            color = scheme.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun HsvSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = QuietTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
