package com.jumlo.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Check
import com.jumlo.app.ui.theme.ColorSystem
import com.jumlo.app.ui.theme.ColorSystems
import com.jumlo.app.ui.theme.QuietTheme

/** Appearance > Color system: exactly four systems, nothing else. Each card
 *  previews its own artwork — four overlapping swatches over a gradient bar,
 *  the way the reference artworks present them — on the system's own light
 *  paper, so the choice is visible before it is applied. Tapping a card
 *  selects it immediately; there is no hex field, no sliders, no dynamic
 *  color, and no legacy preset list anymore. */
@Composable
fun AppearanceColorPicker(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    language: AppLanguage = AppLanguage.ENGLISH
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pref_app_color), style = QuietTheme.typography.sectionTitle)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
            ColorSystems.ALL.forEach { system ->
                SystemCard(
                    system = system,
                    language = language,
                    selected = system.key == current,
                    onClick = { onSelect(system.key) }
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SystemCard(system: ColorSystem, language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val paper = system.light.background
    val ink = system.light.onSurface
    val softInk = system.light.onSurfaceVariant
    val deep = system.ramp.last()
    Surface(
        onClick = onClick,
        color = paper,
        shape = RoundedCornerShape(20.dp),
        border = if (selected) BorderStroke(2.dp, deep) else null
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Four overlapping swatches, light to deep, like the artwork.
                Box(Modifier.width(82.dp).height(28.dp)) {
                    system.ramp.forEachIndexed { i, color ->
                        Box(
                            Modifier.align(Alignment.CenterStart).offset(x = (18 * i).dp)
                                .size(28.dp).background(color, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (selected) Icon(
                    Icons.Rounded.Check, contentDescription = null,
                    tint = deep, modifier = Modifier.size(22.dp)
                )
            }
            Box(
                Modifier.fillMaxWidth().height(12.dp).background(
                    Brush.horizontalGradient(system.ramp), RoundedCornerShape(6.dp)
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (language == AppLanguage.ARABIC) system.nameAr else system.nameEn,
                        style = QuietTheme.typography.body, color = ink
                    )
                    Text(
                        if (language == AppLanguage.ARABIC) system.taglineAr else system.taglineEn,
                        style = QuietTheme.typography.metadata, color = softInk
                    )
                }
                Text(
                    system.ramp.joinToString(" · ") { "#%06X".format(it.toArgb() and 0xFFFFFF) },
                    style = QuietTheme.typography.metadata, color = softInk
                )
            }
        }
    }
}
