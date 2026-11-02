package com.jumlo.app.core.office

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.ext.LexicalHandler

/**
 * Streaming OOXML reader (docx, xlsx, pptx) built on java.util.zip + SAX.
 * No Android, PDF or Office SDK types cross this seam, so the reader is unit
 * testable on the JVM. XML is parsed with DOCTYPEs disabled (no XXE), and
 * entry counts, text volume and image bytes are capped.
 *
 * Fidelity contract: text, headings, lists, tables and pictures survive;
 * headers, footers, footnotes, charts and vector images (EMF/WMF/SVG) are
 * dropped and reported in [OfficeDocument.unsupported]. Multi-section Word
 * documents render at the last section's page size.
 */
object OfficeDocumentReader {
    const val MAX_ENTRIES = 10_000
    const val MAX_BLOCKS = 50_000
    const val MAX_TOTAL_CHARS = 10_000_000L
    const val MAX_IMAGE_BYTES = 256L * 1024 * 1024
    const val MAX_EXPANDED_PACKAGE_BYTES = 512L * 1024 * 1024
    const val MAX_TABLE_ROWS = 50_000
    const val MAX_TABLE_CELLS = 100_000
    const val MAX_TABLE_COLUMNS = 512
    const val MAX_SHARED_STRINGS = 50_000

    private const val EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
    private const val EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"
    private const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
    private const val LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
    private const val LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler"

    /**
     * Android's Expat XMLReader does not expose LexicalHandler on every API
     * level. Keep a streaming DOCTYPE guard in front of SAX so parser-provider
     * differences can never turn that unsupported property into either a
     * broken valid-document path or an XXE bypass.
     */
    private class DoctypeRejectingInputStream(input: InputStream) : FilterInputStream(input) {
        private val patterns = arrayOf(
            "<!DOCTYPE".toByteArray(Charsets.US_ASCII),
            "<!DOCTYPE".toByteArray(Charsets.UTF_16LE),
            "<!DOCTYPE".toByteArray(Charsets.UTF_16BE)
        )
        private val matched = IntArray(patterns.size)

        private fun scan(value: Int) {
            if (value < 0) return
            for (index in patterns.indices) {
                val pattern = patterns[index]
                val next = value.toByte()
                matched[index] = when {
                    next == pattern[matched[index]] -> matched[index] + 1
                    next == pattern[0] -> 1
                    else -> 0
                }
                if (matched[index] == pattern.size) throw IOException("OFFICE_DTD_NOT_ALLOWED")
            }
        }

