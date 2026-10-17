package com.jumlo.app.data.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.jumlo.app.core.media.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.data.processing.DownloadSettings
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import com.jumlo.app.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject

class MediaOperationHandler @Inject constructor(
    @ApplicationContext private val app: Context, private val runtime: MediaRuntime,
    private val secrets: EphemeralSecretStore, private val preferences: UserPreferencesRepository
) : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> = withContext(Dispatchers.IO) {
        val settings = Json.decodeFromString<DownloadSettings>(request.settingsJson)
        val selection = settings.media ?: throw OperationFailure("MEDIA_SELECTION_REQUIRED")
        require(request.sourceIds.isEmpty() && request.toolId == "dl_media") { "INVALID_MEDIA_JOB" }
        val key = "media:0"
        context.outputs.find(request.id,key)?.let { return@withContext listOf(ItemOutcome(key,ItemStatus.CREATED,it.id)) }
        val secret = secrets.copy(settings.urlSecretHandle) ?: throw OperationFailure("SECRET_EXPIRED")
        // A leased retry resumes the same request-owned workspace. Published output identity is stable.
        val root = File(app.noBackupFilesDir,"media-transfers").apply { mkdirs() }
        val directory = File(root,request.id)
        var lastStorageCheck = 0L
        var completed = false
        fun check() {
            context.checkCancellation()
            val cm = app.getSystemService(ConnectivityManager::class.java)
            val network = cm.getNetworkCapabilities(cm.activeNetwork) ?: throw OperationFailure("NETWORK_UNAVAILABLE")
            if(settings.wifiOnly && !network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) throw OperationFailure("WIFI_REQUIRED")
            if(settings.unmeteredOnly && !network.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) throw OperationFailure("UNMETERED_NETWORK_REQUIRED")
            if(root.usableSpace < context.budget.minFreeBytes) throw OperationFailure("INSUFFICIENT_STORAGE")
            val now = SystemClock.elapsedRealtime()
            if(now-lastStorageCheck>=500 && directory.exists()) {
                var size=0L; var count=0
                for(file in directory.walkTopDown()) {
                    if(++count>20000 || Files.isSymbolicLink(file.toPath())) throw OperationFailure("MEDIA_WORKSPACE_LIMIT")
                    if(file.isFile) {
                        val length=file.length()
                        if(length>context.budget.maxMediaWorkspaceBytes-size) throw OperationFailure("MEDIA_WORKSPACE_LIMIT")
                        size+=length
                    }
                }
                lastStorageCheck=now
            }
        }
        try {
            check()
            // Keep .part and fragment files after a transient failure or pause. yt-dlp can
            // continue them on the same request ID; never mix jobs or follow symlinks.
            require(directory.canonicalFile.parentFile == root.canonicalFile) { "MEDIA_WORKSPACE_LIMIT" }
            if (!directory.exists()) require(directory.mkdir()) { "MEDIA_WORKSPACE_MISSING" }
            require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath())) { "MEDIA_WORKSPACE_LIMIT" }
            check()
            val url = MediaLinks.normalize(String(secret),settings.cleartextConsent)
            val mode = MediaMode.valueOf(selection.mode)
            val audio = AudioOutput.valueOf(selection.audioOutput)
            val asset = runtime.inspect(url,settings.cleartextConsent,::check)
            if(MediaCatalog.identity(asset,url)!=selection.assetIdentity) throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
            val choice = MediaCatalog.choices(asset,mode).firstOrNull { it.key==selection.choiceKey }
                ?: throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
            if(MediaCatalog.fingerprint(choice)!=selection.fingerprint) throw OperationFailure("MEDIA_REANALYSIS_REQUIRED")
            choice.estimatedBytes?.let { if(it>context.budget.maxMediaWorkspaceBytes) throw OperationFailure("MEDIA_WORKSPACE_LIMIT") }
            val received = mutableMapOf<String,Long>()
            var lastReport=0L
            runtime.download(url,choice,audio,selection.audioBitrate,settings.cleartextConsent,directory,::check) { progress ->
                received[progress.streamId]=maxOf(received[progress.streamId] ?: 0,progress.downloadedBytes)
                val now=SystemClock.elapsedRealtime()
                if(now-lastReport>=500) {
                    val sum=received.values.fold(0L) { a,b -> if(b>Long.MAX_VALUE-a) Long.MAX_VALUE else a+b }
                    runBlocking { context.report(JobProgress(JobStage.RUNNING,sum,null)) }
                    lastReport=now
                }
            }
            check()
            val file=MediaOutputPolicy.finishedFile(directory)
            val info=MediaOutputPolicy.inspect(file)
            require(info.sizeBytes <= context.budget.maxMediaWorkspaceBytes) { "MEDIA_WORKSPACE_LIMIT" }
            context.report(JobProgress(JobStage.FINALIZING,info.sizeBytes,info.sizeBytes))
            val metadata=validateTracks(file,choice)
            val mime=if(mode==MediaMode.AUDIO && info.extension=="webm") "audio/webm" else info.mimeType
            val ticket=context.outputs.reserve(request.id,key,MediaOutputPolicy.displayName(settings.displayName,info.extension),mime)
            Files.move(file.toPath(),ticket.stageFile.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            check()
            val output=context.outputs.publish(ticket) { staged ->
                require(staged.length()==info.sizeBytes) { "MEDIA_PUBLICATION_MISMATCH" }
                metadata
            }
            secrets.remove(settings.urlSecretHandle)
            completed = true
            listOf(ItemOutcome(key,ItemStatus.CREATED,output.id))
        } finally {
            secret.fill('\u0000')
            // The process runner stops child processes before returning. Preserve fragments
            // only for resumable pauses/retries; remove after successful publication.
            if (completed) directory.deleteRecursively()
        }
    }
    private fun validateTracks(file: File,choice: MediaChoice): OutputMetadata {
        val reader=MediaExtractor()
        try {
            reader.setDataSource(file.absolutePath)
            var audio=false; var video=false; var width:Int?=null;var height:Int?=null
            for(index in 0 until reader.trackCount) {
                val format=reader.getTrackFormat(index)
                val mime=format.getString(MediaFormat.KEY_MIME).orEmpty()
                if(mime.startsWith("audio/")) audio=true
                if(mime.startsWith("video/")) {
                    video=true
                    if(format.containsKey(MediaFormat.KEY_WIDTH)) width=format.getInteger(MediaFormat.KEY_WIDTH)
                    if(format.containsKey(MediaFormat.KEY_HEIGHT)) height=format.getInteger(MediaFormat.KEY_HEIGHT)
                }
                if(mime.startsWith("audio/") || mime.startsWith("video/")) {
                    reader.selectTrack(index)
                    require(reader.sampleTime>=0) { "MEDIA_EMPTY_TRACK" }
                    reader.unselectTrack(index)
                }
            }
            require(!choice.expectsAudio || audio) { "MEDIA_AUDIO_MISSING" }
            if(choice.mode==MediaMode.VIDEO) {
                require(video) { "MEDIA_VIDEO_MISSING" }
                require(choice.expectedHeight==null || height==choice.expectedHeight) { "MEDIA_QUALITY_MISMATCH" }
            } else require(audio && !video) { "MEDIA_AUDIO_EXTRACTION_FAILED" }
            return OutputMetadata(width=width,height=height)
        } finally { reader.release() }
    }
}
