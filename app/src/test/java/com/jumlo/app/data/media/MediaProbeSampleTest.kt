package com.jumlo.app.data.media

import okhttp3.OkHttpClient
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

class MediaProbeSampleTest {
    private class Reply(val code:Int,val bytes:ByteArray=byteArrayOf(),val headers:Map<String,String> = emptyMap())
    private class Server:Closeable {
        private val socket=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
        val port get()=socket.localPort
        @Volatile var reply:(String,Map<String,String>)->Reply = { _,_->Reply(404) }
        private val thread=Thread {
            while(!socket.isClosed) try {
                socket.accept().use { client ->
                    client.soTimeout=5000
                    val input=client.getInputStream().bufferedReader()
                    val path=input.readLine().split(' ')[1]
                    val headers=mutableMapOf<String,String>()
                    while(true) {
                        val line=input.readLine() ?: break
                        if(line.isEmpty()) break
                        headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()
                    }
                    val response=reply(path,headers)
                    val output=client.getOutputStream()
                    val head=buildString {
                        append("HTTP/1.1 ${response.code} Fixture\r\nContent-Length: ${response.bytes.size}\r\nConnection: close\r\n")
                        response.headers.forEach { (name,value)->append("$name: $value\r\n") }
                        append("\r\n")
                    }
                    output.write(head.toByteArray());output.write(response.bytes);output.flush()
                }
            } catch(_:Exception) { if(socket.isClosed) break }
        }.apply { isDaemon=true;start() }
        override fun close() { socket.close();thread.join(1000) }
    }
    private fun fixture(block:(Server,String,OkHttpClient)->Unit) {
        val server=Server()
        val client=OkHttpClient.Builder().dns(object:Dns {
            override fun lookup(hostname:String)=listOf(InetAddress.getByName("127.0.0.1"))
        }).build()
        try { block(server,"http://media.example:${server.port}",client) }
        finally { server.close();client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll() }
    }

    @Test fun samplesOnlyHeadAndTailAndPreservesOffsetsForMp4Metadata()=fixture { server,base,client ->
        val data=ByteArray(100000) { (it%251).toByte() }
        val ranges=CopyOnWriteArrayList<String>()
        server.reply={ _,headers ->
            val range=headers.getValue("range")
            ranges+=range
            val bounds=range.removePrefix("bytes=").split('-').map(String::toInt)
            val end=minOf(bounds[1],data.lastIndex)
            Reply(206,data.copyOfRange(bounds[0],end+1),mapOf(
                "Content-Range" to "bytes ${bounds[0]}-$end/${data.size}","ETag" to "\"stable\""))
        }
        val file=Files.createTempFile("media-probe-",".sample").toFile()
        try {
            assertEquals(data.size.toLong(),MediaProbeSample(client,4096).fetch("$base/clip",true,file))
            val sample=file.readBytes()
            assertEquals(data.size,sample.size)
            assertArrayEquals(data.copyOfRange(0,4096),sample.copyOfRange(0,4096))
            assertArrayEquals(data.takeLast(4096).toByteArray(),sample.takeLast(4096).toByteArray())
            assertTrue(sample.copyOfRange(4096,data.size-4096).all { it==0.toByte() })
            assertEquals(listOf("bytes=0-4095","bytes=95904-99999"),ranges.toList())
        } finally { file.delete() }
    }

    @Test fun ignoredRangesRemainBoundedAndDoNotTriggerASecondFullDownload()=fixture { server,base,client ->
        val requests=AtomicInteger()
        server.reply={ _,_->requests.incrementAndGet();Reply(200,ByteArray(20000)) }
        val file=Files.createTempFile("media-probe-",".sample").toFile()
        try {
            assertEquals(20000L,MediaProbeSample(client,4096).fetch("$base/clip",true,file))
            assertEquals(4096L,file.length());assertEquals(1,requests.get())
        } finally { file.delete() }
    }

    @Test fun privateRedirectIsRejectedBeforeItsDestinationIsRequested()=fixture { server,base,client ->
        val reached=AtomicBoolean(false)
        server.reply={ path,_->
            if(path=="/clip") Reply(302,headers=mapOf("Location" to "http://127.0.0.1:${server.port}/private"))
            else { reached.set(true);Reply(200) }
        }
        val file=Files.createTempFile("media-probe-",".sample").toFile()
        try {
            try { MediaProbeSample(client,4096).fetch("$base/clip",true,file);fail("private redirect accepted") }
            catch(expected:IllegalArgumentException) { assertEquals("INVALID_URL",expected.message) }
            assertFalse(reached.get())
        } finally { file.delete() }
    }
}
