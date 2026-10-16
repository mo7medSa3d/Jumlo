package com.jumlo.app.data.processing

import android.content.Context
import android.webkit.MimeTypeMap
import com.jumlo.app.core.*
import com.jumlo.app.data.jobs.*
import com.jumlo.app.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/** All input bytes come from the immutable intake store; no display-name fallback. */
class ArchiveOperationHandler @Inject constructor(@ApplicationContext private val app:Context):OperationHandler {
    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> = withContext(Dispatchers.IO) {
        require(request.sourceIds.isNotEmpty()) { "NO_SOURCES" }
        when(request.toolId) {
            "file_07" -> create(request,context)
            "file_08" -> extract(request,context)
            "sha256" -> hashes(request,context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
    }
    private fun limits(ctx:ExecutionContext,base:File)=ArchiveLimits(minOf(ctx.budget.maxExpandedArchiveBytes,(base.usableSpace-ctx.budget.minFreeBytes).coerceAtLeast(1)),ctx.budget.maxArchiveEntries,32)
    private suspend fun create(job:JobRequest,ctx:ExecutionContext):List<ItemOutcome> {
        ctx.outputs.find(job.id,"archive")?.let { return listOf(ItemOutcome("archive",ItemStatus.CREATED,it.id)) }
        val settings=Json.decodeFromString<ArchiveSettings>(job.settingsJson)
        require(settings.displayName.endsWith(".zip",true)) { "ZIP_EXTENSION_REQUIRED" }
        val ticket=ctx.outputs.reserve(job.id,"archive",SafeNames.basename(settings.displayName),"application/zip")
        val used=mutableSetOf<String>()
        val inputs=job.sourceIds.mapIndexed { index,id ->
            val source=ctx.sources.get(id)
            val desired=SafeNames.fromDisplayName(source.displayName,"file-${index+1}")
            val unique=SafeNames.unique(used,desired).also { used+=it.lowercase(Locale.ROOT) }
            // ZipOutputStream is blocking. Only opening the owned stream bridges Room's suspend API.
            ArchiveInput(unique) { ctx.checkCancellation(); runBlocking { ctx.sources.openRead(id) } }
        }
        var completed=0L
        ArchiveCore.create(ticket.stageFile,inputs.map { input -> ArchiveInput(input.name) {
            ctx.checkCancellation()
            runBlocking { ctx.report(JobProgress(JobStage.RUNNING,completed++,inputs.size.toLong())) }
            input.open()
        } },settings.compressionLevel,limits(ctx,app.filesDir)) {
            ctx.checkCancellation()
            if(app.filesDir.usableSpace<ctx.budget.minFreeBytes) throw OperationFailure("DISK_BUDGET_EXCEEDED")
        }
        ctx.report(JobProgress(JobStage.FINALIZING,inputs.size.toLong(),inputs.size.toLong()))
        val record=ctx.outputs.publish(ticket) { ctx.outputs.inspect(it,ticket.mimeType) }
        return listOf(ItemOutcome("archive",ItemStatus.CREATED,record.id))
    }
    private suspend fun extract(job:JobRequest,ctx:ExecutionContext):List<ItemOutcome> {
        require(job.sourceIds.size==1) { "ONE_ZIP_REQUIRED" }
        val directory=File(app.cacheDir,"extract/${job.id}/${UUID.randomUUID()}")
        val outcomes=mutableListOf<ItemOutcome>()
        try {
            ctx.sources.withSeekable(job.sourceIds.single(),job.id) { source ->
                val entries=ArchiveCore.extract(source,directory,limits(ctx,app.cacheDir)) {
                    ctx.checkCancellation()
                    if(app.cacheDir.usableSpace<ctx.budget.minFreeBytes) throw OperationFailure("DISK_BUDGET_EXCEEDED")
                }
                require(entries.isNotEmpty()) { "ARCHIVE_HAS_NO_FILES" }
                for ((index, file) in entries.withIndex()) {
                    ctx.checkCancellation()
                    val key="entry:"+file.relativeTo(directory).invariantSeparatorsPath
                    val existing=ctx.outputs.find(job.id,key)
                    if(existing!=null) outcomes+=ItemOutcome(key,ItemStatus.CREATED,existing.id)
                    else try {
                        val mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
                        val ticket=ctx.outputs.reserve(job.id,key,SafeNames.basename(file.name),mime)
                        file.inputStream().use { input -> FileOutputStream(ticket.stageFile).use { output ->
                            val buffer=ByteArray(64*1024)
                            while(true) { ctx.checkCancellation(); val n=input.read(buffer); if(n<0) break
                                require(app.filesDir.usableSpace>=ctx.budget.minFreeBytes+n) { "DISK_BUDGET_EXCEEDED" }
                                if(n>0) output.write(buffer,0,n)
                            }; output.fd.sync()
                        } }
                        // Arbitrary archive members are bytes, not a claim of decodable media.
                        val record=ctx.outputs.publish(ticket) { require(it.length()==file.length()) { "OUTPUT_VERIFY_FAILED" }; OutputMetadata() }
                        outcomes+=ItemOutcome(key,ItemStatus.CREATED,record.id)
                    } catch(cancel:CancellationException) { throw cancel }
                    catch(error:Exception) { outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(error)) }
                    ctx.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),entries.size.toLong()))
                }
            }
        } finally { directory.deleteRecursively() }
        return outcomes
    }
    private suspend fun hashes(job:JobRequest,ctx:ExecutionContext):List<ItemOutcome> {
        val settings=Json.decodeFromString<HashSettings>(job.settingsJson)
        val expected=settings.expectedSha256?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        require(expected==null || Regex("[0-9a-f]{64}").matches(expected)) { "INVALID_SHA256" }
        val outcomes=mutableListOf<ItemOutcome>()
        for ((index, id) in job.sourceIds.withIndex()) {
            val key="sha256:$index"
            ctx.checkCancellation()
            ctx.outputs.find(job.id,key)?.let { outcomes+=ItemOutcome(key,ItemStatus.CREATED,it.id) }
            try {
                val source=ctx.sources.get(id)
                val digest=ctx.sources.openRead(id).use { Hashing.sha256(it,ctx.checkCancellation) }
                if(expected!=null && expected!=digest) throw OperationFailure("SHA256_MISMATCH")
                val base=SafeNames.fromDisplayName(source.displayName,"file-${index+1}")
                val ticket=ctx.outputs.reserve(job.id,key,SafeNames.basename("$base.sha256"),"text/plain")
                FileOutputStream(ticket.stageFile).use { stream -> stream.write("$digest  $base\n".toByteArray(Charsets.UTF_8)); stream.fd.sync() }
                val result=ctx.outputs.publish(ticket) { OutputMetadata() }
                ctx.report(JobProgress(JobStage.RUNNING,(index+1).toLong(),job.sourceIds.size.toLong()))
                outcomes+=ItemOutcome(key,ItemStatus.CREATED,result.id)
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { outcomes+=ItemOutcome(key,ItemStatus.FAILED,errorCode=stableError(error)) }
        }
        return outcomes
    }
}
