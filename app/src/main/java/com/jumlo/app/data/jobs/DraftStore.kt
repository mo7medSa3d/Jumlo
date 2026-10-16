package com.jumlo.app.data.jobs
import com.jumlo.app.data.local.*
import com.jumlo.app.model.JobRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
@Singleton class DraftStore @Inject constructor(private val db:AppDatabase) {
    suspend fun saveWorkspace(draft:com.jumlo.app.model.WorkspaceDraft) { db.processingDao().saveDraft(DraftEntity(WORKSPACE_ID,Json.encodeToString(draft))) }
    suspend fun loadWorkspace():com.jumlo.app.model.WorkspaceDraft?=db.processingDao().draft(WORKSPACE_ID)?.let { Json.decodeFromString(it.requestJson) }
    suspend fun save(id:String,request:JobRequest) { require(id==request.id); db.processingDao().saveDraft(DraftEntity(id,Json.encodeToString(request))) }
    suspend fun load(id:String):JobRequest?=db.processingDao().draft(id)?.let { Json.decodeFromString(it.requestJson) }
}

private const val WORKSPACE_ID="00000000-0000-4000-8000-000000000001"
