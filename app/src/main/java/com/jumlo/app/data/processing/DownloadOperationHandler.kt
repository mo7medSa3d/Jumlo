package com.jumlo.app.data.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.media.MediaSessionStore
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.Properties
import javax.inject.Inject
import kotlin.math.roundToInt

class OkHttpTransport(
    private val client: OkHttpClient,
    private val defaultHeadersProvider: (String) -> Map<String, String> = { PlatformHeaderPolicy.headersFor(it) }
): HttpTransport {
    override fun open(url:String,headers:Map<String,String>):TransportResponse {
        val builder=Request.Builder().url(url).get()
        val merged = LinkedHashMap<String, String>()
        merged.putAll(defaultHeadersProvider(url))
        merged.putAll(headers)
        merged.forEach { (key,value)->builder.header(key,value) }
        val response=client.newCall(builder.build()).execute()
        val body=response.body
        if(body==null) { response.close(); throw IOException("EMPTY_HTTP_BODY") }
        return TransportResponse(response.code,response.headers.names().associate { it.lowercase(Locale.ROOT) to response.header(it).orEmpty() },body.byteStream()) { response.close() }
    }
}

/** Signed URLs remain in memory. Persist only partial bytes, opaque handles and HTTP validators. */
class DownloadOperationHandler @Inject constructor(
    @ApplicationContext private val app:Context,
    private val client:OkHttpClient,
    private val secrets:EphemeralSecretStore,
    private val sessionStore:MediaSessionStore
):OperationHandler {
    // StreamTransfer performs redirects itself, then obtains host-scoped cookies
    // for each hop. OkHttp must not automatically forward cookies to new hosts.
    private val redirectSafeClient = client.newBuilder()
        .followRedirects(false).followSslRedirects(false).build()

    private fun buildHeadersForUrl(targetUrl: String): Map<String, String> {
        val extraMap = sessionStore.getExtraHeaders().lineSequence()
            .map { it.trim() }
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        return PlatformHeaderPolicy.headersFor(
            url = targetUrl,
            customUserAgent = sessionStore.getCustomUserAgent(),
            preferDesktop = sessionStore.isPreferDesktopUa(),
            cookieHeader = sessionStore.cookieHeaderFor(targetUrl),
            customHeaders = extraMap
        )
    }

    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> = withContext(Dispatchers.IO) {
        val settings=Json.decodeFromString<DownloadSettings>(request.settingsJson)
        val secret=secrets.copy(settings.urlSecretHandle) ?: throw OperationFailure("SECRET_EXPIRED")
        try {
            val urls=String(secret).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            require(urls.isNotEmpty() && urls.size<=100 && (request.toolId=="dl_03" || urls.size==1)) { "INVALID_DOWNLOAD_LIST" }
            urls.forEach { rawUrl ->
                HttpPolicy.url(DownloadLinkPolicy.resolveDirectUrl(rawUrl),settings.cleartextConsent)
            }
            val folder=File(app.filesDir,"transfers/${request.id}").apply { mkdirs() }
            val outcomes=mutableListOf<ItemOutcome>()
            for((index,rawUrl) in urls.withIndex()) {
                context.checkCancellation(); networkPolicy(settings)
                val url = DownloadLinkPolicy.resolveDirectUrl(rawUrl)
                val key="download:$index"
                val existing=context.outputs.find(request.id,key)
                if(existing!=null) { outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id); continue }
                val partial=File(folder,"$index.part")
                val journal=File(folder,"$index.properties")
                val old=Properties().apply { if(journal.isFile) journal.inputStream().use(::load) }
                val urlDigest=Hashing.sha256(ByteArrayInputStream(url.toByteArray(Charsets.UTF_8)))
                if(partial.length()>0L && old.getProperty("urlDigest")!=urlDigest) throw OperationFailure("DOWNLOAD_RESTART_REQUIRED")
                try {
                    var lastReport=0L
                    val result=StreamTransfer(OkHttpTransport(redirectSafeClient, ::buildHeadersForUrl)).execute(
                        TransferRequest(url,partial,partial.length(),old.getProperty("etag"),old.getProperty("modified"),old.getProperty("total")?.toLongOrNull(),settings.cleartextConsent,context.budget.maxExpandedArchiveBytes,context.budget.minFreeBytes,finalUrlSha256=old.getProperty("finalUrlSha256")),
                        checkCancel={ context.checkCancellation(); networkPolicy(settings) },
                        progress={ bytes,total ->
                            val now=android.os.SystemClock.elapsedRealtime()
                            if(now-lastReport>=250 || bytes==total) {
                                runBlocking { context.report(JobProgress(JobStage.RUNNING,bytes,total)) }; lastReport=now
                            }
                        },
                        onMetadata={ metadata ->
                            val p=Properties().apply {
                                setProperty("urlDigest",urlDigest)
                                metadata.finalUrlSha256?.let { setProperty("finalUrlSha256",it) }
                                metadata.strongEtag?.let { setProperty("etag",it) }
                                metadata.lastModified?.let { setProperty("modified",it) }
                                metadata.totalBytes?.let { setProperty("total",it.toString()) }
                            }
                            val pending=File(folder,"$index.pending")
                            FileOutputStream(pending).use { p.store(it,null); it.fd.sync() }
                            Files.move(pending.toPath(),journal.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                        }
                    )
                    context.checkCancellation()
                    context.report(JobProgress(JobStage.FINALIZING,result.bytes,result.totalBytes))
                    // An HTML landing page is not a downloaded file. Never silently publish it.
                    val prefix=partial.inputStream().use { input -> ByteArray(256).let { buffer -> buffer.copyOf(input.read(buffer).coerceAtLeast(0)) } }
                    require(!DownloadLinkPolicy.unexpectedHtml(result.mimeType,prefix)) { "DOWNLOAD_NOT_A_FILE" }
                    val derived=DownloadLinkPolicy.cleanName(rawUrl,result.contentDisposition,result.mimeType)
                    val customName=settings.displayName.trim().takeUnless { it.isEmpty() || it=="download.bin" || it=="download" }
                    val baseName=if(customName!=null) DownloadLinkPolicy.cleanName(customName,null,result.mimeType) else derived
                    val name=if(urls.size==1) baseName else "${index+1}-$baseName"
                    val resolvedMime=result.mimeType?.takeIf { Regex("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+").matches(it) } ?: "application/octet-stream"
                    val fileKind=DownloadLinkPolicy.classifyDirectFile(name, resolvedMime)

                    when {
                        fileKind == DirectFileKind.PDF && settings.directMode in setOf("PDF_RECOMMENDED", "PDF_EXTREME") -> {
                            val maxDpi = if (settings.directMode == "PDF_EXTREME") 96 else 150
                            val quality = if (settings.directMode == "PDF_EXTREME") 50 else 75
                            val ticket = context.outputs.reserve(request.id, key, SafeNames.basename(name), "application/pdf")
                            compressDownloadedPdf(partial, ticket.stageFile, maxDpi, quality, context)
                            partial.delete()
                            val output = context.outputs.publish(ticket) { file ->
                                context.outputs.inspect(file, "application/pdf", originalSizeBytes = result.bytes)
                            }
                            journal.delete()
                            outcomes += ItemOutcome(key, ItemStatus.CREATED, output.id)
                        }
                        fileKind == DirectFileKind.PDF && settings.directMode == "PDF_TO_IMAGES" -> {
                            val pdfFolder = AutoOutputName.cleanBaseName(name, "PDF")
                            ParcelFileDescriptor.open(partial, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                PdfRenderer(fd).use { renderer ->
                                    for (pageIdx in 0 until renderer.pageCount) {
                                        context.checkCancellation()
                                        val pageNum = pageIdx + 1
                                        val pageKey = "download:$index:page:$pageNum"
                                        renderer.openPage(pageIdx).use { page ->
                                            val w = (page.width * 144 / 72.0).roundToInt().coerceAtLeast(1)
                                            val h = (page.height * 144 / 72.0).roundToInt().coerceAtLeast(1)
                                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                            try {
                                                bmp.eraseColor(Color.WHITE)
                                                page.render(bmp, null, Matrix().apply { setScale(w.toFloat() / page.width, h.toFloat() / page.height) }, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                                val imgName = AutoOutputName.pageImageName(pdfFolder, pageNum, "png")
                                                val ticket = context.outputs.reserve(request.id, pageKey, imgName, "image/png")
                                                BitmapTransforms.encode(bmp, ticket.stageFile, ImageConversionSettings(format = "PNG", quality = 90), context.budget.maxBitmapBytes)
                                                val rec = context.outputs.publish(ticket) { context.outputs.inspect(it, "image/png", subfolder = pdfFolder) }
                                                outcomes += ItemOutcome(pageKey, ItemStatus.CREATED, rec.id)
                                            } finally { bmp.recycle() }
                                        }
                                    }
                                }
                            }
                            partial.delete()
                            journal.delete()
                        }
                        fileKind == DirectFileKind.IMAGE && settings.directMode == "IMAGE_COMPRESSED" -> {
                            val ticket = context.outputs.reserve(request.id, key, SafeNames.basename(name), resolvedMime)
                            BitmapTransforms.decode(partial, context.budget.maxBitmapBytes).use { decoded ->
                                BitmapTransforms.encodeGuaranteedSmaller(
                                    decoded.bitmap, ticket.stageFile, result.bytes,
                                    ImageConversionSettings(format = "JPEG", quality = 78),
                                    context.budget.maxBitmapBytes, context.checkCancellation
                                )
                            }
                            partial.delete()
                            val output = context.outputs.publish(ticket) { file ->
                                context.outputs.inspect(file, ticket.mimeType, originalSizeBytes = result.bytes)
                            }
                            journal.delete()
                            outcomes += ItemOutcome(key, ItemStatus.CREATED, output.id)
                        }
                        fileKind == DirectFileKind.IMAGE && settings.directMode == "IMAGE_TO_PDF" -> {
                            val pdfName = SafeNames.basename(AutoOutputName.cleanBaseName(name, "Image") + ".pdf")
                            val ticket = context.outputs.reserve(request.id, key, pdfName, "application/pdf")
                            BitmapTransforms.decode(partial, context.budget.maxBitmapBytes).use { decoded ->
                                val bmp = decoded.bitmap
                                PDDocument().use { doc ->
                                    val page = PDPage(PDRectangle(bmp.width.toFloat(), bmp.height.toFloat()))
                                    doc.addPage(page)
                                    val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                                    PDPageContentStream(doc, page).use { cs ->
                                        cs.drawImage(pdImg, 0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                                    }
                                    doc.save(ticket.stageFile)
                                }
                            }
                            partial.delete()
                            val output = context.outputs.publish(ticket) { file ->
                                context.outputs.inspect(file, "application/pdf", originalSizeBytes = result.bytes)
                            }
                            journal.delete()
                            outcomes += ItemOutcome(key, ItemStatus.CREATED, output.id)
                        }
                        else -> {
                            val ticket=context.outputs.reserve(request.id,key,SafeNames.basename(name),resolvedMime)
                            // Same internal storage volume. Atomic move avoids duplicating a large download.
                            Files.move(partial.toPath(),ticket.stageFile.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                            val output=context.outputs.publish(ticket) { file ->
                                require(file.length()==result.bytes) { "DOWNLOAD_VERIFY_FAILED" }
                                context.outputs.inspect(file, resolvedMime)
                            }
                            journal.delete()
                            outcomes+=ItemOutcome(key,ItemStatus.CREATED,output.id)
                        }
                    }
                } catch(cancel:CancellationException) { throw cancel }
                catch(error:Exception) {
                    val code=stableError(error)
                    if(code in setOf("WIFI_REQUIRED","UNMETERED_NETWORK_REQUIRED","DOWNLOAD_RESTART_REQUIRED")) throw OperationFailure(code)
                    outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=code)
                }
            }
            if(outcomes.all { it.outputId!=null }) { secrets.remove(settings.urlSecretHandle); folder.deleteRecursively() }
            outcomes
        } finally { secret.fill('\u0000') }
    }

    private fun compressDownloadedPdf(source: File, dest: File, maxDpi: Int, quality: Int, context: ExecutionContext) {
        val originalSize = source.length()
        val candidate = File(dest.parentFile, "dl-compress-candidate.pdf")
        try {
            runCatching {
                PdfBoxBackend.open(source, null).use { doc ->
                    if (!doc.isEncrypted) {
                        PdfBoxBackend.compressImages(doc, maxDpi, quality) { _, _ -> context.checkCancellation() }
                        doc.save(candidate)
                    }
                }
            }
            if (candidate.isFile && candidate.length() in 1 until originalSize) {
                candidate.copyTo(dest, overwrite = true)
                return
            }
            // Fallback page rasterization if structural pass did not shrink
            runCatching {
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        PDDocument().use { outDoc ->
                            for (i in 0 until renderer.pageCount) {
                                context.checkCancellation()
                                renderer.openPage(i).use { page ->
                                    val pw = page.width.coerceAtLeast(1)
                                    val ph = page.height.coerceAtLeast(1)
                                    val rw = (pw * maxDpi / 72.0).roundToInt().coerceAtLeast(16)
                                    val rh = (ph * maxDpi / 72.0).roundToInt().coerceAtLeast(16)
                                    val bmp = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
                                    try {
                                        bmp.eraseColor(Color.WHITE)
                                        page.render(bmp, null, Matrix().apply { setScale(rw.toFloat() / pw, rh.toFloat() / ph) }, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                        val pdPage = PDPage(PDRectangle(pw.toFloat(), ph.toFloat()))
                                        outDoc.addPage(pdPage)
                                        val pdImg = JPEGFactory.createFromImage(outDoc, bmp, quality / 100f)
                                        PDPageContentStream(outDoc, pdPage).use { cs ->
                                            cs.drawImage(pdImg, 0f, 0f, pw.toFloat(), ph.toFloat())
                                        }
                                    } finally { bmp.recycle() }
                                }
                            }
                            outDoc.save(candidate)
                        }
                    }
                }
            }
            if (candidate.isFile && candidate.length() in 1 until originalSize) {
                candidate.copyTo(dest, overwrite = true)
            } else {
                source.copyTo(dest, overwrite = true)
            }
        } finally {
            candidate.delete()
        }
    }

    private fun networkPolicy(settings:DownloadSettings) {
        val manager=app.getSystemService(ConnectivityManager::class.java)
        val capabilities=manager.getNetworkCapabilities(manager.activeNetwork) ?: throw OperationFailure("NETWORK_UNAVAILABLE")
        if(settings.wifiOnly && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) throw OperationFailure("WIFI_REQUIRED")
        if(settings.unmeteredOnly && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) throw OperationFailure("UNMETERED_NETWORK_REQUIRED")
    }
}
