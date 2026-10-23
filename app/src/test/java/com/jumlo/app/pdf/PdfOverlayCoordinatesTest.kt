package com.jumlo.app.pdf

import com.jumlo.app.core.PdfWatermarkStyle
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/** Reads actual saved PDF operators; no Android bitmap/renderer emulation is involved. */
class PdfOverlayCoordinatesTest {
    @Test fun signatureRotationKeepsItsCenterAndAspectInTheVisiblePage() {
        val folder=java.nio.file.Files.createTempDirectory("pdf-signature-rotation").toFile()
        try {
            val source=PdfFixtures.imageDoc(File(folder,"source.pdf"),false)
            val result=File(folder,"result.pdf")
            PdfBoxBackend.open(source,null).use { doc ->
                val page=doc.getPage(0);page.mediaBox=PDRectangle(200f,300f);page.cropBox=page.mediaBox
                val image=page.resources.xObjectNames.map {page.resources.getXObject(it)}.filterIsInstance<PDImageXObject>().single()
                PdfBoxBackend.stampImageObject(doc,0,image,.2,.2,.8,.6,90f)
                PdfBoxBackend.stampImageObject(doc,0,image,.1,.7,.4,.9,-30f)
                doc.save(result)
            }
            PdfBoxBackend.open(result,null).use { doc ->
                val images=painted(doc.getPage(0)).images.takeLast(2)
                val first=images[0];val center=first.map(.5,.5)
                assertEquals(100.0,center.first,.001);assertEquals(180.0,center.second,.001)
                assertEquals(0.0,first.a,.001);assertEquals(-120.0,first.b,.001)
                assertEquals(1.5,hypot(first.a,first.b)/hypot(first.c,first.d),.001)
                val second=images[1].map(.5,.5)
                assertEquals(50.0,second.first,.001);assertEquals(60.0,second.second,.001)
            }
        } finally {folder.deleteRecursively()}
    }

    @Test fun imageWatermarkUsesRequestedScaleOpacityAndNineTilePositions() {
        val folder=java.nio.file.Files.createTempDirectory("pdf-logo-watermark").toFile()
        try {
            val source=PdfFixtures.imageDoc(File(folder,"source.pdf"),false)
            val result=File(folder,"result.pdf")
            PdfBoxBackend.open(source,null).use { doc ->
                val page=doc.getPage(0);page.mediaBox=PDRectangle(200f,300f);page.cropBox=page.mediaBox
                val image=page.resources.xObjectNames.map {page.resources.getXObject(it)}.filterIsInstance<PDImageXObject>().single()
                PdfBoxBackend.stampWatermarkLogo(doc,listOf(0),image,PdfWatermarkStyle(tiled=true,rotationDegrees=0,opacityPercent=35,imageScale=1.5))
                doc.save(result)
            }
            PdfBoxBackend.open(result,null).use { doc ->
                val page=doc.getPage(0);val images=painted(page).images.takeLast(9)
                assertEquals(9,images.size)
                assertEquals(90.0,hypot(images.first().a,images.first().b),.001)
                assertEquals(setOf(36,100,164),images.map {it.map(.5,.5).first.toInt()}.toSet())
                assertEquals(setOf(54,150,246),images.map {kotlin.math.round(it.map(.5,.5).second).toInt()}.toSet())
                assertTrue(page.resources.extGStateNames.any {kotlin.math.abs(page.resources.getExtGState(it).nonStrokingAlphaConstant-.35f)<.001f})
            }
        } finally {folder.deleteRecursively()}
    }
    private data class Affine(val a:Double=1.0,val b:Double=0.0,val c:Double=0.0,val d:Double=1.0,val e:Double=0.0,val f:Double=0.0) {
        fun map(x:Double,y:Double)=(a*x+c*y+e) to (b*x+d*y+f)
        fun concatenate(n:Affine)=Affine(a*n.a+c*n.b,b*n.a+d*n.b,a*n.c+c*n.d,b*n.c+d*n.d,a*n.e+c*n.f+e,b*n.e+d*n.f+f)
    }
    private data class Painted(val images:List<Affine>,val numbers:List<Pair<Pair<Double,Double>,Double>>)
    private fun painted(page:PDPage):Painted {
        val parser=PDFStreamParser(page);parser.parse()
        var ctm=Affine();val stack=mutableListOf<Affine>();val operands=mutableListOf<Any>()
        val images=mutableListOf<Affine>();val numbers=mutableListOf<Pair<Pair<Double,Double>,Double>>()
        var font=0.0;var tx=0.0;var ty=0.0
        parser.tokens.forEach { token ->
            if(token !is Operator) operands+=token else {
                fun number(index:Int)=(operands[index] as COSNumber).doubleValue()
                when(token.name) {
                    "q" -> stack+=ctm
                    "Q" -> ctm=stack.removeAt(stack.lastIndex)
                    "cm" -> ctm=ctm.concatenate(Affine(number(0),number(1),number(2),number(3),number(4),number(5)))
                    "Do" -> images+=ctm
                    "BT" -> { tx=0.0;ty=0.0 }
                    "Tf" -> font=number(1)
                    "Td" -> { tx+=number(0);ty+=number(1) }
                    "Tj" -> if((operands[0] as COSString).string=="1") numbers+=ctm.map(tx,ty) to (hypot(ctm.a,ctm.b)*font)
                }
                operands.clear()
            }
        }
        return Painted(images,numbers)
    }

