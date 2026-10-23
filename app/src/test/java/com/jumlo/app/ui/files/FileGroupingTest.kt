package com.jumlo.app.ui.files

import com.jumlo.app.model.FileViewMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneId

class FileGroupingTest {
    private val zone = ZoneId.of("America/New_York")
    private fun time(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    @Test fun groupingUsesLocalCalendarDaysAcrossSpringDaylightSaving() {
        val now = time("2025-03-10T00:30:00-04:00[America/New_York]")
        assertEquals(FileDateGroup.YESTERDAY,
            fileDateGroup(time("2025-03-09T00:01:00-05:00[America/New_York]"), now, zone))
        // Less than 24 hours ago can still cross two calendar-day boundaries.
        assertEquals(FileDateGroup.EARLIER,
            fileDateGroup(time("2025-03-08T23:59:00-05:00[America/New_York]"), now, zone))
        assertEquals(FileDateGroup.TODAY,
            fileDateGroup(time("2025-03-10T00:00:00-04:00[America/New_York]"), now, zone))
    }

    @Test fun removedGalleryAndUnknownPreferenceMigrateToGrid() {
        assertEquals(FileViewMode.GRID, FileViewMode.fromStored("GALLERY"))
        assertEquals(FileViewMode.GRID, FileViewMode.fromStored("corrupt"))
        assertEquals(FileViewMode.GRID, FileViewMode.fromStored(null))
        assertEquals(FileViewMode.LIST, FileViewMode.fromStored("LIST"))
    }
}
