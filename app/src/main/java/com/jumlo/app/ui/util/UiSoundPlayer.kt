package com.jumlo.app.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.jumlo.app.R

/**
 * Studio-grade operational audio feedback using preloaded PCM WAV chimes.
 * Plays strictly on key milestone events (capture, operation complete, error) and stays
 * silent on ordinary taps, switches, and navigation.
 */
class UiSoundPlayer(context: Context) {
    enum class Cue { CAPTURE, SUCCESS, ERROR }

    private val audio = runCatching {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }.getOrNull()

    // USAGE_MEDIA avoids OEM STREAM_SYSTEM attenuation while CONTENT_TYPE_SONIFICATION tags brief chimes.
    private val pool: SoundPool? = if (audio == null) null else runCatching {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }.getOrNull()

    private val loaded = mutableSetOf<Int>()
    @Volatile private var released = false
    private val soundIds: Map<Cue, Int>

    init {
        val activePool = pool
        soundIds = if (activePool == null) emptyMap() else runCatching {
            activePool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && !released) synchronized(loaded) { loaded += sampleId }
            }
            mapOf(
                Cue.CAPTURE to activePool.load(context, R.raw.jumlo_capture, 1),
                Cue.SUCCESS to activePool.load(context, R.raw.jumlo_success, 1),
                Cue.ERROR to activePool.load(context, R.raw.jumlo_error, 1)
            )
        }.getOrDefault(emptyMap())
    }

    fun play(cue: Cue, enabled: Boolean = true) {
        if (released || !enabled) return
        val activePool = pool ?: return
        val manager = audio ?: return
        val audible = runCatching {
            manager.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                !manager.isStreamMute(AudioManager.STREAM_MUSIC) &&
                manager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        }.getOrDefault(false)
        if (!audible) return
        val sample = soundIds[cue] ?: return
        val gain = when (cue) {
            Cue.CAPTURE -> 0.42f
            Cue.SUCCESS -> 0.48f
            Cue.ERROR -> 0.40f
        }
        if (synchronized(loaded) { !released && sample in loaded }) {
            runCatching { activePool.play(sample, gain, gain, 1, 0, 1f) }
        }
    }

    fun release() {
        if (released) return
        released = true
        synchronized(loaded) { loaded.clear() }
        pool?.let { activePool ->
            runCatching { activePool.setOnLoadCompleteListener(null) }
            runCatching { activePool.release() }
        }
    }
}
