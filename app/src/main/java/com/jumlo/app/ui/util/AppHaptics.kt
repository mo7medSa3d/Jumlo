package com.jumlo.app.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.jumlo.app.model.HapticIntensity

/** System-respecting tactile feedback; intensity and sound controls are independent. */
class AppHaptics(
    private val context:Context, private val view:View,
    private val enabled:Boolean, private val intensity:HapticIntensity,
    private val soundEnabled:Boolean, private val sound:UiSoundPlayer?
) {
    private fun allowed():Boolean = enabled && intensity!=HapticIntensity.OFF &&
        view.isHapticFeedbackEnabled && runCatching {
            Settings.System.getInt(context.contentResolver,Settings.System.HAPTIC_FEEDBACK_ENABLED,1)!=0
        }.getOrDefault(true)

    @Suppress("DEPRECATION")
    private fun deviceVibrator():Vibrator? = if(Build.VERSION.SDK_INT>=31)
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private fun effect(event:Event) {
        if(!allowed()) return
        val fallback=when(event) {
            Event.TICK -> if(Build.VERSION.SDK_INT>=34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.VIRTUAL_KEY
            Event.START,Event.SUCCESS -> if(Build.VERSION.SDK_INT>=30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Event.ERROR,Event.DELETE -> if(Build.VERSION.SDK_INT>=30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            Event.HOLD -> HapticFeedbackConstants.LONG_PRESS
        }
        if(intensity==HapticIntensity.LIGHT || Build.VERSION.SDK_INT<26) {
            view.performHapticFeedback(fallback)
            return
        }
        val vibrator=deviceVibrator()
        if(vibrator?.hasVibrator()!=true) {view.performHapticFeedback(fallback);return}
        val multiplier=if(intensity==HapticIntensity.STRONG) 1.0 else .65
        val (timings,amplitudes)=when(event) {
            Event.SUCCESS -> longArrayOf(0,18,35,28) to intArrayOf(0, (90*multiplier).toInt(),0,(140*multiplier).toInt())
            Event.ERROR -> longArrayOf(0,35,55,48) to intArrayOf(0,(160*multiplier).toInt(),0,(175*multiplier).toInt())
            Event.DELETE -> longArrayOf(0,22) to intArrayOf(0,(140*multiplier).toInt())
            Event.HOLD -> longArrayOf(0,30) to intArrayOf(0,(130*multiplier).toInt())
            Event.START -> longArrayOf(0,20) to intArrayOf(0,(125*multiplier).toInt())
            Event.TICK -> longArrayOf(0,10) to intArrayOf(0,(65*multiplier).toInt())
        }
        runCatching {vibrator.vibrate(VibrationEffect.createWaveform(timings,amplitudes,-1))}
            .onFailure {view.performHapticFeedback(fallback)}
    }
    private enum class Event { TICK, START, SUCCESS, ERROR, DELETE, HOLD }
    private fun trigger(event:Event,cue:UiSoundPlayer.Cue?) {
        effect(event)
        if(cue!=null) sound?.play(cue,soundEnabled)
    }
    fun navigationTab()=trigger(Event.TICK,UiSoundPlayer.Cue.TAP)
    fun toolExecute()=trigger(Event.START,UiSoundPlayer.Cue.START)
    fun toolSelect()=trigger(Event.TICK,UiSoundPlayer.Cue.TAP)
    fun selectionToggle()=trigger(Event.TICK,UiSoundPlayer.Cue.TOGGLE)
    fun secondaryAction()=trigger(Event.DELETE,UiSoundPlayer.Cue.DELETE)
    fun actionClick()=trigger(Event.TICK,UiSoundPlayer.Cue.TAP)
    fun longPress()=trigger(Event.HOLD,null)
    fun sliderTick()=trigger(Event.TICK,null)
    fun downloadComplete()=trigger(Event.SUCCESS,UiSoundPlayer.Cue.SUCCESS)
    fun error()=trigger(Event.ERROR,UiSoundPlayer.Cue.ERROR)
}

val LocalAppHapticsEnabled = staticCompositionLocalOf { true }
val LocalHapticIntensity = staticCompositionLocalOf { HapticIntensity.LIGHT }
val LocalAppSoundsEnabled = staticCompositionLocalOf { true }
val LocalUiSoundPlayer = staticCompositionLocalOf<UiSoundPlayer?> { null }

@Composable
fun rememberAppHaptics(enabled:Boolean=LocalAppHapticsEnabled.current):AppHaptics {
    val context=LocalContext.current
    val view=LocalView.current
    val level=LocalHapticIntensity.current
    val soundEnabled=LocalAppSoundsEnabled.current
    val sound=LocalUiSoundPlayer.current
    return remember(context,view,enabled,level,soundEnabled,sound) {
        AppHaptics(context,view,enabled,level,soundEnabled,sound)
    }
}
