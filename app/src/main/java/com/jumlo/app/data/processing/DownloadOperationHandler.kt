package com.jumlo.app.data.processing

import com.jumlo.app.data.pdf.PdfRendererFactory

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import com.jumlo.app.core.*
import com.jumlo.app.core.media.MediaLinks
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.media.MediaSessionStore
import com.jumlo.app.data.media.executeCancellable
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.*
import com.tom_roush.pdfbox.io.MemoryUsageSetting
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
    private val defaultHeadersProvider: (String, Int) -> Map<String, String> = { url, profileIndex ->
        PlatformHeaderPolicy.headersFor(url, compatibilityAttempt = profileIndex)
    },
    private val checkCancellation:()->Unit = {}
): HttpTransport {
    override fun open(url:String,headers:Map<String,String>):TransportResponse {
        MediaLinks.normalize(url,allowHttp=true)
        val profiles = BrowserProfilePolicy.forUrl(url)
        for (profileIndex in profiles.indices) {
            val builder=Request.Builder().url(url).get()
            val merged = LinkedHashMap<String, String>()
            merged.putAll(defaultHeadersProvider(url, profileIndex))
            merged.putAll(headers)
            merged.forEach { (key,value)->builder.header(key,value) }
            val response=client.executeCancellable(builder.build(),checkCancellation)
            // The GET request is idempotent. Only content-negotiation errors
            // may trigger a second profile; never rotate to work around denial.
            if (BrowserProfilePolicy.tryNext(response.code, profileIndex, profiles.size)) {
                response.close()
                continue
            }
            val body=response.body
            if(body==null) { response.close(); throw IOException("EMPTY_HTTP_BODY") }
            return TransportResponse(response.code,response.headers.names().associate { it.lowercase(Locale.ROOT) to response.header(it).orEmpty() },body.byteStream()) { response.close() }
        }
        throw IOException("MEDIA_PROFILE_NEGOTIATION_FAILED")
    }
}

