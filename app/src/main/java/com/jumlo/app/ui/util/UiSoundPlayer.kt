package com.jumlo.app.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.jumlo.app.R

/** Preloaded tiny self-generated WAVs: no streaming, network, media-session, or UI thread decoding. */
class UiSoundPlayer(context: Context) {
    enum class Cue { TAP, TOGGLE, TRANSITION, START, SUCCESS, ERROR, DELETE }

    private val audio = runCatching {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }.getOrNull()

    // USAGE_MEDIA avoids OEM STREAM_SYSTEM attenuation/muting while CONTENT_TYPE_SONIFICATION tags UI cues.
    private val pool: SoundPool? = if (audio == null) null else runCatching {
        SoundPool.Builder()
            .setMaxStreams(4)
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
                Cue.TAP to activePool.load(context, R.raw.jumlo_tap, 1),
                Cue.TOGGLE to activePool.load(context, R.raw.jumlo_toggle, 1),
                Cue.TRANSITION to activePool.load(context, R.raw.jumlo_transition, 1),
                Cue.START to activePool.load(context, R.raw.jumlo_start, 1),
                Cue.SUCCESS to activePool.load(context, R.raw.jumlo_success, 1),
                Cue.ERROR to activePool.load(context, R.raw.jumlo_error, 1),
                Cue.DELETE to activePool.load(context, R.raw.jumlo_delete, 1)
            )
        }.getOrDefault(emptyMap())
    }

    fun play(cue: Cue, enabled: Boolean) {
        if (released) return
        // Play only meaningful operation lifecycle cues; keep all UI tap/toggle/navigation sounds silent.
        if (cue != Cue.START && cue != Cue.SUCCESS && cue != Cue.ERROR) return
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
            Cue.START -> 0.34f
            Cue.SUCCESS -> 0.44f
            Cue.ERROR -> 0.38f
            else -> return
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