    @Test fun watermarkAndNumberUseVisibleRotatedCropAndPhysicalPointSizes() {
        val folder=java.nio.file.Files.createTempDirectory("pdf-overlay-coordinates").toFile()
        try {
            val rotations=listOf(0,90,180,270)
            val centers=listOf(130.0 to 520.0,160.0 to 240.0,370.0 to 280.0,340.0 to 560.0)
            val baselines=listOf(241.0 to 216.0,388.0 to 388.0,259.0 to 584.0,112.0 to 412.0)
            val horizontalVectors=listOf(50.0 to 0.0,0.0 to 50.0,-50.0 to 0.0,0.0 to -50.0)
            rotations.forEachIndexed { i,rotation ->
                val source=PdfFixtures.imageDoc(File(folder,"source-$rotation.pdf"),false)
                val result=File(folder,"result-$rotation.pdf")
                PdfBoxBackend.open(source,null).use { doc ->
                    val page=doc.getPage(0)
                    page.mediaBox=PDRectangle(600f,800f)
                    page.cropBox=PDRectangle(100f,200f,300f,400f)
                    page.rotation=rotation;page.userUnit=2f
                    val image=page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>().single()
                    PdfBoxBackend.stampWatermarkImage(doc,listOf(0),image,100f,30f,PdfWatermarkStyle(centerX=.1,centerY=.2,rotationDegrees=0))
                    PdfBoxBackend.stampWatermarkAndNumbers(doc,listOf(0),"",true)
                    doc.save(result)
                }
                PdfBoxBackend.open(result,null).use { doc ->
                    val page=doc.getPage(0);val content=painted(page)
                    assertEquals(rotation,page.rotation);assertEquals(2f,page.userUnit,0f)
                    val watermark=content.images.last();val center=watermark.map(.5,.5)
                    assertEquals("rotation $rotation center x",centers[i].first,center.first,.001)
                    assertEquals("rotation $rotation center y",centers[i].second,center.second,.001)
                    assertTrue(center.first in 100.0..400.0 && center.second in 200.0..600.0)
                    assertEquals(horizontalVectors[i].first,watermark.a,.001)
                    assertEquals(horizontalVectors[i].second,watermark.b,.001)
                    assertEquals("physical watermark width",100.0,hypot(watermark.a,watermark.b)*page.userUnit,.001)
                    assertEquals("physical watermark height",30.0,hypot(watermark.c,watermark.d)*page.userUnit,.001)
                    val number=content.numbers.single()
                    assertEquals("rotation $rotation number x",baselines[i].first,number.first.first,.001)
                    assertEquals("rotation $rotation number y",baselines[i].second,number.first.second,.001)
                    assertEquals("physical page-number font",10.0,number.second*page.userUnit,.001)
                }
            }
        } finally { folder.deleteRecursively() }
    }

