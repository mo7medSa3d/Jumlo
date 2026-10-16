package com.universalutilities.app.hosttest

import com.universalutilities.app.core.media.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

fun mediaProcessTests(test: (String, () -> Unit) -> Unit) {
    fun python(body: String) = listOf("python3", "-c", "import os,sys,time;os.setsid();print('UU_PID:'+str(os.getpid()),flush=True);"+body)
    fun run(body: String, timeout: Long = 5000, limit: Int = 4096, tail: Boolean = false, check: () -> Unit = {}, line: (String)->Unit = {}): MediaProcessResult {
        val dir=Files.createTempDirectory("uu-process-test-").toFile()
        try { return MediaProcessRunner { pid, force -> ProcessBuilder("/bin/kill",if(force) "-KILL" else "-TERM","--","-$pid").start().waitFor() }
            .run(MediaProcessSpec(python(body), dir, timeoutMillis=timeout, stdoutLimit=limit, tailOutput=tail),check,line) }
        finally { dir.deleteRecursively() }
    }
    test("media runner executes actual child argv without shell interpretation") {
        val result=run("print('actual & literal $(not-a-shell)')")
        check(result.exitCode==0 && result.stdout.trim()=="actual & literal $(not-a-shell)")
    }
    test("media runner reports a real subprocess failure without changing it to success") {
        val result=run("print('failure',file=sys.stderr);sys.exit(7)")
        check(result.exitCode==7 && result.stderr.contains("failure"))
    }
    test("media probe output is bounded before JSON parsing") {
        try { run("sys.stdout.write('a'*1000000)",limit=1024);error("overflow accepted") }
        catch(e:MediaProcessException) { check(e.code=="MEDIA_OUTPUT_LIMIT") }
    }
    test("long download diagnostics retain a bounded tail without exhausting memory") {
        val result=run("sys.stdout.write('a'*1000000+'THE_END')",limit=1024,tail=true)
        check(result.stdout.toByteArray().size==1024 && result.stdout.endsWith("THE_END"))
    }
    test("media watchdog terminates an actual hung process") {
        val start=System.nanoTime()
        try { run("time.sleep(10)",timeout=200);error("timeout ignored") }
        catch(e:MediaProcessException) { check(e.code=="MEDIA_PROCESS_TIMEOUT") }
        check((System.nanoTime()-start)/1000000<3500)
    }
    test("media cancellation is checked even when no progress is emitted") {
        val calls=AtomicInteger()
        try { run("time.sleep(10)",check={ if(calls.incrementAndGet()>3) throw java.io.InterruptedIOException("USER_CANCELLED") });error("cancel ignored") }
        catch(e:java.io.InterruptedIOException) { check(e.message=="USER_CANCELLED") }
    }
    test("progress callback receives actual complete lines from a running child") {
        val lines=mutableListOf<String>()
        val result=run("print('UU_PROGRESS:a|10|20|20|5|2',flush=True);time.sleep(.1);print('done')",line={synchronized(lines){lines+=it}})
        check(result.exitCode==0 && lines.contains("UU_PROGRESS:a|10|20|20|5|2"))
    }
    test("process environment is supplied without serializing it into a command line") {
        val dir=Files.createTempDirectory("uu-env-test-").toFile()
        try {
            val result=MediaProcessRunner().run(MediaProcessSpec(python("print(os.environ['UU_TEST_VALUE'])"),dir,environment=mapOf("UU_TEST_VALUE" to "value with spaces")))
            check(result.stdout.trim()=="value with spaces")
        } finally {dir.deleteRecursively()}
    }
    test("non-bootstrap stdout cannot spoof process control messages later") {
        val result=run("print('UU_PID:1');print('data')")
        check(result.stdout.startsWith("UU_PID:1"))
    }
    test("stderr is drained independently without pipe deadlock") {
        val result=run("sys.stderr.write('x'*1000000);print('finished')")
        check(result.exitCode==0 && result.stdout.trim()=="finished" && result.stderr.toByteArray().size<=131072)
    }
}
