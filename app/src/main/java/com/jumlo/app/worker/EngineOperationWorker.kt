package com.jumlo.app.worker

import android.content.Context
import android.os.SystemClock
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jumlo.app.data.jobs.JobStore
import com.jumlo.app.data.jobs.OperationDispatcher
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.diagnostics.AppDiagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker class EngineOperationWorker @AssistedInject constructor(
    @Assisted context:Context,@Assisted params:WorkerParameters,
    private val jobs:JobStore,private val dispatcher:OperationDispatcher
):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val startedAtMs=SystemClock.elapsedRealtime()
        fun elapsedMs():Long=(SystemClock.elapsedRealtime()-startedAtMs).coerceAtLeast(0L)
        val id=inputData.getString("job_id")
        if(id==null) {
            AppDiagnostics.reportFailure("worker_start","MISSING_JOB_ID",elapsedMs())
            return Result.failure()
        }
        return try {
            // WorkManager owns the foreground service lifecycle and OS stop handling.
            // On API 31-36, background retries or FGS quota limits can throw
            // ForegroundServiceStartNotAllowedException (a subclass of IllegalStateException).
            // Degrade to normal WorkManager execution instead of failing the job.
            try { setForeground(ProcessingNotification.create(applicationContext,id)) }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:IllegalStateException) {
                AppDiagnostics.reportFailure("worker_foreground_degraded","FOREGROUND_SERVICE_DEGRADED",elapsedMs(),error)
            }
            catch(error:SecurityException) {
                AppDiagnostics.reportFailure("worker_foreground_degraded","FOREGROUND_SERVICE_DEGRADED",elapsedMs(),error)
            }
            if(jobs.execute(id,dispatcher)) Result.success() else Result.retry()
        }
        catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) {
            AppDiagnostics.reportFailure("worker_start",stableError(error),elapsedMs(),error)
            try { jobs.recordInfrastructureFailure(id) }
            catch(cancel:CancellationException) { throw cancel }
            catch(persistenceError:Exception) {
                AppDiagnostics.reportFailure("worker_failure_record",stableError(persistenceError),elapsedMs(),persistenceError)
            }
            Result.failure()
        }
    }
}
