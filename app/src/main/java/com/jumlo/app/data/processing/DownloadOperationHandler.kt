package com.jumlo.app.data.processing

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.*
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

class OkHttpTransport(private val client:OkHttpClient):HttpTransport {
    override fun open(url:String,headers:Map<String,String>):TransportResponse {
        val builder=Request.Builder().url(url).get()
        headers.forEach { (key,value)->builder.header(key,value) }
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
    private val secrets:EphemeralSecretStore
):OperationHandler {
    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> = withContext(Dispatchers.IO) {
        val settings=Json.decodeFromString<DownloadSettings>(request.settingsJson)
        val secret=secrets.copy(settings.urlSecretHandle) ?: throw OperationFailure("SECRET_EXPIRED")
        try {
            val urls=String(secret).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            require(urls.isNotEmpty() && urls.size<=100 && (request.toolId=="dl_03" || urls.size==1)) { "INVALID_DOWNLOAD_LIST" }
            urls.forEach { HttpPolicy.url(it,settings.cleartextConsent) }
            val folder=File(app.filesDir,"transfers/${request.id}").apply { mkdirs() }
            val outcomes=mutableListOf<ItemOutcome>()
            for((index,url) in urls.withIndex()) {
                context.checkCancellation(); networkPolicy(settings)
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
                    val result=StreamTransfer(OkHttpTransport(client)).execute(
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
                    val derived=DownloadLinkPolicy.cleanName(url,result.contentDisposition,result.mimeType)
                    val name=if(urls.size==1) derived else "${index+1}-$derived"
                    val ticket=context.outputs.reserve(request.id,key,SafeNames.basename(name),result.mimeType?.takeIf { Regex("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+").matches(it) } ?: "application/octet-stream")
                    // Same internal storage volume. Atomic move avoids duplicating a large download.
                    Files.move(partial.toPath(),ticket.stageFile.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
                    val output=context.outputs.publish(ticket) { file ->
                        require(file.length()==result.bytes) { "DOWNLOAD_VERIFY_FAILED" }; OutputMetadata()
                    }
                    journal.delete()
                    outcomes+=ItemOutcome(key,ItemStatus.CREATED,output.id)
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
    private fun networkPolicy(settings:DownloadSettings) {
        val manager=app.getSystemService(ConnectivityManager::class.java)
        val capabilities=manager.getNetworkCapabilities(manager.activeNetwork) ?: throw OperationFailure("NETWORK_UNAVAILABLE")
        if(settings.wifiOnly && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) throw OperationFailure("WIFI_REQUIRED")
        if(settings.unmeteredOnly && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) throw OperationFailure("UNMETERED_NETWORK_REQUIRED")
    }
}
