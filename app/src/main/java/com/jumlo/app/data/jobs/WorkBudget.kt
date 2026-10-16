package com.jumlo.app.data.jobs

import java.io.IOException
import javax.inject.Inject

data class WorkBudget(
    val maxBitmapBytes:Long=minOf(Runtime.getRuntime().maxMemory()/3,128L*1024*1024),
    val maxTemporaryBytes:Long=512L*1024*1024,
    val maxMediaWorkspaceBytes:Long=8L*1024*1024*1024,
    val maxExpandedArchiveBytes:Long=2L*1024*1024*1024,
    val maxArchiveEntries:Int=10000,
    val maxOutputFiles:Int=1000,
    val minFreeBytes:Long=32L*1024*1024
)
class OperationFailure(val code:String):IOException(code)
fun stableError(t:Throwable):String = when(t) {
    is OperationFailure -> t.code
    is SecurityException -> "ACCESS_LOST"
    is java.io.FileNotFoundException -> "ACCESS_LOST"
    is com.jumlo.app.core.RestartRequired -> "DOWNLOAD_RESTART_REQUIRED"
    is OutOfMemoryError -> "BITMAP_BUDGET_EXCEEDED"
    else -> t.message?.takeIf { Regex("[A-Z][A-Z_0-9]{2,80}").matches(it) } ?: "PROCESSING_FAILED"
}
