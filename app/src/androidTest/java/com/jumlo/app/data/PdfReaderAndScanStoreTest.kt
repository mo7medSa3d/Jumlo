package com.jumlo.app.data

import android.content.Context
import android.content.ContextWrapper
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.jumlo.app.data.pdf.PdfReaderSession
import com.jumlo.app.data.pdf.PdfPageRasterizer
import com.jumlo.app.data.pdf.SmartScanResultStore
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class PdfReaderAndScanStoreTest {
    @Test fun cameraFallbackExportsOrderedUprightPagesAndKeepsInputs() = runBlocking<Unit> {
        val app=app()
        val first=scanImage(app,160,80,android.graphics.Color.RED)
        val second=scanImage(app,80,160,android.graphics.Color.BLUE)
        androidx.exifinterface.media.ExifInterface(first).apply {
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,"6");saveAttributes()
        }
        val originals=listOf(first,second).associateWith {it.readBytes()}
        val before=scanCopies(app)
        var result:File?=null
        try {
            val created=SmartScanResultStore.createPdfFromImages(app,listOf(first,second))
            result=created
            assertTrue(created.name.startsWith("jumlo-scan-"))
            PdfPageRasterizer(created).use { raster ->
                assertEquals(2,raster.pageCount)
                assertTrue(raster.pageSize(0).second>raster.pageSize(0).first)
                repeat(2) { index ->
                    val bitmap=raster.render(index,256,1024L*1024)
                    try {
                        val color=bitmap.getPixel(bitmap.width/2,bitmap.height/2)
                        if(index==0) {assertTrue(android.graphics.Color.red(color)>240);assertTrue(android.graphics.Color.blue(color)<15)}
                        else {assertTrue(android.graphics.Color.blue(color)>240);assertTrue(android.graphics.Color.red(color)<15)}
                    } finally {bitmap.recycle()}
                }
            }
            originals.forEach {(file,bytes)->assertArrayEquals(bytes,file.readBytes())}
        } finally {result?.delete();first.delete();second.delete()}
        assertEquals(before,scanCopies(app))
    }

    @Test fun failedCameraFallbackDeletesItsPartialPdfAndPreservesCapturedImages() = runBlocking<Unit> {
        val app=app()
        val valid=scanImage(app,80,100,android.graphics.Color.RED)
        val corrupt=File.createTempFile("scan-invalid-",".jpg",app.cacheDir).apply {writeText("invalid image")}
        val before=scanCopies(app)
        val original=valid.readBytes()
        try {
            assertTrue(runCatching {SmartScanResultStore.createPdfFromImages(app,listOf(valid,corrupt))}.isFailure)
            assertEquals(before,scanCopies(app))
            assertArrayEquals(original,valid.readBytes())
        } finally {valid.delete();corrupt.delete()}
    }

    @Test @SdkSuppress(minSdkVersion = 29)
    fun readerCopiesNonSeekableProviderStreamsAndRemovesOnlyItsPrivateCopy() = runBlocking<Unit> {
        val app = app()
        val source = fixture(app, 2)
        val bytes = source.readBytes()
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/pdf"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                val pipe = ParcelFileDescriptor.createPipe()
                thread(isDaemon = true) {
                    // The first rejected descriptor closes early; the second is consumed as a stream.
                    runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(bytes) } }
                }
                return pipe[0]
            }
        }
        provider.attachInfo(app, ProviderInfo().apply { authority = "jumlo.fixture.pipe" })
        val resolver = ContentResolver.wrap(provider)
        val wrapped = object : ContextWrapper(app) { override fun getContentResolver() = resolver }
        val before = readerCopies(app)
        val reader = PdfReaderSession.open(wrapped, "content://jumlo.fixture.pipe/document.pdf")
        try {
            assertEquals(2, reader.pageCount)
            assertEquals(before.size + 1, readerCopies(app).size)
            assertNotNull(reader.render(1, 512).bitmap)
        } finally { reader.close() }
        assertEquals(before, readerCopies(app))
        assertArrayEquals(bytes, source.readBytes())
        source.delete()
    }

    @Test fun readerSerializesConcurrentPagesAndKeepsDisplayedEvictedBitmapsValid() = runBlocking<Unit> {
        val app = app()
        val source = fixture(app, 30)
        val original = source.readBytes()
        val reader = PdfReaderSession.open(app, uri(app, source).toString())
        try {
            assertEquals(30, reader.pageCount)
            val first = reader.render(0, 1280)
            assertEquals(0xff000000.toInt(), first.bitmap.getPixel(first.bitmap.width / 2, first.bitmap.height / 2))
            val cached = reader.render(0, 1280)
            assertSame(first.bitmap, cached.bitmap)
            coroutineScope { (1 until 30).map { page -> async { reader.render(page, 1280); Unit } }.awaitAll() }
            assertFalse("Eviction must never recycle a bitmap held by Compose", first.bitmap.isRecycled)
            assertEquals(0xff000000.toInt(), first.bitmap.getPixel(first.bitmap.width / 2, first.bitmap.height / 2))
            assertArrayEquals(original, source.readBytes())
        } finally { reader.close() }
        reader.close()
        assertTrue(runCatching { reader.render(0, 512) }.isFailure)
        assertArrayEquals(original, source.readBytes())
        source.delete()
    }

    /**
     * Real Android PdfRenderer / 200-page corpus from generated vector PDF.
     * This tests high-index random access and cache eviction; it is not a
     * physical-device frame-time benchmark or scanned 200-page compression test.
     */
    @Test fun twoHundredPageReaderSupportsRandomAccessWithoutRetainingEveryBitmap() = runBlocking<Unit> {
        val app = app()
        val source = fixture(app, 200)
        val originalLength = source.length()
        val reader = PdfReaderSession.open(app, uri(app, source).toString())
        try {
            assertEquals(200, reader.pageCount)
            val first = reader.render(0, 768).bitmap
            assertTrue(first.width > 0 && first.height > 0)
            val samples = listOf(199, 4, 101, 23, 178, 57, 190, 0, 132, 64, 10, 175, 35, 198, 1, 88, 145, 75, 199)
            samples.forEach { page ->
                val frame = reader.render(page, 768)
                assertTrue("page ${page + 1} must render", frame.bitmap.width > 0 && frame.bitmap.height > 0)
                assertEquals(0xff000000.toInt(), frame.bitmap.getPixel(frame.bitmap.width / 2, frame.bitmap.height / 2))
            }
            assertFalse("A frame still shown by Compose must not be recycled during cache eviction", first.isRecycled)
            assertEquals(0xff000000.toInt(), first.getPixel(first.width / 2, first.height / 2))
            assertEquals(originalLength, source.length())
        } finally {
            reader.close()
            source.delete()
        }
    }

    @Test fun scannerCopyValidatesPageCountAndNeverMutatesTheSource() = runBlocking<Unit> {
        val app = app()
        val source = fixture(app, 3)
        val original = source.readBytes()
        val before = scanCopies(app)
        val copy = SmartScanResultStore.copyPdf(app, uri(app, source), 3)
        try {
            assertNotEquals(source.canonicalPath, copy.canonicalPath)
            assertArrayEquals(original, copy.readBytes())
            assertArrayEquals(original, source.readBytes())
            for (wrongCount in listOf(0, 2, 101)) assertTrue(runCatching {
                SmartScanResultStore.copyPdf(app, uri(app, source), wrongCount)
            }.isFailure)
        } finally { copy.delete() }
        assertEquals(before, scanCopies(app))
        assertArrayEquals(original, source.readBytes())
        source.delete()
    }

    @Test fun corruptAndPasswordProtectedPdfsFailBeforeReaderOrScanReturns() = runBlocking {
        val app = app()
        val protected = fixture(app, 1, encrypted = true)
        val corrupt = File.createTempFile("reader-corrupt-", ".pdf", protected.parentFile).apply { writeText("%PDF-1.7 invalid") }
        val before = scanCopies(app)
        try {
            for (source in listOf(protected, corrupt)) {
                val original = source.readBytes()
                val expected = if (source == protected) "PDF_ENCRYPTED" else "PDF_INVALID"
                assertEquals(expected, runCatching { PdfReaderSession.open(app, uri(app, source).toString()) }.exceptionOrNull()?.message)
                assertEquals(expected, runCatching { SmartScanResultStore.copyPdf(app, uri(app, source), 1) }.exceptionOrNull()?.message)
                assertArrayEquals(original, source.readBytes())
            }
            assertEquals(before, scanCopies(app))
        } finally { protected.delete(); corrupt.delete() }
    }

    /** One instrumentation method/process: a rejection must not poison later framework sessions. */
    @Test fun validPdfsStillRenderAndScanAfterEncryptedAndCorruptRejections() = runBlocking<Unit> {
        val app = app()
        val valid = fixture(app, 2)
        val encrypted = fixture(app, 1, encrypted = true)
        val corrupt = File.createTempFile("reader-corrupt-", ".pdf", valid.parentFile).apply { writeText("%PDF-1.7 invalid") }
        val originals = listOf(valid, encrypted, corrupt).associateWith { it.readBytes() }
        val beforeScans = scanCopies(app)
        val beforeReaderCopies = readerCopies(app)
        suspend fun assertValidStillWorks() {
            val reader = PdfReaderSession.open(app, uri(app, valid).toString())
            try {
                assertEquals(2, reader.pageCount)
                val bitmap = reader.render(1, 256).bitmap
                assertEquals(0xff000000.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
            } finally { reader.close() }
            val scan = SmartScanResultStore.copyPdf(app, uri(app, valid), 2)
            try { assertArrayEquals(originals.getValue(valid), scan.readBytes()) }
            finally { scan.delete() }
            PdfPageRasterizer(valid).use { rasterizer ->
                assertEquals(2, rasterizer.pageCount)
                val bitmap = rasterizer.render(0, 256, 4L * 1024 * 1024)
                try { assertEquals(0xff000000.toInt(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) }
                finally { bitmap.recycle() }
            }
        }
        try {
            assertValidStillWorks()
            for ((rejected, expected) in listOf(encrypted to "PDF_ENCRYPTED", corrupt to "PDF_INVALID")) {
                assertEquals(expected, runCatching { PdfReaderSession.open(app, uri(app, rejected).toString()) }.exceptionOrNull()?.message)
                assertValidStillWorks()
                assertEquals(expected, runCatching { SmartScanResultStore.copyPdf(app, uri(app, rejected), 1) }.exceptionOrNull()?.message)
                assertValidStillWorks()
                assertEquals(expected, runCatching { PdfPageRasterizer(rejected).use { it.pageCount } }.exceptionOrNull()?.message)
                assertValidStillWorks()
            }
            assertEquals(beforeScans, scanCopies(app))
            assertEquals(beforeReaderCopies, readerCopies(app))
            originals.forEach { (file, bytes) -> assertArrayEquals(bytes, file.readBytes()) }
        } finally { originals.keys.forEach { it.delete() } }
    }

    private fun app(): Context = ApplicationProvider.getApplicationContext<Context>().also { PDFBoxResourceLoader.init(it) }
    private fun scanImage(app:Context,width:Int,height:Int,color:Int):File {
        val file=File.createTempFile("scan-image-",".jpg",app.cacheDir)
        val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(color)}
        try {file.outputStream().use {assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,95,it))}}
        finally {bitmap.recycle()}
        return file
    }
    private fun uri(app: Context, file: File): Uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    private fun scanCopies(app: Context): Set<String> = app.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("jumlo-scan-") }.map { it.name }.toSet()
    private fun readerCopies(app: Context): Set<String> = app.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("pdf-reader-") }.map { it.name }.toSet()
    private fun fixture(app: Context, pages: Int, encrypted: Boolean = false): File {
        val file = File(app.filesDir, "processing/results/fixture/reader-${UUID.randomUUID()}.pdf").apply { requireNotNull(parentFile).mkdirs() }
        PDDocument().use { document ->
            repeat(pages) {
                val page = PDPage(PDRectangle(300f, 400f))
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.setNonStrokingColor(0f, 0f, 0f)
                    content.addRect(100f, 150f, 100f, 100f); content.fill()
                }
            }
            if (encrypted) document.protect(StandardProtectionPolicy("owner-password", "reader-password", AccessPermission()).apply { encryptionKeyLength = 128 })
            document.save(file)
        }
        return file
    }
}
