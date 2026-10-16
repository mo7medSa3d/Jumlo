package com.universalutilities.app.core
import java.io.*
interface HttpTransport { fun open(url:String,headers:Map<String,String>):TransportResponse }
class TransportResponse(val code:Int,val headers:Map<String,String>,val body:InputStream,private val closeAction:()->Unit={}):Closeable { override fun close() { try { body.close() } finally { closeAction() } } }
data class TransferRequest(val url:String,val partialFile:File,val offset:Long=0,val strongEtag:String?=null,val lastModified:String?=null,val knownTotalBytes:Long?=null,val cleartextConsent:Boolean=false,val maxBytes:Long=2L*1024*1024*1024,val minFreeBytes:Long=32L*1024*1024,val headers:Map<String,String> = emptyMap(),val finalUrlSha256:String?=null)
data class TransferResult(val bytes:Long,val totalBytes:Long?,val strongEtag:String?,val lastModified:String?,val mimeType:String?,val finalUrlSha256:String?=null)
class StreamTransfer(private val transport:HttpTransport) {
    fun execute(request:TransferRequest,checkCancel:()->Unit={},progress:(Long,Long?)->Unit={_,_->},onMetadata:(TransferResult)->Unit={}):TransferResult {
        require(request.offset>=0 && request.maxBytes>0 && request.minFreeBytes>=0) { "INVALID_TRANSFER_REQUEST" }
        require(request.offset==0L || (request.partialFile.isFile && request.partialFile.length()==request.offset)) { "PARTIAL_FILE_MISMATCH" }
        var target=HttpPolicy.url(request.url,request.cleartextConsent)
        var headers=request.headers.filterKeys { it.lowercase() !in setOf("range","if-range","accept-encoding","host","content-length") }.toMutableMap()
        headers["Accept-Encoding"]="identity"
        if(request.offset>0) {
            if(request.finalUrlSha256==null || !Regex("[0-9a-f]{64}").matches(request.finalUrlSha256)) throw RestartRequired("DOWNLOAD_RESTART_REQUIRED")
            val validator=ResumePolicy.strong(request.strongEtag) ?: request.lastModified?.takeIf { it.isNotBlank() } ?: throw RestartRequired("DOWNLOAD_RESTART_REQUIRED")
            headers["Range"]="bytes=${request.offset}-"; headers["If-Range"]=validator
        }
        if(target.scheme=="http") require(headers.keys.none { it.lowercase() in setOf("authorization","cookie","proxy-authorization") }) { "CLEARTEXT_CREDENTIALS_FORBIDDEN" }
        for(redirect in 0..5) {
            checkCancel()
            transport.open(target.toString(),headers).use { response ->
                if(response.code in setOf(301,302,303,307,308)) {
                    require(redirect<5) { "TOO_MANY_REDIRECTS" }
                    val location=response.headers["location"] ?: throw IllegalArgumentException("INVALID_REDIRECT")
                    val next=HttpPolicy.url(target.resolve(location).toString(),request.cleartextConsent)
                    require(!(target.scheme=="https" && next.scheme=="http")) { "INSECURE_REDIRECT" }
                    if(HttpPolicy.origin(target)!=HttpPolicy.origin(next)) headers=headers.filterKeys { it.lowercase() !in setOf("authorization","cookie","proxy-authorization") }.toMutableMap()
                    target=next
                } else {
                    val h=response.headers
                    val fingerprint=Hashing.sha256(ByteArrayInputStream(target.toString().toByteArray(Charsets.UTF_8)))
                    if(request.offset>0 && fingerprint!=request.finalUrlSha256) throw RestartRequired("DOWNLOAD_RESTART_REQUIRED")
                    ResumePolicy.validate(request.offset,request.strongEtag,request.lastModified,request.knownTotalBytes,response.code,h)
                    require(h["content-encoding"]?.let { it.equals("identity",true) }!=false) { "UNSUPPORTED_CONTENT_ENCODING" }
                    val length=h["content-length"]?.let { it.toLongOrNull()?.takeIf { n->n>=0 } ?: throw IllegalArgumentException("INVALID_CONTENT_LENGTH") }
                    val range=if(request.offset>0) ResumePolicy.range(h["content-range"]) else null
                    if(range!=null && (range.total==null || range.end!=range.total-1)) throw RestartRequired("DOWNLOAD_RESTART_REQUIRED")
                    val total=if(range!=null) range.total else length
                    require(total==null || total<=request.maxBytes) { "DOWNLOAD_BYTE_BUDGET_EXCEEDED" }
                    val expectedBody=range?.let { it.end-it.start+1 } ?: length
                    val metadata=TransferResult(request.offset,total,ResumePolicy.strong(h["etag"]),h["last-modified"],h["content-type"]?.substringBefore(';'),fingerprint)
                    onMetadata(metadata)
                    request.partialFile.parentFile.mkdirs()
                    var received=0L
                    java.io.RandomAccessFile(request.partialFile,"rw").use { output ->
                        if(request.offset==0L) output.setLength(0)
                        output.seek(request.offset)
                        val buffer=ByteArray(64*1024)
                        while(true) {
                            checkCancel(); val n=response.body.read(buffer); if(n<0) break
                            require(n.toLong()<=request.maxBytes-request.offset-received) { "DOWNLOAD_BYTE_BUDGET_EXCEEDED" }
                            if(expectedBody!=null && n.toLong()>expectedBody-received) throw IOException("DOWNLOAD_LENGTH_MISMATCH")
                            require(request.partialFile.parentFile.usableSpace>=request.minFreeBytes+n) { "DISK_BUDGET_EXCEEDED" }
                            if(n>0) { output.write(buffer,0,n); received+=n; progress(request.offset+received,total) }
                        }
                        if(expectedBody!=null && received!=expectedBody) throw EOFException("DOWNLOAD_TRUNCATED")
                        checkCancel(); output.fd.sync()
                    }
                    return metadata.copy(bytes=request.offset+received)
                }
            }
        }
        throw IllegalArgumentException("TOO_MANY_REDIRECTS")
    }
}
