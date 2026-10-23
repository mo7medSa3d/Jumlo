package com.jumlo.app.officetest

import com.jumlo.app.core.office.OfficePackageWriter
import com.jumlo.app.core.office.OfficePackageValidator
import com.jumlo.app.core.office.OfficeSlide
import com.jumlo.app.core.office.DocumentRenderPolicy
import com.jumlo.app.core.office.OfficeParagraph
import com.jumlo.app.core.office.OfficeTextPage
import com.jumlo.app.core.office.OfficeTextSpan
import com.jumlo.app.core.office.OfficeTextLayout
import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

private fun xml(file:File,part:String)=ZipFile(file).use { zip ->
    val entry=checkNotNull(zip.getEntry(part)) { "Missing part $part" }
    zip.getInputStream(entry).use { DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true }.newDocumentBuilder().parse(it) }
}
private var checks=0
private fun verify(name:String,block:()->Unit) { block(); checks++;println("PASS $name") }

fun main(args:Array<String>) {
    val dir=if(args.isNotEmpty()) File(args[0]).apply {mkdirs()} else java.nio.file.Files.createTempDirectory("office-tests-").toFile()
    verify("page rendering bounds memory and edges for large or extreme page geometry") {
        check(DocumentRenderPolicy.dimensions(600,800,2400,24L*1024*1024)==(1800 to 2400))
        for((width,height) in listOf(600 to 800,Int.MAX_VALUE to Int.MAX_VALUE,Int.MAX_VALUE to 1,1 to Int.MAX_VALUE)) {
            for(budget in listOf(4L,4_096L,16L*1024*1024)) {
                val (w,h)=DocumentRenderPolicy.dimensions(width,height,2400,budget)
                check(w>0 && h>0 && maxOf(w,h)<=2400)
                check(w.toLong()*h*4<=budget) {"$width x $height became $w x $h with budget $budget"}
            }
        }
    }
    verify("page rendering rejects invalid inputs before allocating") {
        for((width,height,edge,budget) in listOf(listOf(0L,100L,100L,400L),listOf(100L,-1L,100L,400L),listOf(100L,100L,0L,400L),listOf(100L,100L,100L,3L))) {
            check(runCatching {DocumentRenderPolicy.dimensions(width.toInt(),height.toInt(),edge.toInt(),budget)}.isFailure)
        }
    }
    val doc=File(dir,"paragraphs.docx")
    val pageOne="A & B <review>\nwrapped line\n\nمرحبا بالعالم\n\n• Item one"
    verify("DOCX opens with preserved paragraphs, Unicode and page boundaries") {
        OfficePackageWriter.writeDocx(doc,2,{if(it==0) pageOne else "Second page"})
        OfficePackageValidator.validateDocx(doc,2)
        val parsed=xml(doc,"word/document.xml")
        val ns="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val text=parsed.getElementsByTagNameNS(ns,"t")
        val values=(0 until text.length).map {text.item(it).textContent}
        check(values==listOf("A & B <review>","wrapped line","مرحبا بالعالم","• Item one","Second page")) {values}
        check(parsed.getElementsByTagNameNS(ns,"p").length>=5)
        check(parsed.getElementsByTagNameNS(ns,"bidi").length==1)
        val breaks=parsed.getElementsByTagNameNS(ns,"br")
        check((0 until breaks.length).count {(breaks.item(it) as org.w3c.dom.Element).getAttributeNS(ns,"type")=="page"}==1)
    }
    verify("DOCX removes illegal XML controls without losing Unicode supplementary characters") {
        val safe=File(dir,"unicode.docx")
        OfficePackageWriter.writeDocx(safe,1,{"A\u0000B 😀"})
        val text=xml(safe,"word/document.xml").documentElement.textContent
        check(text.contains("A�B 😀")) {text}
    }
    verify("cancellation preserves a previous output and does not publish partial DOCX") {
        val out=File(dir,"cancelled.docx").apply {writeText("previous bytes")}
        val expected=out.readBytes()
        val error=runCatching {OfficePackageWriter.writeDocx(out,3,{if(it==1) throw CancellationException("stop"); "Page $it"})}.exceptionOrNull()
        check(error is CancellationException)
        check(out.readBytes().contentEquals(expected))
    }
    verify("cancellation during completed ZIP verification preserves a previous destination") {
        val isolated=File(dir,"verification-cancel").apply {mkdirs()}
        val out=File(isolated,"previous.docx").apply {writeText("previous verified bytes")}
        val expected=out.readBytes()
        var checksAfterZipFinished=0
        val error=runCatching {
            OfficePackageWriter.writeDocx(out,1,{"Text paragraph\n\n".repeat(10_000)}) {
                // Request cancellation only after a candidate has a complete ZIP central directory.
                val completeCandidate=isolated.listFiles().orEmpty().any {candidate ->
                    candidate!=out && runCatching {ZipFile(candidate).use {it.getEntry("word/document.xml")!=null}}.getOrDefault(false)
                }
                if(completeCandidate && ++checksAfterZipFinished>=4) throw CancellationException("cancel during verification")
            }
        }.exceptionOrNull()
        check(error is CancellationException) {"Cancellation was not checked after writing the ZIP"}
        check(out.readBytes().contentEquals(expected)) {"Previous destination was replaced after cancellation"}
        check(isolated.listFiles().orEmpty().map {it.name}==listOf("previous.docx")) {"A partial candidate leaked"}
    }
    verify("DOCX rejects empty, excessive and oversized page requests") {
        for(count in listOf(0,10001)) check(runCatching {OfficePackageWriter.writeDocx(File(dir,"bad-$count.docx"),count,{"x"})}.isFailure)
        val out=File(dir,"oversized.docx")
        check(runCatching {OfficePackageWriter.writeDocx(out,1,{"x".repeat(1_000_001)})}.isFailure)
        check(!out.exists())
    }
    verify("DOCX refuses all-blank page content and preserves an earlier output") {
        val out=File(dir,"blank.docx").apply {writeText("previous verified document")}
        val previous=out.readBytes()
        val error=runCatching {OfficePackageWriter.writeDocx(out,2,{" \t\n "})}.exceptionOrNull()
        check(error?.message=="OCR_NO_TEXT") {"All-blank page content was published"}
        check(out.readBytes().contentEquals(previous)) {"A blank document replaced the previous output"}
    }
    verify("PDF font evidence preserves headings without promoting ordinary bold body text") {
        val body="This is ordinary body text with enough characters to establish the page's normal font size."
        val text="Project title\n\n$body\n\nSection heading\n$body\n\nBold body"
        fun span(value:String,size:Double,bold:Boolean=false):OfficeTextSpan {
            val start=text.indexOf(value)
            return OfficeTextSpan(start,start+value.length,size,bold)
        }
        val second=text.lastIndexOf(body)
        val page=OfficeTextLayout.fromExtracted(text,listOf(span("Project title",24.0,true),span(body,12.0),
            span("Section heading",18.0,true),OfficeTextSpan(second,second+body.length,12.0,false),span("Bold body",12.0,true)))
        check(page.paragraphs.map {it.headingLevel}==listOf(1,0,2,0,0)) {page.paragraphs}
        check(page.paragraphs.map {it.text}==listOf("Project title",body,"Section heading",body,"Bold body"))
    }
    verify("structured DOCX uses real heading styles and Arabic paragraph direction") {
        val target=File(dir,"headings.docx")
        OfficePackageWriter.writeStructuredDocx(target,1,{OfficeTextPage(listOf(
            OfficeParagraph("عنوان المستند",1),OfficeParagraph("This is the body."),OfficeParagraph("Second section",2)))})
        val parsed=xml(target,"word/document.xml")
        val ns="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        val styles=parsed.getElementsByTagNameNS(ns,"pStyle")
        check((0 until styles.length).map {(styles.item(it) as org.w3c.dom.Element).getAttributeNS(ns,"val")}==listOf("Heading1","Heading2"))
        check(parsed.getElementsByTagNameNS(ns,"bidi").length==1)
        check(parsed.getElementsByTagNameNS(ns,"rtl").length==1)
        val definitions=xml(target,"word/styles.xml").getElementsByTagNameNS(ns,"outlineLvl")
        check((0 until definitions.length).map {(definitions.item(it) as org.w3c.dom.Element).getAttributeNS(ns,"val")}==listOf("0","1","2"))
    }
    val jpeg=java.io.ByteArrayOutputStream().also {bytes ->
        val image=java.awt.image.BufferedImage(120,180,java.awt.image.BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {color=java.awt.Color.WHITE;fillRect(0,0,120,180);color=java.awt.Color.BLACK;drawString("Sample page",10,30);dispose()}
        check(javax.imageio.ImageIO.write(image,"jpeg",bytes))
    }.toByteArray()
    val ppt=File(dir,"pages.pptx")
    verify("PPTX contains one slide per page with decodable page images and editable text") {
        OfficePackageWriter.writePptx(ppt,2,{OfficeSlide(if(it==0) 595.0 else 842.0,if(it==0) 842.0 else 595.0,jpeg,"Page ${it+1} & title\nمرحبا")})
        OfficePackageValidator.validatePptx(ppt,2)
        ZipFile(ppt).use {zip ->
            repeat(2) {i ->
                check(javax.imageio.ImageIO.read(zip.getInputStream(zip.getEntry("ppt/media/page${i+1}.jpg")))!=null)
                val parsed=xml(ppt,"ppt/slides/slide${i+1}.xml")
                check(parsed.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main","pic").length==1)
                check(parsed.documentElement.textContent.contains("Page ${i+1} & title"))
                check(parsed.documentElement.textContent.contains("مرحبا"))
                val xfrm=parsed.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main","ext")
                for(j in 0 until xfrm.length) {
                    val element=xfrm.item(j) as org.w3c.dom.Element
                    check(element.getAttribute("cx").toLong()>0 && element.getAttribute("cy").toLong()>0)
                }
            }
        }
    }
    verify("PPTX producer streams each page once in source order") {
        val seen=mutableListOf<Int>()
        OfficePackageWriter.writePptx(File(dir,"streamed.pptx"),20,{seen+=it;OfficeSlide(612.0,792.0,jpeg,"$it")})
        check(seen==(0 until 20).toList())
    }
    verify("PPTX rejects invalid geometry and corrupt image bytes without output") {
        for((width,bytes) in listOf(Double.NaN to jpeg,612.0 to byteArrayOf(1,2,3))) {
            val target=File(dir,"invalid-ppt-${width}.pptx")
            check(runCatching {OfficePackageWriter.writePptx(target,1,{OfficeSlide(width,792.0,bytes,"text")})}.isFailure)
            check(!target.exists())
        }
    }
    verify("validator rejects wrong extension renamed arbitrary ZIP and corrupt package") {
        val bad=File(dir,"not-office.docx")
        java.util.zip.ZipOutputStream(bad.outputStream()).use {zip->zip.putNextEntry(java.util.zip.ZipEntry("hello.txt"));zip.write("hello".toByteArray());zip.closeEntry()}
        check(runCatching {OfficePackageValidator.validateDocx(bad,1)}.isFailure)
        bad.writeText("broken zip")
        check(runCatching {OfficePackageValidator.validatePptx(bad,1)}.isFailure)
    }
    println("ALL PASS: $checks Office package checks; samples=${dir.absolutePath}")
}
