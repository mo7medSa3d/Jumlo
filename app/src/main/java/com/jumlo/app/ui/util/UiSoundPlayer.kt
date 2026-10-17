package com.jumlo.app.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.jumlo.app.R

/** Preloaded tiny self-generated WAVs: no streaming, network, media-session, or UI thread decoding. */
class UiSoundPlayer(private val context: Context) {
    enum class Cue { TAP, TOGGLE, START, SUCCESS, ERROR, DELETE }
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool=SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val loaded=mutableSetOf<Int>()
    private val soundIds: Map<Cue,Int>
    init {
        pool.setOnLoadCompleteListener { _,sampleId,status ->
            if(status==0) synchronized(loaded) {loaded+=sampleId}
        }
        soundIds=mapOf(
            Cue.TAP to pool.load(context,R.raw.jumlo_tap,1),
            Cue.TOGGLE to pool.load(context,R.raw.jumlo_toggle,1),
            Cue.START to pool.load(context,R.raw.jumlo_start,1),
            Cue.SUCCESS to pool.load(context,R.raw.jumlo_success,1),
            Cue.ERROR to pool.load(context,R.raw.jumlo_error,1),
            Cue.DELETE to pool.load(context,R.raw.jumlo_delete,1)
        )
    }
    fun play(cue:Cue, enabled:Boolean) {
        if(!enabled || audio.ringerMode!=AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(AudioManager.STREAM_SYSTEM)==0) return
        val sample=soundIds[cue] ?: return
        if(synchronized(loaded) {sample in loaded}) pool.play(sample,.22f,.22f,1,0,1f)
    }
    fun release() = pool.release()
}
