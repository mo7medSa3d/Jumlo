package com.jumlo.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode

/** Preview-only token gallery: no fake document image or fabricated runtime state. */
@Composable
private fun JumloTokensPreview(arabic: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(scheme.background).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (arabic) "أدوات جاملو" else "Jumlo tools",
            style = MaterialTheme.typography.headlineMedium, color = scheme.onBackground)
        Surface(color = scheme.primary, shape = RoundedCornerShape(20.dp)) {
            Text(if (arabic) "إجراء أساسي" else "Primary action",
                modifier = Modifier.padding(PaddingValues(20.dp)),
                color = scheme.onPrimary, style = MaterialTheme.typography.titleMedium)
        }
        Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
            Text(if (arabic) "سطح ملون" else "Colored section surface",
                modifier = Modifier.padding(20.dp),
                color = scheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("pdf_03", "img_01", "dl_media").forEach { id ->
                Surface(modifier = Modifier.weight(1f), color = ToolPalette.containerFor(id, scheme.surfaceContainerLow,
                    dark = M3ColorFactory.contrast(scheme.background, scheme.onBackground) > 0f &&
                        scheme.background.red < 0.25f), shape = RoundedCornerShape(16.dp)) {
                    Text(id.substringBefore('_').uppercase(), modifier = Modifier.padding(12.dp),
                        color = ToolPalette.accentFor(id, dark = scheme.background.red < 0.25f),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Preview(name = "Jumlo · light EN", showBackground = true, widthDp = 390)
@Composable private fun LightEnglishTokens() {
    JumloTheme(ThemeMode.LIGHT, AppLanguage.ENGLISH) { JumloTokensPreview(false) }
}

@Preview(name = "Jumlo · dark EN", showBackground = true, widthDp = 390)
@Composable private fun DarkEnglishTokens() {
    JumloTheme(ThemeMode.DARK, AppLanguage.ENGLISH) { JumloTokensPreview(false) }
}

@Preview(name = "Jumlo · light AR", locale = "ar", showBackground = true, widthDp = 390)
@Composable private fun LightArabicTokens() {
    JumloTheme(ThemeMode.LIGHT, AppLanguage.ARABIC) { JumloTokensPreview(true) }
}

@Preview(name = "Jumlo · dark AR", locale = "ar", showBackground = true, widthDp = 390)
@Composable private fun DarkArabicTokens() {
    JumloTheme(ThemeMode.DARK, AppLanguage.ARABIC) { JumloTokensPreview(true) }
}
