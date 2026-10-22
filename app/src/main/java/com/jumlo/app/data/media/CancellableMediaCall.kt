package com.jumlo.app.data.media

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Wait for headers without hiding cancellation inside a blocking socket read. */
internal fun OkHttpClient.executeCancellable(
    request: Request,
    check: () -> Unit,
    timeoutMillis: Long? = null
): Response {
    require(timeoutMillis == null || timeoutMillis > 0)
    check()
    val call = newCall(request)
    val result = CompletableFuture<Response>()
    val started = System.nanoTime()
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { result.completeExceptionally(e) }
        override fun onResponse(call: Call, response: Response) {
            // Cancellation may win the race with headers arriving on the IO thread.
            if (!result.complete(response)) response.close()
        }
    })
    try {
        while (true) {
            check()
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            if (timeoutMillis != null && elapsed >= timeoutMillis)
                throw SocketTimeoutException("MEDIA_SERVICE_PREPARATION_TIMEOUT")
            val wait = timeoutMillis?.minus(elapsed)?.coerceIn(1, 100) ?: 100L
            try {
                val response = result.get(wait, TimeUnit.MILLISECONDS)
                check()
                return response
            } catch (_: TimeoutException) {
                // Recheck the job/network policy while the server prepares the file.
            }
        }
    } catch (error: Throwable) {
        call.cancel()
        if (!result.cancel(false)) runCatching { result.getNow(null)?.close() }
        if (error is InterruptedException) Thread.currentThread().interrupt()
        throw if (error is ExecutionException) error.cause ?: error else error
    }
}
