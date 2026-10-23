package com.jumlo.app.pdf

import com.jumlo.app.core.PdfInkPoint
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PdfInkAppearanceTest {
    @Test fun inkColorAndPhysicalThicknessSurviveSavingOnScaledPages() {
        val out=Files.createTempFile("ink-style-", ".pdf").toFile()
        try {
            PDDocument().use {doc ->
                doc.addPage(PDPage(PDRectangle(200f,300f)).apply {userUnit=2f})
                PdfBoxBackend.addInkStrokes(doc,0,listOf(listOf(PdfInkPoint(.1,.2),PdfInkPoint(.8,.6))),"", "#C02040",6f)
                doc.save(out)
            }
            PDDocument.load(out).use {doc ->
                val annotation=doc.getPage(0).annotations.single() as com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
                assertArrayEquals(floatArrayOf(192/255f,32/255f,64/255f),annotation.color.components,.0001f)
                assertEquals(3f,annotation.borderStyle.width,.001f)
                assertNotNull(annotation.normalAppearanceStream)
            }
        } finally {out.delete()}
    }
    @Test fun straightStrokesHaveFinitePaddedBoundsAfterSaveAndReopen() {
        val paths=listOf(
            listOf(PdfInkPoint(.2,.5),PdfInkPoint(.8,.5)),
            listOf(PdfInkPoint(.5,.2),PdfInkPoint(.5,.8)))
        for(path in paths) {
            val out=Files.createTempFile("ink-straight-", ".pdf").toFile()
            try {
                PDDocument().use {doc->doc.addPage(PDPage(PDRectangle(200f,300f)));PdfBoxBackend.addInkStrokes(doc,0,listOf(path));doc.save(out)}
                PDDocument.load(out).use {doc ->
                    val rect=doc.getPage(0).annotations.single().rectangle
                    assertTrue("straight strokes need padding on both axes",rect.width>0f && rect.height>0f)
                    assertTrue(listOf(rect.lowerLeftX,rect.lowerLeftY,rect.upperRightX,rect.upperRightY).all {it.isFinite()})
                    val xs=path.map {(it.x*200).toFloat()};val ys=path.map {((1-it.y)*300).toFloat()}
                    assertTrue(rect.lowerLeftX<xs.min() && rect.upperRightX>xs.max())
                    assertTrue(rect.lowerLeftY<ys.min() && rect.upperRightY>ys.max())
                }
            } finally {out.delete()}
        }
    }

    @Test fun exportedInkHasSeparatePaintedPathsAndPrintVisibility() {
        val out=Files.createTempFile("ink-appearance-", ".pdf").toFile()
        try {
            PDDocument().use {doc ->
                doc.addPage(PDPage(PDRectangle(200f,300f)))
                PdfBoxBackend.addInkStrokes(doc,0,listOf(
                    listOf(PdfInkPoint(.1,.2),PdfInkPoint(.3,.4)),
                    listOf(PdfInkPoint(.7,.6),PdfInkPoint(.8,.9))))
                doc.save(out)
            }
            PDDocument.load(out).use {doc ->
                val annotation=doc.getPage(0).annotations.single()
                val appearance=annotation.normalAppearanceStream
                assertNotNull("Ink must carry a rendered appearance for readers and printing",appearance)
                assertTrue(annotation.isPrinted)
                assertTrue(appearance!!.bBox.width>0f && appearance.bBox.height>0f)
                val parser=PDFStreamParser(appearance)
                parser.parse()
                val operators=parser.tokens.filterIsInstance<Operator>().map {it.name}
                assertEquals(2,operators.count {it=="m"})
                assertEquals(2,operators.count {it=="l"})
                assertEquals(2,operators.count {it=="S"})
            }
        } finally {out.delete()}
    }
}
