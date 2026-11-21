package com.jumlo.app.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.jumlo.app.MainActivity
import com.jumlo.app.R
import java.util.UUID

/** Only opaque IDs are attached; source names, signed URLs and secrets stay out of notifications. */
object ProcessingNotification {
    fun create(context:Context,jobId:String):ForegroundInfo {
        require(UUID.fromString(jobId).toString()==jobId) { "INVALID_JOB_ID" }
        val channel="file_processing"
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(channel,context.getString(R.string.processing_channel),NotificationManager.IMPORTANCE_LOW)
        )
        val cancel=PendingIntent.getBroadcast(context,0,
            Intent(context,CancelJobReceiver::class.java).setAction("${context.packageName}.CANCEL_JOB")
                .setData(Uri.parse("utilities-job:$jobId")).putExtra("job_id",jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(context,0,
            Intent(context,MainActivity::class.java)
                .setAction("${context.packageName}.OPEN_JOB")
                .setData(Uri.parse("jumlo-job:$jobId"))
                .putExtra("job_id",jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,channel)
            .setContentTitle(context.getString(R.string.processing_title))
            .setContentText(context.getString(R.string.processing_body))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setProgress(0,0,true)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .addAction(android.R.drawable.ic_delete,context.getString(R.string.processing_cancel),cancel)
            .build()
        val notificationId=jobId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(1)
        return if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId,notification)
        }
    }

    fun postCompleted(context: Context, jobId: String, title: String, summary: String, shareUri: String? = null, shareMime: String? = null) {
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = "file_completed"
        nm.createNotificationChannel(
            NotificationChannel(channel, context.getString(R.string.completed_channel), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context, jobId.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction("${context.packageName}.OPEN_JOB")
                .setData(Uri.parse("jumlo-job:$jobId"))
                .putExtra("job_id", jobId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(summary)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_STATUS)
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
}
