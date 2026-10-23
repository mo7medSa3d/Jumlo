package com.jumlo.app.core.office

import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** A single rendered PDF page. The producer releases each page after the writer consumes it. */
data class OfficeSlide(val widthPt: Double, val heightPt: Double, val jpeg: ByteArray, val text: String)

/**
 * Small, streaming OOXML implementation. No Android, PDF parser or Office SDK types cross this seam.
 * Callers supply one PDF page at a time; images are never retained for the whole presentation.
 * Complex PDF layout cannot be reconstructed as editable Word tables or PowerPoint shapes.
 */
object OfficePackageWriter {
    const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    const val MAX_PAGES = 10_000
    const val MAX_PAGE_TEXT = 1_000_000
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PACKAGE_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val CONTENT = "http://schemas.openxmlformats.org/package/2006/content-types"
    private const val HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    fun writeDocx(destination: File, pageCount: Int, pageText: (Int) -> String, checkCancel: () -> Unit = {}) {
        writeStructuredDocx(destination, pageCount, { OfficeTextPage.fromPlainText(pageText(it)) }, checkCancel)
    }

    fun writeStructuredDocx(destination: File, pageCount: Int, page: (Int) -> OfficeTextPage, checkCancel: () -> Unit = {}) {
        require(pageCount in 1..MAX_PAGES) { "OFFICE_PAGE_LIMIT" }
        publishPackage(destination, checkCancel, { OfficePackageValidator.validateDocx(it, pageCount, checkCancel) }) { zip ->
            zip.xml("[Content_Types].xml", "<Types xmlns=\"$CONTENT\">${defaults()}" +
                override("/word/document.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml") +
                override("/word/styles.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml") + "</Types>")
            zip.xml("_rels/.rels", relationships(listOf(Triple("rId1", "officeDocument", "word/document.xml"))))
            zip.xml("word/_rels/document.xml.rels", relationships(listOf(Triple("rId1", "styles", "styles.xml"))))
            zip.xml("word/styles.xml", """<w:styles xmlns:w="$W"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:cs="Arial"/><w:sz w:val="22"/><w:szCs w:val="22"/><w:lang w:val="en-US" w:bidi="ar-SA"/></w:rPr></w:rPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:pPr><w:spacing w:after="160"/></w:pPr></w:style>${headingStyles()}</w:styles>""")
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.utf8("$HEADER<w:document xmlns:w=\"$W\"><w:body>")
            var hasText = false
            for (index in 0 until pageCount) {
                checkCancel()
                val current = page(index)
                require(current.paragraphs.size <= MAX_PAGE_TEXT && current.paragraphs.sumOf { it.text.length.toLong() } <= MAX_PAGE_TEXT) { "OFFICE_TEXT_BUDGET_EXCEEDED" }
                hasText = hasText || !current.isBlank
                if (index > 0) zip.utf8("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                current.paragraphs.forEach { paragraph ->
                    checkCancel()
                    require(paragraph.headingLevel in 0..3) { "OFFICE_VERIFY_FAILED" }
                    val rtl = isRtl(paragraph.text)
                    val properties = (if (paragraph.headingLevel > 0) "<w:pStyle w:val=\"Heading${paragraph.headingLevel}\"/>" else "") +
                        (if (rtl) "<w:bidi/><w:jc w:val=\"right\"/>" else "")
                    zip.utf8("<w:p>" + if (properties.isNotEmpty()) "<w:pPr>$properties</w:pPr>" else "")
                    paragraph.text.split('\n').forEachIndexed { line, value ->
                        if (line > 0) zip.utf8("<w:r><w:br/></w:r>")
                        zip.utf8("<w:r>${if (rtl) "<w:rPr><w:rtl/></w:rPr>" else ""}<w:t xml:space=\"preserve\">${escape(value)}</w:t></w:r>")
                    }
                    zip.utf8("</w:p>")
                }
            }
            require(hasText) { "OCR_NO_TEXT" }
            zip.utf8("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\" w:header=\"360\" w:footer=\"360\" w:gutter=\"0\"/></w:sectPr></w:body></w:document>")
            zip.closeEntry()
        }
    }

