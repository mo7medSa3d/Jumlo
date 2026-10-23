package com.jumlo.app.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.WorkBudget
import com.jumlo.app.data.local.AppDatabase
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.data.output.PublicOutputStore
import com.jumlo.app.data.preferences.UserPreferencesRepository
import com.jumlo.app.data.processing.PdfAnnotateSettings
import com.jumlo.app.data.processing.PdfImagesSettings
import com.jumlo.app.data.processing.PdfOperationHandler
import com.jumlo.app.data.processing.PdfSplitSettings
import com.jumlo.app.data.processing.PdfSignatureSettings
import com.jumlo.app.data.processing.PdfSignaturePlacement
import com.jumlo.app.data.source.EphemeralSecretStore
import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.model.JobRequest
import com.jumlo.app.model.ItemStatus
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class PdfHandlerReliabilityTest {
    @Test fun multipleRotatedTransparentSignaturesReachThePublishedPages()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        val folder=File(app.filesDir,"processing/results/fixture/signatures-${UUID.randomUUID()}").apply {mkdirs()}
        val source=File(folder,"source.pdf");val signature=File(folder,"signature.png")
        try {
            PDDocument().use {doc ->repeat(2) {doc.addPage(PDPage(PDRectangle(200f,300f)))};doc.save(source)}
            val ink=android.graphics.Bitmap.createBitmap(200,100,android.graphics.Bitmap.Config.ARGB_8888)
            try {
                android.graphics.Canvas(ink).drawRect(20f,40f,180f,60f,android.graphics.Paint().apply {color=Color.RED})
                signature.outputStream().use {assertTrue(ink.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            } finally {ink.recycle()}
            val original=source.readBytes();val originalInk=signature.readBytes()
            val budget=WorkBudget();val sources=SourceStore(app,db,budget)
            val outputs=OutputStore(app,db,PublicOutputStore(app,UserPreferencesRepository(app)))
            val ids=listOf(source,signature).map {file ->sources.inspect(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",file).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION).id}
            val settings=PdfSignatureSettings(placements=listOf(
                PdfSignaturePlacement("first",1,.25,.25,.75,.75,90f),
                PdfSignaturePlacement("second",2,.1,.1,.5,.3,0f)))
            val request=JobRequest(UUID.randomUUID().toString(),"pdf_signature",ids,1,Json.encodeToString(settings),System.currentTimeMillis())
            val outcomes=PdfOperationHandler(EphemeralSecretStore()).execute(request,ExecutionContext(sources,outputs,budget,{},{}))
            assertEquals(ItemStatus.CREATED,outcomes.single().status)
            val output=outputs.find(request.id,"signed")!!
            app.contentResolver.openFileDescriptor(android.net.Uri.parse(output.uri),"r")!!.use {descriptor ->
                com.jumlo.app.data.pdf.PdfRendererFactory.open(descriptor,app.cacheDir).use {renderer ->
                    assertEquals(2,renderer.pageCount)
                    repeat(2) {index ->renderer.openPage(index).use {page ->
                        val bitmap=android.graphics.Bitmap.createBitmap(200,300,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(Color.WHITE)}
                        try {
                            page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pixel=if(index==0) bitmap.getPixel(100,120) else bitmap.getPixel(60,60)
                            assertTrue(Color.red(pixel)>240 && Color.green(pixel)<10)
                            assertEquals(Color.WHITE,bitmap.getPixel(if(index==0) 120 else 90,if(index==0) 150 else 80))
                        } finally {bitmap.recycle()}
                    }}
                }
            }
            assertArrayEquals(original,source.readBytes());assertArrayEquals(originalInk,signature.readBytes())
        } finally {db.close();folder.deleteRecursively()}
    }
    @Test fun corruptStructuralPdfReportsTheDocumentErrorWithoutPublishing()=runBlocking<Unit> {
        val app=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        val source=File(app.filesDir,"processing/results/fixture/corrupt-${UUID.randomUUID()}.pdf").apply {
            requireNotNull(parentFile).mkdirs();writeText("%PDF-1.7\nthis is not a PDF document")
        }
        try {
            val budget=WorkBudget()
            val sources=SourceStore(app,db,budget)
            val outputs=OutputStore(app,db,PublicOutputStore(app,UserPreferencesRepository(app)))
            val ref=sources.inspect(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",source).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val request=JobRequest(UUID.randomUUID().toString(),"pdf_crop",listOf(ref.id),1,
                """{"pages":"all","left":0.1,"top":0.1,"right":0.9,"bottom":0.9}""",System.currentTimeMillis())
            val error=runCatching {PdfOperationHandler(EphemeralSecretStore()).execute(request,ExecutionContext(sources,outputs,budget,{},{}))}.exceptionOrNull()
            assertNotNull(error)
            assertEquals("PDF_INVALID",com.jumlo.app.data.jobs.stableError(error!!))
            assertNull(outputs.find(request.id,"document"))
        } finally {db.close();source.delete()}
    }

    @Test fun legacyAndMultiStrokeDrawingJobsKeepSeparatePaths()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        try {
            val sources=SourceStore(app,db,WorkBudget())
            val outputs=OutputStore(app,db,PublicOutputStore(app,UserPreferencesRepository(app)))
            val source=File(app.filesDir,"processing/results/fixture/ink-${UUID.randomUUID()}.pdf").apply {requireNotNull(parentFile).mkdirs()}
            PDDocument().use {doc->doc.addPage(PDPage(PDRectangle.LETTER));doc.save(source)}
            val original=source.readBytes()
            val ref=sources.inspect(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",source).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val a="[0.1,0.1,0.2,0.2]"
            val b="[0.7,0.8,0.8,0.9]"
            val cases=listOf(
                """{"kind":"ink","inkPoints":$a}""" to 1,
                """{"kind":"ink","inkStrokes":[$a,$b]}""" to 2,
                """{"kind":"ink","inkPoints":$a,"inkStrokes":[$a,$b]}""" to 2
            )
            for((settings,count) in cases) {
                val job=JobRequest(UUID.randomUUID().toString(),"pdf_annotate",listOf(ref.id),1,settings,System.currentTimeMillis())
                val result=PdfOperationHandler(EphemeralSecretStore()).execute(job,ExecutionContext(sources,outputs,WorkBudget(),{},{}))
                assertEquals(ItemStatus.CREATED,result.single().status)
                val record=outputs.find(job.id,"document")!!
                app.contentResolver.openInputStream(android.net.Uri.parse(record.uri))!!.use {input->PDDocument.load(input).use {doc ->
                    val annotation=doc.getPage(0).annotations.single()
                    assertEquals("Ink",annotation.subtype)
                    val paths=annotation.cosObject.getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.INKLIST) as com.tom_roush.pdfbox.cos.COSArray
                    assertEquals(count,paths.size())
                    repeat(count) {index->assertEquals(4,(paths.get(index) as com.tom_roush.pdfbox.cos.COSArray).size())}
                    val first=paths.get(0) as com.tom_roush.pdfbox.cos.COSArray
                    assertEquals(61.2,(first.get(0) as com.tom_roush.pdfbox.cos.COSNumber).doubleValue(),.01)
                    if(count==2) {
                        val second=paths.get(1) as com.tom_roush.pdfbox.cos.COSArray
                        assertEquals(428.4,(second.get(0) as com.tom_roush.pdfbox.cos.COSNumber).doubleValue(),.01)
                    }
                }}
            }
            assertArrayEquals(original,source.readBytes())
        } finally {db.close()}
    }

    @Test fun malformedDrawingCannotPublishAnUnchangedPdf()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        try {
            val sources=SourceStore(app,db,WorkBudget())
            val outputs=OutputStore(app,db,PublicOutputStore(app,UserPreferencesRepository(app)))
            val source=File(app.filesDir,"processing/results/fixture/invalid-ink-${UUID.randomUUID()}.pdf").apply {requireNotNull(parentFile).mkdirs()}
            PDDocument().use {doc->doc.addPage(PDPage(PDRectangle.LETTER));doc.save(source)}
            val ref=sources.inspect(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",source).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            for(paths in listOf("[]","[[0.1,0.2,0.3]]","[[0.1,0.2,1.1,0.4]]")) {
                val job=JobRequest(UUID.randomUUID().toString(),"pdf_annotate",listOf(ref.id),1,"""{"kind":"ink","inkStrokes":$paths}""",System.currentTimeMillis())
                assertTrue(runCatching {PdfOperationHandler(EphemeralSecretStore()).execute(job,ExecutionContext(sources,outputs,WorkBudget(),{},{}))}.isFailure)
                assertNull(outputs.find(job.id,"document"))
            }
        } finally {db.close()}
    }

    @Test fun draggedTextCoordinatesReachThePublishedPdf()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        try {
            val sources=SourceStore(app,db,WorkBudget())
            val outputs=OutputStore(app,db,PublicOutputStore(app,UserPreferencesRepository(app)))
            val source=File(app.filesDir,"processing/results/fixture/annotation-${UUID.randomUUID()}.pdf").apply { requireNotNull(parentFile).mkdirs() }
            PDDocument().use { doc -> doc.addPage(PDPage(PDRectangle.LETTER));doc.save(source) }
            val ref=sources.inspect(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",source).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val settings=PdfAnnotateSettings(text="Placed text",left=.65,top=.40,right=.90,bottom=.55,asAnnotation=true)
            val job=JobRequest(UUID.randomUUID().toString(),"pdf_annotate",listOf(ref.id),1,Json.encodeToString(settings),System.currentTimeMillis())
            PdfOperationHandler(EphemeralSecretStore()).execute(job,ExecutionContext(sources,outputs,WorkBudget(),{},{}))
            val record=outputs.find(job.id,"document")!!
            app.contentResolver.openInputStream(android.net.Uri.parse(record.uri))!!.use { input -> PDDocument.load(input).use { doc ->
                val annotation=doc.getPage(0).annotations.single()
                assertEquals("Placed text",annotation.contents)
                assertEquals(612*.65,annotation.rectangle.lowerLeftX.toDouble(),.05)
                assertEquals(792*.60,annotation.rectangle.lowerLeftY.toDouble(),.05)
            } }
            PDDocument.load(source).use { assertTrue(it.getPage(0).annotations.isEmpty()) }
        } finally { db.close() }
    }

    @Test fun opaquePdfJpegExportFitsOneBitmapBudget()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        val budget=WorkBudget(maxBitmapBytes=40_000)
        try {
            val sources=SourceStore(app,db,budget)
            val outputs=OutputStore(app,db,PublicOutputStore(app,UserPreferencesRepository(app)))
            val source=File(app.filesDir,"processing/results/fixture/render-${UUID.randomUUID()}.pdf").apply { requireNotNull(parentFile).mkdirs() }
            PDDocument().use { doc -> doc.addPage(PDPage(PDRectangle(100f,100f)));doc.save(source) }
            val original=source.readBytes()
            val ref=sources.inspect(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",source).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val job=JobRequest(UUID.randomUUID().toString(),"pdf_07",listOf(ref.id),1,
                Json.encodeToString(PdfImagesSettings(dpi=72,format="JPEG")),System.currentTimeMillis())
            val result=PdfOperationHandler(EphemeralSecretStore()).execute(job,ExecutionContext(sources,outputs,budget,{},{}))
            assertEquals(ItemStatus.CREATED,result.single().status)
            val record=outputs.find(job.id,"page:1")!!
            app.contentResolver.openInputStream(android.net.Uri.parse(record.uri))!!.use { input ->
                val bitmap=BitmapFactory.decodeStream(input)!!
                try {
                    assertEquals(100,bitmap.width);assertEquals(100,bitmap.height)
                    assertEquals(Color.WHITE,bitmap.getPixel(50,50))
                } finally { bitmap.recycle() }
            }
            assertArrayEquals(original,source.readBytes())
        } finally { db.close() }
    }

    @Test fun splitArchiveRestartsFromInterruptedStage()=verifyArchiveRestart(images=false)

    @Test fun pageImageArchiveRestartsFromInterruptedStage()=verifyArchiveRestart(images=true)

    private fun verifyArchiveRestart(images:Boolean)=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).build()
        val budget=WorkBudget(maxOutputFiles=1)
        try {
            val sources=SourceStore(app,db,budget)
            val outputs=OutputStore(app,db,PublicOutputStore(app,UserPreferencesRepository(app)))
            val source=File(app.filesDir,"processing/results/fixture/pdf-archive-${UUID.randomUUID()}.pdf").apply { requireNotNull(parentFile).mkdirs() }
            PDDocument().use { doc -> repeat(2) { doc.addPage(PDPage(PDRectangle(100f,100f))) };doc.save(source) }
            val original=source.readBytes()
            val ref=sources.inspect(FileProvider.getUriForFile(app,"${app.packageName}.fileprovider",source).toString(),Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val key=if(images) "images-archive" else "split-archive"
            val job=JobRequest(UUID.randomUUID().toString(),if(images) "pdf_07" else "pdf_05",listOf(ref.id),1,
                if(images) Json.encodeToString(PdfImagesSettings(dpi=72,format="PNG")) else Json.encodeToString(PdfSplitSettings(pages="all")),System.currentTimeMillis())
            outputs.reserve(job.id,key,"Interrupted.zip","application/zip").stageFile.writeText("PK interrupted bytes")
            val result=PdfOperationHandler(EphemeralSecretStore()).execute(job,ExecutionContext(sources,outputs,budget,{},{}))
            assertEquals(ItemStatus.CREATED,result.single().status)
            val record=outputs.find(job.id,key)!!
            app.contentResolver.openInputStream(android.net.Uri.parse(record.uri))!!.use { input -> ZipInputStream(input).use { zip ->
                repeat(2) {
                    assertNotNull(zip.nextEntry)
                    val payload=zip.readBytes()
                    if(images) {
                        val bitmap=BitmapFactory.decodeByteArray(payload,0,payload.size)!!
                        try { assertEquals(100,bitmap.width);assertEquals(100,bitmap.height) } finally { bitmap.recycle() }
                    } else PDDocument.load(payload).use { part -> assertEquals(1,part.numberOfPages) }
                }
                assertNull(zip.nextEntry)
            } }
            assertArrayEquals(original,source.readBytes())
        } finally { db.close() }
    }
}
