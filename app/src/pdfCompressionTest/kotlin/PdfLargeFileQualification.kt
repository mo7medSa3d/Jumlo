package com.jumlo.app.hosttest

import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.data.pdf.PdfCompressionRunner
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.ceil

/** Real 50+ MiB, 100+ page PDF qualification. JVM codec scope is explicit; all corpus files are removed. */
object PdfLargeFileQualification {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size==1) { "Supply a private empty corpus directory" }
        val directory=File(args[0]).apply {mkdirs()}
        require(directory.listFiles().orEmpty().isEmpty())
        ImageIO.setUseCache(false)
        val peakHeap=AtomicLong();val peakDisk=AtomicLong();val monitoring=AtomicBoolean(true)
        val sampler=Thread {
            while(monitoring.get()) {
                val runtime=Runtime.getRuntime()
                peakHeap.accumulateAndGet(runtime.totalMemory()-runtime.freeMemory(),::maxOf)
                peakDisk.accumulateAndGet(directory.walkTopDown().filter {it.isFile}.sumOf {it.length()},::maxOf)
                try {Thread.sleep(20)} catch (_:InterruptedException) {break}
            }
        }.apply {isDaemon=true;start()}
        try {
            val source=File(directory,"large-source.pdf")
            val pages=generate(source)
            check(pages>100 && source.length()>50L*1024*1024)
            val before=sha256(source)
            println("sourceBytes=${source.length()}; pageCount=$pages; sha256=$before")
            val output=File(directory,"compressed.pdf")
            val result=PdfCompressionRunner.compress(source,output,72,40,HostImageCodec(16L*1024*1024),16L*1024*1024)
            check(result.pageCount==pages && result.replacedImages==pages && result.outputBytes<result.originalBytes)
            PDDocument.load(output,"",MemoryUsageSetting.setupTempFileOnly().setTempDir(directory)).use {doc ->
                check(doc.numberOfPages==pages)
                for(index in listOf(0,pages/2,pages-1)) check(doc.getPage(index).resources.xObjectNames.any())
            }
            println("compression=PASS; outputBytes=${output.length()}; replaced=${result.replacedImages}; skipped=${result.skippedImages}")
            output.delete()

            val extracted=File(directory,"extracted.pdf")
            PdfBoxBackend.open(source,null).use {doc -> PdfBoxBackend.extract(doc,listOf(0,pages-1),extracted)}
            PDDocument.load(extracted).use {check(it.numberOfPages==2)}
            println("extractFirstAndLast=PASS; outputBytes=${extracted.length()}")
            extracted.delete()

            val cropped=File(directory,"cropped.pdf")
            PdfBoxBackend.open(source,null).use {doc ->
                repeat(pages) {PdfBoxBackend.setCrop(doc,it,.1,.1,.9,.9)}
                doc.save(cropped)
            }
            PDDocument.load(cropped,"",MemoryUsageSetting.setupTempFileOnly().setTempDir(directory)).use {doc ->
                check(doc.numberOfPages==pages)
                check(kotlin.math.abs(doc.getPage(pages-1).cropBox.width-489.6f)<.01f)
            }
            println("cropEveryPage=PASS; outputBytes=${cropped.length()}")
            cropped.delete()

            val prior=File(directory,"cancelled.pdf").apply {writeText("existing destination")}
            var checks=0
            try {
                PdfCompressionRunner.compress(source,prior,72,40,HostImageCodec(16L*1024*1024),16L*1024*1024,
                    checkCancel={if(++checks>=15) throw CancellationException("qualification")})
                error("Cancellation was ignored")
            } catch (_:CancellationException) {
                check(prior.readText()=="existing destination")
                check(directory.listFiles().orEmpty().none {it.name.startsWith("pdf-compress-")})
            }
            check(before==sha256(source))
            println("cancellationAndCleanup=PASS; sourceUnchanged=PASS; heapLimitBytes=${Runtime.getRuntime().maxMemory()}")
        } finally {
            monitoring.set(false);sampler.join(1000)
            println("sampledPeakUsedHeapBytes=${peakHeap.get()}; sampledPeakCorpusBytes=${peakDisk.get()}")
            directory.deleteRecursively()
            println("corpusRemoved=${!directory.exists()}")
        }
    }

    /** Stream the fixture directly; PDFBox does not need a second large scratch copy to create it. */
    private fun generate(file:File):Int {
        val image=BufferedImage(960,720,BufferedImage.TYPE_INT_RGB)
        val random=java.util.Random(73)
        for(y in 0 until image.height) for(x in 0 until image.width) image.setRGB(x,y,random.nextInt(0x1000000))
        val bytes=ByteArrayOutputStream()
        val writer=ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            val parameters=writer.defaultWriteParam.apply {compressionMode=ImageWriteParam.MODE_EXPLICIT;compressionQuality=.85f}
            ImageIO.createImageOutputStream(bytes).use {writer.output=it;writer.write(null,IIOImage(image,null,null),parameters)}
        } finally {writer.dispose();image.flush()}
        val jpeg=bytes.toByteArray()
        val pages=maxOf(104,ceil(51.0*1024*1024/jpeg.size).toInt())
        check(pages<=512)
        val count=2+pages*3
        val offsets=LongArray(count+1)
        RandomAccessFile(file,"rw").use {output ->
            fun write(value:String)=output.write(value.toByteArray(Charsets.ISO_8859_1))
            fun begin(id:Int) {offsets[id]=output.filePointer;write("$id 0 obj\n")}
            write("%PDF-1.7\n%âãÏÓ\n")
            begin(1);write("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            begin(2);write("<< /Type /Pages /Count $pages /Kids ["+(0 until pages).joinToString(" ") {"${3+it*3} 0 R"}+"] >>\nendobj\n")
            repeat(pages) {index ->
                val page=3+index*3;val picture=page+1;val content=page+2
                begin(page);write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /XObject << /Im $picture 0 R >> >> /Contents $content 0 R >>\nendobj\n")
                begin(picture);write("<< /Type /XObject /Subtype /Image /Width 960 /Height 720 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n")
                output.write(jpeg);write("\nendstream\nendobj\n")
                val drawing="q 612 0 0 459 0 166 cm /Im Do Q\n"
                begin(content);write("<< /Length ${drawing.length} >>\nstream\n${drawing}endstream\nendobj\n")
            }
            val xref=output.filePointer
            write("xref\n0 ${count+1}\n0000000000 65535 f \n")
            for(id in 1..count) write(String.format(Locale.ROOT,"%010d 00000 n \n",offsets[id]))
            write("trailer\n<< /Size ${count+1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
        return pages
    }

    private fun sha256(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use {input ->val buffer=ByteArray(64*1024);while(true) {val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n)}}
        return digest.digest().joinToString("") {"%02x".format(it)}
    }
}
