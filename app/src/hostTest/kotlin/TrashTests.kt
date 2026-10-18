package com.jumlo.app.hosttest

import com.jumlo.app.core.*
import java.io.File
import java.nio.file.Files
import java.util.UUID

fun trashTests(test:(String,()->Unit)->Unit) {
    fun fixture(block:(File,ManagedTrashStorage)->Unit) {
        val root=Files.createTempDirectory("jumlo-trash-").toFile()
        try { block(root,ManagedTrashStorage(root)) } finally { root.deleteRecursively() }
    }
    fun entry(root:File,name:String):TrashEntry {
        val id=UUID.randomUUID().toString()
        val file=File(root,"results/$id/$name").apply { parentFile.mkdirs(); writeText("original:$name") }
        return TrashEntry(id,name,file.length(),Hashing.sha256(file),"record:$id","a".repeat(64))
    }
    test("trash metadata names cannot overwrite original payload and restoration is exact") { fixture { root,store ->
        for(name in listOf("record.json","journal.properties","payload.bin","entry.properties")) {
            val e=entry(root,name); val before=File(root,"results/${e.id}/$name").readBytes()
            store.move(e)
            check(!File(root,"results/${e.id}/$name").exists())
            check(store.entries().single().recordJson==e.recordJson)
            store.restore(e.id)
            check(File(root,"results/${e.id}/$name").readBytes().contentEquals(before))
            store.acknowledgeRestore(e.id)
        }
    } }
    test("trash purge deletes exactly confirmed IDs and rejects path traversal") { fixture { root,store ->
        val a=entry(root,"a.bin"); val b=entry(root,"b.bin"); store.move(a);store.move(b)
        check(store.purge(setOf(a.id))==1)
        check(store.entries().map{it.id}==listOf(b.id))
        try { store.purge(setOf("../results")); error("Traversal accepted") } catch(_:IllegalArgumentException) {}
        check(store.entries().single().id==b.id)
    } }
    test("trash purge refuses corrupted payload and preserves remaining bytes") { fixture { root,store ->
        val e=entry(root,"a.bin");store.move(e)
        File(root,"trash/${e.id}/payload.bin").writeText("changed")
        try { store.purge(setOf(e.id));error("Corruption accepted") } catch(_:IllegalArgumentException) {}
        check(File(root,"trash/${e.id}/payload.bin").readText()=="changed")
    } }
    test("trash recovery completes prepared move and restore transitions") { fixture { root,store ->
        val e=entry(root,"a.bin");store.move(e)
        // Simulate death after the durable manifest but before the data rename.
        val payload=File(root,"trash/${e.id}/payload.bin")
        val active=File(root,"results/${e.id}/${e.displayName}").apply { parentFile.mkdirs() }
        Files.move(payload.toPath(),active.toPath())
        check(ManagedTrashStorage(root).recover().single().entry==e)
        check(payload.isFile && !active.exists())
        store.restore(e.id)
        // Restore remains journaled until the database acknowledges it.
        val recovered=ManagedTrashStorage(root).recover().single()
        check(recovered.restored && active.readText()=="original:a.bin")
        store.acknowledgeRestore(e.id)
        check(store.entries().isEmpty())
    } }
}
