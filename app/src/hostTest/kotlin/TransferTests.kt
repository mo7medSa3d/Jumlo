package com.jumlo.app.hosttest

import com.jumlo.app.core.*
import com.sun.net.httpserver.HttpServer
import java.io.*
import java.net.*
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

fun transferTests(test:(String,()->Unit)->Unit) {
    fun fingerprint(url:String)=Hashing.sha256(ByteArrayInputStream(HttpPolicy.url(url,true).toString().toByteArray()))
    fun fixture(block:(String,File)->Unit) {
        val dir=Files.createTempDirectory("uu-http-").toFile()
        val server=HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(),0),0)
        val bytes="0123456789".toByteArray()
        val flakyAttempts=AtomicInteger(0)
        server.createContext("/file") { ex -> ex.responseHeaders.add("ETag","\"v1\""); ex.sendResponseHeaders(200,bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }; ex.close() }
        server.createContext("/flaky") { ex ->
            if(flakyAttempts.incrementAndGet()==1) { ex.sendResponseHeaders(503,-1); ex.close() }
            else { ex.responseHeaders.add("ETag","\"v1\""); ex.sendResponseHeaders(200,bytes.size.toLong()); ex.responseBody.use { it.write(bytes) }; ex.close() }
        }
        server.createContext("/unknown") { ex -> ex.sendResponseHeaders(200,0); ex.responseBody.use { it.write(bytes) }; ex.close() }
        server.createContext("/resume") { ex ->
            val range=ex.requestHeaders.getFirst("Range")
            val start=range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            ex.responseHeaders.add("ETag","\"v1\"")
            if(start>0) ex.responseHeaders.add("Content-Range","bytes $start-9/10")
            ex.sendResponseHeaders(if(start>0) 206 else 200,(bytes.size-start).toLong()); ex.responseBody.use { it.write(bytes,start,bytes.size-start) }; ex.close()
        }
        server.createContext("/changed") { ex -> ex.responseHeaders.add("ETag","\"v2\""); ex.responseHeaders.add("Content-Range","bytes 5-9/10"); ex.sendResponseHeaders(206,5); ex.responseBody.use { it.write(bytes,5,5) }; ex.close() }
        server.createContext("/redirect") { ex -> ex.responseHeaders.add("Location","/file"); ex.sendResponseHeaders(302,-1); ex.close() }
        server.createContext("/resume-redirect") { ex -> ex.responseHeaders.add("Location","/resume"); ex.sendResponseHeaders(302,-1); ex.close() }
        server.createContext("/missing") { ex -> ex.sendResponseHeaders(404,-1); ex.close() }
        server.createContext("/truncated") { ex -> ex.sendResponseHeaders(200,20); ex.responseBody.write(bytes); ex.close() }
        server.start()
        try { block("http://127.0.0.1:${server.address.port}",dir) } finally { server.stop(0); dir.deleteRecursively() }
    }
    val transport=object:HttpTransport {
        override fun open(url:String,headers:Map<String,String>):TransportResponse {
            val connection=URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false; connection.connectTimeout=2000; connection.readTimeout=2000
            headers.forEach { (k,v)->connection.setRequestProperty(k,v) }
            val code=connection.responseCode
            val responseHeaders=connection.headerFields.filterKeys { it!=null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(",") }
            val body=if(code<400) connection.inputStream else connection.errorStream ?: ByteArrayInputStream(byteArrayOf())
            return TransportResponse(code,responseHeaders,body) { connection.disconnect() }
        }
    }
    test("resume interruption preserves appended bytes and original I/O failure without stale retry") {
        val dir=Files.createTempDirectory("jumlo-resume-interrupt-").toFile()
        try {
            val file=File(dir,"a.part").apply { writeText("01234") }
            var attempts=0
            val broken=object:HttpTransport {
                override fun open(url:String,headers:Map<String,String>):TransportResponse {
                    attempts++
                    val stream=object:ByteArrayInputStream("56789".toByteArray()) {
                        var delivered=false
                        override fun read(b:ByteArray,off:Int,len:Int):Int {
                            if(delivered) throw IOException("CONNECTION_RESET_FIXTURE")
                            delivered=true;return super.read(b,off,minOf(len,2))
                        }
                    }
                    return TransportResponse(206,mapOf("content-range" to "bytes 5-9/10","content-length" to "5","etag" to "\"v1\""),stream)
                }
            }
            var error:Throwable?=null
            try { StreamTransfer(broken).execute(TransferRequest("https://example.org/resume",file,5,"\"v1\"",knownTotalBytes=10,minFreeBytes=0,finalUrlSha256=fingerprint("https://example.org/resume"),retryBackoffMs=0)) }
            catch(t:Throwable) { error=t }
            check(error is IOException && error.message=="CONNECTION_RESET_FIXTURE")
            check(attempts==1 && file.readText()=="0123456")
        } finally { dir.deleteRecursively() }
    }
    test("real HTTP GET writes exact bytes and measured size") { fixture { base,dir ->
        val out=File(dir,"a.part"); val result=StreamTransfer(transport).execute(TransferRequest("$base/file",out,cleartextConsent=true,maxBytes=1000,minFreeBytes=0))
        check(out.readText()=="0123456789" && result.bytes==10L && result.totalBytes==10L)
    } }
    test("transient 503 retries with backoff and succeeds") { fixture { base,dir ->
        val out=File(dir,"flaky.part")
        val result=StreamTransfer(transport).execute(TransferRequest("$base/flaky",out,cleartextConsent=true,maxBytes=1000,minFreeBytes=0,maxRetries=2,retryBackoffMs=10L))
        check(out.readText()=="0123456789" && result.bytes==10L)
    } }
    test("empty HTTP 200 body is never published as a successful download") {
        val dir=Files.createTempDirectory("jumlo-empty-download-").toFile()
        try {
            val out=File(dir,"empty.part")
            val empty=HttpTransport { _,_ -> TransportResponse(200,emptyMap(),ByteArrayInputStream(byteArrayOf())) }
            val error=runCatching {
                StreamTransfer(empty).execute(TransferRequest("https://example.org/empty",out,minFreeBytes=0,maxRetries=0))
            }.exceptionOrNull()
            check(error is IOException && error.message=="EMPTY_HTTP_BODY")
            check(out.length()==0L)
        } finally { dir.deleteRecursively() }
    }
    test("unknown HTTP size stays unknown") { fixture { base,dir ->
        val result=StreamTransfer(transport).execute(TransferRequest("$base/unknown",File(dir,"a.part"),cleartextConsent=true,maxBytes=1000,minFreeBytes=0))
        check(result.bytes==10L && result.totalBytes==null)
    } }
    test("real HTTP resume validates range and appends once") { fixture { base,dir ->
        val out=File(dir,"a.part"); out.writeText("01234")
        val result=StreamTransfer(transport).execute(TransferRequest("$base/resume",out,5,"\"v1\"",knownTotalBytes=10,cleartextConsent=true,maxBytes=1000,minFreeBytes=0,finalUrlSha256=fingerprint("$base/resume")))
        check(result.bytes==10L && out.readText()=="0123456789")
    } }
    test("resume derives the whole object length from Content-Range when journal lacked size") { fixture { base,dir ->
        val out=File(dir,"unknown-total.part"); out.writeText("01234")
        val result=StreamTransfer(transport).execute(TransferRequest("$base/resume",out,5,"\"v1\"",knownTotalBytes=null,cleartextConsent=true,maxBytes=1000,minFreeBytes=0,finalUrlSha256=fingerprint("$base/resume")))
        check(result.bytes==10L && result.totalBytes==10L && out.readText()=="0123456789")
    } }
    test("resuming a bounded partial range never reports a truncated file as complete") {
        val dir=Files.createTempDirectory("jumlo-partial-range-").toFile()
        try {
            val out=File(dir,"partial.part").apply { writeText("01234") }
            val sliced=object:HttpTransport {
                override fun open(url:String,headers:Map<String,String>)=TransportResponse(206,
                    mapOf("content-range" to "bytes 5-6/10", "content-length" to "2", "etag" to "\"v1\""),
                    ByteArrayInputStream("56".toByteArray()))
            }
            val error=runCatching { StreamTransfer(sliced).execute(TransferRequest("https://example.org/file",out,5,"\"v1\"",knownTotalBytes=null,minFreeBytes=0,maxRetries=0,finalUrlSha256=fingerprint("https://example.org/file"))) }.exceptionOrNull()
            check(error is IOException) { "Expected truncated-content failure, got $error" }
            check(out.readText()=="0123456")
        } finally { dir.deleteRecursively() }
    }
    test("resume rejects an unknown representation total rather than publishing an arbitrary slice") {
        val dir=Files.createTempDirectory("jumlo-range-unknown-").toFile()
        try {
            val out=File(dir,"partial.part").apply { writeText("01234") }
            val unknown=object:HttpTransport {
                override fun open(url:String,headers:Map<String,String>)=TransportResponse(206,
                    mapOf("content-range" to "bytes 5-9/*", "content-length" to "5", "etag" to "\"v1\""),
                    ByteArrayInputStream("56789".toByteArray()))
            }
            val error=runCatching { StreamTransfer(unknown).execute(TransferRequest("https://example.org/file",out,5,"\"v1\"",minFreeBytes=0,maxRetries=0,finalUrlSha256=fingerprint("https://example.org/file"))) }.exceptionOrNull()
            check(error is RestartRequired) { "Expected unknown-total restart, got $error" }
            check(out.readText()=="01234")
        } finally { dir.deleteRecursively() }
    }
    test("real changed ETag leaves prior partial bytes untouched") { fixture { base,dir ->
        val out=File(dir,"a.part"); out.writeText("01234"); var rejected=false
        try { StreamTransfer(transport).execute(TransferRequest("$base/changed",out,5,"\"v1\"",knownTotalBytes=10,cleartextConsent=true,maxBytes=1000,minFreeBytes=0,finalUrlSha256=fingerprint("$base/changed"))) } catch(_:RestartRequired) { rejected=true }
        check(rejected && out.readText()=="01234")
    } }
    test("real 200 response to Range does not append") { fixture { base,dir ->
        val out=File(dir,"a.part"); out.writeText("01234"); var rejected=false
        try { StreamTransfer(transport).execute(TransferRequest("$base/file",out,5,"\"v1\"",knownTotalBytes=10,cleartextConsent=true,maxBytes=1000,minFreeBytes=0,finalUrlSha256=fingerprint("$base/file"))) } catch(_:RestartRequired) { rejected=true }
        check(rejected && out.readText()=="01234")
    } }
    test("HTTP redirects resolve relative locations") { fixture { base,dir -> val out=File(dir,"a.part"); StreamTransfer(transport).execute(TransferRequest("$base/redirect",out,cleartextConsent=true,maxBytes=1000,minFreeBytes=0)); check(out.readText()=="0123456789") } }
    test("HTTP error cannot create successful output") { fixture { base,dir ->
        val out=File(dir,"a.part"); var rejected=false
        try { StreamTransfer(transport).execute(TransferRequest("$base/missing",out,cleartextConsent=true,maxBytes=1000,minFreeBytes=0)) } catch(e:IllegalArgumentException) { rejected=e.message=="HTTP_404" }
        check(rejected && !out.exists())
    } }
    test("truncated HTTP body is a failure") { fixture { base,dir ->
        var rejected=false
        try { StreamTransfer(transport).execute(TransferRequest("$base/truncated",File(dir,"a.part"),cleartextConsent=true,maxBytes=1000,minFreeBytes=0)) } catch(_:IOException) { rejected=true }
        check(rejected)
    } }
    test("streamed HTTP enforces actual byte budget") { fixture { base,dir ->
        var rejected=false
        try { StreamTransfer(transport).execute(TransferRequest("$base/unknown",File(dir,"a.part"),cleartextConsent=true,maxBytes=5,minFreeBytes=0)) } catch(_:IllegalArgumentException) { rejected=true }
        check(rejected)
    } }
    test("HTTP metadata binds resume to the observed final URL without storing its secret") { fixture { base,dir ->
        val result=StreamTransfer(transport).execute(TransferRequest("$base/redirect",File(dir,"a.part"),cleartextConsent=true,maxBytes=1000,minFreeBytes=0))
        check(result.finalUrlSha256==fingerprint("$base/file"))
    } }
    test("resume rejects a changed redirect target even with the same ETag and length") { fixture { base,dir ->
        val out=File(dir,"a.part");out.writeText("01234");var rejected=false
        try {
            StreamTransfer(transport).execute(TransferRequest("$base/resume-redirect",out,5,"\"v1\"",knownTotalBytes=10,cleartextConsent=true,maxBytes=1000,minFreeBytes=0,finalUrlSha256=fingerprint("$base/previous-resource")))
        } catch(_:RestartRequired) { rejected=true }
        check(rejected && out.readText()=="01234")
    } }
}
