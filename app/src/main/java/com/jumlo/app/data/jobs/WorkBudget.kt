package com.jumlo.app.data.jobs

import com.jumlo.app.core.ResourceBudgetPolicy

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class WorkBudget(
    val maxBitmapBytes:Long=minOf(Runtime.getRuntime().maxMemory()/3,128L*1024*1024),
    val maxTemporaryBytes:Long=ResourceBudgetPolicy.maxImportBytes,
    val maxMediaWorkspaceBytes:Long=ResourceBudgetPolicy.maxMediaWorkspaceBytes,
    val maxExpandedArchiveBytes:Long=ResourceBudgetPolicy.maxExpandedArchiveBytes,
    val maxArchiveEntries:Int=ResourceBudgetPolicy.maxArchiveEntries,
    val maxOutputFiles:Int=ResourceBudgetPolicy.maxPublishedOutputsPerJob,
    val minFreeBytes:Long=ResourceBudgetPolicy.minFreeBytes
)
class OperationFailure(val code:String):IOException(code)
fun stableError(t:Throwable):String = when(t) {
    is OperationFailure -> t.code
    is SecurityException -> "ACCESS_LOST"
    is java.io.FileNotFoundException -> "ACCESS_LOST"
    is com.jumlo.app.core.RestartRequired -> "DOWNLOAD_RESTART_REQUIRED"
    is SocketTimeoutException -> "NETWORK_TIMEOUT"
    is UnknownHostException -> "NETWORK_UNAVAILABLE"
    is OutOfMemoryError -> "LOW_MEMORY"
    else -> t.message?.takeIf { Regex("[A-Z][A-Z_0-9]{2,80}").matches(it) } ?: "PROCESSING_FAILED"
}
