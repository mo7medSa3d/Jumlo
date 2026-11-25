package com.jumlo.app.worker

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jumlo.app.R
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.data.jobs.JobStore
import com.jumlo.app.data.jobs.OperationDispatcher
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.diagnostics.AppDiagnostics
import com.jumlo.app.model.ItemStatus
import com.jumlo.app.model.JobStage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@HiltWorker
class EngineOperationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val jobs: JobStore,
    private val dispatcher: OperationDispatcher
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val startedAtMs = SystemClock.elapsedRealtime()
        fun elapsedMs(): Long = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        val id = inputData.getString("job_id")
        if (id == null) {
            AppDiagnostics.reportFailure("worker_start", "MISSING_JOB_ID", elapsedMs())
            return Result.failure()
        }
        val initialToolTitle = runCatching { jobs.snapshot(id)?.request?.toolId }.getOrNull()?.let(::localizedToolTitle)
        return try {
            // WorkManager owns the foreground service lifecycle and OS stop handling.
            // On API 31-36, background retries or FGS quota limits can throw
            // ForegroundServiceStartNotAllowedException (a subclass of IllegalStateException).
            // Degrade to normal WorkManager execution instead of failing the job.
            try {
                setForeground(ProcessingNotification.create(applicationContext, id, title = initialToolTitle))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: IllegalStateException) {
                AppDiagnostics.reportFailure("worker_foreground_degraded", "FOREGROUND_SERVICE_DEGRADED", elapsedMs(), error)
            } catch (error: SecurityException) {
                AppDiagnostics.reportFailure("worker_foreground_degraded", "FOREGROUND_SERVICE_DEGRADED", elapsedMs(), error)
            }

            if (isStopped) throw CancellationException("WORKER_STOPPED")

            val ok = coroutineScope {
                val progressJob = launch {
                    var lastPercent: Int? = Int.MIN_VALUE
                    var lastPostedAtMs = 0L
                    jobs.observe(id).collect { snap ->
                        if (isStopped) return@collect
                        val p = snap.progress
                        if (p.stage == JobStage.COMPLETED || p.stage == JobStage.FAILED || p.stage == JobStage.CANCELLED) {
                            return@collect
                        }
                        val pct = if (p.completedUnits > 0L && p.totalUnits != null && p.totalUnits > 0L) {
                            ((p.completedUnits * 100L) / p.totalUnits).toInt().takeIf { it >= 2 }?.coerceAtMost(100)
                        } else {
                            null
                        }
                        val now = SystemClock.elapsedRealtime()
                        if (pct != lastPercent && (now - lastPostedAtMs >= 350L || pct == 100 || lastPercent == Int.MIN_VALUE)) {
                            lastPercent = pct
                            lastPostedAtMs = now
                            val toolTitle = localizedToolTitle(snap.request.toolId) ?: initialToolTitle
                            ProcessingNotification.updateProgress(applicationContext, id, pct, title = toolTitle)
                        }
                    }
                }
                try {
                    jobs.execute(id, dispatcher)
                } finally {
                    progressJob.cancel()
                }
            }

            if (ok) {
                runCatching {
                    val snap = jobs.snapshot(id)
                    val toolId = snap?.request?.toolId
                    if (!ActiveTaskTracker.shouldNotifyCompletion(id, toolId)) return@runCatching
                    when (snap?.progress?.stage) {
                        JobStage.COMPLETED -> {
                            val created = snap.outcomes.filter { it.status == ItemStatus.CREATED }
                            val firstId = created.firstOrNull()?.outputId
                            val firstOut = firstId?.let { jobs.outputRecord(it) }
                            val displayName = firstOut?.displayName?.takeIf { it.isNotBlank() }
                            val toolTitle = localizedToolTitle(toolId)
                            val title = toolTitle?.let {
                                applicationContext.getString(R.string.completed_notification_tool_title, it)
                            } ?: applicationContext.getString(R.string.completed_notification_title)
                            val summary = when {
                                created.size > 1 -> applicationContext.getString(R.string.completed_notification_multi, created.size)
                                displayName != null -> applicationContext.getString(R.string.completed_notification_single, displayName)
                                else -> applicationContext.getString(R.string.completed_notification_single_generic)
                            }
                            ProcessingNotification.postCompleted(
                                applicationContext, id, title, summary,
                                shareUri = if (created.size == 1) firstOut?.uri else null,
                                shareMime = if (created.size == 1) firstOut?.mimeType else null,
                                toolId = toolId
                            )
                        }
                        JobStage.FAILED -> postFailureNotification(id, toolId)
                        else -> Unit
                    }
                }
                Result.success()
            } else Result.retry()
        } catch (cancel: CancellationException) {
            if (isStopped) {
                val stopCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && stopReason >= 0) "WORKER_STOPPED_$stopReason" else "WORKER_STOPPED"
                AppDiagnostics.reportFailure("worker_stopped", stopCode, elapsedMs())
            }
            throw cancel
        } catch (error: Exception) {
            AppDiagnostics.reportFailure("worker_start", stableError(error), elapsedMs(), error)
            try {
                jobs.recordInfrastructureFailure(id)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (persistenceError: Exception) {
                AppDiagnostics.reportFailure("worker_failure_record", stableError(persistenceError), elapsedMs(), persistenceError)
            }
            runCatching {
                val toolId = jobs.snapshot(id)?.request?.toolId
                if (ActiveTaskTracker.shouldNotifyCompletion(id, toolId)) {
                    postFailureNotification(id, toolId)
                }
            }
            Result.failure()
        } finally {
            ProcessingNotification.cancelProgress(applicationContext, id)
        }
    }

    private fun localizedToolTitle(toolId: String?): String? {
        if (toolId.isNullOrBlank()) return null
        val tool = CapabilityRegistry.allTools.firstOrNull { it.id == toolId } ?: return null
        val language = AppCompatDelegate.getApplicationLocales().get(0)?.language
            ?: applicationContext.resources.configuration.locales.get(0)?.language
        val isArabic = language == "ar"
        return if (isArabic) tool.titleAr else tool.titleEn
    }

    private fun postFailureNotification(jobId: String, toolId: String?) {
        val toolTitle = localizedToolTitle(toolId)
        val title = toolTitle?.let {
            applicationContext.getString(R.string.failed_notification_tool_title, it)
        } ?: applicationContext.getString(R.string.failed_notification_title)
        val body = applicationContext.getString(R.string.failed_notification_body)
        ProcessingNotification.postCompleted(applicationContext, jobId, title, body, toolId = toolId)
    }
}
