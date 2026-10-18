package com.jumlo.app.data.jobs
import com.jumlo.app.data.local.*
import com.jumlo.app.model.JobRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import androidx.room.withTransaction
import com.jumlo.app.model.WorkspaceDraft
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton
@Singleton class DraftStore @Inject constructor(private val db:AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun sanitized(draft:WorkspaceDraft)=json.encodeToString(draft.copy(secret1="",secret2=""))
    suspend fun saveWorkspace(draft:WorkspaceDraft) { db.processingDao().saveDraft(DraftEntity(WORKSPACE_ID,sanitized(draft))) }
    suspend fun loadWorkspace():WorkspaceDraft?=db.processingDao().draft(WORKSPACE_ID)?.let { json.decodeFromString(it.requestJson) }
    suspend fun saveRecovery(draft:WorkspaceDraft) { db.processingDao().saveDraft(DraftEntity(RECOVERY_ID,sanitized(draft))) }
    suspend fun loadRecovery():WorkspaceDraft?=db.processingDao().draft(RECOVERY_ID)?.let { json.decodeFromString(it.requestJson) }
    /** An explicit save must commit the checkpoint and its recovery copy together. */
    suspend fun saveWorkspaceAndRecovery(draft:WorkspaceDraft) = db.withTransaction {
        saveWorkspace(draft)
        saveRecovery(draft)
    }
    /** Persist identity before enqueue, so retry after a process interruption reuses the request. */
    suspend fun reserveSubmission(fingerprint:String,candidate:JobRequest,draft:WorkspaceDraft):JobRequest = db.withTransaction {
        val previous=db.processingDao().draft(SUBMISSION_ID)?.let { json.decodeFromString<WorkspaceSubmission>(it.requestJson) }
        if(previous!=null && previous.fingerprint==fingerprint && previous.request!=null) previous.request
        else {
            saveRecovery(draft)
            db.processingDao().saveDraft(DraftEntity(SUBMISSION_ID,json.encodeToString(WorkspaceSubmission(fingerprint,candidate))))
            candidate
        }
    }
    suspend fun clearSubmission() { db.processingDao().saveDraft(DraftEntity(SUBMISSION_ID,json.encodeToString(WorkspaceSubmission()))) }
    suspend fun save(id:String,request:JobRequest) { require(id==request.id); db.processingDao().saveDraft(DraftEntity(id,Json.encodeToString(request))) }
    suspend fun load(id:String):JobRequest?=db.processingDao().draft(id)?.let { Json.decodeFromString(it.requestJson) }
}

private const val WORKSPACE_ID="00000000-0000-4000-8000-000000000001"
private const val RECOVERY_ID="00000000-0000-4000-8000-000000000002"
private const val SUBMISSION_ID="00000000-0000-4000-8000-000000000003"
@Serializable private data class WorkspaceSubmission(val fingerprint:String="",val request:JobRequest?=null)
