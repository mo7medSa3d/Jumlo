package com.jumlo.app.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Observes animator scale changes, including Accessibility > Remove animations. */
@Composable fun rememberSystemReducedMotion(): Boolean {
    val context=LocalContext.current
    var reduced by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val keys=listOf(Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE)
        fun refresh() {
            reduced=keys.any { key ->
                runCatching { Settings.Global.getFloat(context.contentResolver,key,1f)==0f }.getOrDefault(false)
            }
        }
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange:Boolean) { refresh() }
        }
        keys.forEach { context.contentResolver.registerContentObserver(Settings.Global.getUriFor(it),false,observer) }
        refresh()
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}
