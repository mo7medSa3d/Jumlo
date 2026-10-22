package com.jumlo.app.printing

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo as AndroidPrintDocumentInfo
import com.jumlo.app.R
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.roundToInt

/** Each write owns its resources. Layout changes never mutate an in-flight write's snapshot. */
class JumloPrintAdapter(
    private val context:Context,
    private val source:PrintSource,
    private val options:PrintOptions,
    private val info:PrintDocumentInfo,
    private val composer:PrintEngine,
    private val progress:(Int,Int)->Unit={_,_->},
    private val finished:()->Unit={}
):PrintDocumentAdapter() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val main=Handler(Looper.getMainLooper())
    private val pages=PrintRanges.select(
        PrintPageSelection.arrange(PrintRanges.sourcePages(source.pages,info.pages.size),options.pageOrder,options.pageRotations),
        options.range
    )
    @Volatile private var layout:PrintLayout?=null
    @Volatile private var closed=false

    override fun onLayout(oldAttributes:PrintAttributes?,newAttributes:PrintAttributes,cancellationSignal:CancellationSignal,callback:LayoutResultCallback,extras:Bundle?) {
        if(closed || cancellationSignal.isCanceled) {callback.onLayoutCancelled();return}
        try {
            val next=PrintAndroidAttributes.layout(newAttributes,composer.initialLayout(options,info,pages),options)
            pages.forEach { selected -> PrintGeometry.place(info.pages[selected.sourcePage-1].size(selected.rotationDegrees),next.paper,next.margins,options.scale,options.scalePercent) }
            val changed=layout!=next || oldAttributes!=newAttributes
            layout=next
            if(cancellationSignal.isCanceled) callback.onLayoutCancelled()
            else callback.onLayoutFinished(AndroidPrintDocumentInfo.Builder(source.name.ifBlank {context.getString(R.string.print_document)}).setContentType(AndroidPrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages.size).build(),changed)
        } catch(_:Exception) { if(cancellationSignal.isCanceled) callback.onLayoutCancelled() else callback.onLayoutFailed(context.getString(R.string.print_error_layout)) }
    }
    override fun onWrite(requested:Array<out PageRange>,destination:ParcelFileDescriptor,cancellationSignal:CancellationSignal,callback:WriteResultCallback) {
        val gate=PrintCallbackGate()
        fun result(outcome:PrintCallbackGate.Result,ranges:Array<PageRange>?=null,message:String?=null) {main.post {
            if(cancellationSignal.isCanceled || closed) gate.cancel()
            when(gate.finish(outcome)) {
                PrintCallbackGate.Result.SUCCESS->callback.onWriteFinished(ranges!!)
                PrintCallbackGate.Result.FAILURE->callback.onWriteFailed(message)
                PrintCallbackGate.Result.CANCELED->callback.onWriteCancelled()
                null->Unit
            }
        }}
        val snapshot=layout
        if(closed || cancellationSignal.isCanceled) {runCatching {destination.close()};result(PrintCallbackGate.Result.CANCELED);return}
        if(snapshot==null) {runCatching {destination.close()};result(PrintCallbackGate.Result.FAILURE,message=context.getString(R.string.print_error_layout));return}
        val selected=PrintRanges.requested(requested.map {it.start..it.end},pages.size)
        if(selected.isEmpty()) {runCatching {destination.close()};result(PrintCallbackGate.Result.FAILURE,message=context.getString(R.string.print_error_range));return}
        val operation=scope.launch {
            var temporary:File?=null
            try {
                val owner=currentCoroutineContext()
                fun check() {owner.ensureActive();if(cancellationSignal.isCanceled || closed) throw CancellationException()}
                temporary=File.createTempFile("print-write-",".pdf",context.cacheDir)
                composer.compose(source,options,selected.map {pages[it]},snapshot,temporary!!,::check) {done,total -> main.post {if(!closed && !cancellationSignal.isCanceled) progress(done,total)} }
                check()
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { sink -> temporary!!.inputStream().use { input ->
                    val buffer=ByteArray(64*1024)
                    while(true) {check();val n=input.read(buffer);if(n<0) break;sink.write(buffer,0,n)}
                    sink.flush()
                } }
                check()
                val ranges=PrintRanges.compact(selected).map {PageRange(it.first,it.last)}.toTypedArray()
                result(PrintCallbackGate.Result.SUCCESS,ranges=ranges)
            } catch(_:CancellationException) {result(PrintCallbackGate.Result.CANCELED)}
            catch(_:Exception) {result(PrintCallbackGate.Result.FAILURE,message=context.getString(R.string.print_error_write))}
            finally {temporary?.delete();runCatching {destination.close()}}
        }
        operation.invokeOnCompletion { cause ->
            main.post {cancellationSignal.setOnCancelListener(null)}
            // Also covers cancellation before the coroutine body has entered.
            runCatching {destination.close()}
            if(cause is CancellationException) result(PrintCallbackGate.Result.CANCELED)
        }
        cancellationSignal.setOnCancelListener {gate.cancel();operation.cancel()}
    }
    override fun onFinish() {closed=true;scope.cancel();finished()}
}

object PrintAndroidAttributes {
    fun initial(layout:PrintLayout,options:PrintOptions):PrintAttributes {
        val base=when(options.paper) {
            PrintPaper.A4->PrintAttributes.MediaSize.ISO_A4
            PrintPaper.A5->PrintAttributes.MediaSize.ISO_A5
            PrintPaper.LETTER->PrintAttributes.MediaSize.NA_LETTER
            PrintPaper.LEGAL->PrintAttributes.MediaSize.NA_LEGAL
            else->PrintAttributes.MediaSize("jumlo-custom","Jumlo",(layout.paper.width*1000/72).roundToInt().coerceAtLeast(1),(layout.paper.height*1000/72).roundToInt().coerceAtLeast(1))
        }
        val media=if(layout.paper.width>layout.paper.height) base.asLandscape() else base.asPortrait()
        return PrintAttributes.Builder().setMediaSize(media).setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setResolution(PrintAttributes.Resolution("jumlo-300","300 DPI",300,300))
            .setColorMode(if(options.grayscale) PrintAttributes.COLOR_MODE_MONOCHROME else PrintAttributes.COLOR_MODE_COLOR)
            .setDuplexMode(when(options.duplex) {PrintDuplex.NONE->PrintAttributes.DUPLEX_MODE_NONE;PrintDuplex.LONG_EDGE->PrintAttributes.DUPLEX_MODE_LONG_EDGE;PrintDuplex.SHORT_EDGE->PrintAttributes.DUPLEX_MODE_SHORT_EDGE}).build()
    }
    fun layout(attributes:PrintAttributes,fallback:PrintLayout,options:PrintOptions):PrintLayout {
        val media=attributes.mediaSize
        val paper=if(media==null) fallback.paper else PrintSize(media.widthMils*72/1000.0,media.heightMils*72/1000.0)
        val m=attributes.minMargins
        val minimum=if(m==null) PrintMargins() else PrintMargins(m.leftMils*72/1000.0,m.topMils*72/1000.0,m.rightMils*72/1000.0,m.bottomMils*72/1000.0)
        val gray=when(attributes.colorMode) {PrintAttributes.COLOR_MODE_MONOCHROME->true;PrintAttributes.COLOR_MODE_COLOR->false;else->fallback.grayscale}
        return PrintLayout(paper,options.margins.atLeast(minimum),gray)
    }
}
