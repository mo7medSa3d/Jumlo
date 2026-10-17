package com.jumlo.app.data.output

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.jumlo.app.core.Hashing
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.core.SafeNames
import com.jumlo.app.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Durable private publication stays authoritative; a verified visible copy is also created. */
@Singleton class PublicOutputStore @Inject constructor(
    @ApplicationContext private val app:Context,
    private val prefs:UserPreferencesRepository
) {
    suspend fun publish(file:File,name:String,mime:String,sha256:String):String = withContext(Dispatchers.IO) {
        val location=prefs.settingsFlow.first().downloadDestinationFolder
        if(location.startsWith("content://")) saveSaf(Uri.parse(location),file,name,mime,sha256)
        else if(Build.VERSION.SDK_INT>=29) saveMediaStore(file,name,mime,sha256)
        else saveLegacy(file,name,mime,sha256)
    }
    private fun copyVerified(uri:Uri,file:File,sha256:String) {
        val resolver=app.contentResolver
        file.inputStream().use { input ->
            resolver.openOutputStream(uri,"wt")?.use { output ->
                input.copyTo(output,64*1024)
                output.flush()
            } ?: error("DESTINATION_WRITE_UNAVAILABLE")
        }
        val actual=resolver.openInputStream(uri)?.use { Hashing.sha256(it) }
            ?: error("DESTINATION_VERIFY_UNAVAILABLE")
        check(actual==sha256) { "DESTINATION_VERIFY_FAILED" }
    }
    private fun saveSaf(rootUri:Uri,file:File,name:String,mime:String,sha256:String):String {
        val root=DocumentFile.fromTreeUri(app,rootUri) ?: error("DESTINATION_ACCESS_LOST")
        require(root.canWrite()) { "DESTINATION_READ_ONLY" }
        val branded=if(root.name=="Jevlo") root else root.findFile("Jevlo") ?: root.createDirectory("Jevlo") ?: error("DESTINATION_CREATE_FAILED")
        val category=StoragePaths.category(mime)
        val directory=branded.findFile(category) ?: branded.createDirectory(category) ?: error("DESTINATION_CREATE_FAILED")
        val filenames=directory.listFiles().mapNotNull { it.name }.toSet()
        val filename=StoragePaths.uniqueName(name,filenames)
        val output=directory.createFile(mime,filename) ?: error("DESTINATION_CREATE_FAILED")
        try { copyVerified(output.uri,file,sha256); return output.uri.toString() }
        catch(e:Exception) { output.delete(); throw e }
    }
    private fun saveMediaStore(file:File,name:String,mime:String,sha256:String):String {
        val resolver=app.contentResolver
        val path=StoragePaths.relativePath(mime)
        val existing=mutableSetOf<String>()
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?",arrayOf(path),null)?.use { cursor ->
            while(cursor.moveToNext()) existing+=cursor.getString(0)
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
            copyVerified(uri,file,sha256)
            resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            return uri.toString()
        } catch(e:Exception) { runCatching { resolver.delete(uri,null,null) };throw e }
    }
    private fun saveLegacy(file:File,name:String,mime:String,sha256:String):String {
        val dir=File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"Jevlo/${StoragePaths.category(mime)}").apply { mkdirs() }
        val candidate=StoragePaths.uniqueName(name,dir.list()?.toSet() ?: emptySet())
        val destination=File(dir,SafeNames.basename(candidate))
        try {
            file.copyTo(destination,overwrite=false)
            check(Hashing.sha256(destination)==sha256)
            return Uri.fromFile(destination).toString()
        } catch(e:Exception) { destination.delete();throw e }
    }
    fun deleteIfOwned(publicUri:String?) {
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
