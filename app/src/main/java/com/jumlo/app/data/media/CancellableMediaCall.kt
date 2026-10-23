package com.jumlo.app.data.media

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val cancellationWatcher = ScheduledThreadPoolExecutor(1) { runnable ->
    Thread(runnable,"media-http-cancellation").apply { isDaemon=true }
}.apply { removeOnCancelPolicy=true }

/** Keep cancellation effective until the response body is consumed or closed. */
internal fun OkHttpClient.executeCancellable(
    request: Request,
    check: () -> Unit,
    timeoutMillis: Long? = null
): Response {
    require(timeoutMillis == null || timeoutMillis > 0)
    check()
    val call = newCall(request)
    val result = CompletableFuture<Response>()
    val cancellation = AtomicReference<Throwable?>(null)
    val closed = AtomicBoolean(false)
    val started = System.nanoTime()
    // A blocked socket read cannot run check() itself. Cancelling the actual call
    // unblocks DNS/connect/header/body I/O while retaining its original cause.
    val watcher = cancellationWatcher.scheduleWithFixedDelay({
        if (!closed.get() && cancellation.get()==null) {
            try { check() }
            catch (error: Throwable) {
                if (cancellation.compareAndSet(null,error)) {
                    call.cancel()
                    result.completeExceptionally(error)
                }
            }
        }
    },100,100,TimeUnit.MILLISECONDS)
    fun stopWatching() { closed.set(true);watcher.cancel(false) }
    fun checkActive() { cancellation.get()?.let { throw it };check() }
    try {
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { result.completeExceptionally(cancellation.get() ?: e) }
            override fun onResponse(call: Call, response: Response) {
                if (!result.complete(response)) response.close()
            }
        })
        while (true) {
            checkActive()
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            if (timeoutMillis != null && elapsed >= timeoutMillis)
                throw SocketTimeoutException("MEDIA_SERVICE_PREPARATION_TIMEOUT")
            val wait = timeoutMillis?.minus(elapsed)?.coerceIn(1, 100) ?: 100L
            try {
                val response = result.get(wait, TimeUnit.MILLISECONDS)
                checkActive()
                val body=response.body
                if(body==null) { stopWatching();return response }
                val source=object:ForwardingSource(body.source()) {
                    override fun read(sink:Buffer,byteCount:Long):Long {
                        try {
                            checkActive()
                            val count=super.read(sink,byteCount)
                            checkActive()
                            if(count<0) stopWatching()
                            return count
                        } catch(error:Throwable) {
                            stopWatching()
                            call.cancel()
                            throw cancellation.get() ?: error
                        }
                    }
                    override fun close() {
                        stopWatching()
                        super.close()
                    }
                }.buffer()
                return response.newBuilder().body(object:ResponseBody() {
                    override fun contentType()=body.contentType()
                    override fun contentLength()=body.contentLength()
                    override fun source()=source
                }).build()
            } catch (_: TimeoutException) {
                // Recheck the job/network policy while the server prepares the file.
            }
        }
    } catch (error: Throwable) {
        stopWatching()
        call.cancel()
        if (!result.cancel(false)) runCatching { result.getNow(null)?.close() }
        if (error is InterruptedException) Thread.currentThread().interrupt()
        throw cancellation.get() ?: if (error is ExecutionException) error.cause ?: error else error
    }
}
