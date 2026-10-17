package com.jumlo.app.data.processing

import com.jumlo.app.core.Hashing
import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.jobs.OperationHandler
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.model.ItemOutcome
import com.jumlo.app.model.ItemStatus
import com.jumlo.app.model.JobRequest
import com.jumlo.app.model.OutputMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Batch rename (renamed copies), duplicate report, encrypted ZIP via zip4j (Apache-2.0). Passwords: session handles only. */
@Singleton class FileExtrasHandler @Inject constructor(private val secrets: EphemeralSecretStore) : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> = withContext(Dispatchers.IO) {
        when (request.toolId) {
            "file_batch_rename" -> rename(request, context)
            "file_duplicates", "file_trash" -> duplicates(request, context)
            "file_encrypted_zip" -> encryptedZip(request, context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
    }

    private suspend fun rename(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        val settings = Json.decodeFromString<BatchRenameSettings>(request.settingsJson)
        val names = request.sourceIds.map { context.sources.get(it).displayName }
        val preview = PureDomainAlgorithms.batchRenamePreview(names, settings.pattern, settings.startAt)
        val outcomes = mutableListOf<ItemOutcome>()
        request.sourceIds.forEachIndexed { index, id ->
            val key = "renamed:$index"
            context.outputs.find(request.id, key)?.let { outcomes += ItemOutcome(key, ItemStatus.CREATED, it.id); return@forEachIndexed }
            try {
                val ticket = context.outputs.reserve(request.id, key, preview[index], "application/octet-stream")
                context.sources.openRead(id).use { input -> FileOutputStream(ticket.stageFile).use { input.copyTo(it); it.fd.sync() } }
                val rec = context.outputs.publish(ticket) { OutputMetadata() }
                outcomes += ItemOutcome(key, ItemStatus.CREATED, rec.id)
            } catch (e: Exception) { outcomes += ItemOutcome(key, ItemStatus.FAILED, errorCode = com.jumlo.app.data.jobs.stableError(e)) }
        }
        return outcomes
    }

    private suspend fun duplicates(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        val key = "duplicates-report"
        context.outputs.find(request.id, key)?.let { return listOf(ItemOutcome(key, ItemStatus.CREATED, it.id)) }
        val hashes = request.sourceIds.map { id -> context.sources.openRead(id).use { Hashing.sha256(it, context.checkCancellation) } }
        val groups = PureDomainAlgorithms.duplicateGroups(hashes)
        val names = request.sourceIds.map { context.sources.get(it).displayName }
        val text = buildString {
            append("groups=${groups.size}\n")
            groups.forEach { g -> append(g.joinToString(",") { names[it] }); append(" sha256="); append(hashes[g.first()]); append("\n") }
            if (request.toolId == "file_trash") append("trashNote=Published outputs stay in app storage until you delete them from Files; sources are never deleted by this report.\n")
        }
        val ticket = context.outputs.reserve(request.id, key, "duplicates-report.txt", "text/plain")
        ticket.stageFile.writeText(text)
        val rec = context.outputs.publish(ticket) { OutputMetadata() }
        return listOf(ItemOutcome(key, ItemStatus.CREATED, rec.id))
    }

    private suspend fun encryptedZip(request: JobRequest, context: ExecutionContext): List<ItemOutcome> {
        val settings = Json.decodeFromString<EncryptedArchiveSettings>(request.settingsJson)
        val raw = secrets.copy(settings.secretHandle) ?: throw OperationFailure("SECRET_EXPIRED")
        val password = String(raw).toCharArray()
        return if (!settings.extract) {
            val itemKey = "archive"
            context.outputs.find(request.id, itemKey)?.let { listOf(ItemOutcome(itemKey, ItemStatus.CREATED, it.id)) } ?: run {
                val ticket = context.outputs.reserve(request.id, itemKey, SafeNames.basename(settings.displayName), "application/zip")
                val params = ZipParameters().apply {
                    compressionMethod = CompressionMethod.DEFLATE; compressionLevel = CompressionLevel.NORMAL
                    isEncryptFiles = true; encryptionMethod = EncryptionMethod.AES; aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
                val zip = ZipFile(ticket.stageFile, password)
                request.sourceIds.forEach { id ->
                    val ref = context.sources.get(id)
                    context.sources.withSeekable(id, request.id) { f ->
                        val tmp = File(ticket.stageFile.parentFile, SafeNames.fromDisplayName(ref.displayName, "file.bin"))
                        f.copyTo(tmp, overwrite = true); zip.addFile(tmp, params); tmp.delete()
                    }
                }
                val rec = context.outputs.publish(ticket) { context.outputs.inspect(it, "application/zip") }
                listOf(ItemOutcome(itemKey, ItemStatus.CREATED, rec.id))
            }
        } else {
            require(request.sourceIds.size == 1) { "ONE_ZIP_REQUIRED" }
            val outcomes = mutableListOf<ItemOutcome>()
            context.sources.withSeekable(request.sourceIds.single(), request.id) { src ->
                val dir = File(src.parentFile, "ezx").apply { mkdirs() }
                try {
                    val zip = ZipFile(src, password); zip.extractAll(dir.path)
                    dir.walkTopDown().filter { it.isFile }.forEachIndexed { index, f ->
                        val itemKey = "entry:$index"
                        val ticket = context.outputs.reserve(request.id, itemKey, SafeNames.basename(f.name), "application/octet-stream")
                        f.copyTo(ticket.stageFile, overwrite = true)
                        val rec = context.outputs.publish(ticket) { OutputMetadata() }
                        outcomes += ItemOutcome(itemKey, ItemStatus.CREATED, rec.id)
                    }
                } finally { dir.deleteRecursively() }
            }
            outcomes
        }
    }
}
