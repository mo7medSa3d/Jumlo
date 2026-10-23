package com.jumlo.app.hosttest

import com.jumlo.app.core.AutoOutputName
import com.jumlo.app.core.ToolCatalogue
import com.jumlo.app.data.jobs.CapabilityRegistry

fun main() {
    for (id in listOf("pdf_to_docx", "pdf_to_pptx", "pdf_ocr")) {
        check(CapabilityRegistry.canProcess(id)) { "$id is not connected to processing" }
        check(CapabilityRegistry.tools.any { it.id == id }) { "$id has no user-facing entry" }
        check(ToolCatalogue.isListed(id)) { "$id is hidden from the tools page" }
    }
    check(AutoOutputName.fromSource("pdf_to_docx", "مستند.pdf") == "مستند - Word.docx")
    check(AutoOutputName.fromSource("pdf_to_pptx", "Report.pdf") == "Report - Slides.pptx")
    check(AutoOutputName.fromSource("pdf_ocr", "Scan.pdf") == "Scan - Text.txt")
    println("PASS: Office/OCR tools are accessible and use the correct output extensions")
}
