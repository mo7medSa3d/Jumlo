package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.theme.QuietTheme
import java.security.SecureRandom

/** "Tap, drag, done": the common cases of a text setting are one tap, never a keyboard. */
@Composable
internal fun PresetChipsRow(presets: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(presets, key = { it }) { preset ->
            FilterChip(selected = preset == selected, onClick = { onSelect(preset) }, border = null,
                label = { Text(preset) })
        }
    }
}

/** 16 characters with at least one upper, lower, digit and symbol. Never logged. */
internal fun generateStrongPassword(random: SecureRandom = SecureRandom()): String {
    val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val lower = "abcdefghjkmnpqrstuvwxyz"
    val digits = "23456789"
    val symbols = "!@#$%^&*"
    val all = upper + lower + digits + symbols
    val chars = CharArray(16) { all[random.nextInt(all.length)] }
    chars[0] = upper[random.nextInt(upper.length)]
    chars[1] = lower[random.nextInt(lower.length)]
    chars[2] = digits[random.nextInt(digits.length)]
    chars[3] = symbols[random.nextInt(symbols.length)]
    // Fisher-Yates over the fixed positions so the class positions are not predictable.
    for (i in chars.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
    }
    return chars.concatToString()
}

@Composable
internal fun GeneratePasswordButton(onGenerate: (String) -> Unit) {
    TextButton(onClick = { onGenerate(generateStrongPassword()) }) {
        Text(stringResource(R.string.studio_password_generate))
    }
}

/** Clipboard-first input for values that are pasted, not typed (hashes, tokens). */
@Composable
internal fun PasteButton(onPaste: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    TextButton(onClick = {
        try {
            clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let(onPaste)
        } catch (_: RuntimeException) {
            // Clipboard unavailable: the field stays editable, nothing is lost.
        }
    }) { Text(stringResource(R.string.action_paste)) }
}

/**
 * Read-only summary of the visual page selection. Tools whose page strip is the
 * editor write the same spec, so the typed spec gets no UI field there.
 * Returns null for "all"/blank, the expanded count otherwise, -1 if unparseable.
 */
internal fun countSelectedPages(spec: String): Int? {
    val s = spec.trim()
    if (s.isEmpty() || s.equals("all", true)) return null
    var count = 0
    for (part in s.split(",")) {
        val p = part.trim()
        if (p.isEmpty()) continue
        val range = p.split("-")
        when (range.size) {
            1 -> {
                range[0].toIntOrNull() ?: return -1
                count++
            }
            2 -> {
                val a = range[0].trim().toIntOrNull() ?: return -1
                val b = range[1].trim().toIntOrNull() ?: return -1
                if (b < a) return -1
                count += b - a + 1
            }
            else -> return -1
        }
    }
    return count
}

@Composable
internal fun PageSelectionSummary(spec: String, titleRes: Int, counted: Boolean) {
    val count = if (counted) countSelectedPages(spec) else null
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(titleRes), style = QuietTheme.typography.metadataStrong)
        Text(
            when {
                !counted -> spec.ifBlank { stringResource(R.string.ui_pages_all) }
                count == null -> stringResource(R.string.ui_pages_all)
                count < 0 -> spec
                else -> stringResource(R.string.ui_pages_selected_count, count)
            },
            style = QuietTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(stringResource(R.string.ui_pages_edit_visual), style = QuietTheme.typography.metadata)
    }
}
