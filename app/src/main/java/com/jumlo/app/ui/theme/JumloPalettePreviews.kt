package com.jumlo.app.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode

/** Android Studio source previews; they are not runtime screenshot evidence. */
@Composable
private fun TokenPreview(language: AppLanguage, dark: Boolean) {
    JumloTheme(
        themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
        language = language,
        accentColor = "BLUE"
    ) {
        val isArabic = language.isRtl
        val families = listOf(
            "pdf_03" to (if (isArabic) "تنظيم PDF" else "Organize PDF"),
            "img_01" to (if (isArabic) "تحويل الصور" else "Convert images"),
            "dl_media" to (if (isArabic) "التنزيل" else "Download"),
            "file_07" to (if (isArabic) "الملفات" else "Files")
        )
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (isArabic) "ألوان جملو" else "Jumlo color tokens",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground)
            families.forEach { (id, label) ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ToolPalette.accentFor(id, dark),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(label, modifier = Modifier.padding(18.dp),
                        color = ToolPalette.onAccentFor(id, dark),
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Preview(name = "Jumlo colors Light", showBackground = true, widthDp = 360)
@Composable private fun LightPalettePreview() = TokenPreview(AppLanguage.ENGLISH, dark = false)

@Preview(name = "Jumlo colors Dark", showBackground = true, widthDp = 360,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable private fun DarkPalettePreview() = TokenPreview(AppLanguage.ENGLISH, dark = true)

@Preview(name = "Jumlo colors Arabic RTL", showBackground = true, widthDp = 360, locale = "ar")
@Composable private fun ArabicPalettePreview() = TokenPreview(AppLanguage.ARABIC, dark = false)
