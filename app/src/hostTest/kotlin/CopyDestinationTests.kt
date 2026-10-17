package com.jumlo.app.hosttest

import com.jumlo.app.core.CopyDestinationPolicy
import com.jumlo.app.core.Hashing
import com.jumlo.app.model.CollisionPolicy
import java.io.File
import java.nio.file.Files

fun copyDestinationTests(test:(String,()->Unit)->Unit) {
    fun fixture(block:(File)->Unit) {
        val root=Files.createTempDirectory("jumlo-copy-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }
    test("same-folder Keep both copy creates distinct verified bytes and retains original") { fixture { folder ->
        val original=File(folder,"Report.bin").apply { writeBytes(ByteArray(8192) { (it*31).toByte() }) }
        val existingCopy=File(folder,"report (1).BIN").apply { writeText("previous copy remains unchanged") }
        val originalHash=Hashing.sha256(original);val existingHash=Hashing.sha256(existingCopy)
        val entries=folder.listFiles()!!.map { it.toURI().toString() to it.name }
        val name=CopyDestinationPolicy.name(original.toURI().toString(),original.name,entries,false,CollisionPolicy.KEEP_BOTH)
        check(name=="Report (2).bin")
        val output=File(folder,requireNotNull(name))
        original.copyTo(output,overwrite=false)
        check(output.canonicalFile!=original.canonicalFile && output.readBytes().contentEquals(original.readBytes()))
        check(Hashing.sha256(original)==originalHash && Hashing.sha256(output)==originalHash)
        check(Hashing.sha256(existingCopy)==existingHash)
    } }
    test("same-document move and replacement stay blocked without changing any bytes") { fixture { folder ->
        val original=File(folder,"a.txt").apply { writeText("original") }
        val identity=original.toURI().toString();val entries=listOf(identity to original.name)
        for((move,policy) in listOf(true to CollisionPolicy.KEEP_BOTH,false to CollisionPolicy.REPLACE)) {
            var rejected=false
            try { CopyDestinationPolicy.name(identity,original.name,entries,move,policy) }
            catch(_:IllegalArgumentException) { rejected=true }
            check(rejected && original.readText()=="original" && folder.list()!!.size==1)
        }
        check(CopyDestinationPolicy.name(identity,original.name,entries,false,CollisionPolicy.SKIP)==null)
    } }
    test("replacement of a different existing document fails closed") { fixture { folder ->
        val source=File(folder,"source.bin").apply { writeText("source") }
        val target=File(folder,"target.bin").apply { writeText("target") }
        var rejected=false
        try { CopyDestinationPolicy.name(source.toURI().toString(),target.name,listOf(target.toURI().toString() to target.name),false,CollisionPolicy.REPLACE) }
        catch(e:IllegalArgumentException) { rejected=e.message=="PROVIDER_ATOMIC_REPLACE_UNAVAILABLE" }
        check(rejected && source.readText()=="source" && target.readText()=="target")
    } }
}
