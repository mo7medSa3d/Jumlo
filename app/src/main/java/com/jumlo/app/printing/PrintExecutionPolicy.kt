package com.jumlo.app.printing

import java.util.concurrent.atomic.AtomicBoolean

/** One terminal result per framework callback, even when cancellation races a posted success. */
class PrintCallbackGate {
    enum class Result { SUCCESS, FAILURE, CANCELED }
    private val complete=AtomicBoolean(false)
    private val canceled=AtomicBoolean(false)
    fun cancel() {canceled.set(true)}
    fun finish(requested:Result):Result? {
        if(!complete.compareAndSet(false,true)) return null
        return if(canceled.get()) Result.CANCELED else requested
    }
}
object PrintNumberInput {
    fun parse(text:String):Double? = text.trim().map {c->when(c) {
        in '٠'..'٩' -> '0'+(c-'٠')
        in '۰'..'۹' -> '0'+(c-'۰')
        '٫',',' -> '.'
        else->c
    }}.joinToString("").toDoubleOrNull()?.takeIf {it.isFinite()}
}
object PrintImageBudget {
    /** Reserve room for both decoded pixels and EXIF orientation plus encoder working buffers. */
    fun sampleSize(width:Int,height:Int,maxPixels:Long=2L*1024*1024):Int {
        require(width>0 && height>0 && maxPixels>0) { "PRINT_UNSUPPORTED" }
        var sample=1
        while(((width.toLong()+sample-1)/sample)*((height.toLong()+sample-1)/sample)>maxPixels) {
            require(sample<=1 shl 28) { "PRINT_UNSUPPORTED" };sample*=2
        }
        return sample
    }
}

/** Compact key for SavedState identity; full ordered selections live in durable print data. */
object PrintSourceIdentity {
    fun documentKey(source:PrintSource)=key(source.copy(name="",mimeType=""))
    fun key(source:PrintSource?):String {
        if(source==null) return "none"
        val digest=java.security.MessageDigest.getInstance("SHA-256")
        for(field in listOf(source.path,source.name,source.mimeType)) {
            val bytes=field.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array());digest.update(bytes)
        }
        source.pages.forEach {digest.update(java.nio.ByteBuffer.allocate(8).putInt(it.sourcePage).putInt(it.rotationDegrees).array())}
        return digest.digest().joinToString("") {"%02x".format(it.toInt() and 255)}
    }
}
