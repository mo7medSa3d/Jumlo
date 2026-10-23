package com.jumlo.app.data.output

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.jumlo.app.core.AutoOutputName
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.core.SafeNames
import com.jumlo.app.core.VerifiedFileCopy
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.diagnostics.AppDiagnostics
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** External-publication boundary; verified private output remains owned by OutputStore. */
interface PublicOutputPublisher {
    suspend fun publish(file:File,name:String,mime:String,sha256:String,subfolder:String?=null):String
    fun deleteIfOwned(publicUri:String?)
}

/** Durable private publication stays authoritative; a verified visible copy is also created. */
@Singleton class PublicOutputStore @Inject constructor(
    @ApplicationContext private val app:Context,
    private val prefs:UserPreferencesRepository
) : PublicOutputPublisher {
    override suspend fun publish(file:File,name:String,mime:String,sha256:String,subfolder:String?):String = withPublicationOwnership(
        Dispatchers.IO, ::deleteIfOwned,
        onRollbackFailure = { AppDiagnostics.report("publication_rollback", it) }
    ) {
        val active=currentCoroutineContext()
        val check:()->Unit={ active.ensureActive() }
        check()
        val safeMime = mime.trim().ifBlank { "application/octet-stream" }
        val location=prefs.settingsFlow.first().downloadDestinationFolder
        if(location.startsWith("content://")) saveSaf(Uri.parse(location),file,name,safeMime,sha256,subfolder,check)
        else if(Build.VERSION.SDK_INT>=29) saveMediaStore(file,name,safeMime,sha256,subfolder,check)
        else saveLegacy(file,name,safeMime,sha256,subfolder,check)
    }
    private fun copyVerified(uri:Uri,file:File,sha256:String,check:()->Unit) {
        val resolver=app.contentResolver
        file.inputStream().use { input ->
            (resolver.openOutputStream(uri,"wt") ?: resolver.openOutputStream(uri))?.use { output ->
                val buffer=ByteArray(64*1024)
                while(true) {
                    check()
                    val count=input.read(buffer)
                    if(count<0) break
                    if(count>0) output.write(buffer,0,count)
                }
                output.flush()
            } ?: error("DESTINATION_WRITE_UNAVAILABLE")
        }
        check()
        val actual=resolver.openInputStream(uri)?.use { Hashing.sha256(it,check) }
            ?: error("DESTINATION_VERIFY_UNAVAILABLE")
        check(actual==sha256) { "DESTINATION_VERIFY_FAILED" }
        check()
    }
    private fun saveSaf(rootUri:Uri,file:File,name:String,mime:String,sha256:String,subfolder:String?=null,check:()->Unit):String {
        check()
        val root=DocumentFile.fromTreeUri(app,rootUri) ?: error("DESTINATION_ACCESS_LOST")
        require(root.canWrite()) { "DESTINATION_READ_ONLY" }
        val branded=if(root.name=="Jumlo") root else root.findFile("Jumlo") ?: root.createDirectory("Jumlo") ?: error("DESTINATION_CREATE_FAILED")
        val category=StoragePaths.category(mime)
        val catDir=branded.findFile(category) ?: branded.createDirectory(category) ?: error("DESTINATION_CREATE_FAILED")
        val cleanSub=subfolder?.trim()?.takeIf { it.isNotEmpty() }?.let { AutoOutputName.cleanBaseName(it,"Folder") }
        val directory=if(cleanSub!=null) catDir.findFile(cleanSub) ?: catDir.createDirectory(cleanSub) ?: error("DESTINATION_CREATE_FAILED") else catDir
        val filenames=directory.listFiles().mapNotNull { it.name }.toSet()
        val filename=StoragePaths.uniqueName(name,filenames)
        val output=directory.createFile(mime,filename) ?: error("DESTINATION_CREATE_FAILED")
        try { copyVerified(output.uri,file,sha256,check); return output.uri.toString() }
        catch(e:Throwable) { runCatching { output.delete() }; throw e }
    }
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveMediaStore(file:File,name:String,mime:String,sha256:String,subfolder:String?=null,check:()->Unit):String {
        check()
        val resolver=app.contentResolver
        val path=StoragePaths.relativePath(mime,subfolder)
        val existing=mutableSetOf<String>()
        runCatching {
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?",arrayOf(path),null)?.use { cursor ->
                while(cursor.moveToNext()) existing+=cursor.getString(0)
            }
        }
        val filename=StoragePaths.uniqueName(name,existing)
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,filename)
            put(MediaStore.MediaColumns.MIME_TYPE,mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH,path)
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("DESTINATION_CREATE_FAILED")
        try {
            copyVerified(uri,file,sha256,check)
            require(resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)==1) {
                "DESTINATION_COMMIT_FAILED"
            }
            return uri.toString()
        } catch(e:Throwable) { runCatching { resolver.delete(uri,null,null) };throw e }
    }
    private fun saveLegacy(file:File,name:String,mime:String,sha256:String,subfolder:String?=null,check:()->Unit):String {
        val cleanSub=subfolder?.trim()?.takeIf { it.isNotEmpty() }?.let { AutoOutputName.cleanBaseName(it,"Folder") }
        val rel=if(cleanSub!=null) "Jumlo/${StoragePaths.category(mime)}/$cleanSub" else "Jumlo/${StoragePaths.category(mime)}"
        val dir=File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),rel).apply { mkdirs() }
        val candidate=StoragePaths.uniqueName(name,dir.list()?.toSet() ?: emptySet())
        val destination=File(dir,SafeNames.basename(candidate))
        VerifiedFileCopy.copy(file,destination,sha256,check)
        return Uri.fromFile(destination).toString()
    }
    override fun deleteIfOwned(publicUri:String?) {
        if(publicUri.isNullOrBlank()) return
        val parsed=Uri.parse(publicUri)
        if(parsed.scheme=="content") app.contentResolver.delete(parsed,null,null)
        else if(parsed.scheme=="file") {
            val root=app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.canonicalFile ?: return
            val file=File(parsed.path.orEmpty()).canonicalFile
            if(file.path.startsWith(root.path+File.separator)) file.delete()
        }
    }
}
