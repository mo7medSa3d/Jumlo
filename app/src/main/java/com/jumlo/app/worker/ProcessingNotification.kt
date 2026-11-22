package com.jumlo.app.worker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.jumlo.app.MainActivity
import com.jumlo.app.R
import java.util.UUID

/** Only opaque IDs are attached; source names, signed URLs and secrets stay out of notifications. */
object ProcessingNotification {
    private const val CHANNEL_PROCESSING = "file_processing"
    private const val CHANNEL_COMPLETED = "file_completed"

    @Volatile
    private var cachedLargeIcon: Bitmap? = null

    fun progressNotificationId(jobId: String): Int =
        jobId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)

    fun create(
        context: Context,
        jobId: String,
        percent: Int? = null,
        title: String? = null
    ): ForegroundInfo {
        require(UUID.fromString(jobId).toString() == jobId) { "INVALID_JOB_ID" }
        ensureChannel(context, CHANNEL_PROCESSING, R.string.processing_channel, NotificationManager.IMPORTANCE_LOW)
        val notification = buildProgressNotification(context, jobId, percent, title)
        val notificationId = progressNotificationId(jobId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    fun updateProgress(context: Context, jobId: String, percent: Int?, title: String? = null) {
        if (!canPostNotifications(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, CHANNEL_PROCESSING, R.string.processing_channel, NotificationManager.IMPORTANCE_LOW)
        runCatching { nm.notify(progressNotificationId(jobId), buildProgressNotification(context, jobId, percent, title)) }
    }

    fun cancelProgress(context: Context, jobId: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(progressNotificationId(jobId)) }
    }

    fun postCompleted(
        context: Context,
        jobId: String,
        title: String,
        summary: String,
        shareUri: String? = null,
        shareMime: String? = null,
        toolId: String? = null
    ) {
        // Suppress redundant completion/failure banner only when user is already watching this task.
        if (!ActiveTaskTracker.shouldNotifyCompletion(jobId, toolId)) return
        if (!canPostNotifications(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, CHANNEL_COMPLETED, R.string.completed_channel, NotificationManager.IMPORTANCE_DEFAULT)
        val open = openJobPendingIntent(context, jobId, jobId.hashCode())
        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setContentTitle(title)
            .setContentText(summary)
            .setSmallIcon(R.drawable.ic_stat_jumlo)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_STATUS)
        largeIcon(context)?.let { builder.setLargeIcon(it) }
        if (!shareUri.isNullOrBlank() && shareUri.startsWith("content://")) {
            val parsedUri = Uri.parse(shareUri)
            val sendIntent = Intent(Intent.ACTION_SEND)
                .setType(shareMime ?: "*/*")
                .putExtra(Intent.EXTRA_STREAM, parsedUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.action_share))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val sharePending = PendingIntent.getActivity(
                context, jobId.hashCode() + 1, shareIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, context.getString(R.string.action_share), sharePending)
        }
        val nid = (jobId.hashCode().and(Int.MAX_VALUE) xor 0x5A5A).coerceAtLeast(2)
        runCatching { nm.notify(nid, builder.build()) }
    }

    private fun buildProgressNotification(
        context: Context,
        jobId: String,
        percent: Int?,
        title: String? = null
    ): Notification {
        val requestCode = progressNotificationId(jobId)
        val cancel = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, CancelJobReceiver::class.java)
                .setAction("${context.packageName}.CANCEL_JOB")
                .setData(Uri.parse("utilities-job:$jobId"))
                .putExtra("job_id", jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = openJobPendingIntent(context, jobId, requestCode)
        // Treat 0% and noisy initial 1% as indeterminate and always include a status label rather than bare "%d%%".
        val clamped = percent?.takeIf { it >= 2 }?.coerceIn(2, 100)
        val contentText = if (clamped != null) {
            context.getString(R.string.processing_working_percent, clamped)
        } else {
            context.getString(R.string.processing_working)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROCESSING)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.processing_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_jumlo)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.processing_cancel), cancel)
        if (clamped != null) {
            builder.setProgress(100, clamped, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        largeIcon(context)?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openJobPendingIntent(context: Context, jobId: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java)
                .setAction("${context.packageName}.OPEN_JOB")
                .setData(Uri.parse("jumlo-job:$jobId"))
                .putExtra("job_id", jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun ensureChannel(context: Context, id: String, nameRes: Int, importance: Int) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(id, context.getString(nameRes), importance)
        )
    }

    // Adaptive launcher icons are XML drawables, so render to a cached Bitmap instead of BitmapFactory.
    private fun largeIcon(context: Context): Bitmap? {
        cachedLargeIcon?.let { return it }
        return runCatching {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
            val width = runCatching {
                context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            }.getOrDefault(128).coerceAtLeast(64)
            val height = runCatching {
                context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
            }.getOrDefault(128).coerceAtLeast(64)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            cachedLargeIcon = bitmap
            bitmap
        }.getOrNull()
    }
}