    fun writePptx(destination: File, pageCount: Int, page: (Int) -> OfficeSlide, checkCancel: () -> Unit = {}) {
        require(pageCount in 1..MAX_PAGES) { "OFFICE_PAGE_LIMIT" }
        publishPackage(destination, checkCancel, { OfficePackageValidator.validatePptx(it, pageCount, checkCancel) }) { zip ->
            val types = buildString {
                append("<Types xmlns=\"$CONTENT\">${defaults()}<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>")
                append(override("/ppt/presentation.xml", "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"))
                append(override("/ppt/presProps.xml", "application/vnd.openxmlformats-officedocument.presentationml.presProps+xml"))
                append(override("/ppt/slideMasters/slideMaster1.xml", "application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"))
                append(override("/ppt/slideLayouts/slideLayout1.xml", "application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"))
                append(override("/ppt/theme/theme1.xml", "application/vnd.openxmlformats-officedocument.theme+xml"))
                repeat(pageCount) { append(override("/ppt/slides/slide${it + 1}.xml", "application/vnd.openxmlformats-officedocument.presentationml.slide+xml")) }
                append("</Types>")
            }
            zip.xml("[Content_Types].xml", types)
            zip.xml("_rels/.rels", relationships(listOf(Triple("rId1", "officeDocument", "ppt/presentation.xml"))))
            zip.xml("ppt/presProps.xml", "<p:presentationPr xmlns:p=\"$P\"/>")
            zip.xml("ppt/theme/theme1.xml", theme())
            zip.xml("ppt/slideMasters/slideMaster1.xml", """<p:sldMaster xmlns:a="$A" xmlns:r="$R" xmlns:p="$P"><p:cSld><p:spTree>${group()}</p:spTree></p:cSld><p:clrMap accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" bg1="lt1" bg2="lt2" folHlink="folHlink" hlink="hlink" tx1="dk1" tx2="dk2"/><p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rId1"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>""")
            zip.xml("ppt/slideMasters/_rels/slideMaster1.xml.rels", relationships(listOf(
                Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"), Triple("rId2", "theme", "../theme/theme1.xml"))))
            zip.xml("ppt/slideLayouts/slideLayout1.xml", """<p:sldLayout xmlns:a="$A" xmlns:r="$R" xmlns:p="$P" type="blank" preserve="1"><p:cSld name="Blank"><p:spTree>${group()}</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>""")
            zip.xml("ppt/slideLayouts/_rels/slideLayout1.xml.rels", relationships(listOf(Triple("rId1", "slideMaster", "../slideMasters/slideMaster1.xml"))))
            var canvasWidth = 0L
            var canvasHeight = 0L
            repeat(pageCount) { index ->
                checkCancel()
                val current = page(index)
                require(current.widthPt.isFinite() && current.heightPt.isFinite() && current.widthPt > 0 && current.heightPt > 0) { "INVALID_PDF_PAGE_GEOMETRY" }
                require(current.text.length <= MAX_PAGE_TEXT) { "OFFICE_TEXT_BUDGET_EXCEEDED" }
                require(current.jpeg.size >= 4 && current.jpeg[0] == 0xff.toByte() && current.jpeg[1] == 0xd8.toByte() &&
                    current.jpeg[current.jpeg.lastIndex - 1] == 0xff.toByte() && current.jpeg.last() == 0xd9.toByte()) { "OFFICE_IMAGE_INVALID" }
                if (index == 0) {
                    val scale = 9_144_000.0 / maxOf(current.widthPt, current.heightPt)
                    canvasWidth = (current.widthPt * scale).toLong()
                    canvasHeight = (current.heightPt * scale).toLong()
                    require(minOf(canvasWidth, canvasHeight) >= 914_400) { "OFFICE_PAGE_ASPECT_UNSUPPORTED" }
                }
                val fit = minOf(canvasWidth / current.widthPt, canvasHeight / current.heightPt)
                val width = (current.widthPt * fit).toLong().coerceAtLeast(1)
                val height = (current.heightPt * fit).toLong().coerceAtLeast(1)
                val x = (canvasWidth - width) / 2
                val y = (canvasHeight - height) / 2
                val textXml = paragraphs(current.text).joinToString("") { para ->
                    val rtl = if (isRtl(para)) " rtl=\"1\"" else ""
                    "<a:p><a:pPr$rtl/>" + para.split('\n').mapIndexed { line, value ->
                        (if (line > 0) "<a:br/>" else "") +
                            "<a:r><a:rPr lang=\"${if (isRtl(para)) "ar" else "en-US"}\" sz=\"1200\"><a:solidFill><a:srgbClr val=\"000000\"><a:alpha val=\"0\"/></a:srgbClr></a:solidFill></a:rPr><a:t>${escape(value)}</a:t></a:r>"
                    }.joinToString("") + "</a:p>"
                }
                // Transparent text stays editable/selectable without drawing a second text copy over the page image.
                val textShape = if (current.text.isBlank()) "" else """<p:sp><p:nvSpPr><p:cNvPr id="3" name="Extracted page text"/><p:cNvSpPr txBox="1"/><p:nvPr/></p:nvSpPr><p:spPr>${transform(x,y,width,height)}<a:prstGeom prst="rect"><a:avLst/></a:prstGeom><a:noFill/></p:spPr><p:txBody><a:bodyPr wrap="square" lIns="0" tIns="0" rIns="0" bIns="0"><a:spAutoFit/></a:bodyPr><a:lstStyle/>$textXml</p:txBody></p:sp>"""
                zip.xml("ppt/slides/slide${index + 1}.xml", """<p:sld xmlns:a="$A" xmlns:r="$R" xmlns:p="$P"><p:cSld><p:spTree>${group()}<p:pic><p:nvPicPr><p:cNvPr id="2" name="PDF page ${index + 1}"/><p:cNvPicPr><a:picLocks noChangeAspect="1"/></p:cNvPicPr><p:nvPr/></p:nvPicPr><p:blipFill><a:blip r:embed="rId2"/><a:stretch><a:fillRect/></a:stretch></p:blipFill><p:spPr>${transform(x,y,width,height)}<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></p:spPr></p:pic>$textShape</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>""")
                zip.xml("ppt/slides/_rels/slide${index + 1}.xml.rels", relationships(listOf(
                    Triple("rId1", "slideLayout", "../slideLayouts/slideLayout1.xml"),
                    Triple("rId2", "image", "../media/page${index + 1}.jpg"))))
                zip.putNextEntry(ZipEntry("ppt/media/page${index + 1}.jpg"))
                zip.write(current.jpeg)
                zip.closeEntry()
            }
            zip.xml("ppt/presentation.xml", """<p:presentation xmlns:a="$A" xmlns:r="$R" xmlns:p="$P"><p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rId1"/></p:sldMasterIdLst><p:sldIdLst>${(0 until pageCount).joinToString("") { "<p:sldId id=\"${256 + it}\" r:id=\"rId${it + 3}\"/>" }}</p:sldIdLst><p:sldSz cx="$canvasWidth" cy="$canvasHeight"/><p:notesSz cx="6858000" cy="9144000"/><p:defaultTextStyle/></p:presentation>""")
            zip.xml("ppt/_rels/presentation.xml.rels", relationships(listOf(
                Triple("rId1", "slideMaster", "slideMasters/slideMaster1.xml"), Triple("rId2", "presProps", "presProps.xml")) +
                (0 until pageCount).map { Triple("rId${it + 3}", "slide", "slides/slide${it + 1}.xml") }))
        }
    }

    private fun publishPackage(destination: File, checkCancel: () -> Unit, validate: (File) -> Unit, body: (ZipOutputStream) -> Unit) {
        checkCancel()
        val temporary = File.createTempFile("office-", ".partial", destination.absoluteFile.parentFile)
        try {
            ZipOutputStream(temporary.outputStream().buffered()).use { zip -> body(zip) }
            checkCancel()
            validate(temporary)
            checkCancel()
            // Publication only sees a completed, reopened package. A failure preserves an earlier output.
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
    private fun paragraphs(text: String): List<String> = text.replace("\r\n", "\n").replace('\r', '\n').trim('\n')
        .split(Regex("\n[\\t ]*\n+")).ifEmpty { listOf("") }
    private fun isRtl(text: String): Boolean {
        for (character in text) when (Character.getDirectionality(character)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
        return false
    }
    private fun escape(text: String): String = buildString(text.length) {
        var index = 0
        while (index < text.length) {
            val cp = text.codePointAt(index)
            index += Character.charCount(cp)
            when (cp) {
                38 -> append("&amp;"); 60 -> append("&lt;"); 62 -> append("&gt;"); 34 -> append("&quot;")
                9, 10, 13 -> append(cp.toChar())
                in 32..0xd7ff, in 0xe000..0xfffd -> append(cp.toChar())
                in 0x10000..0x10ffff -> append(String(Character.toChars(cp)))
                else -> append('\ufffd')
            }
        }
    }
    private fun ZipOutputStream.utf8(value: String) { write(value.toByteArray(Charsets.UTF_8)) }
    private fun ZipOutputStream.xml(name: String, value: String) { putNextEntry(ZipEntry(name)); utf8(HEADER + value); closeEntry() }
    private fun defaults() = "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"
    private fun headingStyles() = (1..3).joinToString("") { level ->
        val size = listOf(36, 30, 26)[level - 1]
        """<w:style w:type="paragraph" w:styleId="Heading$level"><w:name w:val="heading $level"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before="240" w:after="120"/><w:outlineLvl w:val="${level - 1}"/></w:pPr><w:rPr><w:b/><w:bCs/><w:sz w:val="$size"/><w:szCs w:val="$size"/></w:rPr></w:style>"""
    }
    private fun override(part: String, type: String) = "<Override PartName=\"$part\" ContentType=\"$type\"/>"
    private fun relationships(items: List<Triple<String, String, String>>) = "<Relationships xmlns=\"$PACKAGE_REL\">" +
        items.joinToString("") { (id, type, target) -> "<Relationship Id=\"$id\" Type=\"$R/$type\" Target=\"$target\"/>" } + "</Relationships>"
    private fun group() = "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1\" cy=\"1\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"1\" cy=\"1\"/></a:xfrm></p:grpSpPr>"
    private fun transform(x: Long, y: Long, width: Long, height: Long) = "<a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$width\" cy=\"$height\"/></a:xfrm>"
    private fun theme(): String {
        val fill = "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>"
        val line = "<a:ln w=\"6350\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\">$fill<a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>"
        val colors = listOf("dk1" to "000000", "lt1" to "FFFFFF", "dk2" to "333333", "lt2" to "EEEEEE", "accent1" to "3056D3", "accent2" to "A53D48", "accent3" to "33805B", "accent4" to "7850A3", "accent5" to "198F99", "accent6" to "D08030", "hlink" to "0000FF", "folHlink" to "800080")
        return """<a:theme xmlns:a="$A" name="Jumlo"><a:themeElements><a:clrScheme name="Jumlo">${colors.joinToString("") { (name,color)->"<a:$name><a:srgbClr val=\"$color\"/></a:$name>" }}</a:clrScheme><a:fontScheme name="Jumlo"><a:majorFont><a:latin typeface="Arial"/><a:ea typeface=""/><a:cs typeface="Arial"/></a:majorFont><a:minorFont><a:latin typeface="Arial"/><a:ea typeface=""/><a:cs typeface="Arial"/></a:minorFont></a:fontScheme><a:fmtScheme name="Jumlo"><a:fillStyleLst>${fill.repeat(3)}</a:fillStyleLst><a:lnStyleLst>${line.repeat(3)}</a:lnStyleLst><a:effectStyleLst>${"<a:effectStyle><a:effectLst/></a:effectStyle>".repeat(3)}</a:effectStyleLst><a:bgFillStyleLst>${fill.repeat(3)}</a:bgFillStyleLst></a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>"""
    }
}

/** Reopens each part, checks XML and internal relationships; keeps no page images in memory. */
object OfficePackageValidator {
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    fun validateDocx(file: File, pages: Int, checkCancel: () -> Unit = {}) {
        validate(file, setOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml"), checkCancel) { name, uri, local, attributes ->
            if (name == "word/document.xml" && uri == W && local == "br" && attributes.getValue(W, "type") == "page") 1 else 0
        }.also { require(it == pages - 1) { "OFFICE_VERIFY_FAILED" } }
    }
    fun validatePptx(file: File, pages: Int, checkCancel: () -> Unit = {}) {
        checkCancel()
        val required = setOf("[Content_Types].xml", "_rels/.rels", "ppt/presentation.xml", "ppt/presProps.xml", "ppt/theme/theme1.xml", "ppt/slideMasters/slideMaster1.xml", "ppt/slideLayouts/slideLayout1.xml") +
            (1..pages).flatMap { listOf("ppt/slides/slide$it.xml", "ppt/slides/_rels/slide$it.xml.rels", "ppt/media/page$it.jpg") }
        validate(file, required, checkCancel) { name, uri, local, _ -> if (name == "ppt/presentation.xml" && uri == P && local == "sldId") 1 else 0 }
            .also { require(it == pages) { "OFFICE_VERIFY_FAILED" } }
    }
    private fun validate(file: File, required: Set<String>, checkCancel: () -> Unit, count: (String, String, String, Attributes) -> Int): Int {
        checkCancel()
        require(file.isFile && file.length() > 0) { "OFFICE_VERIFY_FAILED" }
        var counted = 0
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val names = entries.map { it.name }.toSet()
            require(entries.size == names.size && names.containsAll(required) && entries.size <= OfficePackageWriter.MAX_PAGES * 4 + 64) { "OFFICE_VERIFY_FAILED" }
            for (entry in entries) {
                checkCancel()
                require(!entry.isDirectory && !entry.name.startsWith('/') && entry.name.split('/').none { it == ".." } && entry.size in 0..128L * 1024 * 1024) { "OFFICE_VERIFY_FAILED" }
                zip.getInputStream(entry).use { raw ->
                    val input = LimitedInput(raw, 128L * 1024 * 1024, checkCancel)
                    if (entry.name.endsWith(".xml") || entry.name.endsWith(".rels")) {
                        val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser().xmlReader
                        // External entities are never needed by an Office package. The resolver is a second guard on parsers that don't support the flags.
                        runCatching { reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                        runCatching { reader.setFeature("http://xml.org/sax/features/external-general-entities", false) }
                        runCatching { reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("OFFICE_EXTERNAL_ENTITY") }
                        reader.contentHandler = object : DefaultHandler() {
                            override fun startElement(uri: String, local: String, qName: String, attributes: Attributes) {
                                counted += count(entry.name, uri, local, attributes)
                                if (local == "Relationship") {
                                    require(attributes.getValue("TargetMode") != "External") { "OFFICE_VERIFY_FAILED" }
                                    val base = if (entry.name == "_rels/.rels") "/" else "/" + entry.name.substringBeforeLast("/_rels/") + "/"
                                    val target = java.net.URI(base).resolve(attributes.getValue("Target")).normalize().path.removePrefix("/")
                                    require(target in names) { "OFFICE_RELATIONSHIP_MISSING" }
                                }
                            }
                        }
                        reader.parse(org.xml.sax.InputSource(input))
                    } else {
                        val buffer = ByteArray(32 * 1024)
                        while (input.read(buffer) >= 0) Unit
                    }
                }
            }
        }
        return counted
    }
    private class LimitedInput(input: InputStream, private val limit: Long, private val checkCancel: () -> Unit) : FilterInputStream(input) {
        private var total = 0L
        override fun read(): Int {
            checkCancel()
            return super.read().also { if (it >= 0) checkSize(1) }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            checkCancel()
            return `in`.read(buffer, offset, length).also { if (it > 0) checkSize(it) }
        }
        private fun checkSize(count: Int) { total += count; require(total <= limit) { "OFFICE_TEXT_BUDGET_EXCEEDED" } }
    }
}
