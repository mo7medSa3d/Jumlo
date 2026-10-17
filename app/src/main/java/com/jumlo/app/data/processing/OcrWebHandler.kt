package com.jumlo.app.data.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.MemoryBudget
import com.jumlo.app.core.PageSelection
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.jobs.OperationHandler
import com.jumlo.app.model.ItemOutcome
import com.jumlo.app.model.ItemStatus
import com.jumlo.app.model.JobRequest
import com.jumlo.app.model.OutputMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * pdf_ocr: renders pages, runs on-device ML Kit OCR, publishes extracted .txt
 * and searchable.pdf with an invisible text layer.
 * webpage_pdf: loads a validated URL in a WebView and prints it to PDF.
 */
@Singleton class OcrWebHandler @Inject constructor(@ApplicationContext private val app: Context) : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> = when (request.toolId) {
        "pdf_ocr" -> ocr(request, context)
        "webpage_pdf" -> webpage(request, context)
        else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
    }

    private suspend fun ocr(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        require(request.sourceIds.size == 1) { "ONE_PDF_REQUIRED" }
        val settings = runCatching { Json.decodeFromString<PdfImagesSettings>(request.settingsJson) }.getOrDefault(PdfImagesSettings())
        val itemKey = "ocr-text"
        val existingTxt = context.outputs.find(request.id, itemKey)
        val existingPdf = context.outputs.find(request.id, "searchable-pdf")
        if (existingTxt != null && existingPdf != null) {
            return listOf(
                ItemOutcome(itemKey, ItemStatus.CREATED, existingTxt.id),
                ItemOutcome("searchable-pdf", ItemStatus.CREATED, existingPdf.id)
            )
        }
        val perPageTexts = mutableMapOf<Int, String>()
        val text = context.sources.withSeekable(request.sourceIds.single(), request.id) { source ->
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val selectedPages = if (settings.pages.isBlank() || settings.pages.trim().equals("all", true)) {
                        (1..renderer.pageCount).toList()
                    } else {
                        PageSelection.parse(settings.pages, renderer.pageCount)
                    }
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    val sb = StringBuilder()
                    try {
                        for ((stepIdx, pageNum) in selectedPages.withIndex()) {
                            val index = pageNum - 1
                            context.checkCancellation()
                            renderer.openPage(index).use { page ->
                                val width = (page.width * 200 / 72.0).roundToInt(); val height = (page.height * 200 / 72.0).roundToInt()
                                MemoryBudget.requireBitmaps(width, height, 1, context.budget.maxBitmapBytes)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, Matrix().apply { setScale(width.toFloat() / page.width, height.toFloat() / page.height) }, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    val recognized: String = suspendCancellableCoroutine { cont ->
                                        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                                            .addOnSuccessListener { cont.resume(it.text) }
                                            .addOnFailureListener { cont.resumeWithException(it) }
                                    }
                                    perPageTexts[index] = recognized
                                    sb.append("--- page ${index + 1} ---\n").append(recognized).append("\n")
                                } finally { bitmap.recycle() }
                            }
                            context.report(com.jumlo.app.model.JobProgress(com.jumlo.app.model.JobStage.RUNNING, (stepIdx + 1).toLong(), selectedPages.size.toLong()))
                        }
                    } finally { recognizer.close() }
                    sb.toString()
                }
            }
        }
        val outcomes = mutableListOf<ItemOutcome>()
        if (existingTxt != null) {
            outcomes += ItemOutcome(itemKey, ItemStatus.CREATED, existingTxt.id)
        } else {
            val ticket = context.outputs.reserve(request.id, itemKey, "ocr.txt", "text/plain")
            ticket.stageFile.writeText(text)
            val rec = context.outputs.publish(ticket) { OutputMetadata() }
            outcomes += ItemOutcome(itemKey, ItemStatus.CREATED, rec.id)
        }
        // Searchable PDF: original pages with embedded invisible OCR text layer.
        try {
            val sKey = "searchable-pdf"
            context.outputs.find(request.id, sKey)?.let { outcomes += ItemOutcome(sKey, ItemStatus.CREATED, it.id) } ?: run {
                val sTicket = context.outputs.reserve(request.id, sKey, "searchable.pdf", "application/pdf")
                context.sources.withSeekable(request.sourceIds.single(), request.id) { src ->
                    com.jumlo.app.data.pdf.PdfBoxBackend.open(src, null).use { doc ->
                        perPageTexts.forEach { (pageIdx, pageText) ->
                            if (pageIdx in 0 until doc.numberOfPages && pageText.isNotBlank()) {
                                runCatching {
                                    com.jumlo.app.data.pdf.PdfBoxBackend.stampInvisibleText(
                                        doc, pageIdx, pageText.replace(Regex("[^\\x20-\\x7E\\n]"), " "), 0.05, 0.05, 10f
                                    )
                                }
                            }
                        }
                        doc.save(sTicket.stageFile)
                    }
                }
                val sRec = context.outputs.publish(sTicket) { context.outputs.inspect(it, "application/pdf") }
                outcomes += ItemOutcome(sKey, ItemStatus.CREATED, sRec.id)
            }
        } catch (e: Exception) { outcomes += ItemOutcome("searchable-pdf", ItemStatus.FAILED, errorCode = com.jumlo.app.data.jobs.stableError(e)) }
        return outcomes
    }

    private suspend fun webpage(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        val settings = Json.decodeFromString<PdfImagesSettings>(request.settingsJson)
        val validatedUri = HttpPolicy.url(settings.pages.trim(), cleartext = false)
        val url = validatedUri.toASCIIString()
        val itemKey = "webpage"
        context.outputs.find(request.id, itemKey)?.let { return listOf(ItemOutcome(itemKey, ItemStatus.CREATED, it.id)) }
        val ticket = context.outputs.reserve(request.id, itemKey, "webpage.pdf", "application/pdf")
        val ok = withContext(Dispatchers.Main) {
            withTimeoutOrNull(60_000) {
                suspendCancellableCoroutine { cont ->
                    val webView = WebView(app)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.allowFileAccess = false
                    webView.settings.allowContentAccess = false
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, finishedUrl: String?) {
                            val adapter = view.createPrintDocumentAdapter("webpage")
                            val attrs = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
                            val file = ticket.stageFile
                            val stream = FileOutputStream(file)
                            val layoutCallback = android.print.PrintAdapterHelper.createLayoutCallback(object : android.print.PrintAdapterHelper.LayoutCallback {
                                override fun onLayoutFinished(info: android.print.PrintDocumentInfo?, changed: Boolean) {
                                    val writeCallback = android.print.PrintAdapterHelper.createWriteCallback(object : android.print.PrintAdapterHelper.WriteCallback {
                                        override fun onWriteFinished(pages: Array<out android.print.PageRange>?) { stream.close(); webView.destroy(); if (cont.isActive) cont.resume(true) }
                                        override fun onWriteFailed(error: CharSequence?) { stream.close(); webView.destroy(); if (cont.isActive) cont.resume(false) }
                                    })
                                    adapter.onWrite(arrayOf(android.print.PageRange.ALL_PAGES), ParcelFileDescriptor.dup(stream.fd), null, writeCallback)
                                }
                                override fun onLayoutFailed(error: CharSequence?) { stream.close(); webView.destroy(); if (cont.isActive) cont.resume(false) }
                            })
                            adapter.onLayout(null, attrs, null, layoutCallback, null)
                        }
                    }
                    webView.loadUrl(url)
                    cont.invokeOnCancellation { try { webView.destroy() } catch (_: Exception) {} }
                }
            } ?: false
        }
        require(ok && ticket.stageFile.length() > 0) { "WEBPAGE_PDF_FAILED" }
        val rec = context.outputs.publish(ticket) { context.outputs.inspect(it, "application/pdf") }
        return listOf(ItemOutcome(itemKey, ItemStatus.CREATED, rec.id))
    }
}
