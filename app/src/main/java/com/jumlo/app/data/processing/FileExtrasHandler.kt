package com.jumlo.app.data.processing

import com.jumlo.app.core.ArchiveLimits
import com.jumlo.app.data.files.EncryptedZip
import com.jumlo.app.data.files.EncryptedZipEntrySource
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.data.files.GoogleDriveClient
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
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Batch rename (renamed copies), duplicate report, managed trash summary, encrypted ZIP via zip4j, and Google Drive v3 import/export. Passwords/tokens: session handles only. */
@Singleton class FileExtrasHandler @Inject constructor(
    private val secrets: EphemeralSecretStore,
    private val okHttpClient: OkHttpClient
) : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> = withContext(Dispatchers.IO) {
        when (request.toolId) {
            "file_batch_rename" -> rename(request, context)
            "file_duplicates", "file_trash" -> duplicatesAndTrash(request, context)
            "file_encrypted_zip" -> encryptedZip(request, context)
            "file_drive" -> googleDrive(request, context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
    }

    private suspend fun rename(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
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
                context.sources.openRead(id).use { input -> FileOutputStream(ticket.stageFile).use { output ->
                    val buffer=ByteArray(64*1024);var copied=0L
                    while(true) {
                        context.checkCancellation();val n=input.read(buffer);if(n<0) break
                        require(n.toLong()<=context.budget.maxTemporaryBytes-copied && requireNotNull(ticket.stageFile.parentFile) { "OUTPUT_CREATE_FAILED" }.usableSpace>=context.budget.minFreeBytes+n) { "DISK_BUDGET_EXCEEDED" }
                        if(n>0) { output.write(buffer,0,n);copied+=n }
                    };output.fd.sync()
                } }
                context.checkCancellation()
                val rec = context.outputs.publish(ticket) { OutputMetadata() }
                outcomes += ItemOutcome(key, ItemStatus.CREATED, rec.id)
            } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (e: Exception) { outcomes += ItemOutcome(key, ItemStatus.FAILED, errorCode = com.jumlo.app.data.jobs.stableError(e)) }
            context.report(JobProgress(JobStage.RUNNING, (index + 1).toLong(), request.sourceIds.size.toLong()))
        }
        return outcomes
    }

    private suspend fun duplicatesAndTrash(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        val key = if (request.toolId == "file_trash") "trash-report" else "duplicates-report"
        context.outputs.find(request.id, key)?.let { return listOf(ItemOutcome(key, ItemStatus.CREATED, it.id)) }
        val hashes = request.sourceIds.mapIndexed { idx, id ->
            val h = context.sources.openRead(id).use { Hashing.sha256(it, context.checkCancellation) }
            context.report(JobProgress(JobStage.RUNNING, (idx + 1).toLong(), request.sourceIds.size.toLong()))
            h
        }
        val groups = PureDomainAlgorithms.duplicateGroups(hashes)
        val names = request.sourceIds.map { context.sources.get(it).displayName }
        val trashedItems = if (request.toolId == "file_trash") context.outputs.refreshTrash() else emptyList()
        val text = buildString {
            append("groups=${groups.size}\n")
            groups.forEach { g -> append(g.joinToString(",") { names[it] }); append(" sha256="); append(hashes[g.first()]); append("\n") }
            if (request.toolId == "file_trash") {
                append("trashedCount=${trashedItems.size}\n")
                trashedItems.forEach { item -> append("trashed=${item.displayName}|size=${item.sizeBytes}|sha256=${item.sha256}\n") }
                append("trashNote=Trashed outputs stay in app-managed trash until restored or purged; sources are never deleted by this report.\n")
            }
        }
        val reportName = if (request.toolId == "file_trash") "trash-report.txt" else "duplicates-report.txt"
        val ticket = context.outputs.reserve(request.id, key, reportName, "text/plain")
        ticket.stageFile.writeText(text)
        val rec = context.outputs.publish(ticket) { OutputMetadata() }
        return listOf(ItemOutcome(key, ItemStatus.CREATED, rec.id))
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
                    } finally {
                        tempSourcesDir.deleteRecursively()
                    }
                    // EncryptedZip.create has already reopened and authenticated
                    // every payload. java.util.zip.ZipFile (used by the generic
                    // inspector) cannot open AES ZIP method 99 on all runtimes.
                    val rec = context.outputs.publish(ticket) {
                        OutputMetadata(entryCount = request.sourceIds.size, originalSizeBytes = stagedBytes.takeIf { it > 0L })
                    }
                    listOf(ItemOutcome(itemKey, ItemStatus.CREATED, rec.id))
                }
            } else {
                require(request.sourceIds.size == 1) { "ONE_ZIP_REQUIRED" }
                val outcomes = mutableListOf<ItemOutcome>()
                context.sources.withSeekable(request.sourceIds.single(), request.id) { src ->
                    val dir = File(src.parentFile, "ezx-${UUID.randomUUID()}")
                    try {
                        val extractedFiles = EncryptedZip.extract(src, dir, password, limits, context.checkCancellation)
                        extractedFiles.forEachIndexed { index, f ->
                            val itemKey = "entry:$index"
                            context.outputs.find(request.id, itemKey)?.let {
                                outcomes += ItemOutcome(itemKey, ItemStatus.CREATED, it.id)
                            } ?: run {
                                val ticket = context.outputs.reserve(request.id, itemKey, SafeNames.basename(f.name), "application/octet-stream")
                                require(f.length() <= context.budget.maxTemporaryBytes) { "ARCHIVE_SIZE_LIMIT" }
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
                                val rec = context.outputs.publish(ticket) { OutputMetadata() }
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

    private suspend fun googleDrive(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        val settings = Json.decodeFromString<GoogleDriveSettings>(request.settingsJson)
        val tokenChars = secrets.copy(settings.tokenSecretHandle) ?: throw OperationFailure("LOGIN_REQUIRED")
        try {
            require(tokenChars.isNotEmpty()) { "LOGIN_REQUIRED" }
            val client = GoogleDriveClient(OkHttpTransport(okHttpClient), okHttpClient)
            return if (!settings.exportMode) {
                val itemKey = "drive-import"
                context.outputs.find(request.id, itemKey)?.let { return listOf(ItemOutcome(itemKey, ItemStatus.CREATED, it.id)) }
                val meta = client.fetchMetadata(settings.fileIdOrUrl, tokenChars)
                val ticket = context.outputs.reserve(request.id, itemKey, meta.name, meta.mimeType)
                val downloaded = client.downloadFile(
                    fileIdOrUrl = settings.fileIdOrUrl,
                    accessToken = tokenChars,
                    targetFile = ticket.stageFile,
                    maxBytes = context.budget.maxExpandedArchiveBytes,
                    minFreeBytes = context.budget.minFreeBytes,
                    checkCancel = context.checkCancellation
                )
                context.report(JobProgress(JobStage.FINALIZING, downloaded.sizeBytes ?: ticket.stageFile.length(), downloaded.sizeBytes))
                val rec = context.outputs.publish(ticket) { OutputMetadata() }
                secrets.remove(settings.tokenSecretHandle)
                listOf(ItemOutcome(itemKey, ItemStatus.CREATED, rec.id))
            } else {
                require(request.sourceIds.isNotEmpty()) { "NO_SOURCES" }
                val outcomes = mutableListOf<ItemOutcome>()
                val uploadedLines = mutableListOf<String>()
                for ((index, id) in request.sourceIds.withIndex()) {
                    context.checkCancellation()
                    val srcRef = context.sources.get(id)
                    context.sources.withSeekable(id, request.id) { file ->
                        val uploaded = client.uploadFile(
                            sourceFile = file,
                            displayName = srcRef.displayName,
                            mimeType = srcRef.mimeType ?: "application/octet-stream",
                            folderId = settings.folderId.takeIf { it.isNotBlank() },
                            accessToken = tokenChars,
                            checkCancel = context.checkCancellation
                        )
                        uploadedLines += "uploaded=${uploaded.name}|driveId=${uploaded.id}|mime=${uploaded.mimeType}"
                    }
                    context.report(JobProgress(JobStage.RUNNING, (index + 1).toLong(), request.sourceIds.size.toLong()))
                }
                val reportKey = "drive-export-receipt"
                context.outputs.find(request.id, reportKey)?.let {
                    outcomes += ItemOutcome(reportKey, ItemStatus.CREATED, it.id)
                } ?: run {
                    val ticket = context.outputs.reserve(request.id, reportKey, "drive_export_receipt.txt", "text/plain")
                    ticket.stageFile.writeText(uploadedLines.joinToString("\n") + "\n")
                    val rec = context.outputs.publish(ticket) { OutputMetadata() }
                    outcomes += ItemOutcome(reportKey, ItemStatus.CREATED, rec.id)
                }
                secrets.remove(settings.tokenSecretHandle)
                outcomes
            }
        } finally {
            tokenChars.fill('\u0000')
        }
    }
}
