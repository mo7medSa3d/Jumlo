package com.jumlo.app.pdf

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import java.io.File

/** Test-only builders for real PDF fixtures. Pure Java/JVM; no android classes. */
object PdfFixtures {
    private val font: PDFont = PDType1Font.HELVETICA

    /** One text page per entry, drawn at a known position (72pt, 700pt from bottom). */
    fun simpleDoc(target: File, texts: List<String>): File {
        PDDocument().use { doc ->
            texts.forEachIndexed { index, text ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true).use { cs ->
                    cs.beginText()
                    cs.setFont(font, 12.0f)
                    cs.newLineAtOffset(72.0f, 700.0f)
                    cs.showText(text)
                    cs.endText()
                }
                index
            }
            save(doc, target)
        }
        return target
    }

    /** Page 0: raw flate image; page 1: text "marker". */
    fun imageDoc(target: File, textPage: Boolean): File {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val image = rawImage(doc, 600, 400, 87)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true).use { cs ->
                cs.drawImage(image, 72.0f, 400.0f, 300.0f, 200.0f)
                cs.beginText()
                cs.setFont(font, 12.0f)
                cs.newLineAtOffset(72.0f, 200.0f)
                cs.showText("vectors stay")
                cs.endText()
            }
            if (textPage) {
                val second = PDPage(PDRectangle.A4)
                doc.addPage(second)
                PDPageContentStream(doc, second, PDPageContentStream.AppendMode.OVERWRITE, true, true).use { cs ->
                    cs.beginText()
                    cs.setFont(font, 12.0f)
                    cs.newLineAtOffset(72.0f, 700.0f)
                    cs.showText("marker")
                    cs.endText()
                }
            }
            save(doc, target)
        }
        return target
    }

    /** AcroForm with one visible text field ("name") and one checkbox ("agree"). */
    fun formDoc(target: File): File {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val acro = PDAcroForm(doc)
            doc.documentCatalog.acroForm = acro
            acro.defaultResources = PDResources().also { it.put(COSName.getPDFName("Helv"), font) }
            val text = PDTextField(acro)
            text.partialName = "name"
            text.defaultAppearance = "/Helv 12 Tf 0 g"
            val check = PDCheckBox(acro)
            check.partialName = "agree"
            val textWidget = PDAnnotationWidget()
            textWidget.rectangle = PDRectangle(150f, 700f, 160f, 20f)
            textWidget.setPage(page)
            val checkWidget = PDAnnotationWidget()
            checkWidget.rectangle = PDRectangle(150f, 650f, 20f, 20f)
            checkWidget.setPage(page)
            text.widgets = listOf(textWidget)
            check.widgets = listOf(checkWidget)
            acro.setFields(listOf(text, check))
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true).use { cs ->
                cs.beginText()
                cs.setFont(font, 10.0f)
                cs.newLineAtOffset(150.0f, 640.0f)
                cs.showText("form reference")
                cs.endText()
            }
            acro.refreshAppearances()
            save(doc, target)
        }
        return target
    }

    private fun rawImage(doc: PDDocument, width: Int, height: Int, seed: Int): PDImageXObject {
        // Deterministic pseudo-random noise: the realistic worst case for compression,
        // where fewer pixels must mean a strictly smaller flate stream.
        val data = ByteArray(width * height * 3)
        java.util.Random(seed.toLong()).nextBytes(data)
        val stream = COSStream()
        stream.setItem(COSName.SUBTYPE, COSName.IMAGE)
        stream.setInt(COSName.WIDTH, width)
        stream.setInt(COSName.HEIGHT, height)
        stream.setItem(COSName.COLORSPACE, COSName.DEVICERGB)
        stream.setInt(COSName.BITS_PER_COMPONENT, 8)
        stream.setItem(COSName.FILTER, COSName.FLATE_DECODE)
        // This port's createOutputStream() compresses on close when /Filter is set.
        stream.createOutputStream().use { it.write(data) }
        return PDImageXObject(com.tom_roush.pdfbox.pdmodel.common.PDStream(stream), null)
    }

    private fun save(doc: PDDocument, target: File) {
        doc.save(target)
    }
}
