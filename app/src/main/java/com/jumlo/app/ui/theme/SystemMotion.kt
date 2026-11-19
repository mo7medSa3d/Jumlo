package com.jumlo.app.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

private val MOTION_SCALE_KEYS = listOf(
    Settings.Global.ANIMATOR_DURATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
    Settings.Global.WINDOW_ANIMATION_SCALE
)

private fun readSystemReducedMotion(context: Context): Boolean =
    MOTION_SCALE_KEYS.any { key ->
        runCatching { Settings.Global.getFloat(context.contentResolver, key, 1f) == 0f }.getOrDefault(false)
    }

/** Observes animator scale changes, including Accessibility > Remove animations. */
@Composable fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    // Read initial value synchronously so cold-start frame 1 honors Remove animations.
    var reduced by remember(context) { mutableStateOf(readSystemReducedMotion(context)) }
    DisposableEffect(context) {
        fun refresh() { reduced = readSystemReducedMotion(context) }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh() }
        }
        MOTION_SCALE_KEYS.forEach {
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, observer)
        }
        refresh()
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}
