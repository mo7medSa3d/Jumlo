package com.jumlo.app.data.media

import okhttp3.OkHttpClient
import okhttp3.Request
import com.jumlo.app.core.ResumePolicy
import com.jumlo.app.core.media.MediaLinks
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.util.concurrent.TimeUnit

/** A bounded HTTP sample for local-only media inspection. */
internal class MediaProbeSample(private val client:OkHttpClient,private val partBytes:Int=2*1024*1024) {
    private val http=client.newBuilder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).build()
    private class Part(val url:String,val total:Long?,val count:Long,val ranged:Boolean,val etag:String?,val modified:String?)

    fun fetch(url:String,allowHttp:Boolean,file:File,headers:Map<String,String> = emptyMap(),check:()->Unit = {}):Long? {
        require(partBytes in 4096..8*1024*1024) { "INVALID_MEDIA_PROBE" }
        val allowedHeaders=headers.filter { (name,value) ->
            name.lowercase() in setOf("user-agent","referer","origin","accept","accept-language") &&
                value.length<=2048 && value.none { it<' ' || it=='\u007f' }
        }
        RandomAccessFile(file,"rw").use { output ->
            output.setLength(0)
            fun part(initial:String,start:Long,end:Long,previous:Part?=null):Part {
                var current=MediaLinks.normalize(initial,allowHttp)
                repeat(9) { hop ->
                    check()
                    val request=Request.Builder().url(current).get().apply {
                        allowedHeaders.forEach { (key,value)->header(key,value) }
                        header("Accept-Encoding","identity")
                        header("Range","bytes=$start-$end")
                        (previous?.etag ?: previous?.modified)?.let { header("If-Range",it) }
                    }.build()
                    http.executeCancellable(request,check).use { response ->
                        if(response.code in setOf(301,302,303,307,308)) {
                            require(hop<8) { "TOO_MANY_REDIRECTS" }
                            val next=MediaLinks.normalize(URI(current).resolve(response.header("Location")
                                ?: throw IllegalArgumentException("INVALID_REDIRECT")).toString(),allowHttp)
                            require(!current.startsWith("https:",true) || next.startsWith("https:",true)) { "INSECURE_REDIRECT" }
                            current=next
                        } else {
                            require(response.code==200 || response.code==206) { "HTTP_${response.code}" }
                            require(response.header("Content-Encoding")?.equals("identity",true)!=false) { "INVALID_MEDIA_PROBE" }
                            val mime=response.header("Content-Type")?.substringBefore(';')?.trim()
                            require(mime !in setOf("text/html","application/xhtml+xml")) { "DOWNLOAD_NOT_A_FILE" }
                            val range=if(response.code==206) ResumePolicy.range(response.header("Content-Range"))
                                ?: throw IllegalArgumentException("INVALID_CONTENT_RANGE") else null
                            if(start>0) require(range!=null) { "DOWNLOAD_RESTART_REQUIRED" }
                            if(range!=null) require(range.start==start && range.end<=end) { "INVALID_CONTENT_RANGE" }
                            val total=range?.total ?: response.body?.contentLength()?.takeIf { it>=0 && range==null }
                            if(previous!=null) {
                                require(total==previous.total) { "DOWNLOAD_RESTART_REQUIRED" }
                                previous.etag?.let { require(response.header("ETag")==it) { "DOWNLOAD_RESTART_REQUIRED" } }
                                if(previous.etag==null) previous.modified?.let {
                                    require(response.header("Last-Modified")==it) { "DOWNLOAD_RESTART_REQUIRED" }
                                }
                            }
                            val input=response.body?.byteStream() ?: throw IllegalArgumentException("EMPTY_HTTP_BODY")
                            val limit=end-start+1
                            var received=0L
                            var eof=false
                            output.seek(start)
                            val buffer=ByteArray(8192)
                            while(received<limit) {
                                check()
                                val count=input.read(buffer,0,minOf(buffer.size.toLong(),limit-received).toInt())
                                if(count<0) { eof=true;break }
                                output.write(buffer,0,count);received+=count
                            }
                            check()
                            require(received>0) { "EMPTY_HTTP_BODY" }
                            if(range!=null) require(received==range.end-range.start+1) { "DOWNLOAD_TRUNCATED" }
                            val fullSize=total ?: if(eof && start==0L && range==null) received else null
                            return Part(current,fullSize,received,range!=null,ResumePolicy.strong(response.header("ETag")),response.header("Last-Modified"))
                        }
                    }
                }
                throw IllegalArgumentException("TOO_MANY_REDIRECTS")
            }
            val first=part(url,0,partBytes.toLong()-1)
            val total=first.total
            if(first.ranged && total!=null && total>first.count) {
                val start=maxOf(first.count,total-partBytes)
                part(first.url,start,total-1,first)
            }
            check()
            return total
        }
    }
}
