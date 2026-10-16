package com.jumlo.app.hosttest

import com.jumlo.app.core.*
import java.io.*
import java.nio.file.Files
import java.util.UUID
import java.util.zip.*

private var passed = 0
private var failed = 0
private fun test(name: String, block: () -> Unit) {
    try { block(); passed++; println("PASS $name") }
    catch (e: Throwable) { failed++; println("FAIL $name: ${e.javaClass.simpleName}: ${e.message}") }
}
private inline fun <reified T : Throwable> rejects(block: () -> Unit) {
    try { block() } catch (e: Throwable) { check(e is T) { "Expected ${T::class}, got ${e.javaClass}" }; return }
    error("Expected ${T::class.simpleName}")
}
private fun tmp(block: (File) -> Unit) {
    val root = Files.createTempDirectory("uu-test-").toFile()
    try { block(root) } finally { root.deleteRecursively() }
}
private fun zip(file: File, entries: List<Pair<String, ByteArray>>) {
    ZipOutputStream(file.outputStream()).use { out ->
        entries.forEach { (name, bytes) -> out.putNextEntry(ZipEntry(name)); out.write(bytes); out.closeEntry() }
    }
}

fun main() {
    test("pages preserve requested order and remove duplicates") { check(PageSelection.parse("3,1-2,3", 3) == listOf(3,1,2)) }
    test("reversed pages are rejected") { rejects<IllegalArgumentException> { PageSelection.parse("5-2", 8) } }
    test("Arabic and Persian digits normalize") { check(PageSelection.parse("\u0661\u060c\u06f3-\u06f4", 4) == listOf(1,3,4)) }
    test("empty and out-of-range pages fail") { for (s in listOf("", "0", "4", "1,,2", "1-2-3")) rejects<IllegalArgumentException> { PageSelection.parse(s,3) } }
    test("archive paths reject both platform traversal forms") { tmp { root -> for (s in listOf("../x", "/x", "C:/x", "a\\..\\x", "a/../x", "a//x")) rejects<IllegalArgumentException> { SafeNames.entry(root,s) } } }
    test("safe names never escape result folder") { rejects<IllegalArgumentException> { SafeNames.basename("../../owned.pdf") }; check(SafeNames.basename("notes.pdf") == "notes.pdf") }
    test("memory budgets fail instead of downscaling") { rejects<IllegalArgumentException> { MemoryBudget.requireBitmaps(100,100,1,1024) }; MemoryBudget.requireBitmaps(10,10,2,800) }
    test("invalid geometry is rejected") { rejects<IllegalArgumentException> { ImageGeometry.resolve(0,10,20,20,FitMode.FIT,false) } }
    test("fit preserves aspect") { val g=ImageGeometry.resolve(400,200,100,100,FitMode.FIT,true); check(g.width==100 && g.height==50) }
    test("pad retains requested canvas") { val g=ImageGeometry.resolve(400,200,100,100,FitMode.PAD,true); check(g.width==100 && g.height==100); check(g.draw.top==25.0 && g.draw.bottom==75.0) }
    test("fill crops only the excess axis") { val g=ImageGeometry.resolve(400,200,100,100,FitMode.FILL,true); check(g.crop.left==0.25 && g.crop.right==0.75) }
    test("no-upscale applies to one growing axis") { val g=ImageGeometry.resolve(400,200,100,1000,FitMode.STRETCH,false); check(g.width<=400 && g.height<=200) }
    test("zip roundtrip reads original bytes") { tmp { root ->
        val a=ByteArray(16384) { (it*17).toByte() }; val z=File(root,"a.zip")
        ArchiveCore.create(z,listOf(ArchiveInput("folder/a.bin") { ByteArrayInputStream(a) }),6,ArchiveLimits(100000,20,32))
        val dest=File(root,"out"); val files=ArchiveCore.extract(z,dest,ArchiveLimits(100000,20,32))
        check(files.single().readBytes().contentEquals(a)); check(ZipFile(z).use { it.size() }==1)
    } }
    test("zip traversal failure leaves no files") { tmp { root ->
        val z=File(root,"a.zip"); zip(z,listOf("ok" to byteArrayOf(1), "../outside" to byteArrayOf(2)))
        val dest=File(root,"out"); rejects<IllegalArgumentException> { ArchiveCore.extract(z,dest,ArchiveLimits(100,10,32)) }; check(!dest.exists()); check(!File(root,"outside").exists())
    } }
    test("zip expanded byte budget is measured") { tmp { root ->
        val z=File(root,"a.zip"); zip(z,listOf("bomb" to ByteArray(10000)))
        val dest=File(root,"out"); rejects<IllegalArgumentException> { ArchiveCore.extract(z,dest,ArchiveLimits(100,10,32)) }; check(!dest.exists())
    } }
    test("zip entry count and depth are bounded") { tmp { root ->
        val z=File(root,"a.zip"); zip(z,listOf("a/b/c" to byteArrayOf(1)))
        rejects<IllegalArgumentException> { ArchiveCore.extract(z,File(root,"out"),ArchiveLimits(100,10,2)) }
        zip(z,listOf("a" to byteArrayOf(1),"b" to byteArrayOf(2)))
        rejects<IllegalArgumentException> { ArchiveCore.extract(z,File(root,"out"),ArchiveLimits(100,1,32)) }
    } }
    test("archive cancellation removes incomplete archive") { tmp { root ->
        val out=File(root,"partial.zip"); rejects<InterruptedIOException> { ArchiveCore.create(out,listOf(ArchiveInput("a") { ByteArrayInputStream(ByteArray(10000)) }),6,ArchiveLimits(100000,10,32),{ throw InterruptedIOException() }) }; check(!out.exists())
    } }
    test("archive creation rejects duplicate normalized names") { tmp { root -> rejects<IllegalArgumentException> {
        ArchiveCore.create(File(root,"a.zip"),listOf(ArchiveInput("a") { ByteArrayInputStream(byteArrayOf(1)) },ArchiveInput("a") { ByteArrayInputStream(byteArrayOf(2)) }),6,ArchiveLimits(1000,10,32))
    } } }
    test("hash verification handles case and whitespace") { tmp { root -> val f=File(root,"a"); f.writeText("abc"); check(Hashing.sha256(f)=="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"); check(Hashing.matches(f,Hashing.sha256(f).uppercase())) } }
    test("hash rejects invalid digest") { tmp { root -> val f=File(root,"a"); f.writeText("abc"); rejects<IllegalArgumentException> { Hashing.matches(f,"abc") } } }
    test("publication is idempotent and metadata is measured") { tmp { root ->
        val p=DurablePublication(root); val job=UUID.randomUUID().toString(); val t=p.reserve(job,"one","result.bin","application/octet-stream"); t.stageFile.writeText("real bytes")
        val record=p.publish(t); check(record.sizeBytes==10L); check(record.file.readText()=="real bytes"); check(p.publish(t)==record)
        check(p.reconcile().size==1)
    } }
    test("different bytes for same output key are integrity failure") { tmp { root ->
        val p=DurablePublication(root); val job=UUID.randomUUID().toString(); val t=p.reserve(job,"one","r.bin","application/octet-stream"); t.stageFile.writeText("a"); p.publish(t); t.stageFile.parentFile.mkdirs(); t.stageFile.writeText("b")
        rejects<IllegalStateException> { p.publish(t) }; check(p.reconcile().single().file.readText()=="a")
    } }
    test("journal recovery completes rename once") { tmp { root ->
        val p=DurablePublication(root); val t=p.reserve(UUID.randomUUID().toString(),"one","r.bin","application/octet-stream"); t.stageFile.writeText("recover"); p.prepare(t)
        val restored=DurablePublication(root); check(restored.reconcile().single().file.readText()=="recover"); check(restored.reconcile().size==1)
    } }
    test("missing staged output cannot publish success") { tmp { root -> val p=DurablePublication(root); val t=p.reserve(UUID.randomUUID().toString(),"a","r.bin","application/octet-stream"); rejects<IllegalArgumentException> { p.publish(t) }; check(p.reconcile().isEmpty()) } }
    test("URL validation never invents network metadata") { val url=HttpPolicy.url("https://example.org/file?q=signed"); check(url.scheme=="https"); rejects<IllegalArgumentException> { HttpPolicy.url("https://") }; rejects<IllegalArgumentException> { HttpPolicy.url("https://user:pass@example.org/x") } }
    test("cleartext needs explicit consent") { rejects<IllegalArgumentException> { HttpPolicy.url("http://example.org/f") }; check(HttpPolicy.url("http://example.org/f",true).scheme=="http") }
    test("valid resume requires exact representation range") { ResumePolicy.validate(10,"\"v1\"",null,20,206,mapOf("content-range" to "bytes 10-19/20","etag" to "\"v1\"","content-length" to "10")) }
    test("200 to range never appends") { rejects<RestartRequired> { ResumePolicy.validate(10,"\"v1\"",null,20,200,emptyMap()) } }
    test("wrong range and changed validator are rejected") { for (h in listOf(mapOf("content-range" to "bytes 9-19/20","etag" to "\"v1\""),mapOf("content-range" to "bytes 10-19/20","etag" to "\"v2\""))) rejects<RestartRequired> { ResumePolicy.validate(10,"\"v1\"",null,20,206,h) } }
    test("missing and weak resume validators are rejected") { for (tag in listOf(null,"W/\"v1\"")) rejects<RestartRequired> { ResumePolicy.validate(10,tag,null,20,206,mapOf("content-range" to "bytes 10-19/20")) } }
    test("legacy and security gates stay closed") { check(!Capabilities.isEnabled("legacy_demo_engine")); check(!Capabilities.isEnabled("xf_05")); check(!Capabilities.isEnabled("xp_redaction")) }
    test("keep both respects case-insensitive filesystem names") { check(SafeNames.unique(setOf("Photo.JPG"),"photo.jpg")=="photo (1).jpg") }
    test("padding without upscaling keeps requested canvas") {
        val g=ImageGeometry.resolve(100,50,400,400,FitMode.PAD,false)
        check(g.width==400 && g.height==400); check(g.draw.width==100.0 && g.draw.height==50.0)
    }
    test("ZIP directory entries cannot hide unaccounted payload") { tmp { root ->
        val z=File(root,"dirs.zip"); zip(z,listOf("dir/" to ByteArray(10000)))
        rejects<IllegalArgumentException> { ArchiveCore.extract(z,File(root,"out"),ArchiveLimits(20000,10,32)) }
        check(!File(root,"out").exists())
    } }
    test("HTTP scheme normalization cannot bypass cleartext policy") { check(HttpPolicy.url("HTTP://example.org/file",true).scheme=="http");check(HttpPolicy.url("HTTPS://example.org/file").scheme=="https") }
    test("legacy batch parser never claims an HTTP response or size") {
        val result=com.jumlo.app.data.engine.PureDomainAlgorithms.parseBatchUrls("https://example.org/q1.zip").single()
        check(result.estimatedSizeLabel=="Unknown");check(result.statusReason=="Not requested")
    }
    test("legacy batch parser validates host not just a prefix") {
        val result=com.jumlo.app.data.engine.PureDomainAlgorithms.parseBatchUrls("https://").single()
        check(result.status==com.jumlo.app.model.UrlValidationStatus.MALFORMED)
    }
    test("legacy page parser rejects reversed ranges") { check(com.jumlo.app.data.engine.PureDomainAlgorithms.parsePageRangeSyntax("3-1",4).isFailure) }
    test("publication recovery retains validated metadata without guessing from MIME") { tmp { root ->
        val p=DurablePublication(root)
        val metadata="{\"pageCount\":3}"
        val t=p.reserve(UUID.randomUUID().toString(),"a","result.pdf","application/pdf").copy(metadataJson=metadata)
        t.stageFile.writeText("fixture bytes: core does not parse media")
        p.prepare(t)
        val restored=DurablePublication(root).reconcile().single()
        check(restored.metadataJson==metadata)
        check(restored.file.readText()=="fixture bytes: core does not parse media")
    } }
    test("publication identity rejects changed metadata for the same output") { tmp { root ->
        val p=DurablePublication(root)
        val t=p.reserve(UUID.randomUUID().toString(),"a","result.bin","application/octet-stream")
        t.stageFile.writeText("a");p.publish(t)
        rejects<IllegalStateException> { p.publish(t.copy(metadataJson="{\"width\":1}")) }
        check(p.reconcile().single().metadataJson=="{}")
    } }
    test("page range overflow is rejected rather than coerced to a single page") {
        rejects<IllegalArgumentException> { PageSelection.parse("1-99999999999999999999",10) }
    }
    test("media downloader capability has a real dispatch candidate") { check(Capabilities.isEnabled("dl_media")) }
    mediaPolicyTests(::test)
    mediaProcessTests(::test)
    if(System.getenv("UU_TEST_REAL_MEDIA")=="1") mediaFileTests(::test)
    transferTests(::test)
    println("RESULT passed=$passed failed=$failed")
    check(failed==0) { "$failed tests failed" }
}
