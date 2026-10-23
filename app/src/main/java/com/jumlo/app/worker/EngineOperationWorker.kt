package com.jumlo.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jumlo.app.data.jobs.JobStore
import com.jumlo.app.data.jobs.OperationDispatcher
import com.jumlo.app.diagnostics.AppDiagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker class EngineOperationWorker @AssistedInject constructor(
    @Assisted context:Context,@Assisted params:WorkerParameters,
    private val jobs:JobStore,private val dispatcher:OperationDispatcher
):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val id=inputData.getString("job_id") ?: return Result.failure()
        return try {
            // WorkManager owns the foreground service lifecycle and OS stop handling.
            // Do not start a long transfer if the OS denied foreground promotion.
            // The failure is recorded below; cancellation must propagate to WorkManager.
            setForeground(ProcessingNotification.create(applicationContext,id))
            if(jobs.execute(id,dispatcher)) Result.success() else Result.retry()
        }
        catch(cancel:CancellationException) { throw cancel }
        catch(error:Exception) {
            AppDiagnostics.report("worker_start",error)
            try { jobs.recordInfrastructureFailure(id) }
            catch(cancel:CancellationException) { throw cancel }
            catch(persistenceError:Exception) { AppDiagnostics.report("worker_failure_record",persistenceError) }
            Result.failure()
        }
    }
}
