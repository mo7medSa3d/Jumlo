package com.jumlo.app.ui.files

import java.time.Instant
import java.time.ZoneId

/** Calendar-day grouping uses local days, not a 24-hour duration (DST-safe). */
enum class FileDateGroup { TODAY, YESTERDAY, EARLIER }

fun fileDateGroup(epochMillis: Long, nowEpochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): FileDateGroup {
    val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    return when (day) {
        today -> FileDateGroup.TODAY
        today.minusDays(1) -> FileDateGroup.YESTERDAY
        else -> FileDateGroup.EARLIER
    }
}
