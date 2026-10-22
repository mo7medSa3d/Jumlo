package com.jumlo.app.pdf

import com.jumlo.app.core.PdfInkPoint
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PdfInkDrawingTest {
    private val first=listOf(PdfInkPoint(.1,.2),PdfInkPoint(.3,.4))
    private val second=listOf(PdfInkPoint(.7,.6),PdfInkPoint(.8,.9))

    @Test fun separateStrokesSurviveSaveAndReopenWithoutABridge() {
        for(rotation in listOf(0,90)) {
            val out=Files.createTempFile("ink-drawing-", ".pdf").toFile()
            try {
                PDDocument().use {doc ->
                    doc.addPage(PDPage(PDRectangle(200f,300f)).apply {this.rotation=rotation})
                    PdfBoxBackend.addInkStrokes(doc,0,listOf(first,second),"Drawing")
                    doc.save(out)
                }
                PDDocument.load(out).use {doc ->
                    val annotation=doc.getPage(0).annotations.single()
                    assertEquals("Ink",annotation.subtype)
                    assertEquals("Drawing",annotation.contents)
                    val paths=annotation.cosObject.getDictionaryObject(COSName.INKLIST) as COSArray
                    assertEquals("pen lifts must remain separate paths",2,paths.size())
                    val a=paths.get(0) as COSArray
                    val b=paths.get(1) as COSArray
                    assertEquals(4,a.size());assertEquals(4,b.size())
                    val expected=if(rotation==0) listOf(20f,240f,60f,180f,140f,120f,160f,30f)
                        else listOf(40f,30f,80f,90f,120f,210f,180f,240f)
                    val actual=listOf(a,b).flatMap {path->(0 until path.size()).map {(path.get(it) as COSNumber).floatValue()}}
                    expected.zip(actual).forEach {(e,v)->assertEquals(e,v,.01f)}
                    assertTrue(annotation.rectangle.width>0f && annotation.rectangle.height>0f)
                }
            } finally {out.delete()}
        }
    }

    @Test fun legacyOneStrokeApiStillWritesOnePath() {
        PDDocument().use {doc ->
            doc.addPage(PDPage(PDRectangle.LETTER))
            PdfBoxBackend.addInkAnnotation(doc,0,first)
            val paths=doc.getPage(0).annotations.single().cosObject.getDictionaryObject(COSName.INKLIST) as COSArray
            assertEquals(1,paths.size())
        }
    }

    @Test fun invalidCoordinatesCannotProduceAPartialAnnotation() {
        PDDocument().use {doc ->
            doc.addPage(PDPage(PDRectangle.LETTER))
            for(bad in listOf(Double.NaN,Double.POSITIVE_INFINITY,-.01,1.01)) {
                assertTrue(runCatching {PdfBoxBackend.addInkStrokes(doc,0,listOf(first,listOf(PdfInkPoint(bad,.2),PdfInkPoint(.3,.4))))}.isFailure)
                assertTrue(doc.getPage(0).annotations.isEmpty())
            }
        }
    }
}
