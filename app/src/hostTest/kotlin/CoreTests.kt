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
    test("PDF crop top-left coordinates map to PDF bottom-left") {
        val upper = PdfCropCoordinates.fromTopLeft(0f, 0f, 600f, 800f, 0.0, 0.0, 1.0, 0.25)
        check(upper == PdfCropRect(0f, 600f, 600f, 200f))
        val lower = PdfCropCoordinates.fromTopLeft(10f, 20f, 600f, 800f, 0.25, 0.75, 0.75, 1.0)
        check(lower == PdfCropRect(160f, 20f, 300f, 200f))
        check(PdfCropCoordinates.fromTopLeft(10f, 20f, 600f, 800f, 0.0, 0.0, 1.0, 1.0) ==
            PdfCropRect(10f, 20f, 600f, 800f))
    }
    test("PDF crop rejects invalid and nonfinite input") {
        rejects<IllegalArgumentException> { PdfCropCoordinates.fromTopLeft(0f, 0f, 600f, 800f, 0.3, 0.4, 0.2, 0.8) }
        rejects<IllegalArgumentException> { PdfCropCoordinates.fromTopLeft(0f, 0f, 600f, 800f, 0.0, Double.NaN, 1.0, 1.0) }
        rejects<IllegalArgumentException> { PdfCropCoordinates.fromTopLeft(0f, 0f, Float.POSITIVE_INFINITY, 800f, 0.0, 0.0, 1.0, 1.0) }
    }
    test("Drive link parser accepts only official HTTPS link locations") {
        val id = "1abcDE_fgHiJ23KLmno"
        check(DriveLink.fileId(id) == id)
        check(DriveLink.fileId("https://drive.google.com/file/d/$id/view?usp=sharing") == id)
        check(DriveLink.fileId("https://drive.google.com/open?id=$id&usp=sharing") == id)
        check(DriveLink.fileId("https://docs.google.com/document/d/$id/edit") == id)
        check(DriveLink.fileId("https://docs.google.com/spreadsheets/d/$id/edit") == id)
    }
    test("Drive link parser rejects impostors, credentials and embedded links") {
        val id = "1abcDE_fgHiJ23KLmno"
        for (value in listOf(
            "https://drive.google.com.evil.example/file/d/$id/view",
            "https://drive.google.com@evil.example/file/d/$id/view",
            "https://evil.example/redirect?next=https://drive.google.com/file/d/$id",
            "http://drive.google.com/file/d/$id/view",
            "https://drive.google.com:444/file/d/$id/view",
            "https://drive.google.com/file/d/$id/view#fragment",
            "https://drive.google.com/open?id=$id&id=$id",
            "https://drive.google.com/drive/folders/$id",
            "look at https://docs.google.com/document/d/$id/edit"
        )) rejects<IllegalArgumentException> { DriveLink.fileId(value) }
    }
    test("zip roundtrip reads original bytes") { tmp { root ->
        val a=ByteArray(16384) { (it*17).toByte() }; val z=File(root,"a.zip")
        ArchiveCore.create(z,listOf(ArchiveInput("folder/a.bin") { ByteArrayInputStream(a) }),6,ArchiveLimits(100000,20,32))
        val dest=File(root,"out"); val files=ArchiveCore.extract(z,dest,ArchiveLimits(100000,20,32))
        check(files.single().readBytes().contentEquals(a)); check(ZipFile(z).use { it.size() }==1)
    } }
    test("Unix symlink ZIP member is materialized only as inert bytes") { tmp { root ->
        val z=File(root,"symlink.zip");zip(z,listOf("link" to "../outside".toByteArray()))
        val bytes=z.readBytes()
        val central=bytes.indices.first { i -> i+4<bytes.size && bytes[i]==0x50.toByte() && bytes[i+1]==0x4b.toByte() && bytes[i+2]==1.toByte() && bytes[i+3]==2.toByte() }
        bytes[central+5]=3 // Unix creator
        val external=0xA1FF0000L // symlink + 0777 in upper Unix mode bits
        for(i in 0..3) bytes[central+38+i]=(external shr (8*i)).toByte()
        z.writeBytes(bytes);val original=Hashing.sha256(z)
        val extracted=ArchiveCore.extract(z,File(root,"out"),ArchiveLimits(1000,10,32)).single()
        check(!Files.isSymbolicLink(extracted.toPath()) && extracted.readText()=="../outside")
        check(!File(root,"outside").exists() && Hashing.sha256(z)==original)
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
    test("ZIP Unicode-equivalent paths are rejected before extraction") { tmp { root ->
        val z=File(root,"unicode.zip");zip(z,listOf("caf\u00e9.txt" to "one".toByteArray(),"cafe\u0301.txt" to "two".toByteArray()))
        val before=Hashing.sha256(z)
        rejects<IllegalArgumentException> { ArchiveCore.extract(z,File(root,"out"),ArchiveLimits(1000,10,32)) }
        check(!File(root,"out").exists() && Hashing.sha256(z)==before)
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
    test("error log export includes bounded rotation and clear deletes both files") { tmp { root ->
        val file = File(root, "error.log")
        val log = ErrorLog(file, 15)
        log.record("TEST_ERROR")
        log.record("SECOND_ERROR")
        check(File(root, "error.log.1").isFile)
        val export = log.read()
        check(export.contains("TEST_ERROR") && export.contains("SECOND_ERROR"))
        log.clear()
        check(!file.exists() && !File(root, "error.log.1").exists())
        check(log.read().isEmpty())
    } }
    test("error log never includes credentials or signed links after rotation") { tmp { root ->
        val log = ErrorLog(File(root, "error.log"), 15)
        log.record("https://example.org/?token=SECRET")
        log.record("password=hunter2")
        log.record("STABLE_CODE", "dl_02")
        log.record("SECOND_ERROR", "dl_03")
        val exported = log.read()
        check("SECRET" !in exported && "hunter2" !in exported && "https://" !in exported)
        check("STABLE_CODE" in exported && "SECOND_ERROR" in exported)
    } }
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
    test("unified media capability is registered for the standard build") {
        check(Capabilities.isEnabled("dl_media"))
        check(CapabilityPolicy.canProcess("dl_media"))
    }
    test("release security-sensitive candidates remain unavailable") {
        for (id in listOf("pdf_protect", "pdf_redact", "file_encrypted_zip", "file_drive", "pdf_ocr", "webpage_pdf"))
            check(!Capabilities.isEnabled(id)) { "$id must be gated" }
    }
    test("direct link parser distinguishes media landing pages from real files") {
        check(!DownloadLinkPolicy.isDirect("https://www.youtube.com/watch?v=abc123"))
        check(!DownloadLinkPolicy.isDirect("https://m.instagram.com/reel/abc"))
        check(!DownloadLinkPolicy.isDirect("https://drive.google.com/file/d/abc/view"))
        check(DownloadLinkPolicy.isDirect("https://youtube.com.evil.example/video.mp4"))
        check(DownloadLinkPolicy.isDirect("https://files.example.com/archive.ZIP?token=123"))
        check(DownloadLinkPolicy.isDirect("https://files.example.com/file.pdf"))
    }
    test("download automatic naming takes safe title and exact MIME extension") {
        check(DownloadLinkPolicy.cleanName("https://example.com/generate?x=1", "attachment; filename=record", "application/pdf")=="record.pdf")
        check(DownloadLinkPolicy.cleanName("https://example.com/anything", "attachment; filename*=UTF-8''hello%20world.mp4", "video/mp4")=="hello world.mp4")
        check(DownloadLinkPolicy.cleanName("https://example.com/video.mp3", null, "video/mp4")=="video.mp4")
        check(!DownloadLinkPolicy.cleanName("https://example.com/file.pdf", "attachment; filename=../evil.zip", "application/zip").contains('/'))
    }
    test("HTML payloads are never published as file results") {
        check(DownloadLinkPolicy.unexpectedHtml("text/html", byteArrayOf(1,2,3)))
        check(DownloadLinkPolicy.unexpectedHtml("application/octet-stream", " <!doctype html><html>".toByteArray()))
        check(!DownloadLinkPolicy.unexpectedHtml("video/mp4", byteArrayOf(0,0,0,16,102,116,121,112)))
    }
    test("recent tools are real sessions and duplicate entries never leak") {
        val recent=ToolUsagePolicy.recent(listOf("pdf_01","img_02"),"img_02")
        check(recent==listOf("img_02","pdf_01"))
        check(ToolUsagePolicy.recent(recent,"../invalid")==recent)
        val counts=ToolUsagePolicy.count(ToolUsagePolicy.count(emptyMap(),"img_02"),"img_02")
        check(counts["img_02"]==2)
        check(ToolUsagePolicy.top(mapOf("pdf_02" to 4,"img_02" to 2),setOf("img_02"))==listOf("img_02"))
    }
    test("output names derive safely from source content without asking the user") {
        check(AutoOutputName.fromSource("pdf_03","Annual report.pdf")=="Annual report - Merged.pdf")
        check(AutoOutputName.fromSource("file_07","backup.zip")=="backup - Archive.zip")
        check(AutoOutputName.fromSource("pdf_02","../../unsafe.jpg")?.contains('/')==false)
        check(AutoOutputName.fromSource("img_03","image.png")==null)
    }
    test("downloads folder categories and duplicate names are deterministic") {
        check(StoragePaths.relativePath("audio/mpeg")=="Download/Jumlo/Audio/")
        check(StoragePaths.relativePath("application/pdf")=="Download/Jumlo/PDF/")
        check(StoragePaths.canonicalDownloadFolder(null)=="Download/Jumlo")
        check(StoragePaths.canonicalDownloadFolder("Download/Jevlo")=="Download/Jumlo")
        check(StoragePaths.canonicalDownloadFolder("content://com.android.providers.downloads.documents/tree/42")=="content://com.android.providers.downloads.documents/tree/42")
        check(StoragePaths.category("image/webp")=="Images")
        check(StoragePaths.uniqueName("work.pdf",setOf("work.pdf","work (2).pdf"))=="work (3).pdf")
        rejects<IllegalArgumentException> { StoragePaths.uniqueName("../../escape.pdf",emptySet()) }
    }
    test("navigation-only download settings is a supported capability") { check(Capabilities.isEnabled("dl_settings")) }
    test("publication normalizes names once before journal recovery") { tmp { root ->
        val p=DurablePublication(root)
        val ticket=p.reserve(UUID.randomUUID().toString(),"unicode","cafe\u0301.bin","application/octet-stream")
        ticket.stageFile.writeText("original bytes")
        val published=p.publish(ticket)
        check(published.displayName=="caf\u00e9.bin")
        check(DurablePublication(root).reconcile().single().file.readText()=="original bytes")
    } }
    test("navigation routes can never enter the processing dispatcher policy") {
        for (id in listOf("dl_settings", "file_02", "pdf_01", "file_trash")) {
            check(CapabilityPolicy.availability(id).visible)
            check(!CapabilityPolicy.canProcess(id))
        }
        check(CapabilityPolicy.canProcess("file_07"))
        check(!CapabilityPolicy.availability("unknown-direct-route").visible)
    }
    test("searchable OCR never silently truncates or substitutes recognized text") {
        check(OcrTextPolicy.requireRepresentable("First line\nSecond line")==listOf("First line","Second line"))
        rejects<IllegalArgumentException> { OcrTextPolicy.requireRepresentable("Arabic: \u0627") }
        rejects<IllegalArgumentException> { OcrTextPolicy.requireRepresentable("x".repeat(501)) }
        rejects<IllegalArgumentException> { OcrTextPolicy.requireRepresentable((1..201).joinToString("\n") { "line" }) }
    }
    test("seeded page-range properties preserve order and bounds (256 cases)") {
        val rng = kotlin.random.Random(0x4A554D4C)
        repeat(256) {
            val pageCount = rng.nextInt(1, 48)
            val expected = linkedSetOf<Int>()
            val segments = (0 until rng.nextInt(1, 14)).map {
                val start = rng.nextInt(1, pageCount + 1)
                val end = rng.nextInt(start, pageCount + 1)
                (start..end).forEach(expected::add)
                if (start == end || rng.nextBoolean()) "$start" else "$start-$end"
            }
            // Reconstruct expectation from the actual emitted syntax, rather
            // than a segment that deliberately requested one page of a range.
            val oracle = linkedSetOf<Int>()
            segments.forEach { part ->
                val range = part.split('-').map(String::toInt)
                (range[0]..range.last()).forEach(oracle::add)
            }
            val actual = PageSelection.parse(segments.joinToString(","), pageCount)
            check(actual == oracle.toList()) { "Range property mismatch: $segments" }
            check(actual.size <= pageCount && actual.all { it in 1..pageCount })
        }
    }
    test("seeded archive-entry path property blocks traversal (256 cases)") { tmp { root ->
        val rng = kotlin.random.Random(0x1234ABCD)
        val badPrefixes = listOf("../", "safe/../", "safe//", "/abs/", "C:/", "safe\\")
        repeat(256) {
            val filename = "file${rng.nextInt(1, 100000)}.dat"
            val dangerous = badPrefixes[it % badPrefixes.size] + filename
            rejects<IllegalArgumentException> { SafeNames.entry(root, dangerous) }
            val safe = SafeNames.entry(root, "safe/sub/$filename")
            check(safe.isAbsolute && safe.path.startsWith(root.canonicalPath + File.separator))
        }
    } }
    test("seeded HTTPS URL policy excludes credentials and cleartext (256 cases)") {
        val rng = kotlin.random.Random(0x76543210)
        repeat(256) {
            val suffix = "file${rng.nextInt(1, 100000)}.bin"
            check(HttpPolicy.url("https://example.org/$suffix").host == "example.org")
            rejects<IllegalArgumentException> { HttpPolicy.url("http://example.org/$suffix") }
            rejects<IllegalArgumentException> { HttpPolicy.url("https://user:pass@example.org/$suffix") }
            rejects<IllegalArgumentException> { HttpPolicy.url("https://example.org/$suffix#fragment") }
        }
    }
    mediaPolicyTests(::test)
    mediaProcessTests(::test)
    if(System.getenv("UU_TEST_REAL_MEDIA")=="1") mediaFileTests(::test)
    transferTests(::test)
    trashTests(::test)
    copyDestinationTests(::test)
    println("RESULT passed=$passed failed=$failed")
    check(failed==0) { "$failed tests failed" }
}
