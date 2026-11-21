package com.jumlo.app.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun fileDateLabel(epochMillis: Long): String {
    val formatter = remember { SimpleDateFormat("yyyy/MM/dd", Locale.ROOT) }
    return remember(epochMillis, formatter) { "\u2066${formatter.format(Date(epochMillis))}\u2069" }
}
