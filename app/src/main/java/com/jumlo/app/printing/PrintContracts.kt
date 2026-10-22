package com.jumlo.app.printing

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Boundaries keep the Android ViewModel testable without disk, PDF rendering, or a print service. */
interface PrintStore {
    val data:Flow<PrintSavedData>
    fun resolve(source:PrintSource):File
    suspend fun snapshot(source:PrintSource):PrintSnapshot
    suspend fun releaseSnapshot(snapshot:PrintSnapshot)
    suspend fun session(source:PrintSource?)
    suspend fun settings(options:PrintOptions)
    suspend fun recent(source:PrintSource)
    suspend fun favorite(source:PrintSource)
    suspend fun preset(name:String,options:PrintOptions)
    suspend fun deletePreset(name:String)
    suspend fun record(entry:PrintHistoryEntry)
    suspend fun reconcile(states:Map<String,PrintJobState>)
}
interface PrintEngine {
    fun inspect(source:PrintSource,options:PrintOptions,check:()->Unit={}):PrintDocumentInfo
    fun initialLayout(options:PrintOptions,info:PrintDocumentInfo,pages:List<PrintPage>):PrintLayout
    fun compose(source:PrintSource,options:PrintOptions,pages:List<PrintPage>,layout:PrintLayout,destination:File,check:()->Unit={},progress:(Int,Int)->Unit={_,_->})
    fun preview(file:File,index:Int,maxEdge:Int=1400):Bitmap
}
