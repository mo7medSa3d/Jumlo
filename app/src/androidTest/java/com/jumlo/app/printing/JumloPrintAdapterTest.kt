package com.jumlo.app.printing

import android.content.Context
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAdapterHelper
import android.print.PrintDocumentInfo as FrameworkDocumentInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real framework/PFD callbacks on device; not a physical printer or spooler acceptance test. */
@RunWith(AndroidJUnit4::class)
class JumloPrintAdapterTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val info=PrintDocumentInfo(List(5) {PrintPageInfo(PrintCrop(0.0,0.0,200.0,300.0),0)})
    private val source=PrintSource("fixture.pdf","Fixture","application/pdf",listOf(PrintPage(5,90),PrintPage(2),PrintPage(5,180)))
    private val options=PrintOptions()
    @Test fun repeatedSubsetWritesReportOriginalLogicalRangesAndCloseDestination() {
        val engine=FixtureEngine()
        val adapter=JumloPrintAdapter(context,source,options,info,engine)
        try {
            val firstLayout=layout(adapter,engine)
            assertEquals(3,firstLayout.pageCount)
            val first=write(adapter,arrayOf(PageRange(1,2)))
            assertEquals(listOf(PrintPage(2),PrintPage(5,180)),engine.writes[0])
            assertEquals(listOf(1..2),first.second.map {it.start..it.end})
            PDDocument.load(first.first).use {assertEquals(2,it.numberOfPages)}
            val second=write(adapter,arrayOf(PageRange(0,0)))
            assertEquals(listOf(PrintPage(5,90)),engine.writes[1])
            PDDocument.load(second.first).use {assertEquals(1,it.numberOfPages)}
            first.first.delete();second.first.delete()
        } finally {instrumentation.runOnMainSync {adapter.onFinish()}}
    }
    @Test fun writeBeforeLayoutFailsOnceAndDoesNotRender() {
        val engine=FixtureEngine();val adapter=JumloPrintAdapter(context,source,options,info,engine)
        val output=File.createTempFile("adapter-no-layout-",".pdf",context.cacheDir)
        val latch=CountDownLatch(1);val outcomes=CopyOnWriteArrayList<String>()
        val fd=ParcelFileDescriptor.open(output,ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
        instrumentation.runOnMainSync {
            adapter.onWrite(arrayOf(PageRange.ALL_PAGES),fd,CancellationSignal(),PrintAdapterHelper.createWriteCallback(object:PrintAdapterHelper.WriteCallback {
                override fun onWriteFinished(pages:Array<out PageRange>) {outcomes+="success";latch.countDown()}
                override fun onWriteFailed(error:CharSequence?) {outcomes+="failure";latch.countDown()}
            }))
        }
        assertTrue(latch.await(10,TimeUnit.SECONDS));instrumentation.waitForIdleSync()
        assertEquals(listOf("failure"),outcomes.toList());assertTrue(engine.writes.isEmpty())
        assertFalse(fd.fileDescriptor.valid())
        instrumentation.runOnMainSync {adapter.onFinish()};output.delete()
    }
    private fun layout(adapter:JumloPrintAdapter,engine:FixtureEngine):FrameworkDocumentInfo {
        val latch=CountDownLatch(1);var document:FrameworkDocumentInfo?=null;var failure:CharSequence?=null
        instrumentation.runOnMainSync {
            adapter.onLayout(null,PrintAndroidAttributes.initial(engine.initialLayout(options,info,source.pages),options),CancellationSignal(),PrintAdapterHelper.createLayoutCallback(object:PrintAdapterHelper.LayoutCallback {
                override fun onLayoutFinished(info:FrameworkDocumentInfo,changed:Boolean) {document=info;latch.countDown()}
                override fun onLayoutFailed(error:CharSequence?) {failure=error;latch.countDown()}
            }),null)
        }
        assertTrue(latch.await(10,TimeUnit.SECONDS));assertNull(failure);return requireNotNull(document)
    }
    private fun write(adapter:JumloPrintAdapter,ranges:Array<PageRange>):Pair<File,List<PageRange>> {
        val file=File.createTempFile("adapter-write-",".pdf",context.cacheDir)
        val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
        val latch=CountDownLatch(1);val outcomes=CopyOnWriteArrayList<String>();var reported=emptyList<PageRange>()
        instrumentation.runOnMainSync {adapter.onWrite(ranges,descriptor,CancellationSignal(),PrintAdapterHelper.createWriteCallback(object:PrintAdapterHelper.WriteCallback {
            override fun onWriteFinished(pages:Array<out PageRange>) {reported=pages.toList();outcomes+="success";latch.countDown()}
            override fun onWriteFailed(error:CharSequence?) {outcomes+="failure";latch.countDown()}
        }))}
        assertTrue(latch.await(10,TimeUnit.SECONDS));instrumentation.waitForIdleSync()
        assertEquals(listOf("success"),outcomes.toList());assertFalse(descriptor.fileDescriptor.valid())
        return file to reported
    }
    private class FixtureEngine:PrintEngine {
        val writes=CopyOnWriteArrayList<List<PrintPage>>()
        override fun inspect(source:PrintSource,options:PrintOptions,check:()->Unit):PrintDocumentInfo=throw UnsupportedOperationException()
        override fun initialLayout(options:PrintOptions,info:PrintDocumentInfo,pages:List<PrintPage>)=PrintLayout(PrintSize(300.0,500.0),options.margins,false)
        override fun compose(source:PrintSource,options:PrintOptions,pages:List<PrintPage>,layout:PrintLayout,destination:File,check:()->Unit,progress:(Int,Int)->Unit) {
            writes+=pages
            PDDocument().use {document ->pages.forEach {check();document.addPage(PDPage(PDRectangle(300f,500f)))};document.save(destination)}
        }
        override fun preview(file:File,index:Int,maxEdge:Int):Bitmap=throw UnsupportedOperationException()
    }
}
