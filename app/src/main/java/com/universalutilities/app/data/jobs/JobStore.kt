package com.universalutilities.app.data.jobs

import android.content.Context
import androidx.work.*
import com.universalutilities.app.data.local.*
import com.universalutilities.app.data.output.OutputStore
import com.universalutilities.app.data.source.SourceStore
import com.universalutilities.app.model.*
import com.universalutilities.app.worker.EngineOperationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class JobStore @Inject constructor(@ApplicationContext private val app:Context,private val db:AppDatabase,private val sources:SourceStore,private val outputs:OutputStore,private val budget:WorkBudget) {
    private val dao get()=db.processingDao()
    private val work get()=WorkManager.getInstance(app)
    val snapshots=dao.jobs().map { rows -> rows.map { Json.decodeFromString<JobSnapshot>(it.snapshotJson) } }
    fun observe(jobId:String):Flow<JobSnapshot> = dao.observeJob(jobId).filterNotNull().map { Json.decodeFromString(it.snapshotJson) }
    suspend fun recoverPending() {
        dao.jobs().first().filter { it.stage in setOf("QUEUED","VALIDATING","RUNNING","FINALIZING","INTERRUPTED") }.forEach { row -> schedule(Json.decodeFromString(row.requestJson)) }
    }
    suspend fun enqueue(request:JobRequest):String {
        require(UUID.fromString(request.id).toString()==request.id && request.sourceIds.size<=1000 && request.settingsJson.length<=100000) { "INVALID_JOB_REQUEST" }
        require(request.schemaVersion==1) { "UNSUPPORTED_SETTINGS_VERSION" }
        val snapshot=JobSnapshot(request,JobProgress(JobStage.QUEUED,0,null),emptyList(),System.currentTimeMillis())
        val encoded=Json.encodeToString(request)
        dao.insertJob(JobEntity(request.id,encoded,Json.encodeToString(snapshot),JobStage.QUEUED.name))
        check(dao.job(request.id)?.requestJson==encoded) { "JOB_IDENTITY_CONFLICT" }
        schedule(request)
        return request.id
    }
    private fun schedule(request:JobRequest,policy:ExistingWorkPolicy=ExistingWorkPolicy.KEEP) {
        val builder=OneTimeWorkRequestBuilder<EngineOperationWorker>().setInputData(workDataOf("job_id" to request.id)).addTag("processing")
        if(request.toolId.startsWith("dl_")) {
            val settings=Json.decodeFromString<com.universalutilities.app.data.processing.DownloadSettings>(request.settingsJson)
            builder.setConstraints(Constraints.Builder().setRequiredNetworkType(if(settings.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
        }
        work.enqueueUniqueWork("job:${request.id}",policy,builder.build())
    }
    suspend fun cancel(jobId:String) {
        val row=dao.job(jobId) ?: return
        val old=Json.decodeFromString<JobSnapshot>(row.snapshotJson)
        if(old.progress.stage in setOf(JobStage.COMPLETED,JobStage.CANCELLED)) return
        val cancelled=old.copy(progress=old.progress.copy(stage=JobStage.CANCELLED),updatedAtEpochMs=System.currentTimeMillis())
        dao.setStage(jobId,JobStage.CANCELLED.name,Json.encodeToString(cancelled))
        work.cancelUniqueWork("job:$jobId")
    }
    suspend fun retryFailed(jobId:String):String {
        val row=dao.job(jobId) ?: throw OperationFailure("JOB_NOT_FOUND")
        val old=Json.decodeFromString<JobSnapshot>(row.snapshotJson)
        // Same logical job/output keys: successfully published members are not reproduced.
        val next=old.copy(progress=JobProgress(JobStage.QUEUED,0,null),updatedAtEpochMs=System.currentTimeMillis())
        if(dao.retry(jobId,Json.encodeToString(next))!=1) throw OperationFailure("JOB_NOT_RETRYABLE")
        schedule(old.request,ExistingWorkPolicy.REPLACE)
        return jobId
    }
    suspend fun recordInfrastructureFailure(jobId:String) {
        val row=dao.job(jobId) ?: return
        val snapshot=Json.decodeFromString<JobSnapshot>(row.snapshotJson)
        val failed=snapshot.copy(progress=snapshot.progress.copy(stage=JobStage.FAILED),
            outcomes=snapshot.outcomes+ItemOutcome("worker",ItemStatus.FAILED,errorCode="WORKER_START_FAILED"),
            updatedAtEpochMs=System.currentTimeMillis())
        dao.failBeforeClaim(jobId,Json.encodeToString(failed))
    }
    /** Returns false only when another worker still holds the durable lease. */
    suspend fun execute(jobId:String,dispatcher:OperationDispatcher):Boolean=coroutineScope {
        val row=dao.job(jobId) ?: throw OperationFailure("JOB_NOT_FOUND")
        if(row.stage in listOf("COMPLETED","PARTIAL","FAILED","CANCELLED","WAITING_FOR_INPUT")) return@coroutineScope true
        val owner=UUID.randomUUID().toString(); val now=System.currentTimeMillis()
        if(dao.claim(jobId,owner,now,now+60000)!=1) return@coroutineScope false
        var snapshot=Json.decodeFromString<JobSnapshot>(row.snapshotJson)
        val leased=AtomicBoolean(true); val runningContext=currentCoroutineContext()
        suspend fun save(progress:JobProgress,outcomes:List<ItemOutcome> = snapshot.outcomes) {
            val next=snapshot.copy(progress=progress,outcomes=outcomes,updatedAtEpochMs=System.currentTimeMillis())
            if(dao.updateOwned(jobId,owner,Json.encodeToString(next),progress.stage.name)!=1) throw CancellationException("JOB_LEASE_LOST")
            snapshot=next
        }
        val heartbeat=launch(Dispatchers.IO) {
            while(isActive) {
                delay(15000)
                if(dao.heartbeat(jobId,owner,System.currentTimeMillis()+60000)!=1) { leased.set(false); break }
            }
        }
        try {
            outputs.reconcile()
            save(JobProgress(JobStage.VALIDATING,0,null))
            val ctx=ExecutionContext(sources,outputs,budget,{ save(it) },{
                runningContext.ensureActive()
                if(!leased.get()) throw CancellationException("JOB_LEASE_LOST")
            })
            save(JobProgress(JobStage.RUNNING,0,null))
            val outcomes=dispatcher.execute(snapshot.request,ctx)
            ctx.checkCancellation()
            val failures=outcomes.count { it.status==ItemStatus.FAILED }
            val successes=outcomes.count { it.outputId!=null }
            val state=when { outcomes.isEmpty() -> JobStage.FAILED; failures==0 -> JobStage.COMPLETED; successes>0 -> JobStage.PARTIAL; else -> JobStage.FAILED }
            save(snapshot.progress.copy(stage=state),outcomes)
            true
        } catch(cancel:CancellationException) {
            withContext(NonCancellable+Dispatchers.IO) {
                val current=dao.job(jobId)
                if(current?.stage!="CANCELLED") {
                    val interrupted=snapshot.copy(progress=snapshot.progress.copy(stage=JobStage.INTERRUPTED),updatedAtEpochMs=System.currentTimeMillis())
                    dao.updateOwned(jobId,owner,Json.encodeToString(interrupted),JobStage.INTERRUPTED.name)
                }
            }
            throw cancel
        } catch(oom:OutOfMemoryError) {
            save(snapshot.progress.copy(stage=JobStage.FAILED),snapshot.outcomes+ItemOutcome("job",ItemStatus.FAILED,errorCode="BITMAP_BUDGET_EXCEEDED"))
            true
        } catch(e:Exception) {
            val code=stableError(e)
            val state=if(code in setOf("ACCESS_LOST","SECRET_EXPIRED","WIFI_REQUIRED","UNMETERED_NETWORK_REQUIRED","DOWNLOAD_RESTART_REQUIRED","MEDIA_REANALYSIS_REQUIRED","NETWORK_UNAVAILABLE","CLEARTEXT_CONSENT_REQUIRED")) JobStage.WAITING_FOR_INPUT else JobStage.FAILED
            save(JobProgress(state,snapshot.progress.completedUnits,snapshot.progress.totalUnits),snapshot.outcomes+ItemOutcome("job",ItemStatus.FAILED,errorCode=code))
            true
        } finally {
            withContext(NonCancellable+Dispatchers.IO) {
                heartbeat.cancelAndJoin()
                try { outputs.discardIncomplete(jobId) } finally { dao.release(jobId,owner) }
            }
        }
    }
}
