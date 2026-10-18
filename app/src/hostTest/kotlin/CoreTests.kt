package com.jumlo.app.hosttest

import com.jumlo.app.core.*
import com.jumlo.app.core.media.MediaSubmissionSlot
import java.io.*
import java.nio.file.Files
import java.util.UUID
import java.util.zip.*

private var passed = 0
private var failed = 0
private fun test(name: String, block: () -> Unit) {
    try { block(); passed++; println("PASS $name") }
    catch (e: Throwable) { failed++; println("FAIL $name: ${e.javaClass.simpleName}: ${e.message}"); e.printStackTrace() }
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
    test("download compression chooses a truthful image name and format") {
        val compressed=DownloadCompressionPolicy.choose("صورة جميلة.png","image/png",4000,1500)
        check(compressed.useCompressed)
        check(compressed.filename=="صورة جميلة.jpg")
        check(compressed.mimeType=="image/jpeg")
        val retained=DownloadCompressionPolicy.choose("graphic.webp","image/webp",800,950)
        check(!retained.useCompressed)
        check(retained.filename=="graphic.webp" && retained.mimeType=="image/webp")
        val tiny=DownloadCompressionPolicy.choose("small.jpg","image/jpeg",100,250)
        check(!tiny.useCompressed)
        val emoji=DownloadCompressionPolicy.choose("لقطة 📷.png","image/png",12000,3000)
        check(emoji.filename=="لقطة 📷.jpg")
    }
    test("manual pause is durable and distinct from crash recovery") {
        check(JobLifecyclePolicy.shouldRecover("INTERRUPTED"))
        check(JobLifecyclePolicy.shouldRecover("RUNNING"))
        check(!JobLifecyclePolicy.shouldRecover("PAUSED"))
        check(!JobLifecyclePolicy.shouldRecover("WAITING_FOR_INPUT"))
        check(JobLifecyclePolicy.canPause("RUNNING"))
        check(!JobLifecyclePolicy.canPause("FINALIZING"))
    }
    test("PDF compression image decoding has bounded ARGB heap cost") {
        val heap = 256L * 1024 * 1024
        check(PdfCompressionPolicy.canDecodeImage(1000, 1000, heap))
        check(!PdfCompressionPolicy.canDecodeImage(4000, 3000, heap))
        check(!PdfCompressionPolicy.canDecodeImage(Int.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE))
        check(!PdfCompressionPolicy.canDecodeImage(0, 100, heap))
        check(!PdfCompressionPolicy.canDecodeImage(-1, 100, heap))
        check(!PdfCompressionPolicy.canDecodeImage(1000, 1000, 0))
        check(PdfCompressionPolicy.canDecodeImage(1000, 1000, Long.MAX_VALUE))
    }
    test("workflow batch accepts selected outputs in visual order") {
        val files = listOf(
            OutputFileChoice("00000000-0000-4000-8000-000000000001", "a.pdf", "application/pdf", 300),
            OutputFileChoice("00000000-0000-4000-8000-000000000002", "b.pdf", "application/pdf", 400)
        )
        check(OutputWorkflowPolicy.validate(files, "pdf_03").map { it.name } == listOf("a.pdf", "b.pdf"))
        check(OutputWorkflowPolicy.validate(files, "file_07").size == 2)
        check(OutputWorkflowPolicy.reuseTool("application/pdf") == "pdf_04")
        check(OutputWorkflowPolicy.reuseTool("image/jpeg") == "img_01")
        check(OutputWorkflowPolicy.reuseTool("application/zip") == "file_08")
        check(OutputWorkflowPolicy.reuseTool("video/mp4") == "file_07")
    }
    test("large file workflows do not reject valid inputs at 4 GiB or 100 files") {
        val one = OutputFileChoice("00000000-0000-4000-8000-000000000003", "large.pdf", "application/pdf", 6L * 1024 * 1024 * 1024)
        val many = List(250) { index -> one.copy(id = java.util.UUID.nameUUIDFromBytes("large-batch:$index".toByteArray()).toString()) }
        check(OutputWorkflowPolicy.validate(listOf(one), "pdf_04").single().bytes == one.bytes)
        check(OutputWorkflowPolicy.validate(many, "file_07").size == 250)
        check(ResourceBudgetPolicy.maxImportBytes > 4L * 1024 * 1024 * 1024)
    }
    test("large PDFs can describe more than 100000 pages without a magic size rejection") {
        check(PageSelection.parse("120001", 120001) == listOf(120001))
        check(PdfPageOrder.parse("120001", 120001) == listOf(120001))
        check(PdfSplitPlan.splitAfter(120001, "60000") == listOf(0 to 60000, 60000 to 60001))
    }
    test("large page fanouts become a ZIP rather than failing the output-count budget") {
        check(LargeResultStrategy.forPageOutputs(512, 512) == LargeResultStrategy.INDIVIDUAL)
        check(LargeResultStrategy.forPageOutputs(513, 512) == LargeResultStrategy.ARCHIVE)
        check(LargeResultStrategy.forPageOutputs(12000, 512) == LargeResultStrategy.ARCHIVE)
    }
    test("more than 512 generated page results stream into a CRC-verified ZIP") { tmp { root ->
        val archive = File(root, "many-pages.zip")
        val inputs = (1..600).map { page -> ArchiveInput("Page %06d.pdf".format(page)) {
            ByteArrayInputStream("page:$page".toByteArray(Charsets.UTF_8))
        } }
        ArchiveCore.create(archive, inputs, 6, ArchiveLimits(Long.MAX_VALUE, 600, 1))
        check(ArchiveCore.verify(archive, ArchiveLimits(Long.MAX_VALUE, 600, 1)) == ArchiveSummary(600, (1..600).sumOf { "page:$it".length }.toLong()))
        ZipFile(archive).use { file ->
            check(file.size() == 600)
            check(file.getInputStream(file.getEntry("Page 000600.pdf")).bufferedReader().readText() == "page:600")
        }
    } }
    test("cached immutable sources are reused only inside their exact private folder") { tmp { root ->
        val directory = File(root, "inbound").apply { mkdirs() }
        val id = java.util.UUID.randomUUID().toString()
        val owned = File(directory, "$id.bin").apply { writeText("immutable") }
        check(SourceSnapshotPolicy.cachedFile(directory,id,owned.absolutePath) == owned.canonicalFile)
        check(SourceSnapshotPolicy.cachedFile(directory,id,File(root,"escape.bin").absolutePath) == null)
        check(SourceSnapshotPolicy.cachedFile(directory,id,File(directory,"some-other.bin").absolutePath) == null)
        val link = File(directory,"another-id.bin")
        runCatching { java.nio.file.Files.createSymbolicLink(link.toPath(),owned.toPath()) }
        check(SourceSnapshotPolicy.cachedFile(directory,id,link.absolutePath) == null)
        check(SourceSnapshotPolicy.cachedFile(directory,id,null) == null)
    } }
    test("very long document text comparison is lazy and stops at first mismatch") {
        var left = 0
        var right = 0
        val mismatch = PageTextComparison.firstMismatch(15000,
            left = { index -> left++; if (index == 14500) "revised invoice" else "page $index" },
            right = { index -> right++; if (index == 14500) "original invoice" else "page $index" })
        check(mismatch == 14501)
        check(left == 14501 && right == 14501)
        check(PageTextComparison.firstMismatch(2, { "  Hello\n World   " }, { "Hello World" }) == null)
    }
    test("document text search stops without accumulating all page text") {
        var scanned = 0
        check(PageTextComparison.anyPageContains(100000, "invoice") { index ->
            scanned++
            if (index == 100) "invoice 103" else "boring"
        })
        check(scanned == 101)
        check(!PageTextComparison.anyPageContains(0, "invoice") { error("should not be read") })
    }
    test("workflow refuses incompatible, duplicate, oversized and invalid sources") {
        val one = OutputFileChoice("00000000-0000-4000-8000-000000000003", "photo.png", "image/png", 100)
        check(OutputWorkflowPolicy.validate(listOf(one), "pdf_02").size == 1)
        rejects<IllegalArgumentException> { OutputWorkflowPolicy.validate(listOf(one), "pdf_03") }
        rejects<IllegalArgumentException> { OutputWorkflowPolicy.validate(listOf(one, one), "file_07") }
        rejects<IllegalArgumentException> { OutputWorkflowPolicy.validate(listOf(one.copy(bytes = -1)), "file_07") }
        rejects<IllegalArgumentException> { OutputWorkflowPolicy.validate(listOf(one.copy(id = "../../arbitrary")), "file_07") }
        rejects<IllegalArgumentException> { OutputWorkflowPolicy.validate(listOf(one.copy(mime = "application/pdf")), "pdf_02") }
        rejects<IllegalArgumentException> { OutputWorkflowPolicy.validate(List(1001) { i -> one.copy(id=java.util.UUID.nameUUIDFromBytes("$i".toByteArray()).toString()) }, "file_07") }
        rejects<IllegalArgumentException> { OutputWorkflowPolicy.validate(listOf(one), "not_a_real_tool") }
    }
    test("pinned outputs are distinct, retained and bounded") {
        val first = "00000000-0000-4000-8000-000000000003"
        check(OutputFavorites.toggle(emptyList(), first) == listOf(first))
        check(OutputFavorites.toggle(listOf(first), first).isEmpty())
        val many=(0 until 501).map { java.util.UUID.nameUUIDFromBytes("favorite:$it".toByteArray()).toString() }
        val result=OutputFavorites.toggle(many.take(500),many.last())
        check(result.size==500 && result.first()==many[1] && result.last()==many.last())
        check(OutputFavorites.sanitize(listOf(first, "bad-id", first)) == listOf(first))
    }
    test("generic document imports identify real signatures, never merely file extensions") {
        val pdf="%PDF-1.7\nexample".toByteArray()
        check(SourceMediaType.resolve(null,"invoice.pdf",pdf)=="application/pdf")
        check(SourceMediaType.resolve("application/octet-stream","invoice.pdf",pdf)=="application/pdf")
        check(SourceMediaType.resolve(null,"invoice.pdf","not pdf".toByteArray())=="application/octet-stream")
        check(SourceMediaType.resolve("text/plain","strange.pdf",pdf)=="text/plain")
    }
    test("generic image archive and MP4 imports use bounded magic bytes") {
        val png=byteArrayOf(-119,80,78,71,13,10,26,10,0,0,0,0)
        val jpeg=byteArrayOf(-1,-40,-1,0)
        val zip=byteArrayOf(80,75,3,4,0,0,0,0)
        val heic=byteArrayOf(0,0,0,20,102,116,121,112,104,101,105,99,0,0,0,0)
        val mp4=byteArrayOf(0,0,0,20,102,116,121,112,105,115,111,109,0,0,0,0)
        check(SourceMediaType.resolve(null,"picture.png",png)=="image/png")
        check(SourceMediaType.resolve("application/octet-stream","picture.jpeg",jpeg)=="image/jpeg")
        check(SourceMediaType.resolve(null,"collection.zip",zip)=="application/zip")
        check(SourceMediaType.resolve(null,"photo.heic",heic)=="image/heic")
        check(SourceMediaType.resolve(null,"clip.mp4",mp4)=="video/mp4")
        check(SourceMediaType.resolve(null,"song.m4a",mp4)=="audio/mp4")
        val avif=byteArrayOf(0,0,0,20,102,116,121,112,97,118,105,102,0,0,0,0)
        val unknown=byteArrayOf(0,0,0,20,102,116,121,112,120,120,120,120,0,0,0,0)
        check(SourceMediaType.resolve(null,"art.avif",avif)=="image/avif")
        check(SourceMediaType.resolve(null,"bad.mp4",unknown)=="application/octet-stream")
    }
    test("generic webpages enter media analysis rather than blind HTML download") {
        check(DownloadLinkPolicy.prefersMediaAnalysis("https://unknown.example.org/watch/123?origin=share"))
        check(DownloadLinkPolicy.prefersMediaAnalysis("https://www.youtube.com/watch?v=test"))
        check(!DownloadLinkPolicy.prefersMediaAnalysis("https://cdn.example.org/assets/report.pdf"))
        check(!DownloadLinkPolicy.prefersMediaAnalysis("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view"))
    }
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
        check(DriveLink.fileId("https://drive.google.com/file/u/0/d/$id/view?usp=sharing") == id)
        check(DriveLink.fileId("https://docs.google.com/document/u/1/d/$id/edit") == id)
        check(DriveLink.toDirectDownloadUrl("https://drive.google.com/file/u/0/d/$id/view#heading=h.123") == "https://drive.usercontent.google.com/download?id=$id&export=download&confirm=t")
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
    test("archive publication has a streaming payload verifier, not only a central-directory check") {
        check(ArchiveCore::class.java.methods.any { it.name == "verify" }) {
            "ArchiveCore.verify must inspect actual compressed entry bytes and CRC before declaring success"
        }
    }
    test("mobile processing has finite import, archive and output-count budgets") {
        check(Class.forName("com.jumlo.app.core.ResourceBudgetPolicy").declaredFields.isNotEmpty())
    }
    test("archive payload verifier reads real bytes and rejects CRC-tampered ZIP") { tmp { root ->
        val payload="A customer invoice with valid content".toByteArray()
        val z=File(root,"archive.zip")
        val crc=java.util.zip.CRC32().apply { update(payload) }.value
        ZipOutputStream(z.outputStream()).use { out ->
            val member=ZipEntry("invoice.txt").apply { method=ZipEntry.STORED; size=payload.size.toLong(); compressedSize=size; this.crc=crc }
            out.putNextEntry(member); out.write(payload); out.closeEntry()
        }
        val limits=ArchiveLimits(1000,10,10)
        check(ArchiveCore.verify(z,limits)==ArchiveSummary(1,payload.size.toLong()))
        val damaged=z.readBytes()
        // For a STORED member, payload starts after the 30-byte local header and its filename.
        val nameLength=(damaged[26].toInt() and 255) or ((damaged[27].toInt() and 255) shl 8)
        val extraLength=(damaged[28].toInt() and 255) or ((damaged[29].toInt() and 255) shl 8)
        damaged[30+nameLength+extraLength]=(damaged[30+nameLength+extraLength].toInt() xor 1).toByte()
        z.writeBytes(damaged)
        check(ZipFile(z).use { it.size() }==1) // Central directory still opens.
        check(runCatching { ArchiveCore.verify(z,limits) }.isFailure)
    } }
    test("archive verifier refuses decompression budgets and excess entry counts") { tmp { root ->
        val z=File(root,"large.zip");zip(z,listOf("one.bin" to ByteArray(10000), "two.bin" to byteArrayOf(1)))
        rejects<IllegalArgumentException> { ArchiveCore.verify(z, ArchiveLimits(10,10,10)) }
        rejects<IllegalArgumentException> { ArchiveCore.verify(z, ArchiveLimits(20000,1,10)) }
        check(ArchiveCore.verify(z, ArchiveLimits(20000,10,10)).entryCount==2)
    } }
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
    test("Arabic and emoji collision resolution stays within 200 UTF-8 bytes without splitting surrogates") {
        val longArabic = "\u0645\u0633\u062a\u0646\u062f_\u0639\u0631\u0628\u064a_".repeat(12) + "\uD83D\uDCF7"
        val clean = AutoOutputName.cleanBaseName("$longArabic.pdf")
        check(clean.toByteArray(Charsets.UTF_8).size <= 140)
        val derived = AutoOutputName.fromSource("pdf_03", "$longArabic.pdf")!!
        check(derived.toByteArray(Charsets.UTF_8).size <= 200)
        val unique1 = SafeNames.unique(setOf(derived), derived)
        check(unique1.endsWith(" (1).pdf") && unique1.toByteArray(Charsets.UTF_8).size <= 200)
        val unique2 = StoragePaths.uniqueName(derived, setOf(derived, unique1))
        check(unique2.endsWith(" (2).pdf") && unique2.toByteArray(Charsets.UTF_8).size <= 200)
    }
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
    test("media job idempotency binds network restrictions without merging different intents") {
        val original=MediaSubmissionKey.key("direct:ORIGINAL","https://example.org/file.pdf","file.pdf",false,false,false)
        val wifi=MediaSubmissionKey.key("direct:ORIGINAL","https://example.org/file.pdf","file.pdf",true,false,false)
        val unmetered=MediaSubmissionKey.key("direct:ORIGINAL","https://example.org/file.pdf","file.pdf",false,true,false)
        val allowHttp=MediaSubmissionKey.key("direct:ORIGINAL","https://example.org/file.pdf","file.pdf",false,false,true)
        check(setOf(original,wifi,unmetered,allowHttp).size==4)
        check(MediaSubmissionKey.key("direct:ORIGINAL","https://example.org/file.pdf","file.pdf",false,false,false)==original)
        check(MediaSubmissionKey.key("ab","c","name",false,false,false)!=MediaSubmissionKey.key("a","bc","name",false,false,false))
        val submissions=MediaSubmissionSlot<String>()
        var serial=0
        check(submissions.prepare(original) { (++serial).toString() }=="1")
        check(submissions.prepare(original) { (++serial).toString() }=="1")
        check(submissions.prepare(wifi) { (++serial).toString() }=="2")
    }
    test("download names keep whole Unicode code points and fit the public filename budget") {
        val emoji = "\uD83D\uDE00"
        val title = emoji.repeat(70) + ".pdf"
        val safe = DownloadLinkPolicy.cleanName("https://example.org/get", "attachment; filename=\"$title\"", "application/pdf")
        check(safe.endsWith(".pdf") && safe.contains(emoji))
        check(safe.toByteArray(Charsets.UTF_8).size <= 200)
        check(safe.codePoints().noneMatch { cp -> cp in 0xD800..0xDFFF })
        check(SafeNames.basename(safe) == safe)
    }
    test("automatic names and collision suffixes never split a UTF-16 surrogate pair") {
        val emoji = "\uD83D\uDE00"
        val long = "a".repeat(179) + emoji + ".pdf"
        val suffixed = StoragePaths.uniqueName(long, setOf(long))
        check(suffixed.contains(emoji) && suffixed.endsWith(" (2).pdf"))
        check(suffixed.codePoints().noneMatch { cp -> cp in 0xD800..0xDFFF })
        val suggested = AutoOutputName.cleanBaseName("a".repeat(111) + emoji + ".pdf")
        check(suggested.codePoints().noneMatch { cp -> cp in 0xD800..0xDFFF })
    }
    test("download naming keeps RFC5987 plus signs and catches case-only collisions") {
        check(DownloadLinkPolicy.cleanName("https://example.org/get", "attachment; filename*=UTF-8''hello+team.pdf", "application/pdf") == "hello+team.pdf")
        check(StoragePaths.uniqueName("report.pdf", setOf("REPORT.pdf")) == "report (2).pdf")
        rejects<IllegalArgumentException> { SafeNames.basename("\uD83D.pdf") }
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
        check(AutoOutputName.pageImageName("Report.pdf", 3, "png")=="Report - صفحة 003.png")
        check(AutoOutputName.splitPartName("MySplit.pdf", "doc.pdf", 2)=="MySplit - 002.pdf")
        check(StoragePaths.relativePath("image/png", "Report")=="Download/Jumlo/Images/Report/")
        check(DownloadLinkPolicy.isGoogleDriveDownload("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view?usp=sharing"))
        check(DriveLink.toDirectDownloadUrl("https://drive.google.com/file/d/1abcDE_fgHiJ23KLmno/view?usp=sharing")=="https://drive.usercontent.google.com/download?id=1abcDE_fgHiJ23KLmno&export=download&confirm=t")
        check(DownloadLinkPolicy.detectPlatformName("https://www.youtube.com/watch?v=abc123")=="YouTube")
        check(DownloadLinkPolicy.detectPlatformName("https://www.tiktok.com/@u/video/1")=="TikTok")
        check(DownloadLinkPolicy.detectPlatformName("https://www.instagram.com/reel/abc")=="Instagram")
        check(DownloadLinkPolicy.detectPlatformName("https://www.facebook.com/watch/?v=1")=="Facebook")
        check(DownloadLinkPolicy.detectPlatformName("https://www.linkedin.com/posts/1")=="LinkedIn")
        check(DownloadLinkPolicy.classifyDirectFile("report.pdf", "application/pdf")==DirectFileKind.PDF)
    }
    test("downloads folder categories and duplicate names are deterministic") {
        check(StoragePaths.relativePath("audio/mpeg")=="Download/Jumlo/Audio/")
        check(StoragePaths.relativePath("application/pdf")=="Download/Jumlo/PDF/")
        check(StoragePaths.canonicalDownloadFolder(null)=="Download/Jumlo")
        check(StoragePaths.canonicalDownloadFolder("Download/OldDefault")=="Download/Jumlo")
        check(StoragePaths.canonicalDownloadFolder("")=="Download/Jumlo")
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
    test("HTML5 media discovery resolves allowed source tags without inventing streams") {
        val html = """<html><head><meta property="og:title" content="Summer &amp; Sea"></head>
            <body><video poster="poster.jpg"><source src="../media/movie.mp4" type="video/mp4"></video>
            <audio src='/audio/track.mp3'></audio><source src='javascript:alert(1)'></body></html>"""
        val found = com.jumlo.app.core.media.Html5MediaDiscovery.discover("https://example.org/watch/page", html)
        check(found.size == 2)
        check(found[0].url == "https://example.org/media/movie.mp4")
        check(found[0].extension == "mp4")
        check(found[1].url == "https://example.org/audio/track.mp3")
        check(com.jumlo.app.core.media.Html5MediaDiscovery.title(html) == "Summer & Sea")
    }
    test("HTML5 media discovery accepts declared OG video and rejects hostile links") {
        val html = """<meta property='og:video' content='https://cdn.example.net/clip.webm'>
            <source src='http://example.org/unsafe.mp4' type='video/mp4'>
            <a download href='https://example.org/file.mp4'>Video</a>
            <source src='data:video/mp4;base64,bad' type='video/mp4'>"""
        val found = com.jumlo.app.core.media.Html5MediaDiscovery.discover("https://example.org/watch",html)
        check(found.map { it.extension } == listOf("webm", "mp4"))
        check(found.none { it.url.contains("unsafe") || it.url.startsWith("data:") })
    }
    test("unprotected social OpenGraph media with signed extensionless URL requires later HTTP probe") {
        val html = """<meta property='og:video:secure_url' content='https://cdn.example.net/get?token=abc&amp;id=5'>
             <meta property='og:image' content='/covers/screen.webp'>"""
        val sources = com.jumlo.app.core.media.Html5MediaDiscovery.discover("https://social.example.org/view/3", html)
        check(sources.size == 1 && sources.single().url == "https://cdn.example.net/get?token=abc&id=5")
        check(sources.single().extension.isBlank()) // a probed MIME, never a fabricated MP4
        check(com.jumlo.app.core.media.Html5MediaDiscovery.thumbnail("https://social.example.org/view/3", html) == "https://social.example.org/covers/screen.webp")
    }
    test("JSON-LD contentUrl discovers a direct file but never treats embedUrl as video") {
        val html = """<script type='application/ld+json'>{"@type":"VideoObject", "embedUrl":"https://evil.example/click.mp4",
                 "contentUrl":"https:\/\/cdn.example.org\/clip.mp4?x=1\u0026y=2"}</script>"""
        val sources = com.jumlo.app.core.media.Html5MediaDiscovery.discover("https://example.org/watch", html)
        check(sources.map { it.url } == listOf("https://cdn.example.org/clip.mp4?x=1&y=2")) { "Discovered: $sources" }
    }
    test("static media discovery retries alternate HTML only when no playable media") {
        check(BrowserProfilePolicy.tryAlternatePage(false, 0, 2))
        check(!BrowserProfilePolicy.tryAlternatePage(true, 0, 2))
        check(!BrowserProfilePolicy.tryAlternatePage(false, 1, 2))
        check(!BrowserProfilePolicy.tryAlternatePage(false, 0, 1))
    }
    test("media compatibility profiles are internal, bounded, stable and do not retry denial") {
        val profiles = BrowserProfilePolicy.forUrl("https://www.instagram.com/p/123")
        check(profiles.map { it.id }.toSet().size == profiles.size && profiles.size in 2..3)
        check(profiles == BrowserProfilePolicy.forUrl("https://www.instagram.com/p/456"))
        check(profiles.all { it.userAgent.startsWith("Mozilla/5.0") })
        check(BrowserProfilePolicy.tryNext(406, 0, profiles.size))
        check(BrowserProfilePolicy.tryNext(415, 0, profiles.size))
        for (code in listOf(200, 401, 403, 404, 429, 500))
            check(!BrowserProfilePolicy.tryNext(code, 0, profiles.size))
        check(!BrowserProfilePolicy.tryNext(406, profiles.lastIndex, profiles.size))
    }
    test("platform transport uses transparent built-in browser profiles without user settings") {
        val base = PlatformHeaderPolicy.headersFor("https://www.instagram.com/p/demo")
        val alternate = PlatformHeaderPolicy.headersFor("https://www.instagram.com/p/demo", compatibilityAttempt=1)
        check(base.getValue("User-Agent").contains("Android"))
        check(alternate.getValue("User-Agent").contains("X11"))
        check(!base.containsKey("Cookie"))
        check(PlatformHeaderPolicy.headersFor("https://www.linkedin.com/posts/demo").getValue("User-Agent").contains("X11"))
    }
    test("legacy custom user agent cannot silently disable automatic fallback") {
        val url="https://www.instagram.com/p/test"
        val custom=PlatformHeaderPolicy.headersFor(url, customUserAgent="LegacyBrowser/1", compatibilityAttempt=0)
        val fallback=PlatformHeaderPolicy.headersFor(url, customUserAgent="LegacyBrowser/1",
            customHeaders=mapOf("User-Agent" to "LegacyBrowser/1"), compatibilityAttempt=1)
        check(custom.getValue("User-Agent")=="LegacyBrowser/1")
        check(fallback.getValue("User-Agent").contains("X11"))
    }
    test("static HTML5 sources never pretend unrelated audio is a muxable video track") {
        val asset=com.jumlo.app.core.media.MediaAsset("id", "Html5", "Public sample", null, listOf(
            com.jumlo.app.core.media.MediaFormat("page_0", "mp4", videoCodec="direct"),
            com.jumlo.app.core.media.MediaFormat("page_1", "mp3", audioCodec="direct")
        ))
        val choices=com.jumlo.app.core.media.MediaCatalog.choices(asset,com.jumlo.app.core.media.MediaMode.VIDEO)
        check(choices.size==1 && choices.single().formatIds==listOf("page_0"))
        check(!choices.single().requiresMux)
    }
    test("HTML5 media discovery rejects media pretending to be file and bounds input") {
        check(com.jumlo.app.core.media.Html5MediaDiscovery.discover("https://example.org", "<div>video</div>").isEmpty())
        check(com.jumlo.app.core.media.Html5MediaDiscovery.discover("https://example.org", "<source src='playlist.m3u8'>").isEmpty())
        rejects<IllegalArgumentException> { com.jumlo.app.core.media.Html5MediaDiscovery.discover("https://example.org", "x".repeat(1024*1024)) }
    }
    test("automatic image PDF pages preserve image aspect and orientation") {
        val landscape = PdfPageGeometry.resolve(4032, 3024, "AUTO", 595, 842, "AUTO")
        check(kotlin.math.abs(landscape.widthPt / landscape.heightPt - 4.0 / 3.0) < 0.001)
        check(landscape.widthPt > landscape.heightPt && landscape.widthPt <= 842f)
        val portrait = PdfPageGeometry.resolve(3024, 4032, "AUTO", 595, 842, "AUTO")
        check(portrait.heightPt > portrait.widthPt)
        check(kotlin.math.abs(portrait.heightPt / portrait.widthPt - 4.0 / 3.0) < 0.001)
    }
    test("image PDF presets use actual paper geometry and respect orientation") {
        val a4 = PdfPageGeometry.resolve(4000, 3000, "A4", 123, 456, "AUTO")
        check(a4.widthPt == 842f && a4.heightPt == 595f)
        val letter = PdfPageGeometry.resolve(4000, 3000, "LETTER", 123, 456, "PORTRAIT")
        check(letter.widthPt == 612f && letter.heightPt == 792f)
        val custom = PdfPageGeometry.resolve(4000, 3000, "CUSTOM", 500, 700, "LANDSCAPE")
        check(custom.widthPt == 700f && custom.heightPt == 500f)
    }
    test("image PDF dimensions reject impossible input and never distort extreme ratios") {
        rejects<IllegalArgumentException> { PdfPageGeometry.resolve(0, 100, "AUTO", 595, 842, "AUTO") }
        rejects<IllegalArgumentException> { PdfPageGeometry.resolve(100, 100, "CUSTOM", 0, 842, "AUTO") }
        val wide = PdfPageGeometry.resolve(20000, 1000, "AUTO", 595, 842, "AUTO")
        check(kotlin.math.abs(wide.widthPt/wide.heightPt - 20f) < 0.01f)
    }
    test("PDF page ordering must not silently delete nonnumeric, duplicate or out-of-range entries") {
        check(Class.forName("com.jumlo.app.core.PdfPageOrder").declaredMethods.any { it.name=="parse" })
    }
    test("PDF split boundaries must produce contiguous groups rather than individual extracted pages") {
        check(Class.forName("com.jumlo.app.core.PdfSplitPlan").declaredMethods.any { it.name=="splitAfter" })
    }
    test("visual split markers produce exact contiguous pieces for mixed page counts") {
        check(PdfSplitPlan.splitAfter(6,"2,4")==listOf(0 to 2,2 to 2,4 to 2))
        check(PdfSplitPlan.splitAfter(5,"4,1")==listOf(0 to 1,1 to 3,4 to 1))
        check(PdfSplitPlan.splitAfter(4,"\u0662")==listOf(0 to 2,2 to 2))
        check(PdfSplitPlan.splitAfter(3,"1-2")==listOf(0 to 1,1 to 1,2 to 1))
    }
    test("visual split boundaries reject empty, final-page and out-of-bounds markers") {
        for (value in listOf("", "all", "2,6", "0", "8", "word"))
            rejects<IllegalArgumentException> { PdfSplitPlan.splitAfter(6,value) }
        rejects<IllegalArgumentException> { PdfSplitPlan.splitAfter(1,"1") }
    }
    test("strict PDF edit order preserves intentional deletion and localized page numbers") {
        check(PdfPageOrder.parse("",4)==listOf(1,2,3,4))
        check(PdfPageOrder.parse("4, 2, 1",4)==listOf(4,2,1))
        check(PdfPageOrder.parse("\u0663\u060c\u06f1",4)==listOf(3,1))
    }
    test("strict PDF edit order never silently drops invalid or duplicate pages") {
        for (value in listOf("1,x,3","1,,3","1,2,2","1,0,3","1,5","1,9999999999999999","1-3",","))
            rejects<IllegalArgumentException> { PdfPageOrder.parse(value,4) }
        rejects<IllegalArgumentException> { PdfPageOrder.parse("1",0) }
    }
    test("visual PDF reorder is deterministic and preserves page identities") {
        val initial = PdfPageEdit.start(4)
        val reordered = initial.move(4, 1)
        check(reordered.order == listOf(4, 1, 2, 3))
        check(reordered.order.toSet() == setOf(1, 2, 3, 4))
        check(reordered.move(1, 4).order == listOf(4, 2, 3, 1))
        rejects<IllegalArgumentException> { reordered.move(9, 1) }
    }
    test("visual PDF selection and deletion are reversible immutable operations") {
        val start = PdfPageEdit.start(5)
        val selected = start.selectRange(2, 4)
        check(selected.selected == setOf(2, 3, 4))
        check(selected.deleteSelected().order == listOf(1, 5))
        check(start.order == listOf(1, 2, 3, 4, 5))
        rejects<IllegalArgumentException> { start.selectRange(0, 9) }
        rejects<IllegalArgumentException> { start.selectRange(1, 5).deleteSelected() }
    }
    test("compression profiles preserve requested quality unless smaller-file preset is chosen") {
        check(PdfCompressionPolicy.profiles(150, 80) == listOf(150 to 80))
        check(PdfCompressionPolicy.profiles(300, 95) == listOf(300 to 95))
        check(PdfCompressionPolicy.profiles(90, 50) == listOf(90 to 50))
        check(PdfCompressionPolicy.profiles(150, 50) == listOf(150 to 50, 108 to 50))
    }
    test("compression refuses unbounded bitmap decoding before allocation") {
        check(PdfCompressionPolicy.canDecodeImage(1024, 1024, 256L * 1024 * 1024))
        check(!PdfCompressionPolicy.canDecodeImage(12000, 12000, 256L * 1024 * 1024))
        check(!PdfCompressionPolicy.canDecodeImage(4000, 3000, 128L * 1024 * 1024))
        check(!PdfCompressionPolicy.canDecodeImage(-1, 10, 256L * 1024 * 1024))
    }
    test("compression acceptance requires smaller verified PDF with same page count") {
        check(PdfCompressionPolicy.accept(10_000, 8_000, 7, 7))
        check(!PdfCompressionPolicy.accept(10_000, 10_000, 7, 7))
        check(!PdfCompressionPolicy.accept(10_000, 2_000, 7, 6))
        check(!PdfCompressionPolicy.accept(10_000, 0, 7, 7))
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
