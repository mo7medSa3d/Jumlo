package com.universalutilities.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.universalutilities.app.data.jobs.JobStore
import com.universalutilities.app.data.jobs.OperationDispatcher
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
            setForeground(ProcessingNotification.create(applicationContext,id))
            if(jobs.execute(id,dispatcher)) Result.success() else Result.retry()
        }
        catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { jobs.recordInfrastructureFailure(id); Result.failure() }
    }
}
