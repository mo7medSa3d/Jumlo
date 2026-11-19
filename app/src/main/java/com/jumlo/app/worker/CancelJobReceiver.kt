package com.jumlo.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jumlo.app.data.jobs.JobStore
import com.jumlo.app.diagnostics.AppDiagnostics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** Non-exported: cancellation is persisted before WorkManager is stopped, preventing recovery restarts. */
@AndroidEntryPoint
class CancelJobReceiver:BroadcastReceiver() {
    @Inject lateinit var jobs:JobStore
    override fun onReceive(context:Context,intent:Intent) {
        if(intent.action!="${context.packageName}.CANCEL_JOB") return
        val jobId=intent.getStringExtra("job_id")?.takeIf {
            runCatching { java.util.UUID.fromString(it).toString()==it }.getOrDefault(false)
        } ?: return
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { withTimeout(8_000L) { jobs.cancel(jobId) } }
            catch(timeout:TimeoutCancellationException) { AppDiagnostics.report("worker_cancel",timeout); android.util.Log.w("JobCancellation","Cancellation timed out") }
            catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
            catch(error:Exception) { AppDiagnostics.report("worker_cancel",error); android.util.Log.w("JobCancellation","Cancellation could not be persisted") }
            finally { pending.finish() }
        }
    }
}
