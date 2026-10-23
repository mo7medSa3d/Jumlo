package com.jumlo.app.worker

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProcessingNotificationTest {
    @Test fun foregroundNoticeIsPrivateAndHasCancelAction() {
        val app=ApplicationProvider.getApplicationContext<Context>()
        val info=ProcessingNotification.create(app,UUID.randomUUID().toString())
        val notification=info.notification
        assertEquals(Notification.VISIBILITY_PRIVATE,notification.visibility)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.actions.isNotEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,info.foregroundServiceType)
        } else {
            assertEquals(0,info.foregroundServiceType)
        }
        assertFalse(notification.extras.toString().contains("https://"))
    }
}
