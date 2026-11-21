package com.jumlo.app.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DateFormat
import java.util.Date

@Composable fun fileDateLabel(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
    val formatter = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }
    return remember(epochMillis, formatter) { "⁦${formatter.format(Date(epochMillis))}⁩" }
}
