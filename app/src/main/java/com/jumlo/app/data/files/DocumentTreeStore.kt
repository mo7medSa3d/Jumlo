package com.jumlo.app.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.CopyDestinationPolicy
import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.model.CollisionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Provider operations use document IDs and URI grants, never guessed filesystem paths. */
@Singleton class DocumentTreeStore @Inject constructor(@ApplicationContext private val app:Context) {
    private val preferences=app.getSharedPreferences("document_tree",Context.MODE_PRIVATE)
    suspend fun restore():DocumentListing? = withContext(Dispatchers.IO) { preferences.getString("tree_uri",null)?.let { list(it) } }
    suspend fun accept(uri:String,offeredFlags:Int):DocumentListing=withContext(Dispatchers.IO) {
        val parsed=Uri.parse(uri)
        require(parsed.scheme=="content") { "CONTENT_URI_REQUIRED" }
        val flags=offeredFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION==0) throw OperationFailure("ACCESS_LOST")
        try { app.contentResolver.takePersistableUriPermission(parsed,flags) } catch(_:SecurityException) { throw OperationFailure("PERSISTABLE_GRANT_REQUIRED") }
        val result=list(uri)
        check(preferences.edit().putString("tree_uri",uri).commit()) { "PREFERENCE_WRITE_FAILED" }
        result
    }
    suspend fun list(uri:String):DocumentListing=withContext(Dispatchers.IO) {
        val root=DocumentFile.fromTreeUri(app,Uri.parse(uri)) ?: throw OperationFailure("ACCESS_LOST")
        if(!root.canRead()) throw OperationFailure("ACCESS_LOST")
        val children=root.listFiles()
        require(children.size<=10000) { "DIRECTORY_ENTRY_BUDGET_EXCEEDED" }
        DocumentListing(uri,root.name ?: "Folder",children.map { DocumentEntry(it.uri.toString(),it.name ?: "Document",it.type,if(it.isFile) measuredSize(it.uri) else null,it.isDirectory,it.canRead(),it.canWrite(),supports(it.uri,DocumentsContract.Document.FLAG_SUPPORTS_RENAME),supports(it.uri,DocumentsContract.Document.FLAG_SUPPORTS_DELETE)) }.sortedWith(compareByDescending<DocumentEntry> { it.isDirectory }.thenBy { it.name.lowercase() }),root.canWrite())
    }
    private fun supports(uri:Uri,flag:Int):Boolean = runCatching {
        app.contentResolver.query(uri,arrayOf(DocumentsContract.Document.COLUMN_FLAGS),null,null,null)?.use { cursor ->
            val index=cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            cursor.moveToFirst() && index>=0 && !cursor.isNull(index) && (cursor.getLong(index) and flag.toLong())!=0L
        } ?: false
    }.getOrDefault(false)
    private fun measuredSize(uri:Uri):Long? {
        return app.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { cursor ->
            val index=cursor.getColumnIndex(OpenableColumns.SIZE)
            if(!cursor.moveToFirst() || index<0 || cursor.isNull(index)) null
            else cursor.getLong(index).takeIf { it>=0 }
        }
    }
    suspend fun createFolder(treeUri:String,name:String):Unit=withContext(Dispatchers.IO) {
        val root=DocumentFile.fromTreeUri(app,Uri.parse(treeUri)) ?: throw OperationFailure("ACCESS_LOST")
        val safe=SafeNames.basename(name)
        require(root.canWrite()) { "PROVIDER_READ_ONLY" }
        require(root.findFile(safe)==null) { "DESTINATION_EXISTS" }
        requireNotNull(root.createDirectory(safe)) { "PROVIDER_CREATE_FAILED" }
    }
    suspend fun rename(uri:String,name:String):Unit=withContext(Dispatchers.IO) {
        val tree=preferences.getString("tree_uri",null) ?: throw OperationFailure("NO_FOLDER_SELECTED")
        val root=DocumentFile.fromTreeUri(app,Uri.parse(tree)) ?: throw OperationFailure("ACCESS_LOST")
        val children=root.listFiles()
        require(children.size<=10000) { "DIRECTORY_ENTRY_BUDGET_EXCEEDED" }
        val item=children.firstOrNull { it.uri.toString()==uri } ?: throw OperationFailure("ACCESS_LOST")
        val safe=SafeNames.basename(name)
        require(children.none { it.uri!=item.uri && it.name?.let { name -> sameDocumentName(name,safe) }==true }) { "DESTINATION_EXISTS" }
        require(item.canWrite() && supports(item.uri,DocumentsContract.Document.FLAG_SUPPORTS_RENAME)) { "PROVIDER_RENAME_UNAVAILABLE" }
        require(item.name==safe || item.renameTo(safe)) { "PROVIDER_RENAME_FAILED" }
    }
    suspend fun deleteFile(uri:String):Unit=withContext(Dispatchers.IO) {
        val tree=preferences.getString("tree_uri",null) ?: throw OperationFailure("NO_FOLDER_SELECTED")
        val root=DocumentFile.fromTreeUri(app,Uri.parse(tree)) ?: throw OperationFailure("ACCESS_LOST")
        val children=root.listFiles()
        require(children.size<=10000) { "DIRECTORY_ENTRY_BUDGET_EXCEEDED" }
        val item=children.firstOrNull { it.uri.toString()==uri } ?: throw OperationFailure("ACCESS_LOST")
        require(item.isFile && item.canWrite() && supports(item.uri,DocumentsContract.Document.FLAG_SUPPORTS_DELETE)) { "FILE_DELETE_UNAVAILABLE" }
        require(item.delete()) { "PROVIDER_DELETE_FAILED" }
    }
    /**
     * Provider-aware copy/move with explicit conflict policy (KEEP_BOTH, SKIP, REPLACE)
     * and SHA-256 verification before deleting the source on move.
     */
    suspend fun copyOrMove(
        sourceUri: String,
        targetTreeUri: String,
        desiredName: String? = null,
        move: Boolean = false,
        collisionPolicy: CollisionPolicy = CollisionPolicy.KEEP_BOTH
    ): DocumentEntry? = withContext(Dispatchers.IO) {
        val srcParsed = Uri.parse(sourceUri)
        val srcDoc = DocumentFile.fromSingleUri(app, srcParsed) ?: throw OperationFailure("ACCESS_LOST")
        require(srcDoc.isFile && srcDoc.canRead()) { "ACCESS_LOST" }
        val targetRoot = DocumentFile.fromTreeUri(app, Uri.parse(targetTreeUri)) ?: throw OperationFailure("ACCESS_LOST")
        require(targetRoot.canWrite()) { "PROVIDER_READ_ONLY" }
        val existingChildren = targetRoot.listFiles()
        require(existingChildren.size <= 10000) { "DIRECTORY_ENTRY_BUDGET_EXCEEDED" }
        val baseName = SafeNames.basename(desiredName?.takeIf { it.isNotBlank() } ?: srcDoc.name ?: "file.bin")
        val finalName=CopyDestinationPolicy.name(srcParsed.toString(),baseName,
            existingChildren.mapNotNull { item -> item.name?.let { item.uri.toString() to it } },move,collisionPolicy)
            ?: return@withContext null
        if(move) require(srcDoc.canWrite() && supports(srcParsed,DocumentsContract.Document.FLAG_SUPPORTS_DELETE)) { "PROVIDER_MOVE_SOURCE_DELETE_UNAVAILABLE" }
        val mime = srcDoc.type ?: "application/octet-stream"
        val destDoc = targetRoot.createFile(mime, finalName) ?: throw OperationFailure("PROVIDER_CREATE_FAILED")
        require(destDoc.uri!=srcParsed && existingChildren.none { it.uri==destDoc.uri }) { "PROVIDER_DESTINATION_COLLISION" }
        var verified=false
        val cancellationContext=coroutineContext
        try {
            val expectedHash = app.contentResolver.openInputStream(srcParsed)?.use { Hashing.sha256(it) { cancellationContext.ensureActive() } }
                ?: throw OperationFailure("ACCESS_LOST")
            app.contentResolver.openInputStream(srcParsed)?.use { input ->
                app.contentResolver.openOutputStream(destDoc.uri, "wt")?.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n > 0) output.write(buffer, 0, n)
                    }
                    output.flush()
                } ?: throw OperationFailure("EXPORT_WRITE_FAILED")
            } ?: throw OperationFailure("ACCESS_LOST")
            val actualHash = app.contentResolver.openInputStream(destDoc.uri)?.use { Hashing.sha256(it) { cancellationContext.ensureActive() } }
                ?: throw OperationFailure("EXPORT_UNVERIFIED")
            check(actualHash == expectedHash) { "EXPORT_HASH_MISMATCH" }
            verified=true
            // Read provider metadata before source deletion: a metadata failure must not destroy both copies.
            val result=DocumentEntry(destDoc.uri.toString(), destDoc.name ?: finalName, destDoc.type, measuredSize(destDoc.uri), false, destDoc.canRead(), destDoc.canWrite(),supports(destDoc.uri,DocumentsContract.Document.FLAG_SUPPORTS_RENAME),supports(destDoc.uri,DocumentsContract.Document.FLAG_SUPPORTS_DELETE))
            coroutineContext.ensureActive()
            if (move) {
                require(supports(srcParsed,DocumentsContract.Document.FLAG_SUPPORTS_DELETE) && srcDoc.delete()) { "PROVIDER_MOVE_SOURCE_DELETE_FAILED" }
            }
            result
        } catch (t: Throwable) {
            // A verified destination is a recoverable copy even if deletion of the source fails.
            if(!verified) runCatching { destDoc.delete() }
            throw t
        }
    }
}
