package com.jumlo.app.core.media

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Arguments are a vector, never a shell command. stdout is either bounded complete data or a bounded diagnostic tail. */
data class MediaProcessSpec(
    val command: List<String>, val directory: File, val environment: Map<String,String> = emptyMap(),
    val timeoutMillis: Long = 90000, val stdoutLimit: Int = 4 * 1024 * 1024, val tailOutput: Boolean = false
)
data class MediaProcessResult(val exitCode: Int, val stdout: String, val stderr: String)
class MediaProcessException(val code: String): IOException(code)

/**
 * The trusted Python bootstrap creates a POSIX session and emits its PID before any extractor output.
 * Android supplies Os.kill(-pid, signal), so cancellation also stops FFmpeg descendants in that session.
 * No PID/command is accepted from URL metadata or a shell-expanded string.
 */
class MediaProcessRunner(private val signalGroup: (Int, Boolean) -> Unit = { _, _ -> }) {
    fun run(spec: MediaProcessSpec, checkCancel: () -> Unit = {}, onLine: (String) -> Unit = {}): MediaProcessResult {
        require(spec.command.isNotEmpty() && spec.command.size <= 256 && spec.command.all { it.length <= 65536 && '\u0000' !in it }) { "INVALID_MEDIA_COMMAND" }
        require(spec.directory.isDirectory && spec.timeoutMillis > 0 && spec.stdoutLimit in 1..16*1024*1024) { "INVALID_MEDIA_PROCESS_LIMITS" }
        checkCancel()
        val pid = AtomicInteger(0)
        val failure = AtomicReference<Throwable?>(null)
        val out = BoundedCapture(spec.stdoutLimit, spec.tailOutput)
        val err = BoundedCapture(131072, true)
        val process = ProcessBuilder(spec.command).directory(spec.directory).apply { environment().putAll(spec.environment) }.start()
        process.outputStream.close() // Downloaders may not wait for hidden interactive prompts.
        val start = System.nanoTime()
        var finished = false
        fun reader(stream: InputStream, stdout: Boolean) = thread(start=true, isDaemon=true, name=if(stdout) "media-stdout" else "media-stderr") {
            try {
                val buf=ByteArray(8192)
                val handshake=ByteArrayOutputStream()
                val line=ByteArrayOutputStream()
                var gotPid=!stdout
                var oversizedLine=false
                stream.use { input ->
                    while(true) {
                        val count=input.read(buf)
                        if(count<0) break
                        var offset=0
                        if(!gotPid) {
                            while(offset<count) {
                                val b=buf[offset++].toInt() and 255
                                if(b==10) {
                                    val header=handshake.toString("US-ASCII").trimEnd('\r')
                                    val match=Regex("UU_PID:([1-9][0-9]{0,9})").matchEntire(header)
                                    val processId=match?.groupValues?.get(1)?.toIntOrNull()
                                    if(processId==null || processId<=1) throw MediaProcessException("MEDIA_PROCESS_HANDSHAKE")
                                    pid.set(processId); gotPid=true; break
                                }
                                if(handshake.size()>=64) throw MediaProcessException("MEDIA_PROCESS_HANDSHAKE")
                                handshake.write(b)
                            }
                        }
                        if(offset<count) {
                            val capture=if(stdout) out else err
                            capture.add(buf,offset,count-offset)
                            if(stdout) for(i in offset until count) {
                                val b=buf[i].toInt() and 255
                                if(b==10 || b==13) {
                                    if(!oversizedLine && line.size()>0) onLine(line.toString("UTF-8"))
                                    line.reset(); oversizedLine=false
                                } else if(!oversizedLine) {
                                    if(line.size()<32768) line.write(b) else { line.reset(); oversizedLine=true }
                                }
                            }
                        }
                    }
                }
                if(!gotPid) throw MediaProcessException("MEDIA_PROCESS_HANDSHAKE")
                if(stdout && !oversizedLine && line.size()>0) onLine(line.toString("UTF-8"))
            } catch(t: Throwable) { failure.compareAndSet(null,t) }
        }
        val stdout=reader(process.inputStream,true)
        val stderr=reader(process.errorStream,false)
        try {
            while(true) {
                failure.get()?.let { throw it }
                checkCancel()
                if((System.nanoTime()-start)/1000000 >= spec.timeoutMillis) throw MediaProcessException("MEDIA_PROCESS_TIMEOUT")
                if(process.waitFor(100,TimeUnit.MILLISECONDS)) break
            }
            stdout.join(1500); stderr.join(1500)
            failure.get()?.let { throw it }
            if(stdout.isAlive || stderr.isAlive) throw MediaProcessException("MEDIA_PROCESS_STREAM_TIMEOUT")
            checkCancel()
            val result=MediaProcessResult(process.exitValue(),out.text(),err.text())
            finished=result.exitCode==0
            return result
        } finally {
            if(!finished) {
                if(pid.get()>1) runCatching { signalGroup(pid.get(),false) }
                process.destroy()
                runCatching { process.waitFor(250,TimeUnit.MILLISECONDS) }
                // A terminated Python parent alone is not proof its postprocessor stopped.
                if(pid.get()>1) runCatching { signalGroup(pid.get(),true) }
                if(process.isAlive) process.destroyForcibly()
                runCatching { process.waitFor(1000,TimeUnit.MILLISECONDS) }
            }
            runCatching { process.inputStream.close() }; runCatching { process.errorStream.close() }
            stdout.join(1000); stderr.join(1000)
        }
    }
}

private class BoundedCapture(private val max: Int, private val tail: Boolean) {
    private val bytes=ByteArray(max)
    private var start=0
    private var size=0
    @Synchronized fun add(input: ByteArray, offset: Int, count: Int) {
        if(!tail && count>max-size) throw MediaProcessException("MEDIA_OUTPUT_LIMIT")
        if(count>=max) {
            System.arraycopy(input,offset+count-max,bytes,0,max)
            start=0;size=max;return
        }
        val overflow=(size+count-max).coerceAtLeast(0)
        start=(start+overflow)%max;size-=overflow
        val end=(start+size)%max
        val first=minOf(count,max-end)
        System.arraycopy(input,offset,bytes,end,first)
        if(first<count) System.arraycopy(input,offset+first,bytes,0,count-first)
        size+=count
    }
    @Synchronized fun text(): String {
        val result=ByteArray(size)
        val first=minOf(size,max-start)
        System.arraycopy(bytes,start,result,0,first)
        if(first<size) System.arraycopy(bytes,0,result,first,size-first)
        return result.toString(Charsets.UTF_8)
    }
}
