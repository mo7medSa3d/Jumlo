package com.jumlo.app.ui.intake

import android.content.Intent
import android.net.Uri
import android.os.Build

data class Intake(val uris:List<String>,val offeredFlags:Int,val sharedText:String?)
object IntakeController {
    @Suppress("DEPRECATION")
    fun collect(intent:Intent?):Intake {
        if(intent==null) return Intake(emptyList(),0,null)
        val urls=linkedSetOf<Uri>()
        intent.data?.let(urls::add)
        val clip=runCatching { intent.clipData }.getOrNull()
        if(clip!=null) for(i in 0 until minOf(clip.itemCount,1000)) runCatching { clip.getItemAt(i)?.uri }.getOrNull()?.let(urls::add)
        val isShare = intent.action==Intent.ACTION_SEND || intent.action==Intent.ACTION_SEND_MULTIPLE
        if(intent.action==Intent.ACTION_SEND) {
            val uri=runCatching {
                if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java) else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }.getOrNull()
            uri?.let(urls::add)
        }
        if(intent.action==Intent.ACTION_SEND_MULTIPLE) {
            val list=runCatching {
                if(Build.VERSION.SDK_INT>=33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM,Uri::class.java) else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }.getOrNull()
            list?.asSequence()?.filterNotNull()?.take(1000)?.forEach(urls::add)
        }
        val text=runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(100000) }.getOrNull()
            ?: if(isShare && clip!=null && clip.itemCount>0) runCatching { clip.getItemAt(0)?.text?.toString()?.take(100000) }.getOrNull() else null
        return Intake(urls.filter { it.scheme.equals("content", ignoreCase = true) }.take(1000).map(Uri::toString),intent.flags,text)
    }
}
