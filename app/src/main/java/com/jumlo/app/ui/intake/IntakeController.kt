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
        val clip=intent.clipData
        if(clip!=null) for(i in 0 until minOf(clip.itemCount,1000)) clip.getItemAt(i).uri?.let(urls::add)
        if(intent.action==Intent.ACTION_SEND) {
            val uri=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java) else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let(urls::add)
        }
        if(intent.action==Intent.ACTION_SEND_MULTIPLE) {
            val list=if(Build.VERSION.SDK_INT>=33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM,Uri::class.java) else intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            list?.take(1000)?.let(urls::addAll)
        }
        val text=intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(100000)
        return Intake(urls.filter { it.scheme=="content" }.take(1000).map(Uri::toString),intent.flags,text)
    }
}
