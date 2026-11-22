package com.jumlo.app.worker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActiveTaskTrackerTest {
    @Before fun resetBefore() {
        ActiveTaskTracker.setAppForeground(false)
        ActiveTaskTracker.updateNavigation("")
    }

    @After fun resetAfter() {
        ActiveTaskTracker.setAppForeground(false)
        ActiveTaskTracker.updateNavigation("")
    }

    @Test fun backgroundAppAlwaysNotifiesCompletion() {
        ActiveTaskTracker.setAppForeground(false)
        ActiveTaskTracker.updateNavigation("workspace", jobId = "job-1", toolId = "pdf_03")
        assertTrue(ActiveTaskTracker.shouldNotifyCompletion("job-1", "pdf_03"))
        assertTrue(ActiveTaskTracker.shouldNotifyCompletion("job-2", "img_04"))
    }

    @Test fun foregroundDownloadsAndMatchingActivitySuppressAlerts() {
        ActiveTaskTracker.setAppForeground(true)

        ActiveTaskTracker.updateNavigation("downloads")
        assertFalse(ActiveTaskTracker.shouldNotifyCompletion("job-1", "dl_media"))

        ActiveTaskTracker.updateNavigation("activity", jobId = null)
        assertFalse(ActiveTaskTracker.shouldNotifyCompletion("job-1", "pdf_03"))

        ActiveTaskTracker.updateNavigation("activity", jobId = "job-1")
        assertFalse(ActiveTaskTracker.shouldNotifyCompletion("job-1", "pdf_03"))
        assertTrue(ActiveTaskTracker.shouldNotifyCompletion("job-2", "pdf_03"))
    }

    @Test fun foregroundWorkspaceSuppressesOnlyMatchingJobOrTool() {
        ActiveTaskTracker.setAppForeground(true)
        ActiveTaskTracker.updateNavigation("workspace", jobId = "job-1", toolId = "pdf_compress")

        assertFalse(ActiveTaskTracker.shouldNotifyCompletion("job-1", "other_tool"))
        assertFalse(ActiveTaskTracker.shouldNotifyCompletion("job-2", "pdf_compress"))
        assertTrue(ActiveTaskTracker.shouldNotifyCompletion("job-2", "file_07"))
        assertTrue(ActiveTaskTracker.shouldNotifyCompletion("job-2", null))
    }

    @Test fun leavingWorkspaceOrActivityClearsTrackedJobAndToolIds() {
        ActiveTaskTracker.setAppForeground(true)
        ActiveTaskTracker.updateNavigation("workspace", jobId = "job-1", toolId = "pdf_03")
        assertEquals("job-1", ActiveTaskTracker.activeJobId)
        assertEquals("pdf_03", ActiveTaskTracker.activeToolId)

        ActiveTaskTracker.updateNavigation("home", jobId = "job-1", toolId = "pdf_03")
        assertEquals("home", ActiveTaskTracker.currentRoute)
        assertNull(ActiveTaskTracker.activeJobId)
        assertNull(ActiveTaskTracker.activeToolId)
        assertTrue(ActiveTaskTracker.shouldNotifyCompletion("job-1", "pdf_03"))
    }
}
