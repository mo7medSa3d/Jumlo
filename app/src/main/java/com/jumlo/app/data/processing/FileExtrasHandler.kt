package com.jumlo.app.data.processing

import android.webkit.MimeTypeMap
import com.jumlo.app.core.ArchiveLimits
import com.jumlo.app.core.ResourceBudgetPolicy
import com.jumlo.app.data.files.EncryptedZip
import com.jumlo.app.data.files.EncryptedZipEntrySource
import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.jobs.OperationHandler
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.ItemOutcome
import com.jumlo.app.model.ItemStatus
import com.jumlo.app.model.JobProgress
import com.jumlo.app.model.JobRequest
import com.jumlo.app.model.JobStage
import com.jumlo.app.model.OutputMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Batch rename (renamed copies) and AES encrypted ZIP via zip4j. Passwords: session handles only. */
@Singleton class FileExtrasHandler @Inject constructor(
    private val secrets: EphemeralSecretStore
) : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> = withContext(Dispatchers.IO) {
        when (request.toolId) {
            "file_batch_rename" -> rename(request, context)
            "file_encrypted_zip" -> encryptedZip(request, context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
    }

    private suspend fun rename(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        require(request.sourceIds.isNotEmpty()) { "NO_SOURCES" }
        ResourceBudgetPolicy.requireOutputCount(request.sourceIds.size + 1, context.budget.maxOutputFiles)
        val settings = Json.decodeFromString<BatchRenameSettings>(request.settingsJson)
        val names = request.sourceIds.map { context.sources.get(it).displayName }
        val preview = PureDomainAlgorithms.batchRenamePreview(names, settings.pattern, settings.startAt,
            settings.date.ifBlank { java.time.Instant.ofEpochMilli(request.submittedAtEpochMs).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() })
        val outcomes = mutableListOf<ItemOutcome>()
        val previewKey = "rename-preview"
        context.outputs.find(request.id, previewKey)?.let { outcomes += ItemOutcome(previewKey, ItemStatus.CREATED, it.id) } ?: run {
            val ticket = context.outputs.reserve(request.id, previewKey, "rename_preview.txt", "text/plain")
            val mapping = names.indices.joinToString("\n") { i -> "${names[i]} -> ${preview[i]}" }
            ticket.stageFile.writeText(mapping + "\n")
            val rec = context.outputs.publish(ticket) { OutputMetadata() }
            outcomes += ItemOutcome(previewKey, ItemStatus.CREATED, rec.id)
        }
        request.sourceIds.forEachIndexed { index, id ->
            val key = "renamed:$index"
            context.outputs.find(request.id, key)?.let { outcomes += ItemOutcome(key, ItemStatus.CREATED, it.id); return@forEachIndexed }
            try {
                val source = context.sources.get(id)
                val ticket = context.outputs.reserve(request.id, key, preview[index], source.mimeType ?: "application/octet-stream")
                val rec = try {
                    context.sources.openRead(id).use { input -> FileOutputStream(ticket.stageFile).use { output ->
                        val buffer=ByteArray(64*1024);var copied=0L
                        while(true) {
                            context.checkCancellation();val n=input.read(buffer);if(n<0) break
                            require(n.toLong()<=context.budget.maxTemporaryBytes-copied && requireNotNull(ticket.stageFile.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace>=context.budget.minFreeBytes+n) { "DISK_BUDGET_EXCEEDED" }
                            if(n>0) { output.write(buffer,0,n);copied+=n }
                        };output.fd.sync()
                    } }
                    context.checkCancellation()
                    context.outputs.publish(ticket) { OutputMetadata() }
                } catch (t: Throwable) { ticket.stageFile.delete(); throw t }
                outcomes += ItemOutcome(key, ItemStatus.CREATED, rec.id)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (e: Exception) { outcomes += ItemOutcome(key, ItemStatus.FAILED, errorCode = com.jumlo.app.data.jobs.stableError(e)) }
            context.report(JobProgress(JobStage.RUNNING, (index + 1).toLong(), request.sourceIds.size.toLong()))
        }
        return outcomes
    }

    private suspend fun encryptedZip(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        val settings = Json.decodeFromString<EncryptedArchiveSettings>(request.settingsJson)
        val raw = secrets.copy(settings.secretHandle) ?: throw OperationFailure("SECRET_EXPIRED")
        require(raw.isNotEmpty()) { "ZIP_PASSWORD_REQUIRED" }
        val password = raw.copyOf()
        raw.fill('\u0000')
        val limits = ArchiveLimits(
            maxBytes = context.budget.maxExpandedArchiveBytes,
            maxEntries = if(settings.extract) minOf(context.budget.maxArchiveEntries,context.budget.maxOutputFiles)
                else context.budget.maxArchiveEntries,
            maxDepth = 32
        )
        try {
            require(request.sourceIds.isNotEmpty()) { "NO_SOURCES" }
            return if (!settings.extract) {
                val itemKey = "archive"
                context.outputs.find(request.id, itemKey)?.let { listOf(ItemOutcome(itemKey, ItemStatus.CREATED, it.id)) } ?: run {
                    val outName = SafeNames.basename(settings.displayName.let { if (it.endsWith(".zip", true)) it else "$it.zip" })
                    val ticket = context.outputs.reserve(request.id, itemKey, outName, "application/zip")
                    val tempSourcesDir = File(ticket.stageFile.parentFile, "ezip-inputs-${UUID.randomUUID()}").apply { mkdirs() }
                    var stagedBytes = 0L
                    try {
                        val entrySources = request.sourceIds.mapIndexed { idx, id ->
                            context.checkCancellation()
                            val ref = context.sources.get(id)
                            val tempFile = File(tempSourcesDir, "src-$idx.bin")
                            context.sources.openRead(id).use { input ->
                                FileOutputStream(tempFile).use { out ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (true) {
                                        context.checkCancellation()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        require(count.toLong() <= limits.maxBytes - stagedBytes) { "ARCHIVE_SIZE_LIMIT" }
                                        if (count > 0) {
                                            require(tempSourcesDir.usableSpace >= context.budget.minFreeBytes + count) { "DISK_BUDGET_EXCEEDED" }
                                            out.write(buffer, 0, count)
                                            stagedBytes += count
                                        }
                                    }
                                    out.fd.sync()
                                }
                            }
                            context.report(JobProgress(JobStage.RUNNING, (idx + 1).toLong(), request.sourceIds.size.toLong()))
                            EncryptedZipEntrySource(ref.displayName, tempFile)
                        }
                        if (ticket.stageFile.exists()) require(ticket.stageFile.delete()) { "OUTPUT_STAGE_CLEANUP_FAILED" }
                        EncryptedZip.create(ticket.stageFile, entrySources, password, limits) {
                            context.checkCancellation()
                            require(requireNotNull(ticket.stageFile.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace >= context.budget.minFreeBytes) { "DISK_BUDGET_EXCEEDED" }
                        }
                    } catch (t: Throwable) {
                        ticket.stageFile.delete()
                        throw t
                    } finally {
                        tempSourcesDir.deleteRecursively()
                    }
                    // EncryptedZip.create has already reopened and authenticated
                    // every payload. java.util.zip.ZipFile (used by the generic
                    // inspector) cannot open AES ZIP method 99 on all runtimes.
                    val rec = try {
                        context.outputs.publish(ticket) {
                            OutputMetadata(entryCount = request.sourceIds.size, originalSizeBytes = stagedBytes.takeIf { it > 0L })
                        }
                    } catch (t: Throwable) { ticket.stageFile.delete(); throw t }
                    listOf(ItemOutcome(itemKey, ItemStatus.CREATED, rec.id))
                }
            } else {
                require(request.sourceIds.size == 1) { "ONE_ZIP_REQUIRED" }
                val outcomes = mutableListOf<ItemOutcome>()
                context.sources.withSeekable(request.sourceIds.single(), request.id) { src ->
                    val dir = File(src.parentFile, "ezx-${UUID.randomUUID()}")
                    try {
                        val extractParent = requireNotNull(src.parentFile) { "OUTPUT_CREATE_FAILED" }
                        val free = extractParent.usableSpace - context.budget.minFreeBytes
                        if (free <= 0L) throw OperationFailure("DISK_BUDGET_EXCEEDED")
                        val extractLimits = limits.copy(maxBytes = minOf(limits.maxBytes, free))
                        val extractedFiles = EncryptedZip.extract(src, dir, password, extractLimits) {
                            context.checkCancellation()
                            if (extractParent.usableSpace < context.budget.minFreeBytes) throw OperationFailure("DISK_BUDGET_EXCEEDED")
                        }
                        extractedFiles.forEachIndexed { index, f ->
                            val itemKey = "entry:$index"
                            context.outputs.find(request.id, itemKey)?.let {
                                outcomes += ItemOutcome(itemKey, ItemStatus.CREATED, it.id)
                            } ?: run {
                                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
                                require(f.length() <= context.budget.maxTemporaryBytes) { "ARCHIVE_SIZE_LIMIT" }
                                val ticket = context.outputs.reserve(request.id, itemKey, SafeNames.basename(f.name), mime)
                                val rec = try {
                                    f.inputStream().use { input ->
                                        FileOutputStream(ticket.stageFile).use { out ->
                                            val buffer = ByteArray(64 * 1024)
                                            while (true) {
                                                context.checkCancellation()
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                if (count > 0) {
                                                    require(requireNotNull(ticket.stageFile.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace >= context.budget.minFreeBytes + count) { "DISK_BUDGET_EXCEEDED" }
                                                    out.write(buffer, 0, count)
                                                }
                                            }
                                            out.fd.sync()
                                        }
                                    }
                                    context.outputs.publish(ticket) { OutputMetadata() }
                                } catch (t: Throwable) { ticket.stageFile.delete(); throw t }
                                outcomes += ItemOutcome(itemKey, ItemStatus.CREATED, rec.id)
                            }
                            context.report(JobProgress(JobStage.RUNNING, (index + 1).toLong(), extractedFiles.size.toLong()))
                        }
                    } finally { dir.deleteRecursively() }
                }
                outcomes
            }
        } finally {
            password.fill('\u0000')
        }
    }
}
