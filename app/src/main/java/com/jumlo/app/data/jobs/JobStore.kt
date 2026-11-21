package com.jumlo.app.data.jobs

import android.content.Context
import androidx.work.*
import com.jumlo.app.data.local.*
import com.jumlo.app.core.JobLifecyclePolicy
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.diagnostics.AppDiagnostics
import com.jumlo.app.model.*
import com.jumlo.app.worker.EngineOperationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class JobStore @Inject constructor(@ApplicationContext private val app:Context,private val db:AppDatabase,private val sources:SourceStore,private val outputs:OutputStore,private val budget:WorkBudget,
    private val preferences:UserPreferencesRepository,private val admission:DownloadAdmission,private val secrets:EphemeralSecretStore) {
    private val dao get()=db.processingDao()
    private val work get()=WorkManager.getInstance(app)
    private val json=Json { ignoreUnknownKeys=true }
    val snapshots=dao.jobs().map { rows -> rows.mapNotNull { row ->
        runCatching { json.decodeFromString<JobSnapshot>(row.snapshotJson) }
            .onFailure { AppDiagnostics.report("job_snapshot_decode",it) }
            .getOrNull()
    } }
    suspend fun contains(jobId:String):Boolean = dao.job(jobId)!=null
    fun observe(jobId:String):Flow<JobSnapshot> = dao.observeJob(jobId).filterNotNull().map { json.decodeFromString(it.snapshotJson) }
    suspend fun recoverPending() {
        val now=System.currentTimeMillis()
        val rows=dao.jobs().first()
        withContext(Dispatchers.IO) {
            rows.filter { it.stage in CLEANABLE_TERMINAL_STAGES && (it.leaseOwner==null || it.leaseUntil<now) }
                .forEach { row ->
                    runCatching { cleanupTerminalArtifacts(row.id,json.decodeFromString(row.requestJson)) }
                        .onFailure { AppDiagnostics.report("job_recovery",it) }
                }
        }
        rows.filter { row ->
            JobLifecyclePolicy.shouldRecover(row.stage) && (row.leaseOwner==null || row.leaseUntil<now)
        }.forEach { row ->
            val policy=if(row.stage==JobStage.INTERRUPTED.name) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            runCatching { schedule(json.decodeFromString(row.requestJson),policy) }
                .onFailure { AppDiagnostics.report("job_recovery",it) }
        }
    }
    suspend fun enqueue(request:JobRequest):String {
        require(CapabilityRegistry.canProcess(request.toolId)) { "CAPABILITY_UNAVAILABLE" }
        require(UUID.fromString(request.id).toString()==request.id && request.sourceIds.size<=1000 && request.settingsJson.length<=100000) { "INVALID_JOB_REQUEST" }
        require(request.schemaVersion==1) { "UNSUPPORTED_SETTINGS_VERSION" }
        val snapshot=JobSnapshot(request,JobProgress(JobStage.QUEUED,0,null),emptyList(),System.currentTimeMillis())
        val encoded=json.encodeToString(request)
        dao.insertJob(JobEntity(request.id,encoded,json.encodeToString(snapshot),JobStage.QUEUED.name))
        check(dao.job(request.id)?.requestJson==encoded) { "JOB_IDENTITY_CONFLICT" }
        schedule(request)
        return request.id
    }
    private fun schedule(request:JobRequest,policy:ExistingWorkPolicy=ExistingWorkPolicy.KEEP) {
        val builder=OneTimeWorkRequestBuilder<EngineOperationWorker>().setInputData(workDataOf("job_id" to request.id)).addTag("processing")
        if(request.toolId.startsWith("dl_")) {
            builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        }
        work.enqueueUniqueWork("job:${request.id}",policy,builder.build())
    }
    /** Only call after the terminal state is durable. Paused/retryable jobs still need their URLs. */
    private suspend fun cleanupTerminalArtifacts(jobId:String,request:JobRequest) {
        if(runCatching { UUID.fromString(jobId).toString()==jobId }.getOrDefault(false)) {
            runCatching { File(app.filesDir,"transfers/$jobId").deleteRecursively() }
            runCatching { File(app.noBackupFilesDir,"media-transfers/$jobId").deleteRecursively() }
        }
        runCatching {
            val obj=JSONObject(request.settingsJson)
            for(key in SECRET_HANDLE_KEYS) {
                val handle=obj.optString(key,"")
                if(handle.isNotBlank()) secrets.remove(handle)
            }
        }
        // Automatically reap temporary inbound tool-input copies so only published outputs & explicit library imports stay in app storage.
        if (request.sourceIds.isNotEmpty()) {
            runCatching {
                val activeJobSources = dao.jobs().first()
                    .filter { it.id != jobId && it.stage !in setOf(JobStage.COMPLETED.name, JobStage.CANCELLED.name) }
                    .flatMap { runCatching { json.decodeFromString<JobRequest>(it.requestJson).sourceIds }.getOrDefault(emptyList()) }
                    .toSet()
                val draftBlobs = dao.allDrafts().map { it.requestJson }
                request.sourceIds.distinct().forEach { sourceId ->
                    if (sourceId !in activeJobSources && draftBlobs.none { it.contains(sourceId) }) {
                        runCatching { sources.discardNewImport(sourceId) }
                    }
                }
            }
        }
    }
    suspend fun snapshot(jobId: String): JobSnapshot? = dao.job(jobId)?.let { runCatching { json.decodeFromString<JobSnapshot>(it.snapshotJson) }.getOrNull() }
    suspend fun outputRecord(outputId: String): OutputRecord? = dao.outputById(outputId)?.let { runCatching { json.decodeFromString<OutputRecord>(it.recordJson) }.getOrNull() }
    suspend fun pause(jobId:String) {
        val row=dao.job(jobId) ?: return
        val old=json.decodeFromString<JobSnapshot>(row.snapshotJson)
        if(!old.request.toolId.startsWith("dl_") || !JobLifecyclePolicy.canPause(old.progress.stage.name)) return
        val paused=old.copy(progress=old.progress.copy(stage=JobStage.PAUSED),updatedAtEpochMs=System.currentTimeMillis())
        if(dao.pauseIfActive(jobId,json.encodeToString(paused))==1) work.cancelUniqueWork("job:$jobId")
    }
    suspend fun cancel(jobId:String) {
        val row=dao.job(jobId) ?: return
        val old=json.decodeFromString<JobSnapshot>(row.snapshotJson)
        if(old.progress.stage==JobStage.COMPLETED || old.progress.stage==JobStage.CANCELLED) return
        val cancelled=old.copy(progress=old.progress.copy(stage=JobStage.CANCELLED),updatedAtEpochMs=System.currentTimeMillis())
        val stoppingFailed = old.progress.stage in STOPPABLE_FAILED_STAGES
        val changed = if(stoppingFailed) dao.stopRetryingIfUnleased(jobId,json.encodeToString(cancelled),System.currentTimeMillis())
            else dao.cancelIfOpen(jobId,json.encodeToString(cancelled))
        if(stoppingFailed && changed==0) throw OperationFailure("JOB_BUSY")
        if(changed==1) {
            work.cancelUniqueWork("job:$jobId")
            withContext(Dispatchers.IO) {
                val current=dao.job(jobId)
                if(current?.leaseOwner==null || current.leaseUntil<System.currentTimeMillis()) {
                    cleanupTerminalArtifacts(jobId,old.request)
                }
            }
        }
    }
    suspend fun retryFailed(jobId:String):String {
        val row=dao.job(jobId) ?: throw OperationFailure("JOB_NOT_FOUND")
        val old=json.decodeFromString<JobSnapshot>(row.snapshotJson)
        // Committed output keys survive retries. Failed direct transfers restart under
        // the next worker's fenced lease; pause/resume preserves its generation.
        val retainedUnits=old.progress.completedUnits.coerceAtLeast(0L)
        val retainedTotal=old.progress.totalUnits?.takeIf { it>=retainedUnits }
        val next=old.copy(progress=JobProgress(JobStage.QUEUED,retainedUnits,retainedTotal),outcomes=old.outcomes.filter { it.status!=ItemStatus.FAILED },updatedAtEpochMs=System.currentTimeMillis(),
            downloadRestartGeneration=com.jumlo.app.core.DownloadRestartPolicy.retryGeneration(old.request.toolId,row.stage,old.downloadRestartGeneration,old.outcomes.lastOrNull { it.status==ItemStatus.FAILED }?.errorCode))
        // Wait briefly for the cancelled/interrupted worker to release its fenced lease.
        if(row.stage in RETRY_WAIT_STAGES) {
            for(attempt in 0 until 20) {
                val current=dao.job(jobId)
                if(current?.leaseOwner==null || current.leaseUntil<System.currentTimeMillis()) break
                delay(100)
            }
        }
        // Reclaim an expired worker atomically with the stage change; its old owner
        // can no longer update or release the new attempt even if that process wakes.
        if(dao.retry(jobId,json.encodeToString(next),System.currentTimeMillis())!=1) throw OperationFailure("JOB_NOT_RETRYABLE")
        schedule(old.request,ExistingWorkPolicy.REPLACE)
        return jobId
    }
    suspend fun recordInfrastructureFailure(jobId:String) {
        val row=dao.job(jobId) ?: return
        val snapshot=try { json.decodeFromString<JobSnapshot>(row.snapshotJson) }
        catch(_:SerializationException) {
            // The request is persisted separately, so a malformed snapshot must
            // not make the worker's error-reporting path decode the same bad data.
            JobSnapshot(json.decodeFromString(row.requestJson),JobProgress(JobStage.QUEUED,0,null),emptyList(),System.currentTimeMillis())
        }
        val failed=snapshot.copy(progress=snapshot.progress.copy(stage=JobStage.FAILED),
            outcomes=snapshot.outcomes+ItemOutcome("worker",ItemStatus.FAILED,errorCode="WORKER_START_FAILED"),
            updatedAtEpochMs=System.currentTimeMillis())
        dao.failBeforeClaim(jobId,json.encodeToString(failed))
    }
    /** Returns false only when another worker still holds the durable lease. */
    suspend fun execute(jobId:String,dispatcher:OperationHandler):Boolean=coroutineScope {
        val row=dao.job(jobId) ?: throw OperationFailure("JOB_NOT_FOUND")
        if(row.stage in NON_EXECUTABLE_STAGES) {
            if(row.stage in CLEANABLE_TERMINAL_STAGES && (row.leaseOwner==null || row.leaseUntil<System.currentTimeMillis())) withContext(Dispatchers.IO) {
                cleanupTerminalArtifacts(jobId,json.decodeFromString(row.requestJson))
            }
            return@coroutineScope true
        }
        // Decode before claiming: a malformed snapshot cannot strand a durable lease.
        var snapshot=json.decodeFromString<JobSnapshot>(row.snapshotJson)
        require(snapshot.request.id==jobId) { "INVALID_JOB_REQUEST" }
        val owner=UUID.randomUUID().toString(); val now=System.currentTimeMillis()
        val leased=AtomicBoolean(true); val runningContext=currentCoroutineContext()
        suspend fun save(progress:JobProgress,outcomes:List<ItemOutcome> = snapshot.outcomes) {
            val next=snapshot.copy(progress=progress,outcomes=outcomes,updatedAtEpochMs=System.currentTimeMillis())
            if(dao.updateOwned(jobId,owner,json.encodeToString(next),progress.stage.name)!=1) throw CancellationException("JOB_LEASE_LOST")
            snapshot=next
        }
        var heartbeat:Job?=null
        try {
            // Cover the suspending claim too: its SQL update may commit immediately
            // before cancellation discards the returned affected-row count.
            if(dao.claim(jobId,owner,now,now+60000)!=1) return@coroutineScope false
            heartbeat=launch(Dispatchers.IO) {
                while(isActive) {
                    delay(15000)
                    if(dao.heartbeat(jobId,owner,System.currentTimeMillis()+60000)!=1) { leased.set(false); break }
                }
            }
            outputs.reconcile()
            outputs.retryPublicSaves(jobId)
            val priorCompleted=snapshot.progress.completedUnits.coerceAtLeast(0L)
            val priorTotal=snapshot.progress.totalUnits?.takeIf { it>=priorCompleted }
            save(JobProgress(JobStage.VALIDATING,priorCompleted,priorTotal))
            val ctx=ExecutionContext(sources,outputs,budget,{ save(it) },{
                runningContext.ensureActive()
                if(!leased.get()) throw CancellationException("JOB_LEASE_LOST")
            },downloadRestartGeneration=snapshot.downloadRestartGeneration)
            val processed=if(snapshot.request.toolId.startsWith("dl_")) {
                admission.withSlot(2,ctx.checkCancellation) {
                    save(JobProgress(JobStage.RUNNING,snapshot.progress.completedUnits,snapshot.progress.totalUnits))
                    dispatcher.execute(snapshot.request,ctx).also {ctx.checkCancellation()}
                }
            } else {
                save(JobProgress(JobStage.RUNNING,snapshot.progress.completedUnits,snapshot.progress.totalUnits))
                dispatcher.execute(snapshot.request,ctx).also {ctx.checkCancellation()}
            }
            val outcomes=outputs.reportPublicSaveFailures(processed)
            val failures=outcomes.count { it.status!=ItemStatus.CREATED && it.status!=ItemStatus.SKIPPED }
            val successes=outcomes.count { it.outputId!=null }
            val state=when { outcomes.isEmpty() -> JobStage.FAILED; failures==0 -> JobStage.COMPLETED; successes>0 -> JobStage.PARTIAL; else -> JobStage.FAILED }
            save(snapshot.progress.copy(stage=state),outcomes)
            true
        } catch(cancel:CancellationException) {
            withContext(NonCancellable+Dispatchers.IO) {
                val current=dao.job(jobId)
                if(current?.stage !in NON_EXECUTABLE_STAGES) {
                    val interrupted=snapshot.copy(progress=snapshot.progress.copy(stage=JobStage.INTERRUPTED),updatedAtEpochMs=System.currentTimeMillis())
                    dao.updateOwned(jobId,owner,json.encodeToString(interrupted),JobStage.INTERRUPTED.name)
                }
            }
            throw cancel
        } catch(oom:OutOfMemoryError) {
            save(snapshot.progress.copy(stage=JobStage.FAILED),snapshot.outcomes+ItemOutcome("job",ItemStatus.FAILED,errorCode="LOW_MEMORY"))
            true
        } catch(e:Exception) {
            val code=stableError(e)
            val state=if(code in WAITING_FOR_INPUT_CODES) JobStage.WAITING_FOR_INPUT else JobStage.FAILED
            save(JobProgress(state,snapshot.progress.completedUnits,snapshot.progress.totalUnits),snapshot.outcomes+ItemOutcome("job",ItemStatus.FAILED,errorCode=code))
            true
        } finally {
            withContext(NonCancellable+Dispatchers.IO) {
                heartbeat?.cancelAndJoin()
                try {
                    val current=dao.job(jobId)
                    if(current?.leaseOwner==owner) {
                        outputs.discardIncomplete(jobId)
                        if(current.stage in CLEANABLE_TERMINAL_STAGES) cleanupTerminalArtifacts(jobId,snapshot.request)
                    }
                } finally { dao.release(jobId,owner) }
            }
        }
    }
    private companion object {
        val CLEANABLE_TERMINAL_STAGES = setOf("COMPLETED","CANCELLED")
        val NON_EXECUTABLE_STAGES = setOf("COMPLETED","PARTIAL","FAILED","CANCELLED","PAUSED","WAITING_FOR_INPUT")
        val STOPPABLE_FAILED_STAGES = setOf(JobStage.FAILED,JobStage.PARTIAL,JobStage.WAITING_FOR_INPUT)
        val RETRY_WAIT_STAGES = setOf(JobStage.PAUSED.name,JobStage.INTERRUPTED.name,JobStage.WAITING_FOR_INPUT.name)
        val SECRET_HANDLE_KEYS = listOf("urlSecretHandle","secretHandle","tokenSecretHandle")
        val WAITING_FOR_INPUT_CODES = setOf("ACCESS_LOST","SECRET_EXPIRED","LOGIN_REQUIRED","DRIVE_TOKEN_MISSING","DRIVE_TOKEN_EXPIRED","WIFI_REQUIRED","UNMETERED_NETWORK_REQUIRED","DOWNLOAD_RESTART_REQUIRED","MEDIA_REANALYSIS_REQUIRED","NETWORK_UNAVAILABLE","CLEARTEXT_CONSENT_REQUIRED")
    }
}
