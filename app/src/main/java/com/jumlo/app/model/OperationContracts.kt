package com.jumlo.app.model

import kotlinx.serialization.Serializable
import java.io.File

@Serializable data class SourceRef(
    val id:String,val uri:String,val displayName:String,val mimeType:String?,val sizeBytes:Long?,
    val readable:Boolean,val writable:Boolean,val persistedReadGrant:Boolean,val modifiedAtEpochMs:Long?
)
@Serializable data class JobRequest(val id:String,val toolId:String,val sourceIds:List<String>,val schemaVersion:Int,val settingsJson:String,val submittedAtEpochMs:Long)
@Serializable enum class JobStage { VALIDATING, QUEUED, RUNNING, FINALIZING, COMPLETED, PARTIAL, FAILED, CANCELLED, INTERRUPTED, PAUSED, WAITING_FOR_INPUT }
@Serializable data class JobProgress(val stage:JobStage,val completedUnits:Long,val totalUnits:Long?)
@Serializable data class OutputMetadata(
    val pageCount:Int?=null,
    val width:Int?=null,
    val height:Int?=null,
    val entryCount:Int?=null,
    val originalSizeBytes:Long?=null,
    val subfolder:String?=null
)
data class OutputTicket(val jobId:String,val itemKey:String,val stageFile:File,val displayName:String,val mimeType:String)
@Serializable data class OutputRecord(val id:String,val jobId:String,val itemKey:String,val uri:String,val displayName:String,val mimeType:String,val sizeBytes:Long,val sha256:String,val metadata:OutputMetadata,val createdAtEpochMs:Long,val publicUri:String?=null)
@Serializable enum class ItemStatus { CREATED, FAILED, CANCELLED, SKIPPED, UNCHANGED, LARGER }
@Serializable data class ItemOutcome(val itemKey:String,val status:ItemStatus,val outputId:String?=null,val errorCode:String?=null)
@Serializable data class JobSnapshot(val request:JobRequest,val progress:JobProgress,val outcomes:List<ItemOutcome>,val updatedAtEpochMs:Long)
