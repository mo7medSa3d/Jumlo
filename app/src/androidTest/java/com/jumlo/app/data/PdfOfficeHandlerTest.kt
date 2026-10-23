package com.jumlo.app.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.core.office.OfficePackageValidator
import com.jumlo.app.core.office.OfficePackageWriter
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.WorkBudget
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.ocr.MlKitOcrEngine
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.output.PublicOutputStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.processing.OcrWebHandler
import com.jumlo.app.data.processing.PdfOfficeOperationHandler
import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.model.ItemStatus
import com.jumlo.app.model.JobRequest
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/** Uses real PDFBox input, Android rasterization and durable publication. No OCR model is needed. */
@RunWith(AndroidJUnit4::class)
class PdfOfficeHandlerTest {
    @Test fun digitalPdfFontEvidenceBecomesARealWordHeading() = withFixture {
        val source=File(folder,"heading.pdf")
        PDDocument().use { document ->
            val page=PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document,page).use { content ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA_BOLD,24f)
                content.newLineAtOffset(40f,730f)
                content.showText("Document heading")
                content.endText()
                content.beginText()
                content.setFont(PDType1Font.HELVETICA,12f)
                content.newLineAtOffset(40f,680f)
                content.showText("This body paragraph establishes the normal text size for heading recovery.")
                content.newLineAtOffset(0f,-18f)
                content.showText("Its words remain editable and must not be promoted into heading styles.")
                content.endText()
            }
            document.save(source)
        }
        val request=request("pdf_to_docx",inspect(source))
        PdfOfficeOperationHandler(MlKitOcrEngine(app)).execute(request,execution())
        val record=outputs.find(request.id,"document")!!
        published(record.uri) { file ->
            val xml=part(file,"word/document.xml")
            val namespace="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            val headings=xml.getElementsByTagNameNS(namespace,"pStyle")
            assertEquals(1,headings.length)
            assertEquals("Heading1",(headings.item(0) as org.w3c.dom.Element).getAttributeNS(namespace,"val"))
            assertTrue(xml.documentElement.textContent.contains("Document heading"))
            assertTrue(xml.documentElement.textContent.contains("Its words remain editable"))
        }
    }

    @Test fun digitalPdfExportsDocxTextParagraphsAndSelectedPagesWithoutChangingInput() = withFixture {
        val source = document("document.pdf")
        val original = source.readBytes()
        val ref = inspect(source)
        val handler = PdfOfficeOperationHandler(MlKitOcrEngine(app))
        for ((selection, expectedPages) in listOf("all" to 2, "2" to 1)) {
            val request = request("pdf_to_docx", ref, """{"pages":"$selection"}""")
            val progress = mutableListOf<Long>()
            val result = handler.execute(request, execution(report = { progress += it.completedUnits }))
            assertEquals(ItemStatus.CREATED, result.single().status)
            val record = outputs.find(request.id, "document")!!
            assertEquals(OfficePackageWriter.DOCX_MIME, record.mimeType)
            assertEquals(expectedPages, record.metadata.pageCount)
            assertEquals(original.size.toLong(), record.metadata.originalSizeBytes)
            assertEquals((1L..expectedPages.toLong()).toList(), progress)
            published(record.uri) { file ->
                OfficePackageValidator.validateDocx(file, expectedPages)
                val text = part(file, "word/document.xml").documentElement.textContent
                assertTrue(text.contains("Second source page"))
                assertEquals(selection == "all", text.contains("First source page"))
            }
        }
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun powerpointExportContainsRenderedPageImagesAndAnEditableTextLayer() = withFixture {
        val source = document("slides.pdf")
        val original = source.readBytes()
        val request = request("pdf_to_pptx", inspect(source))
        PdfOfficeOperationHandler(MlKitOcrEngine(app)).execute(request, execution())
        val record = outputs.find(request.id, "document")!!
        assertEquals(OfficePackageWriter.PPTX_MIME, record.mimeType)
        published(record.uri) { file ->
            OfficePackageValidator.validatePptx(file, 2)
            ZipFile(file).use { zip ->
                repeat(2) { index ->
                    val bytes = zip.getInputStream(zip.getEntry("ppt/media/page${index + 1}.jpg")).use { it.readBytes() }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })!!
                    try {
                        assertTrue(bitmap.width > 100 && bitmap.height > 100)
                        var ink = false
                        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
                            if ((bitmap.getPixel(x, y) and 0x00ffffff) < 0x00808080) ink = true
                        }
                        assertTrue("Rendered slide must contain the source text pixels", ink)
                    } finally { bitmap.recycle() }
                    val text = part(file, "ppt/slides/slide${index + 1}.xml").documentElement.textContent
                    assertTrue(text.contains(if (index == 0) "First source page" else "Second source page"))
                }
            }
        }
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun encryptedAndCorruptPdfsCannotPublishOfficeFiles() = withFixture {
        val encrypted = document("encrypted.pdf", password = "user-password")
        val corrupt = File(folder, "corrupt.pdf").apply { writeText("%PDF-1.7\nnot a document") }
        for (source in listOf(encrypted, corrupt)) {
            val original = source.readBytes()
            val request = request("pdf_to_docx", inspect(source))
            val error = runCatching { PdfOfficeOperationHandler(MlKitOcrEngine(app)).execute(request, execution()) }.exceptionOrNull()
            assertNotNull(error)
            assertEquals(if (source == encrypted) "PDF_ENCRYPTED" else "PDF_INVALID", error!!.message)
            assertNull(outputs.find(request.id, "document"))
            assertArrayEquals(original, source.readBytes())
        }
    }

    @Test fun cancellingWhileReportingProgressStopsTheWriterAndDoesNotPublish() = withFixture {
        val source = document("cancel.pdf")
        val original = source.readBytes()
        val request = request("pdf_to_docx", inspect(source), """{"displayName":"Cancel.docx"}""")
        val ticket = outputs.reserve(request.id, "document", "Cancel.docx", OfficePackageWriter.DOCX_MIME)
        val entered = CompletableDeferred<Unit>()
        val worker = CoroutineScope(currentCoroutineContext()).launch(Dispatchers.IO) {
            val parent = currentCoroutineContext()
            PdfOfficeOperationHandler(MlKitOcrEngine(app)).execute(request, execution(report = {
                entered.complete(Unit)
                awaitCancellation()
            }, check = { parent.ensureActive() }))
        }
        withTimeout(10_000) { entered.await() }
        withTimeout(5_000) { worker.cancelAndJoin() }
        assertNull(outputs.find(request.id, "document"))
        assertFalse(ticket.stageFile.exists())
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun digitalPdfOcrPreservesTextWithoutRequiringDownloadedModels() = withFixture {
        val source = document("text.pdf")
        val original = source.readBytes()
        val request = request("pdf_ocr", inspect(source))
        val results = OcrWebHandler(MlKitOcrEngine(app)).execute(request, execution())
        assertEquals(setOf("text", "document"), results.map { it.itemKey }.toSet())
        val text = outputs.find(request.id, "text")!!
        val value = app.contentResolver.openInputStream(Uri.parse(text.uri))!!.bufferedReader().use { it.readText() }
        assertTrue(value.contains("First source page"))
        assertTrue(value.contains("Second source page"))
        assertTrue(value.contains('\u000c'))
        assertArrayEquals(original, source.readBytes())
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        val folder = File(app.filesDir, "processing/results/fixture/office-${UUID.randomUUID()}").apply { mkdirs() }
        try { Fixture(app, db, folder).block() } finally { db.close(); folder.deleteRecursively() }
    }

    private class Fixture(val app: Context, db: AppDatabase, val folder: File) {
        val budget = WorkBudget()
        val sources = SourceStore(app, db, budget)
        val outputs = OutputStore(app, db, PublicOutputStore(app, UserPreferencesRepository(app)))
        fun execution(report: suspend (com.jumlo.app.model.JobProgress) -> Unit = {}, check: () -> Unit = {}) =
            ExecutionContext(sources, outputs, budget, report, check)
        suspend fun inspect(file: File): String = sources.inspect(FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file).toString(), Intent.FLAG_GRANT_READ_URI_PERMISSION).id
        fun request(tool: String, source: String, settings: String = "{}") = JobRequest(UUID.randomUUID().toString(), tool, listOf(source), 1, settings, System.currentTimeMillis())
        fun document(name: String, password: String? = null): File = File(folder, name).also { file ->
            PDDocument().use { doc ->
                for (text in listOf("First source page", "Second source page")) {
                    val page = PDPage(PDRectangle.LETTER)
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { content ->
                        content.beginText(); content.setFont(PDType1Font.HELVETICA, 24f)
                        content.newLineAtOffset(48f, 700f); content.showText(text); content.endText()
                    }
                }
                password?.let { doc.protect(StandardProtectionPolicy("owner-password", it, AccessPermission()).apply { encryptionKeyLength = 128 }) }
                doc.save(file)
            }
        }
        fun published(uri: String, block: (File) -> Unit) {
            val copy = File.createTempFile("office-result-", ".zip", app.cacheDir)
            try {
                app.contentResolver.openInputStream(Uri.parse(uri))!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
                block(copy)
            } finally { copy.delete() }
        }
    }

    private fun part(file: File, path: String) = ZipFile(file).use { zip ->
        zip.getInputStream(zip.getEntry(path)).use { input -> DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(input) }
    }
}
