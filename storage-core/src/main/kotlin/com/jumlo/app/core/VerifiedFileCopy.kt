package com.jumlo.app.core

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/** Verified public copy; a failed write must never remove an existing output. */
object VerifiedFileCopy {
    fun copy(source:File,target:File,expectedSha256:String,check:()->Unit = {}) {
        var ownsTarget=false
        try {
            check()
            source.inputStream().use { input ->
                Files.newOutputStream(target.toPath(),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE).use { output ->
                    ownsTarget=true
                    val buffer=ByteArray(64*1024)
                    while(true) {
                        check()
                        val count=input.read(buffer)
                        if(count<0) break
                        if(count>0) output.write(buffer,0,count)
                    }
                    output.flush()
                }
            }
            check()
            val actual=target.inputStream().use { Hashing.sha256(it,check) }
            check(actual==expectedSha256) { "DESTINATION_VERIFY_FAILED" }
            check()
        } catch(error:Throwable) {
            if(ownsTarget) target.delete()
            throw error
        }
    }
}
