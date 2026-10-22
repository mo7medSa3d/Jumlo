package com.jumlo.app.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix as AndroidMatrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.harmony.awt.geom.AffineTransform
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

/** These descriptors contain physical source sizes, not preview bitmap dimensions. */
data class PrintPageInfo(val crop:PrintCrop,val rotation:Int) {
    fun size(relative:Int=0)=PrintGeometry.displaySize(crop,rotation+relative)
}
data class PrintDocumentInfo(val pages:List<PrintPageInfo>,val image:Boolean=false)
data class PrintLayout(val paper:PrintSize,val margins:PrintMargins,val grayscale:Boolean,val rasterDpi:Int=300)

/** Owns no original files. Each call uses its own PDFBox documents and bounded tile/bitmap memory. */
@Singleton class PrintPdfComposer @Inject constructor(@ApplicationContext private val context:Context,private val repository:PrintRepository):PrintEngine {
    private fun memory()=MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
    override fun inspect(source:PrintSource,options:PrintOptions,check:()->Unit):PrintDocumentInfo {
        options.validate();check()
        val file=repository.resolve(source)
        return if(source.mimeType=="application/pdf") {
            PDDocument.load(file,memory()).use { doc ->
                require(doc.currentAccessPermission.canPrint()) { "PRINT_PERMISSION" }
                require(doc.numberOfPages in 1..PrintRanges.MAX_PAGES) { "PRINT_PAGE_COUNT" }
                PrintDocumentInfo((0 until doc.numberOfPages).map { i -> check(); info(doc.getPage(i)) })
            }
        } else {
            require(source.mimeType.startsWith("image/")) { "PRINT_UNSUPPORTED" }
            val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
            BitmapFactory.decodeFile(file.path,bounds)
            require(bounds.outMimeType in setOf("image/jpeg","image/png","image/webp","image/heic","image/heif") && bounds.outWidth>0 && bounds.outHeight>0) { "PRINT_UNSUPPORTED" }
            val exif=runCatching { ExifInterface(file) }.getOrNull()
            val swap=exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION,1) in listOf(5,6,7,8)
            val w=if(swap) bounds.outHeight else bounds.outWidth
            val h=if(swap) bounds.outWidth else bounds.outHeight
            val dpi=options.imageDpi.toDouble()
            PrintDocumentInfo(listOf(PrintPageInfo(PrintCrop(0.0,0.0,w*72/dpi,h*72/dpi),0)),true)
        }
    }
    private fun info(page:PDPage):PrintPageInfo {
        val box=page.cropBox
        val crop=PrintCrop(box.lowerLeftX.toDouble(),box.lowerLeftY.toDouble(),box.width.toDouble(),box.height.toDouble(),page.userUnit.toDouble())
        val size=PrintGeometry.displaySize(crop,page.rotation)
        require(size.width<=72000 && size.height<=72000) { "PRINT_PAPER" }
        return PrintPageInfo(crop,page.rotation)
    }
    override fun initialLayout(options:PrintOptions,info:PrintDocumentInfo,pages:List<PrintPage>):PrintLayout {
        val first=pages.first();val paper=options.paperSize(info.pages[first.sourcePage-1].size(first.rotationDegrees))
        // PrintAttributes describes one media size for the job. Mixed source sizes are fitted to
        // the first selected source page's physical media when SOURCE is selected.
        return PrintLayout(paper,options.margins,options.grayscale)
    }
    override fun compose(source:PrintSource,options:PrintOptions,pages:List<PrintPage>,layout:PrintLayout,destination:File,
                check:()->Unit,progress:(Int,Int)->Unit) {
        require(pages.isNotEmpty()) { "PRINT_RANGE" }
        options.validate();check()
        val file=repository.resolve(source)
        val vector=if(layout.grayscale) File.createTempFile("print-vector-",".pdf",context.cacheDir) else destination
        try {
            progress(0,pages.size)
            if(source.mimeType=="application/pdf") {
                PDDocument.load(file,memory()).use { original ->
                    require(original.currentAccessPermission.canPrint()) { "PRINT_PERMISSION" }
                    val ordered=PrintRanges.sourcePages(pages,original.numberOfPages)
                    // Generate missing widget appearances before embedding printable annotations.
                    original.documentCatalog.acroForm?.let { form ->
                        require(!form.xfaIsDynamic()) { "PRINT_APPEARANCE" }
                        if(form.needAppearances) form.refreshAppearances()
                    }
                    ordered.map {it.sourcePage}.distinct().forEach { number -> check(); flattenPrintable(original,original.getPage(number-1)) }
                    PDDocument(memory()).use { output ->
                        val importer=LayerUtility(output)
                        val forms=mutableMapOf<Int,com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject>()
                        ordered.forEachIndexed { index,selected ->
                            check();diskBudget()
                            val page=original.getPage(selected.sourcePage-1);val metadata=info(page)
                            val form=forms.getOrPut(selected.sourcePage) {
                                importer.importPageAsForm(original,page).also {
                                    // PDFBox's LayerUtility applies aspect-distorting rotation for 90/270.
                                    // Replace it; shared geometry handles CropBox, UserUnit and both rotations.
                                    it.setMatrix(AffineTransform())
                                    it.bBox=page.cropBox
                                }
                            }
                            val target=PDPage(PDRectangle(layout.paper.width.toFloat(),layout.paper.height.toFloat()));output.addPage(target)
                            val placement=PrintGeometry.place(metadata.size(selected.rotationDegrees),layout.paper,layout.margins,options.scale,options.scalePercent)
                            val normalize=PrintGeometry.normalize(metadata.crop,metadata.rotation+selected.rotationDegrees)
                            PDPageContentStream(output,target).use { cs ->
                                cs.addRect(placement.clipX.toFloat(),placement.clipY.toFloat(),placement.clipWidth.toFloat(),placement.clipHeight.toFloat());cs.clip()
                                cs.transform(Matrix(placement.scale.toFloat(),0f,0f,placement.scale.toFloat(),placement.x.toFloat(),placement.y.toFloat()))
                                cs.transform(normalize.pdfMatrix());cs.drawForm(form)
                            }
                            if(!layout.grayscale) progress(index+1,ordered.size)
                        }
                        check();output.save(vector)
                    }
                }
            } else {
                val info=inspect(source,options,check)
                val ordered=PrintRanges.sourcePages(pages,1)
                PrintImageDecoder.decode(file).use { decoded ->
                    PDDocument(memory()).use { output ->
                        val image=LosslessFactory.createFromImage(output,decoded.bitmap)
                        val metadata=info.pages.first()
                        ordered.forEachIndexed { index,selected ->
                            check();diskBudget()
                            val target=PDPage(PDRectangle(layout.paper.width.toFloat(),layout.paper.height.toFloat()));output.addPage(target)
                            val place=PrintGeometry.place(metadata.size(selected.rotationDegrees),layout.paper,layout.margins,options.scale,options.scalePercent)
                            PDPageContentStream(output,target).use { cs ->
                                cs.addRect(place.clipX.toFloat(),place.clipY.toFloat(),place.clipWidth.toFloat(),place.clipHeight.toFloat());cs.clip()
                                cs.transform(Matrix(place.scale.toFloat(),0f,0f,place.scale.toFloat(),place.x.toFloat(),place.y.toFloat()))
                                cs.transform(PrintGeometry.normalize(metadata.crop,selected.rotationDegrees).pdfMatrix())
                                cs.drawImage(image,0f,0f,metadata.crop.width.toFloat(),metadata.crop.height.toFloat())
                            }
                            if(!layout.grayscale) progress(index+1,ordered.size)
                        }
                        check();output.save(vector)
                    }
                }
            }
            check()
            if(layout.grayscale) grayscale(vector,destination,layout.rasterDpi,check,progress)
            require(destination.length() in 1..MAX_OUTPUT_BYTES) { "PRINT_STORAGE" }
            check()
        } catch(t:Throwable) { destination.delete();throw t }
        finally { if(vector!=destination) vector.delete() }
    }
    /** Paint appearances into page content before form import; the original is only read from disk.
     * Hidden/non-printable annotations are excluded. Refuse missing appearances rather than silently
     * dropping a signature or field. JavaScript/dynamic XFA is not evaluated by this pipeline. */
    private fun flattenPrintable(doc:PDDocument,page:PDPage) {
        for(annotation in page.annotations) {
            if(!annotation.isPrinted || annotation.isHidden || annotation.isInvisible) continue
            val rect=annotation.rectangle ?: continue
            val appearance=annotation.normalAppearanceStream ?: throw IllegalArgumentException("PRINT_APPEARANCE")
            val box=appearance.bBox
            val matrix=appearance.matrix
            val points=listOf(box.lowerLeftX to box.lowerLeftY,box.upperRightX to box.lowerLeftY,box.lowerLeftX to box.upperRightY,box.upperRightX to box.upperRightY).map { matrix.transformPoint(it.first,it.second) }
            val x=points.minOf {it.x};val y=points.minOf {it.y};val width=points.maxOf {it.x}-x;val height=points.maxOf {it.y}-y
            require(width>0 && height>0) { "PRINT_APPEARANCE" }
            val sx=rect.width/width;val sy=rect.height/height
            PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { cs ->
                cs.transform(Matrix(sx,0f,0f,sy,rect.lowerLeftX-sx*x,rect.lowerLeftY-sy*y));cs.drawForm(appearance)
            }
        }
    }
    /** Fixed 300 DPI grayscale with 1024-square tiles: peak raster size is 4 MiB regardless of paper.
     * It is deliberately rasterized (including text), stated in the print UI. No giant page bitmap. */
    private fun grayscale(input:File,output:File,dpi:Int,check:()->Unit,progress:(Int,Int)->Unit) {
        ParcelFileDescriptor.open(input,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer ->
            PDDocument(memory()).use { pdf ->
                for(i in 0 until renderer.pageCount) {
                    check();renderer.openPage(i).use { page ->
                        val target=PDPage(PDRectangle(page.width.toFloat(),page.height.toFloat()));pdf.addPage(target)
                        val factor=dpi/72f;val width=ceil(page.width*factor).toInt();val height=ceil(page.height*factor).toInt()
                        PDPageContentStream(pdf,target).use { cs ->
                            for(top in 0 until height step 1024) for(left in 0 until width step 1024) {
                                check();diskBudget()
                                val w=min(1024,width-left);val h=min(1024,height-top)
                                val tile=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                                try {
                                    tile.eraseColor(Color.WHITE)
                                    val matrix=AndroidMatrix().apply {setScale(factor,factor);postTranslate(-left.toFloat(),-top.toFloat())}
                                    page.render(tile,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    val row=IntArray(w)
                                    for(y in 0 until h) {
                                        tile.getPixels(row,0,w,0,y,w,1)
                                        for(x in row.indices) { val p=row[x];val gray=(Color.red(p)*.2126+Color.green(p)*.7152+Color.blue(p)*.0722).roundToInt();row[x]=Color.rgb(gray,gray,gray) }
                                        tile.setPixels(row,0,w,0,y,w,1)
                                    }
                                    val image=LosslessFactory.createFromImage(pdf,tile)
                                    cs.drawImage(image,left/factor,page.height-(top+h)/factor,w/factor,h/factor)
                                } finally {tile.recycle()}
                            }
                        }
                    }
                    progress(i+1,renderer.pageCount)
                }
                check();pdf.save(output)
            }
        } }
    }
    override fun preview(file:File,index:Int,maxEdge:Int):Bitmap {
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer ->
            renderer.openPage(index).use { page ->
                val scale=min(maxEdge.toDouble()/page.width,maxEdge.toDouble()/page.height)
                val bitmap=Bitmap.createBitmap((page.width*scale).roundToInt().coerceAtLeast(1),(page.height*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                try {bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return bitmap}
                catch(t:Throwable) {bitmap.recycle();throw t}
            }
        } }
    }
    private fun diskBudget() { require(context.cacheDir.usableSpace>64L*1024*1024) { "PRINT_STORAGE" } }
    private fun PrintMatrix.pdfMatrix()=Matrix(a.toFloat(),b.toFloat(),c.toFloat(),d.toFloat(),e.toFloat(),f.toFloat())
    companion object {const val MAX_OUTPUT_BYTES=512L*1024*1024}
}
