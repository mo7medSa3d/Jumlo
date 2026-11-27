package com.jumlo.app.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
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
    @Suppress("DEPRECATION")
    private fun legacySystemHapticsAllowed():Boolean = runCatching {
        Settings.System.getInt(context.contentResolver,Settings.System.HAPTIC_FEEDBACK_ENABLED,1)!=0
    }.getOrDefault(true)

    private fun allowed():Boolean = enabled && intensity!=HapticIntensity.OFF &&
        view.isHapticFeedbackEnabled && (Build.VERSION.SDK_INT >= 33 || legacySystemHapticsAllowed())

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    private val touchAttributes: VibrationAttributes? by lazy(LazyThreadSafetyMode.NONE) {
        if (Build.VERSION.SDK_INT >= 33) {
            VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build()
        } else null
    }

    private fun effect(event:Event) {
        if(!allowed()) return
        val fallback=when(event) {
            Event.TICK -> if(Build.VERSION.SDK_INT>=34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.VIRTUAL_KEY
            Event.START,Event.SUCCESS -> if(Build.VERSION.SDK_INT>=30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            Event.ERROR,Event.DELETE -> if(Build.VERSION.SDK_INT>=30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            Event.HOLD -> HapticFeedbackConstants.LONG_PRESS
            Event.DRAG -> HapticFeedbackConstants.LONG_PRESS
        }
        if(intensity==HapticIntensity.LIGHT) {
            view.performHapticFeedback(fallback)
            return
        }
        val activeVibrator=vibrator
        val hasVibrator=runCatching { activeVibrator?.hasVibrator()==true }.getOrDefault(false)
        if(!hasVibrator || activeVibrator==null) {view.performHapticFeedback(fallback);return}
        val multiplier=if(intensity==HapticIntensity.STRONG) 1.0 else .65
        val (timings,amplitudes)=when(event) {
            Event.SUCCESS -> longArrayOf(0,18,35,28) to intArrayOf(0, (90*multiplier).toInt(),0,(140*multiplier).toInt())
            Event.ERROR -> longArrayOf(0,35,55,48) to intArrayOf(0,(160*multiplier).toInt(),0,(175*multiplier).toInt())
            Event.DELETE -> longArrayOf(0,22) to intArrayOf(0,(140*multiplier).toInt())
            Event.HOLD -> longArrayOf(0,30) to intArrayOf(0,(130*multiplier).toInt())
            Event.DRAG -> longArrayOf(0,12,40,18) to intArrayOf(0,(70*multiplier).toInt(),0,(110*multiplier).toInt())
            Event.START -> longArrayOf(0,20) to intArrayOf(0,(125*multiplier).toInt())
            Event.TICK -> longArrayOf(0,10) to intArrayOf(0,(65*multiplier).toInt())
        }
        runCatching {
            val waveform = VibrationEffect.createWaveform(timings,amplitudes,-1)
            val attrs = touchAttributes
            if (Build.VERSION.SDK_INT >= 33 && attrs != null) {
                // On Android 13+, OS applies global touch feedback preference via USAGE_TOUCH.
                activeVibrator.vibrate(waveform, attrs)
            } else {
                activeVibrator.vibrate(waveform)
            }
        }.onFailure {view.performHapticFeedback(fallback)}
    }
    private enum class Event { TICK, START, SUCCESS, ERROR, DELETE, HOLD, DRAG }
    private fun trigger(event:Event,cue:UiSoundPlayer.Cue?) {
        effect(event)
        if(cue!=null) sound?.play(cue,soundEnabled)
    }
    fun navigationTab()=trigger(Event.TICK,null)
    fun screenTransition()=trigger(Event.TICK,null)
    fun toolExecute()=trigger(Event.START,null)
    fun toolSelect()=trigger(Event.TICK,null)
    fun selectionToggle()=trigger(Event.TICK,null)
    fun secondaryAction()=trigger(Event.DELETE,null)
    fun actionClick()=trigger(Event.TICK,null)
    fun longPress()=trigger(Event.HOLD,null)
    fun sliderTick()=trigger(Event.TICK,null)
    /** A drag was picked up. Distinct from the click tick so lifts feel different from taps. */
    fun dragStart()=trigger(Event.DRAG,null)
    /** A drag was released. */
    fun dragEnd()=trigger(Event.TICK,null)
    /** A value snapped to a step, grid line or neighbour. */
    fun snap()=trigger(Event.TICK,null)
    /** Camera/ML frame capture or scan page milestone. */
    fun captureSuccess()=trigger(Event.START,UiSoundPlayer.Cue.CAPTURE)
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
