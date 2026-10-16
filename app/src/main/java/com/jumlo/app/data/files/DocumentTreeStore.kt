package com.jumlo.app.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.jobs.OperationFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class DocumentEntry(val uri:String,val name:String,val mimeType:String?,val sizeBytes:Long?,val isDirectory:Boolean,val readable:Boolean,val writable:Boolean)
data class DocumentListing(val treeUri:String,val name:String,val entries:List<DocumentEntry>)

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
        DocumentListing(uri,root.name ?: "Folder",children.map { DocumentEntry(it.uri.toString(),it.name ?: "Document",it.type,if(it.isFile) measuredSize(it.uri) else null,it.isDirectory,it.canRead(),it.canWrite()) }.sortedWith(compareByDescending<DocumentEntry> { it.isDirectory }.thenBy { it.name.lowercase() }))
    }
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
        require(children.none { it.uri!=item.uri && it.name?.equals(safe,ignoreCase=true)==true }) { "DESTINATION_EXISTS" }
        require(item.canWrite()) { "PROVIDER_READ_ONLY" }
        require(item.name==safe || item.renameTo(safe)) { "PROVIDER_RENAME_FAILED" }
    }
    suspend fun deleteFile(uri:String):Unit=withContext(Dispatchers.IO) {
        val item=DocumentFile.fromSingleUri(app,Uri.parse(uri)) ?: throw OperationFailure("ACCESS_LOST")
        require(item.isFile && item.canWrite()) { "FILE_DELETE_UNAVAILABLE" }
        require(item.delete()) { "PROVIDER_DELETE_FAILED" }
    }
}