/** Room stores opaque URL handles; the secret store encrypts submitted URLs for durable resume. */
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

    private fun buildHeadersForUrl(targetUrl: String, profileIndex: Int): Map<String, String> {
        val extraMap = sessionStore.getExtraHeaders().lineSequence()
            .map { it.trim() }
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
        return PlatformHeaderPolicy.headersFor(
            url = targetUrl,
            customUserAgent = sessionStore.getCustomUserAgent(),
            preferDesktop = sessionStore.isPreferDesktopUa(),
            cookieHeader = sessionStore.cookieHeaderFor(targetUrl),
            customHeaders = extraMap,
            compatibilityAttempt = profileIndex
        )
    }

    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> = withContext(Dispatchers.IO) {
        val workerContext=currentCoroutineContext()
        // Retired UI controls must not strand requests restored from earlier versions.
        val settings=Json.decodeFromString<DownloadSettings>(request.settingsJson).copy(
            wifiOnly=false, unmeteredOnly=false, cleartextConsent=true
        )
        val secret=secrets.copy(settings.urlSecretHandle) ?: throw OperationFailure("SECRET_EXPIRED")
        try {
            val urls=String(secret).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            require(urls.isNotEmpty() && urls.size<=100 && (request.toolId=="dl_03" || urls.size==1)) { "INVALID_DOWNLOAD_LIST" }
            urls.forEach { rawUrl ->
                MediaLinks.normalize(DownloadLinkPolicy.resolveDirectUrl(rawUrl),settings.cleartextConsent)
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
                val journalGeneration=old.getProperty("restartGeneration")?.toLongOrNull()?.takeIf { it>=0 } ?: 0L
                // Only the executing worker mutates these files, after checking its
                // claimed lease. Committed output keys were checked above.
                if(DownloadRestartPolicy.resetUncommittedFiles(context.downloadRestartGeneration,journalGeneration,
                        partial,journal,File(folder,"$index.pending"),context.checkCancellation)) old.clear()
                val urlDigest=Hashing.sha256(ByteArrayInputStream(url.toByteArray(Charsets.UTF_8)))
                if(partial.length()>0L && old.getProperty("urlDigest")!=urlDigest) throw OperationFailure("DOWNLOAD_RESTART_REQUIRED")
                try {
                    var lastReport=0L
                    // A fully transferred file whose publication was interrupted must be
                    // published, not downloaded again.
                    val knownTotal=old.getProperty("total")?.toLongOrNull()
                    val alreadyComplete=DownloadRestartPolicy.isTransferComplete(partial.length(),knownTotal)
                    val result=if(alreadyComplete){
                        TransferResult(partial.length(),knownTotal,old.getProperty("etag"),old.getProperty("modified"),
                            old.getProperty("finalUrlSha256")?:urlDigest,old.getProperty("mime"),old.getProperty("disposition"))
                    } else StreamTransfer(OkHttpTransport(redirectSafeClient, ::buildHeadersForUrl,
                        { context.checkCancellation(); networkPolicy(settings) })).execute(
                        TransferRequest(url,partial,partial.length(),old.getProperty("etag"),old.getProperty("modified"),old.getProperty("total")?.toLongOrNull(),settings.cleartextConsent,context.budget.maxMediaWorkspaceBytes,context.budget.minFreeBytes,finalUrlSha256=old.getProperty("finalUrlSha256")),
                        checkCancel={ context.checkCancellation(); networkPolicy(settings) },
                        progress={ bytes,total ->
                            val now=android.os.SystemClock.elapsedRealtime()
                            if(now-lastReport>=250 || bytes==total) {
                                runBlocking(workerContext) { context.report(JobProgress(JobStage.RUNNING,bytes,total)) }; lastReport=now
                            }
                        },
                        onMetadata={ metadata ->
                            val p=Properties().apply {
                                setProperty("urlDigest",urlDigest)
                                setProperty("restartGeneration",context.downloadRestartGeneration.toString())
                                metadata.finalUrlSha256?.let { setProperty("finalUrlSha256",it) }
                                metadata.strongEtag?.let { setProperty("etag",it) }
                                metadata.lastModified?.let { setProperty("modified",it) }
                                metadata.totalBytes?.let { setProperty("total",it.toString()) }
                                // The type of a finished transfer is needed to publish it again.
                                metadata.mimeType?.let { setProperty("mime",it) }
                                metadata.contentDisposition?.let { setProperty("disposition",it) }
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

                    val processDownloaded: suspend () -> Unit = {
                    when {
                        fileKind == DirectFileKind.PDF && settings.directMode in setOf("PDF_RECOMMENDED", "PDF_EXTREME") -> {
                            val maxDpi = if (settings.directMode == "PDF_EXTREME") 96 else 150
                            val quality = if (settings.directMode == "PDF_EXTREME") 50 else 75
                            val ticket = context.outputs.reserve(request.id, key, SafeNames.basename(name), "application/pdf")
                            compressDownloadedPdf(partial, ticket.stageFile, maxDpi, quality, context)
                            val output = context.outputs.publish(ticket) { file ->
                                context.outputs.inspect(file, "application/pdf", originalSizeBytes = result.bytes)
                            }
                            partial.delete()
                            journal.delete()
                            outcomes += ItemOutcome(key, ItemStatus.CREATED, output.id)
                        }
                        fileKind == DirectFileKind.PDF && settings.directMode == "PDF_TO_IMAGES" -> {
                            val pdfFolder = AutoOutputName.cleanBaseName(name, "PDF")
                            ParcelFileDescriptor.open(partial, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                                val renderer = PdfRendererFactory.open(fd, app.cacheDir, context.checkCancellation)
                                renderer.use { renderer ->
                                    val totalPages = renderer.pageCount
                                    require(totalPages > 0) { "EMPTY_PDF" }
                                    fun renderPageTo(pageIdx: Int, target: File) {
                                        context.checkCancellation()
                                        renderer.openPage(pageIdx).use { page ->
                                            val nominalW = (page.width * 144 / 72.0).roundToInt().coerceAtLeast(1)
                                            val nominalH = (page.height * 144 / 72.0).roundToInt().coerceAtLeast(1)
                                            val w = nominalW
                                            val h = nominalH
                                            ImageWorkPolicy.requireFits(w,h,0L,context.budget.maxBitmapBytes)
                                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                            try {
                                                bmp.eraseColor(Color.WHITE)
                                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                                context.checkCancellation()
                                                BitmapTransforms.encode(bmp, target, ImageConversionSettings(format = "PNG", quality = 90), context.budget.maxBitmapBytes)
                                            } finally { bmp.recycle() }
                                        }
                                    }
                                    if (LargeResultStrategy.forPageOutputs(totalPages, context.budget.maxOutputFiles) == LargeResultStrategy.ARCHIVE) {
                                        val archiveKey = "download:$index:images-archive"
                                        val existingArchive = context.outputs.find(request.id, archiveKey)
                                        if (existingArchive != null) {
                                            outcomes += ItemOutcome(archiveKey, ItemStatus.CREATED, existingArchive.id)
                                        } else {
                                            val display = SafeNames.fromDisplayName("$pdfFolder - images.zip", "PDF images.zip")
                                            val ticket = context.outputs.reserve(request.id, archiveKey, display, "application/zip")
                                            val inputs = (0 until totalPages).map { pageIdx ->
                                                val pageNum = pageIdx + 1
                                                ArchiveInput(AutoOutputName.pageImageName(pdfFolder, pageNum, "png")) {
                                                    context.checkCancellation()
                                                    val temp = File.createTempFile("dl-render-page-", ".png", ticket.stageFile.parentFile)
                                                    try {
                                                        renderPageTo(pageIdx, temp)
                                                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                                        BitmapFactory.decodeFile(temp.path, options)
                                                        require(options.outWidth > 0 && options.outHeight > 0) { "IMAGE_VERIFY_FAILED" }
                                                        object : FilterInputStream(FileInputStream(temp)) {
                                                            override fun close() {
                                                                try { super.close() } finally {
                                                                    temp.delete()
                                                                    runBlocking(workerContext) { context.report(JobProgress(JobStage.RUNNING, pageNum.toLong(), totalPages.toLong())) }
                                                                }
                                                            }
                                                        }
                                                    } catch (t: Throwable) { temp.delete(); throw t }
                                                }
                                            }
                                            ArchiveCore.create(ticket.stageFile, inputs, 6, ArchiveLimits(Long.MAX_VALUE, inputs.size, 1)) {
                                                context.checkCancellation()
                                                if (requireNotNull(ticket.stageFile.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace < context.budget.minFreeBytes)
                                                    throw OperationFailure("DISK_BUDGET_EXCEEDED")
                                            }
                                            val rec = context.outputs.publish(ticket) { context.outputs.inspect(it, "application/zip", subfolder = pdfFolder) }
                                            outcomes += ItemOutcome(archiveKey, ItemStatus.CREATED, rec.id)
                                        }
                                    } else {
                                        for (pageIdx in 0 until totalPages) {
                                            context.checkCancellation()
                                            val pageNum = pageIdx + 1
                                            val pageKey = "download:$index:page:$pageNum"
                                            val existingPage = context.outputs.find(request.id, pageKey)
                                            if (existingPage != null) {
                                                outcomes += ItemOutcome(pageKey, ItemStatus.CREATED, existingPage.id)
                                            } else {
                                                val imgName = AutoOutputName.pageImageName(pdfFolder, pageNum, "png")
                                                val ticket = context.outputs.reserve(request.id, pageKey, imgName, "image/png")
                                                renderPageTo(pageIdx, ticket.stageFile)
                                                val rec = context.outputs.publish(ticket) { context.outputs.inspect(it, "image/png", subfolder = pdfFolder) }
                                                outcomes += ItemOutcome(pageKey, ItemStatus.CREATED, rec.id)
                                            }
                                            context.report(JobProgress(JobStage.RUNNING, pageNum.toLong(), totalPages.toLong()))
                                        }
                                    }
                                }
                            }
                            partial.delete()
                            journal.delete()
                        }
                        fileKind == DirectFileKind.IMAGE && settings.directMode == "IMAGE_COMPRESSED" -> {
                            // Conversion is always JPEG; never publish JPEG bytes under a .png/.webp label.
                            // Compression is optional: if the encoded file is not smaller, preserve the original.
                            val candidate=File(folder,"$index-compressed.jpg")
                            try {
                                try {
                                    BitmapTransforms.decode(partial, context.budget.maxBitmapBytes).use { decoded ->
                                        BitmapTransforms.encodeGuaranteedSmaller(
                                            decoded.bitmap, candidate, result.bytes,
                                            ImageConversionSettings(format = "JPEG", quality = 78),
                                            context.budget.maxBitmapBytes, context.checkCancellation
                                        )
                                    }
                                } catch (_: ImageSizeBoundException) { candidate.delete() }
                                catch (resource: IllegalArgumentException) {
                                    if(resource.message !in setOf("IMAGE_MEMORY_BUDGET_EXCEEDED","IMAGE_DIMENSIONS_UNREPRESENTABLE")) throw resource
                                    // Compression is optional; a valid completed download
                                    // remains publishable when exact image work cannot fit.
                                    candidate.delete()
                                }
                                context.checkCancellation()
                                val choice=DownloadCompressionPolicy.choose(name,resolvedMime,result.bytes,if(candidate.isFile) candidate.length() else result.bytes)
                                val ticket=context.outputs.reserve(request.id,key,choice.filename,choice.mimeType)
                                val source=if(choice.useCompressed) candidate else partial
                                Files.move(source.toPath(),ticket.stageFile.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                                val output=context.outputs.publish(ticket) { file ->
                                    context.outputs.inspect(file,ticket.mimeType,originalSizeBytes=result.bytes)
                                }
                                partial.delete()
                                journal.delete()
                                outcomes += ItemOutcome(key, ItemStatus.CREATED, output.id)
                            } finally { candidate.delete() }
                        }
                        fileKind == DirectFileKind.IMAGE && settings.directMode == "IMAGE_TO_PDF" -> {
                            val pdfName = SafeNames.basename(AutoOutputName.cleanBaseName(name, "Image") + ".pdf")
                            val ticket = context.outputs.reserve(request.id, key, pdfName, "application/pdf")
                            BitmapTransforms.decode(partial, context.budget.maxBitmapBytes).use { decoded ->
                                val bmp = decoded.bitmap
                                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                                    val geometry = PdfPageGeometry.resolve(bmp.width,bmp.height)
                                    val page = PDPage(PDRectangle(geometry.widthPt, geometry.heightPt))
                                    doc.addPage(page)
                                    val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                                    PDPageContentStream(doc, page).use { cs ->
                                        cs.drawImage(pdImg, 0f, 0f, geometry.widthPt, geometry.heightPt)
                                    }
                                    doc.save(ticket.stageFile)
                                }
                            }
                            val output = context.outputs.publish(ticket) { file ->
                                context.outputs.inspect(file, "application/pdf", originalSizeBytes = result.bytes)
                            }
                            partial.delete()
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
                    }
                    if(settings.directMode=="ORIGINAL") processDownloaded() else BitmapWorkGate.run { processDownloaded() }
                } catch(cancel:CancellationException) { throw cancel }
                catch(error:Exception) {
                    val code=if(stableError(error)=="MISSING_RESUME_VALIDATOR") "DOWNLOAD_RESTART_REQUIRED" else stableError(error)
                    if(code in setOf("WIFI_REQUIRED","UNMETERED_NETWORK_REQUIRED","DOWNLOAD_RESTART_REQUIRED")) throw OperationFailure(code)
                    outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=code)
                }
            }
            // URL ownership is released only after JobStore commits a terminal state.
            if(outcomes.all { it.outputId!=null }) folder.deleteRecursively()
            outcomes
        } finally { secret.fill('\u0000') }
    }

    private fun compressDownloadedPdf(source: File, dest: File, maxDpi: Int, quality: Int, context: ExecutionContext) {
        // A downloaded document must not become a flattened bitmap just to make the size smaller.
        // Preserve vectors, text, selectable content, page dimensions and the original partial file.
        val originalBytes=source.length()
        val candidate=File(dest.parentFile,"download-compress-candidate.pdf")
        try {
            val sourcePages = PdfBoxBackend.open(source,null).use { doc ->
                require(!doc.isEncrypted) { "PDF_ENCRYPTED" }
                doc.numberOfPages
            }
            candidate.delete()
            PdfBoxBackend.open(source,null).use { doc ->
                PdfBoxBackend.compressImages(doc, maxDpi, quality, onPage = { _, _ -> context.checkCancellation() },
                    maxBitmapBytes = context.budget.maxBitmapBytes)
                doc.save(candidate)
            }
            val validPages = runCatching { PdfBoxBackend.open(candidate,null).use { it.numberOfPages } }.getOrNull()
            // Downloaded PDFs that cannot get smaller are still useful and valid.
            // Preserve the original stream instead of failing the whole download.
            if (validPages != null && PdfCompressionPolicy.accept(originalBytes,candidate.length(),sourcePages,validPages))
                candidate.copyTo(dest,overwrite=true)
            else source.copyTo(dest,overwrite=true)
        } finally { candidate.delete() }
    }

    private fun networkPolicy(settings:DownloadSettings) {
        val manager=app.getSystemService(ConnectivityManager::class.java)
        val capabilities=manager.getNetworkCapabilities(manager.activeNetwork) ?: throw OperationFailure("NETWORK_UNAVAILABLE")
        if(settings.wifiOnly && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) throw OperationFailure("WIFI_REQUIRED")
        if(settings.unmeteredOnly && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) throw OperationFailure("UNMETERED_NETWORK_REQUIRED")
    }
}