    @Test fun widthCapUsesOrientedCropWidthAndStyleRotationUsesDisplayDirection() {
        val folder=java.nio.file.Files.createTempDirectory("pdf-overlay-cap").toFile()
        try {
            val source=PdfFixtures.imageDoc(File(folder,"source.pdf"),false)
            val result=File(folder,"result.pdf")
            PdfBoxBackend.open(source,null).use { doc ->
                val page=doc.getPage(0)
                page.cropBox=PDRectangle(100f,200f,100f,300f)
                page.rotation=90;page.userUnit=2f
                val image=page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>().single()
                PdfBoxBackend.stampWatermarkImage(doc,listOf(0),image,1000f,100f,PdfWatermarkStyle(rotationDegrees=90))
                doc.save(result)
            }
            PdfBoxBackend.open(result,null).use { doc ->
                val watermark=painted(doc.getPage(0)).images.last()
                // Display width = crop height 300 * UserUnit 2, cap = .8*600.
                assertEquals(480.0,hypot(watermark.a,watermark.b)*2,.001)
                // A clockwise watermark quarter-turn cancels the inverse page
                // quarter-turn, so its raw baseline points right, not left.
                assertEquals(240.0,watermark.a,.001);assertEquals(0.0,watermark.b,.001)
            }
        } finally { folder.deleteRecursively() }
    }
    @Test fun imageSignatureAndUnicodeNoteUseDisplayedCropCoordinates() {
        val folder=java.nio.file.Files.createTempDirectory("pdf-image-note-coordinates").toFile()
        try {
            val centers=listOf(205.0 to 400.0,250.0 to 340.0,295.0 to 400.0,250.0 to 460.0)
            val noteOrigins=listOf(130.0 to 520.0,150.0 to 240.0,360.0 to 270.0,340.0 to 550.0)
            listOf(0,90,180,270).forEachIndexed { i,rotation ->
                val source=PdfFixtures.imageDoc(File(folder,"source-$rotation.pdf"),false)
                val result=File(folder,"result-$rotation.pdf")
                PdfBoxBackend.open(source,null).use { doc ->
                    val page=doc.getPage(0)
                    page.mediaBox=PDRectangle(600f,800f);page.cropBox=PDRectangle(100f,200f,300f,400f)
                    page.rotation=rotation;page.userUnit=2f
                    val image=page.resources.xObjectNames.map { page.resources.getXObject(it) }.filterIsInstance<PDImageXObject>().single()
                    PdfBoxBackend.stampImageObject(doc,0,image,.1,.2,.6,.8)
                    PdfBoxBackend.addTextAnnotation(doc,0,"ملاحظة",.1,.2)
                    doc.save(result)
                }
                PdfBoxBackend.open(result,null).use { doc ->
                    val page=doc.getPage(0);val image=painted(page).images.last()
                    val center=image.map(.5,.5)
                    assertEquals(centers[i].first,center.first,.001);assertEquals(centers[i].second,center.second,.001)
                    val physicalWidth=hypot(image.a,image.b)*2
                    val physicalHeight=hypot(image.c,image.d)*2
                    assertEquals(if(rotation%180==0) 300.0 else 400.0,physicalWidth,.001)
                    assertEquals(1.5,physicalWidth/physicalHeight,.001)
                    val expectedBaseline=when(rotation) { 90->0.0 to 200.0;180->-150.0 to 0.0;270->0.0 to -200.0;else->150.0 to 0.0 }
                    assertEquals(expectedBaseline.first,image.a,.001);assertEquals(expectedBaseline.second,image.b,.001)
                    val note=page.annotations.single()
                    assertEquals("ملاحظة",note.contents)
                    assertEquals(noteOrigins[i].first,note.rectangle.lowerLeftX.toDouble(),.001)
                    assertEquals(noteOrigins[i].second,note.rectangle.lowerLeftY.toDouble(),.001)
                    assertEquals(10f,note.rectangle.width,.001f);assertEquals(10f,note.rectangle.height,.001f)
                    assertFalse(note.isNoRotate)
                    assertTrue(note.page.cosObject===page.cosObject)
                }
            }
        } finally { folder.deleteRecursively() }
    }

    @Test fun cropSelectionUsesCurrentVisibleFrameForEveryIntrinsicRotation() {
        val folder=java.nio.file.Files.createTempDirectory("pdf-crop-coordinates").toFile()
        try {
            val expected=listOf(listOf(130f,280f,150f,240f),listOf(160f,240f,180f,200f),
                listOf(220f,280f,150f,240f),listOf(160f,360f,180f,200f))
            listOf(0,90,180,270).forEachIndexed { i,rotation ->
                val source=PdfFixtures.simpleDoc(File(folder,"source-$rotation.pdf"),listOf("crop marker"))
                val result=File(folder,"result-$rotation.pdf")
                PdfBoxBackend.open(source,null).use { doc ->
                    val page=doc.getPage(0)
                    page.mediaBox=PDRectangle(600f,800f);page.cropBox=PDRectangle(100f,200f,300f,400f)
                    page.rotation=rotation;page.userUnit=2f
                    PdfBoxBackend.setCrop(doc,0,0.0,0.0,1.0,1.0)
                    assertEquals(100f,page.cropBox.lowerLeftX,0f);assertEquals(200f,page.cropBox.lowerLeftY,0f)
                    assertEquals(300f,page.cropBox.width,0f);assertEquals(400f,page.cropBox.height,0f)
                    PdfBoxBackend.setCrop(doc,0,.1,.2,.6,.8)
                    doc.save(result)
                }
                PdfBoxBackend.open(result,null).use { doc ->
                    val page=doc.getPage(0);val crop=page.cropBox
                    val values=listOf(crop.lowerLeftX,crop.lowerLeftY,crop.width,crop.height)
                    expected[i].zip(values).forEach { (wanted,actual) -> assertEquals("rotation $rotation",wanted,actual,.001f) }
                    assertEquals(600f,page.mediaBox.width,0f);assertEquals(800f,page.mediaBox.height,0f)
                    assertEquals(rotation,page.rotation);assertEquals(2f,page.userUnit,0f)
                }
            }
        } finally { folder.deleteRecursively() }
    }
}
