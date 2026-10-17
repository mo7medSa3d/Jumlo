package com.jumlo.app.ui.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Material 3 tactile haptic engine for Jumlo.
 * Uses modern Android haptic constants (SEGMENT_TICK, CONFIRM, REJECT)
 * with robust backward compatibility for tactile UI feel.
 */
class AppHaptics(
    private val hapticFeedback: HapticFeedback,
    private val view: View,
    private val enabled: Boolean = true
) {
    /**
     * Tactile feedback for main navigation tabs (dock, navigation rail, back button).
     */
    fun navigationTab() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Tactile feedback for primary tool execution (Process Selected Files, Start Run, Download Media, Scan Document).
     */
    fun toolExecute() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Tactile feedback when selecting or launching a tool card.
     */
    fun toolSelect() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    /**
     * Tactile feedback for filter chips, category pills, radio buttons, and segment pickers.
     */
    fun selectionToggle() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Tactile feedback for secondary/destructive actions: cancel, delete, purge, restore.
     */
    fun secondaryAction() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Tactile feedback for general action buttons (e.g. Add Sources, Clear Cache, Share).
     */
    fun actionClick() {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/** The persisted preference is shared by every descendant control, including dialogs. */
val LocalAppHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberAppHaptics(enabled: Boolean = LocalAppHapticsEnabled.current): AppHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(hapticFeedback, view, enabled) {
        AppHaptics(hapticFeedback, view, enabled)
    }
}