        override fun read(): Int {
            val value = super.read()
            scan(value)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) for (index in offset until offset + count) scan(buffer[index].toInt() and 0xff)
            return count
        }
    }

    // ---- generic SAX helpers -------------------------------------------------

    private fun parseXml(stream: InputStream, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // Android's Expat SAXParserFactory throws UnsupportedOperationException
            // from setXIncludeAware(). XInclude is disabled by default by JAXP;
            // explicitly disable it on providers that implement the setter.
            runCatching { isXIncludeAware = false }
        }
        val reader = factory.newSAXParser().xmlReader

        // These two SAX controls are mandatory. Android's Expat reader explicitly
        // supports both as read-only false features; Xerces supports setting them.
        reader.setFeature(EXTERNAL_GENERAL_ENTITIES, false)
        reader.setFeature(EXTERNAL_PARAMETER_ENTITIES, false)
        require(!reader.getFeature(EXTERNAL_GENERAL_ENTITIES) &&
            !reader.getFeature(EXTERNAL_PARAMETER_ENTITIES)) { "OFFICE_XML_SECURITY_UNAVAILABLE" }

        // These provider-specific controls add defense in depth where available.
        // The mandatory portable DOCTYPE rejection below does not depend on them.
        runCatching { reader.setFeature(DISALLOW_DOCTYPE, true) }
        runCatching { reader.setFeature(LOAD_EXTERNAL_DTD, false) }
        runCatching { reader.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }

        reader.entityResolver = EntityResolver { _, _ ->
            throw SAXException("OFFICE_EXTERNAL_ENTITY_BLOCKED")
        }
        runCatching {
            reader.setProperty(LEXICAL_HANDLER, object : LexicalHandler {
                override fun startDTD(name: String?, publicId: String?, systemId: String?) {
                    throw SAXException("OFFICE_DTD_NOT_ALLOWED")
                }
                override fun endDTD() = Unit
                override fun startEntity(name: String?) = Unit
                override fun endEntity(name: String?) = Unit
                override fun startCDATA() = Unit
                override fun endCDATA() = Unit
                override fun comment(ch: CharArray, start: Int, length: Int) = Unit
            })
        }
        reader.contentHandler = handler
        reader.errorHandler = handler
        // Parse through the configured XMLReader directly. SAXParser.parse(...,
        // DefaultHandler) replaces the reader's resolver with the handler's default.
        reader.parse(InputSource(DoctypeRejectingInputStream(stream)))
    }

    private fun packageByteLimit(requested: Long): Long {
        require(requested > 0L) { "OFFICE_SIZE_LIMIT" }
        return minOf(requested, MAX_EXPANDED_PACKAGE_BYTES)
    }

    private class BoundedEntryInputStream(input: InputStream, private val limit: Long) :
        FilterInputStream(input) {
        private var consumed = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) {
                consumed++
                require(consumed <= limit) { "OFFICE_SIZE_LIMIT" }
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val remaining = limit - consumed
            if (remaining <= 0L) {
                return if (super.read() == -1) -1 else throw IllegalArgumentException("OFFICE_SIZE_LIMIT")
            }
            val count = super.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count > 0) consumed += count
            return count
        }
    }

    private const val OFFICE_RELATIONSHIPS_NS =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    /** Relationship references such as r:id must not be confused with numeric id attributes. */
    private fun relationshipId(attrs: Attributes): String? {
        for (i in 0 until attrs.length) {
            if (attrs.getLocalName(i) == "id" && attrs.getURI(i) == OFFICE_RELATIONSHIPS_NS) {
                return attrs.getValue(i)
            }
        }
        return null
    }

    /** Attribute by local name, tolerant of prefix differences (val vs w:val). */
    private fun attr(attrs: Attributes, name: String): String? {
        for (i in 0 until attrs.length) {
            if (attrs.getLocalName(i) == name || attrs.getQName(i).substringAfter(':') == name) {
                return attrs.getValue(i)
            }
        }
        return null
    }

    private fun entries(zip: ZipFile, maxExpandedBytes: Long): Map<String, ZipEntry> {
        require(zip.size() <= MAX_ENTRIES) { "OFFICE_SIZE_LIMIT" }
        val map = LinkedHashMap<String, ZipEntry>(zip.size())
        var totalExpandedBytes = 0L
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            val name = entry.name
            require(name.isNotEmpty() && !name.startsWith("/") && !name.contains(92.toChar()) &&
                name.none { it == 0.toChar() } && name.split('/').none { it == ".." }) { "OFFICE_INVALID" }
            require(!map.containsKey(name)) { "OFFICE_INVALID" }
            val declaredSize = entry.size
            require(declaredSize >= 0L && declaredSize <= maxExpandedBytes - totalExpandedBytes) {
                "OFFICE_SIZE_LIMIT"
            }
            totalExpandedBytes += declaredSize
            map[name] = entry
        }
        return map
    }

    private fun open(zip: ZipFile, map: Map<String, ZipEntry>, name: String): InputStream? =
        map[name]?.let { entry ->
            BoundedEntryInputStream(zip.getInputStream(entry), entry.size)
        }

    /** Resolve a relationship target against the part that owns the rels file. */
    private fun resolve(basePart: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val dir = basePart.substringBeforeLast('/', "")
        val parts = ArrayDeque<String>()
        (if (dir.isEmpty()) target else "$dir/$target").split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> parts.removeLastOrNull()
                else -> parts.addLast(segment)
            }
        }
        return parts.joinToString("/")
    }

    /** Id -> target for one .rels part. */
    private fun rels(zip: ZipFile, map: Map<String, ZipEntry>, relsPath: String): Map<String, String> {
        val stream = open(zip, map, relsPath) ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        stream.use { parseXml(it, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                if (local == "Relationship") {
                    val id = attr(attrs, "Id")
                    val target = attr(attrs, "Target")
                    if (id != null && target != null) out[id] = target
                }
            }
        }) }
        return out
    }

    /** Id -> target, plus the relationship types (for header/footer detection). */
    private fun relsWithTypes(zip: ZipFile, map: Map<String, ZipEntry>, relsPath: String): List<Triple<String, String, String>> {
        val stream = open(zip, map, relsPath) ?: return emptyList()
        val out = ArrayList<Triple<String, String, String>>()
        stream.use { parseXml(it, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                if (local == "Relationship") {
                    val id = attr(attrs, "Id") ?: return
                    val target = attr(attrs, "Target") ?: return
                    out += Triple(id, target, attr(attrs, "Type") ?: "")
                }
            }
        }) }
        return out
    }

    // ---- detection -----------------------------------------------------------

    fun detect(
        file: File,
        displayName: String,
        maxBytes: Long = MAX_EXPANDED_PACKAGE_BYTES
    ): OfficeKind {
        val packageLimit = packageByteLimit(maxBytes)
        require(file.length() <= packageLimit) { "OFFICE_SIZE_LIMIT" }
        try {
            ZipFile(file).use { zip ->
                val map = entries(zip, packageLimit)
                open(zip, map, "[Content_Types].xml")?.use { stream ->
                    var found: OfficeKind? = null
                    var mainPart: String? = null
                    parseXml(stream, object : DefaultHandler() {
                        override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                            if (local != "Override") return
                            val type = attr(attrs, "ContentType") ?: return
                            val part = attr(attrs, "PartName")?.removePrefix("/") ?: return
                            when {
                                type.contains("wordprocessingml.document.main") -> { found = OfficeKind.DOCX; mainPart = part }
                                type.contains("spreadsheetml.sheet.main") -> { found = OfficeKind.XLSX; mainPart = part }
                                type.contains("presentationml.presentation.main") -> { found = OfficeKind.PPTX; mainPart = part }
                            }
                        }
                    })
                    if (found != null) {
                        // The main part must actually exist; a lying manifest is invalid.
                        if (mainPart != null && map.containsKey(mainPart)) return found!!
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: SAXException) {
            throw IllegalArgumentException("OFFICE_INVALID", e)
        } catch (_: Exception) {
            // A non-OOXML file may still be identified by its extension, but
            // malformed XML inside an OOXML package must never use that fallback.
        }
        return when (displayName.substringAfterLast('.', "").lowercase()) {
            "docx" -> OfficeKind.DOCX
            "xlsx" -> OfficeKind.XLSX
            "pptx" -> OfficeKind.PPTX
            else -> throw IllegalArgumentException("OFFICE_INVALID")
        }
    }

    // ---- entry point ---------------------------------------------------------

    fun read(
        file: File,
        kind: OfficeKind,
        maxBytes: Long,
        maxImageBytes: Long = MAX_IMAGE_BYTES,
        checkCancel: () -> Unit = {}
    ): OfficeDocument {
        val packageLimit = packageByteLimit(maxBytes)
        require(maxImageBytes in 0L..MAX_IMAGE_BYTES) { "OFFICE_SIZE_LIMIT" }
        require(file.length() <= packageLimit) { "OFFICE_SIZE_LIMIT" }
        try {
            ZipFile(file).use { zip ->
                val map = entries(zip, packageLimit)
                return when (kind) {
                    OfficeKind.DOCX -> readDocx(zip, map, maxImageBytes, checkCancel)
                    OfficeKind.XLSX -> readXlsx(zip, map, maxImageBytes, checkCancel)
                    OfficeKind.PPTX -> readPptx(zip, map, maxImageBytes, checkCancel)
                }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("OFFICE_INVALID", e)
        }
    }

    // ---- shared accumulation ---------------------------------------------------

    private class Accumulator(private val maxImageBytes: Long) {
        val blocks = ArrayList<OfficeBlock>()
        val unsupported = LinkedHashSet<String>()
        var totalChars = 0L
        var imageBytes = 0L
        private var tableRows = 0
        private var tableCells = 0

        fun reserveTableRow() {
            require(tableRows < MAX_TABLE_ROWS) { "OFFICE_SIZE_LIMIT" }
            tableRows++
        }

        fun reserveTableCell() {
            require(tableCells < MAX_TABLE_CELLS) { "OFFICE_SIZE_LIMIT" }
            tableCells++
        }

        fun add(block: OfficeBlock) {
            require(blocks.size < MAX_BLOCKS) { "OFFICE_SIZE_LIMIT" }
            val addedChars = when (block) {
                is OfficeBlock.ParagraphBlock -> block.paragraph.text.length.toLong()
                is OfficeBlock.TableBlock -> block.rows.sumOf { row ->
                    row.sumOf { cell -> cell.text.length.toLong() }
                }
                else -> 0L
            }
            require(addedChars <= MAX_TOTAL_CHARS - totalChars) { "OFFICE_SIZE_LIMIT" }
            totalChars += addedChars
            blocks += block
        }
        fun addImage(bytes: ByteArray, extension: String) {
            require(bytes.size.toLong() <= maxImageBytes - imageBytes) { "OFFICE_SIZE_LIMIT" }
            imageBytes += bytes.size
            add(OfficeBlock.ImageBlock(bytes, extension))
        }
        fun readImage(input: InputStream, extension: String, checkCancel: () -> Unit) {
            val remaining = maxImageBytes - imageBytes
            require(remaining > 0L) { "OFFICE_SIZE_LIMIT" }
            val output = ByteArrayOutputStream(minOf(remaining, 64L * 1024L).toInt())
            val buffer = ByteArray(16 * 1024)
            while (true) {
                checkCancel()
                val count = input.read(buffer)
                if (count < 0) break
                require(count.toLong() <= remaining - output.size().toLong()) { "OFFICE_SIZE_LIMIT" }
                output.write(buffer, 0, count)
            }
            addImage(output.toByteArray(), extension)
        }
    }

    private fun imageExtension(target: String): String =
        target.substringAfterLast('.', "").lowercase()

    private fun isRasterImage(extension: String): Boolean =
        extension in setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    // ---- docx ------------------------------------------------------------------

    private fun readDocx(zip: ZipFile, map: Map<String, ZipEntry>, maxImageBytes: Long, checkCancel: () -> Unit): OfficeDocument {
        val main = "word/document.xml"
        require(map.containsKey(main)) { "OFFICE_INVALID" }
        val relsList = relsWithTypes(zip, map, "word/_rels/document.xml.rels")
        val relTargets = relsList.associate { (id, target, _) -> id to resolve(main, target) }
        // Headers and footers are dropped, not rendered into the body.
        val hasHeaders = relsList.any { (_, _, type) -> type.contains("/header") || type.contains("/footer") }
        val hasFootnotes = map.keys.any { it == "word/footnotes.xml" || it == "word/endnotes.xml" }
        // Numbering: numId -> (ilvl -> format), plus abstract fallback.
        val numbering = loadNumbering(zip, map)
        val acc = Accumulator(maxImageBytes)
        if (hasHeaders) acc.unsupported += "HEADER_FOOTER"
        if (hasFootnotes) acc.unsupported += "FOOTNOTE"

        var pageWidthPt = 595.0
        var pageHeightPt = 842.0
        var marginPt = 56.0
        val counters = LinkedHashMap<String, Int>()

        open(zip, map, main)!!.use { stream ->
            parseXml(stream, object : DefaultHandler() {
                var pDepth = 0
                var inTable = 0
                var inCell = false
                var cellParagraphs = ArrayList<String>()
                var cellBold = false
                var cellSpan = 1
                var rows = ArrayList<ArrayList<OfficeCell>>()
                var currentRow: ArrayList<OfficeCell>? = null
                var headerRow = false
                var rowHasHeader = false
                // Paragraph state (also used for table-cell paragraphs).
                var runs = ArrayList<OfficeRun>()
                var runText = StringBuilder()
                var runBold = false
                var runItalic = false
                var inRun = false
                var inText = false
                var inInstr = 0
                var inDel = 0
                var styleId: String? = null
                var align = "left"
                var numId: String? = null
                var ilvl = 0
                var inPPr = false
                var inRPr = false
                var inNumPr = false
                var inTcPr = false
                var inTrPr = false
                var inSectPr = false
                var documentTextChars = 0L

                fun flushRun() {
                    if (!inRun) return
                    val text = runText.toString()
                    if (text.isNotEmpty()) runs += OfficeRun(text, runBold, runItalic)
                    runText = StringBuilder()
                    inRun = false
                }

                fun flushParagraph() {
                    flushRun()
                    if (runs.isEmpty()) return
                    val paragraph = OfficeParagraph(
                        runs = runs.toList(),
                        level = when (styleId) {
                            "Title" -> 1
                            "Heading1" -> 2
                            "Heading2" -> 3
                            "Heading3", "Heading4", "Heading5", "Heading6" -> 4
                            else -> 0
                        },
                        align = align,
                        listMarker = numId?.let { id ->
                            val format = numbering[id]?.get(ilvl) ?: "bullet"
                            if (format == "bullet") "• " else {
                                val next = (counters[id] ?: 0) + 1
                                counters[id] = next
                                "$next. "
                            }
                        },
                        listIndent = if (numId != null) ilvl else 0
                    )
                    runs = ArrayList()
                    if (inCell) {
                        cellParagraphs += paragraph.text
                        if (paragraph.runs.any { it.bold }) cellBold = true
                    } else if (inTable == 0) {
                        acc.add(OfficeBlock.ParagraphBlock(paragraph))
                    } else {
                        // Nested table inside a cell: flatten its text into the cell.
                        cellParagraphs += paragraph.text
                    }
                    styleId = null; align = "left"; numId = null; ilvl = 0
                }

                fun flushCell() {
                    if (!inCell) return
                    val text = cellParagraphs.joinToString(" ").trim()
                    currentRow?.add(OfficeCell(text, cellBold, cellSpan.coerceAtLeast(1)))
                    cellParagraphs = ArrayList(); cellBold = false; cellSpan = 1
                    inCell = false
                }

                fun flushRow() {
                    val row = currentRow
                    if (row != null && row.isNotEmpty()) {
                        rows += row
                        if (rowHasHeader && rows.size == 1) headerRow = true
                    }
                    currentRow = null; rowHasHeader = false
                }

                override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                    when (local) {
                        "p" -> {
                            if (pDepth == 0) {
                                runs = ArrayList(); styleId = null; align = "left"; numId = null; ilvl = 0
                            }
                            pDepth++
                        }
                        "pPr" -> inPPr = true
                        "pStyle" -> if (inPPr) styleId = attr(attrs, "val")
                        "jc" -> if (inPPr) align = when (attr(attrs, "val")) {
                            "center" -> "center"; "right" -> "right"; "both" -> "justify"; else -> "left"
                        }
                        "numPr" -> inNumPr = true
                        "numId" -> if (inNumPr) numId = attr(attrs, "val")
                        "ilvl" -> if (inNumPr) ilvl = attr(attrs, "val")?.toIntOrNull() ?: 0
                        "r" -> { inRun = true; runBold = false; runItalic = false }
                        "rPr" -> inRPr = true
                        "b" -> if (inRPr) runBold = true
                        "i" -> if (inRPr) runItalic = true
                        "instrText" -> inInstr++
                        "del" -> inDel++
                        "t" -> inText = true
                        "br" -> {
                            if (attr(attrs, "type") == "page") {
                                flushParagraph()
                                if (inTable == 0) acc.add(OfficeBlock.PageBreak)
                            } else if (inRun) runText.append('\n')
                        }
                        "tab" -> if (inRun) runText.append("  ")
                        "tbl" -> {
                            flushParagraph()
                            if (inTable == 0) { rows = ArrayList(); headerRow = false }
                            inTable++
                        }
                        // Nested tables (depth > 1) only contribute their text
                        // to the outer cell; their rows never reach the grid.
                        "tr" -> if (inTable == 1) {
                            acc.reserveTableRow()
                            currentRow = ArrayList()
                            rowHasHeader = false
                        }
                        "trPr" -> inTrPr = true
                        "tblHeader" -> if (inTrPr) rowHasHeader = true
                        "tc" -> if (inTable == 1) {
                            acc.reserveTableCell()
                            inCell = true
                            cellParagraphs = ArrayList()
                            cellBold = false
                            cellSpan = 1
                        }
                        "tcPr" -> inTcPr = true
                        "gridSpan" -> if (inTcPr) {
                            val span = attr(attrs, "val")?.toIntOrNull()
                                ?: throw IllegalArgumentException("OFFICE_INVALID")
                            require(span in 1..MAX_TABLE_COLUMNS) { "OFFICE_SIZE_LIMIT" }
                            cellSpan = span
                        }
                        "sectPr" -> inSectPr = true
                        "pgSz" -> if (inSectPr) {
                            attr(attrs, "w")?.toDoubleOrNull()?.let { pageWidthPt = it / 20.0 }
                            attr(attrs, "h")?.toDoubleOrNull()?.let { pageHeightPt = it / 20.0 }
                        }
                        "mar" -> if (inSectPr) {
                            attr(attrs, "left")?.toDoubleOrNull()?.let { marginPt = (it / 20.0).coerceIn(36.0, 108.0) }
                        }
                        "blip" -> {
                            val embed = attr(attrs, "embed")
                            val target = embed?.let { relTargets[it] }
                            if (target != null) {
                                flushParagraph()
                                val ext = imageExtension(target)
                                if (isRasterImage(ext)) {
                                    open(zip, map, target)?.use { bytes ->
                                        acc.readImage(bytes, ext, checkCancel)
                                    }
                                } else {
                                    acc.unsupported += "UNSUPPORTED_IMAGE"
                                }
                            }
                        }
                        "drawing", "pict" -> { /* containers; blip handled above */ }
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inRun && inText && inInstr == 0 && inDel == 0) {
                        documentTextChars += length.toLong()
                        require(documentTextChars <= MAX_TOTAL_CHARS) { "OFFICE_SIZE_LIMIT" }
                        runText.append(ch, start, length)
                    }
                }

                override fun endElement(uri: String?, local: String, qName: String) {
                    when (local) {
                        "p" -> {
                            pDepth--
                            if (pDepth == 0) flushParagraph()
                        }
                        "pPr" -> inPPr = false
                        "rPr" -> inRPr = false
                        "numPr" -> inNumPr = false
                        "instrText" -> if (inInstr > 0) inInstr--
                        "del" -> if (inDel > 0) inDel--
                        "t" -> inText = false
                        "r" -> flushRun()
                        "tbl" -> {
                            inTable--
                            if (inTable == 0 && rows.isNotEmpty()) {
                                acc.add(OfficeBlock.TableBlock(rows.map { it.toList() }, headerRow))
                                rows = ArrayList()
                            }
                        }
                        "tr" -> if (inTable == 1) flushRow()
                        "tc" -> if (inTable == 1) flushCell()
                        "tcPr" -> inTcPr = false
                        "trPr" -> inTrPr = false
                        "sectPr" -> inSectPr = false
                    }
                }
            })
        }
        checkCancel()
        return OfficeDocument(OfficeKind.DOCX, pageWidthPt, pageHeightPt, marginPt, acc.blocks, acc.unsupported.toList())
    }

    /** numId -> (ilvl -> "bullet" or "decimal"). Falls back to bullet. */
    private fun loadNumbering(zip: ZipFile, map: Map<String, ZipEntry>): Map<String, Map<Int, String>> {
        val stream = open(zip, map, "word/numbering.xml") ?: return emptyMap()
        val abstract = LinkedHashMap<String, MutableMap<Int, String>>()
        val numToAbstract = LinkedHashMap<String, String>()
        var currentAbstract: String? = null
        var currentNum: String? = null
        var currentLvl: Int? = null
        stream.use { parseXml(it, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                when (local) {
                    "abstractNum" -> currentAbstract = attr(attrs, "abstractNumId")
                    "num" -> currentNum = attr(attrs, "numId")
                    "lvl" -> currentLvl = attr(attrs, "ilvl")?.toIntOrNull() ?: 0
                    "abstractNumId" -> {
                        if (currentNum != null) numToAbstract[currentNum!!] = attr(attrs, "val") ?: ""
                    }
                    "numFmt" -> {
                        val abs = currentAbstract
                        val lvl = currentLvl
                        if (abs != null && lvl != null) {
                            val format = if (attr(attrs, "val") == "bullet") "bullet" else "decimal"
                            abstract.getOrPut(abs) { LinkedHashMap() }[lvl] = format
                        }
                    }
                }
            }
            override fun endElement(uri: String?, local: String, qName: String) {
                when (local) {
                    "abstractNum" -> currentAbstract = null
                    "num" -> currentNum = null
                    "lvl" -> currentLvl = null
                }
            }
        }) }
        return numToAbstract.mapValues { (_, abs) -> abstract[abs].orEmpty() }
    }

    // ---- xlsx ------------------------------------------------------------------

    private fun loadSharedStrings(zip: ZipFile, map: Map<String, ZipEntry>): List<String> {
        val stream = open(zip, map, "xl/sharedStrings.xml") ?: return emptyList()
        val out = ArrayList<String>()
        var current: StringBuilder? = null
        var totalChars = 0L
        var inText = false
        stream.use { parseXml(it, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                when (local) {
                    "si" -> {
                        require(out.size < MAX_SHARED_STRINGS) { "OFFICE_SIZE_LIMIT" }
                        current = StringBuilder()
                    }
                    "t" -> inText = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inText) {
                    totalChars += length.toLong()
                    require(totalChars <= MAX_TOTAL_CHARS) { "OFFICE_SIZE_LIMIT" }
                    current?.append(ch, start, length)
                }
            }
            override fun endElement(uri: String?, local: String, qName: String) {
                when (local) {
                    "t" -> inText = false
                    "si" -> { out += current?.toString().orEmpty(); current = null }
                }
            }
        }) }
        return out
    }

    /** numFmtId -> true when the format renders a date or time. */
    private fun loadDateFormats(zip: ZipFile, map: Map<String, ZipEntry>): Set<Int> {
        val stream = open(zip, map, "xl/styles.xml") ?: return emptySet()
        val formats = LinkedHashMap<Int, String>()
        val cellFormats = ArrayList<Int>()
        var inNumFmts = false
        var inCellXfs = false
        stream.use { parseXml(it, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                when (local) {
                    "numFmts" -> inNumFmts = true
                    "cellXfs" -> inCellXfs = true
                    "numFmt" -> if (inNumFmts) {
                        attr(attrs, "numFmtId")?.toIntOrNull()?.let { formats[it] = attr(attrs, "formatCode") ?: "" }
                    }
                    "xf" -> if (inCellXfs) {
                        cellFormats += attr(attrs, "numFmtId")?.toIntOrNull() ?: 0
                    }
                }
            }
            override fun endElement(uri: String?, local: String, qName: String) {
                when (local) {
                    "numFmts" -> inNumFmts = false
                    "cellXfs" -> inCellXfs = false
                }
            }
        }) }
        val builtInDates = setOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 45, 46, 47, 50, 57)
        return cellFormats.indices.filter { index ->
            val id = cellFormats[index]
            id in builtInDates || formats[id]?.let { code ->
                val lower = code.lowercase()
                ('y' in lower && ('m' in lower || 'd' in lower)) || 'h' in lower
            } ?: false
        }.toSet()
    }

    private fun colIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }
        require(letters.length in 1..3 && letters.all { it.uppercaseChar() in 'A'..'Z' }) {
            "OFFICE_INVALID"
        }
        var index = 0
        for (ch in letters) {
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        require(index in 1..16_384) { "OFFICE_SIZE_LIMIT" }
        return index - 1
    }

    private fun formatExcelNumber(value: Double, isDate: Boolean): String {
        if (isDate) {
            // Excel epoch 1899-12-30 already accounts for the 1900 leap bug.
            val days = value.toLong()
            val seconds = ((value - days) * 86400).toLong()
            val date = java.time.LocalDate.of(1899, 12, 30).plusDays(days)
            return if (seconds == 0L) date.toString()
            else date.atTime(java.time.LocalTime.ofSecondOfDay(seconds.coerceIn(0, 86399))).toString().replace('T', ' ')
        }
        return if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private fun readXlsx(zip: ZipFile, map: Map<String, ZipEntry>, maxImageBytes: Long, checkCancel: () -> Unit): OfficeDocument {
        val workbook = "xl/workbook.xml"
        require(map.containsKey(workbook)) { "OFFICE_INVALID" }
        val shared = loadSharedStrings(zip, map)
        val dateStyles = loadDateFormats(zip, map)
        val acc = Accumulator(maxImageBytes)
        // Sheet order from workbook.xml, resolved through workbook rels.
        val sheets = ArrayList<Pair<String, String>>()
        open(zip, map, workbook)!!.use { stream ->
            parseXml(stream, object : DefaultHandler() {
                override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                    if (local == "sheet") {
                        val name = attr(attrs, "name") ?: return
                        val rid = attr(attrs, "id") ?: return
                        sheets += name to rid
                    }
                }
            })
        }
        val wbRels = rels(zip, map, "xl/_rels/workbook.xml.rels")
        sheets.forEach { (name, rid) ->
            checkCancel()
            val target = wbRels[rid]?.let { resolve(workbook, it) } ?: return@forEach
            val sheetStream = open(zip, map, target) ?: return@forEach
            acc.add(OfficeBlock.Section(name))
            val rows = ArrayList<ArrayList<OfficeCell>>()
            var currentCells = sortedMapOf<Int, OfficeCell>()
            var cellType: String? = null
            var cellStyle = 0
            var cellValue = StringBuilder()
            var inlineText = StringBuilder()
            var inValue = false
            var inInline = false
            var inInlineText = false
            var inCell = false
            var cellCol = 0
            var sheetXmlChars = 0L
            var sheetOutputChars = 0L
            sheetStream.use { parseXml(it, object : DefaultHandler() {
                override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                    when (local) {
                        "row" -> {
                            acc.reserveTableRow()
                            currentCells = sortedMapOf()
                        }
                        "c" -> {
                            acc.reserveTableCell()
                            inCell = true
                            cellCol = attr(attrs, "r")?.let { colIndex(it) } ?: 0
                            cellType = attr(attrs, "t")
                            cellStyle = attr(attrs, "s")?.toIntOrNull() ?: 0
                            cellValue = StringBuilder(); inlineText = StringBuilder()
                        }
                        "v" -> inValue = true
                        "is" -> inInline = true
                        "t" -> if (inInline) inInlineText = true
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inValue || inInlineText) {
                        sheetXmlChars += length.toLong()
                        require(sheetXmlChars <= MAX_TOTAL_CHARS) { "OFFICE_SIZE_LIMIT" }
                    }
                    if (inValue) cellValue.append(ch, start, length)
                    else if (inInlineText) inlineText.append(ch, start, length)
                }
                override fun endElement(uri: String?, local: String, qName: String) {
                    when (local) {
                        "v" -> inValue = false
                        "t" -> inInlineText = false
                        "is" -> inInline = false
                        "c" -> {
                            if (inCell) {
                                val raw = cellValue.toString()
                                val text = when (cellType) {
                                    "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                                    "str", "e" -> raw
                                    "b" -> if (raw == "1" || raw.equals("true", true)) "TRUE" else "FALSE"
                                    "inlineStr" -> inlineText.toString()
                                    else -> raw.toDoubleOrNull()?.let { formatExcelNumber(it, cellStyle in dateStyles) } ?: raw
                                }
                                sheetOutputChars += text.length.toLong()
                                require(sheetOutputChars <= MAX_TOTAL_CHARS) { "OFFICE_SIZE_LIMIT" }
                                currentCells[cellCol] = OfficeCell(text)
                            }
                            inCell = false
                        }
                        "row" -> {
                            val cells = currentCells.values.toList()
                            val trimmed = cells.dropLastWhile { it.text.isBlank() }
                            if (trimmed.isNotEmpty()) rows += ArrayList(trimmed)
                        }
                    }
                }
            }) }
            val nonEmpty = rows.dropLastWhile { row -> row.all { it.text.isBlank() } }
            if (nonEmpty.isNotEmpty()) acc.add(OfficeBlock.TableBlock(nonEmpty.map { it.toList() }))
        }
        return OfficeDocument(OfficeKind.XLSX, 842.0, 595.0, 56.0, acc.blocks, acc.unsupported.toList())
    }

    // ---- pptx ------------------------------------------------------------------

    private fun readPptx(zip: ZipFile, map: Map<String, ZipEntry>, maxImageBytes: Long, checkCancel: () -> Unit): OfficeDocument {
        val presentation = "ppt/presentation.xml"
        require(map.containsKey(presentation)) { "OFFICE_INVALID" }
        var slideWidthPt = 960.0
        var slideHeightPt = 540.0
        val slideIds = ArrayList<String>()
        open(zip, map, presentation)!!.use { stream ->
            parseXml(stream, object : DefaultHandler() {
                override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                    when (local) {
                        "sldSz" -> {
                            attr(attrs, "cx")?.toDoubleOrNull()?.let { slideWidthPt = it / 12700.0 }
                            attr(attrs, "cy")?.toDoubleOrNull()?.let { slideHeightPt = it / 12700.0 }
                        }
                        // The required numeric id and r:id both have local name "id".
                        // Only the relationship namespace contains the slide target id.
                        "sldId" -> relationshipId(attrs)?.let { slideIds += it }
                    }
                }
            })
        }
        val presRels = rels(zip, map, "ppt/_rels/presentation.xml.rels")
        val acc = Accumulator(maxImageBytes)
        slideIds.forEachIndexed { slideIndex, rid ->
            checkCancel()
            val target = presRels[rid]?.let { resolve(presentation, it) } ?: return@forEachIndexed
            if (slideIndex > 0) acc.add(OfficeBlock.PageBreak)
            val slideRels = rels(zip, map, target.substringBeforeLast('/') + "/_rels/" + target.substringAfterLast('/') + ".rels")
            val slideRelTargets = slideRels.mapValues { (_, t) -> resolve(target, t) }
            val stream = open(zip, map, target) ?: return@forEachIndexed
            stream.use { parseXml(it, object : DefaultHandler() {
                // Shape text state.
                var runs = ArrayList<OfficeRun>()
                var runText = StringBuilder()
                var runBold = false
                var runItalic = false
                var runSize = 0
                var inRun = false
                var inText = false
                var inTxBody = false
                var paragraphSize = 0
                var inTable = false
                var tableRows = ArrayList<ArrayList<OfficeCell>>()
                var currentRow: ArrayList<OfficeCell>? = null
                var inCell = false
                var cellText = StringBuilder()
                var inTblPr = false
                var tableHeader = false
                var slideTextChars = 0L

                fun flushRun() {
                    if (!inRun) return
                    val text = runText.toString()
                    if (text.isNotEmpty()) {
                        runs += OfficeRun(text, runBold, runItalic)
                        if (runSize > paragraphSize) paragraphSize = runSize
                    }
                    runText = StringBuilder(); inRun = false
                }

                fun flushParagraph() {
                    flushRun()
                    if (runs.isEmpty()) return
                    val level = if (paragraphSize >= 2800) 1 else 0
                    val paragraph = OfficeParagraph(runs.toList(), level = level)
                    runs = ArrayList(); paragraphSize = 0
                    if (inCell) {
                        cellText.append(paragraph.text).append(' ')
                    } else if (!inTable) {
                        acc.add(OfficeBlock.ParagraphBlock(paragraph))
                    }
                }

                override fun startElement(uri: String?, local: String, qName: String, attrs: Attributes) {
                    when (local) {
                        "txBody" -> inTxBody = true
                        "p" -> if (inTxBody) { runs = ArrayList(); paragraphSize = 0 }
                        "r" -> if (inTxBody) { inRun = true; runBold = false; runItalic = false; runSize = 0 }
                        "rPr" -> if (inRun) {
                            runBold = attr(attrs, "b") == "1"
                            runItalic = attr(attrs, "i") == "1"
                            runSize = attr(attrs, "sz")?.toIntOrNull() ?: 0
                        }
                        "t" -> if (inRun) inText = true
                        "br" -> if (inRun) runText.append('\n')
                        "tbl" -> { inTable = true; tableRows = ArrayList(); tableHeader = false }
                        "tblPr" -> {
                            inTblPr = true
                            // firstRow is an attribute of tblPr, not an element.
                            tableHeader = attr(attrs, "firstRow") == "1"
                        }
                        "tr" -> if (inTable) {
                            acc.reserveTableRow()
                            currentRow = ArrayList()
                        }
                        "tc" -> if (inTable) {
                            acc.reserveTableCell()
                            inCell = true
                            cellText = StringBuilder()
                        }
                        "blip" -> {
                            val embed = attr(attrs, "embed")
                            val target = embed?.let { slideRelTargets[it] }
                            if (target != null) {
                                val ext = imageExtension(target)
                                if (isRasterImage(ext)) {
                                    open(zip, map, target)?.use { bytes ->
                                        acc.readImage(bytes, ext, checkCancel)
                                    }
                                } else {
                                    acc.unsupported += "UNSUPPORTED_IMAGE"
                                }
                            }
                        }
                        "chart" -> acc.unsupported += "CHART"
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inRun && inText) {
                        slideTextChars += length.toLong()
                        require(slideTextChars <= MAX_TOTAL_CHARS) { "OFFICE_SIZE_LIMIT" }
                        runText.append(ch, start, length)
                    }
                }
                override fun endElement(uri: String?, local: String, qName: String) {
                    when (local) {
                        "txBody" -> inTxBody = false
                        "p" -> if (inTxBody) flushParagraph()
                        "r" -> flushRun()
                        "t" -> inText = false
                        "tbl" -> {
                            inTable = false
                            if (tableRows.isNotEmpty()) {
                                acc.add(OfficeBlock.TableBlock(tableRows.map { it.toList() }, tableHeader))
                            }
                        }
                        "tblPr" -> inTblPr = false
                        "tr" -> {
                            val row = currentRow
                            if (row != null && row.isNotEmpty()) tableRows += row
                            currentRow = null
                        }
                        "tc" -> {
                            if (inCell) currentRow?.add(OfficeCell(cellText.toString().trim()))
                            inCell = false
                        }
                    }
                }
            }) }
        }
        return OfficeDocument(OfficeKind.PPTX, slideWidthPt, slideHeightPt, 56.0, acc.blocks, acc.unsupported.toList())
    }
}
