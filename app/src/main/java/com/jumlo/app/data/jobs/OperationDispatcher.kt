package com.jumlo.app.data.jobs

import com.jumlo.app.data.source.SourceStore
import com.jumlo.app.data.output.OutputStore
import com.jumlo.app.model.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

interface OperationHandler { suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> }
data class ExecutionContext(val sources:SourceStore,val outputs:OutputStore,val budget:WorkBudget,val report:suspend (JobProgress)->Unit,val checkCancellation:()->Unit,val downloadRestartGeneration:Long=0)

@Singleton class OperationDispatcher @Inject constructor(
    private val images:com.jumlo.app.data.processing.ImageOperationHandler,
    private val pdf:com.jumlo.app.data.processing.PdfOperationHandler,
    private val office:com.jumlo.app.data.processing.PdfOfficeOperationHandler,
    private val archives:com.jumlo.app.data.processing.ArchiveOperationHandler,
    private val downloads:com.jumlo.app.data.processing.DownloadOperationHandler,
    private val media:com.jumlo.app.data.media.MediaOperationHandler,
    private val fileExtras:com.jumlo.app.data.processing.FileExtrasHandler,
    private val ocrWeb:com.jumlo.app.data.processing.OcrWebHandler
) : OperationHandler {
    private val driveSlots=Semaphore(2)
    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        if(!CapabilityRegistry.canProcess(request.toolId)) throw OperationFailure("CAPABILITY_UNAVAILABLE")
        return if(request.toolId.startsWith("img_") || request.toolId.startsWith("pdf_")) {
            BitmapWorkGate.run { dispatch(request,context) }
        } else if(request.toolId=="file_drive") {
            driveSlots.withPermit { dispatch(request,context) }
        } else dispatch(request,context)
    }
    private suspend fun dispatch(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        if(request.schemaVersion!=1) throw OperationFailure("UNSUPPORTED_SETTINGS_VERSION")
        return when(request.toolId) {
            "pdf_to_docx","pdf_to_pptx" -> office.execute(request,context)
            "pdf_02","pdf_07","pdf_03","pdf_04","pdf_05","pdf_06","pdf_crop","pdf_compress","pdf_stamp","pdf_annotate","pdf_signature","pdf_image","pdf_metadata","pdf_protect","pdf_forms","pdf_clean" -> pdf.execute(request,context)
            "pdf_ocr" -> ocrWeb.execute(request,context)
            "file_encrypted_zip","file_batch_rename","file_duplicates","file_drive" -> fileExtras.execute(request,context)
            "img_01","img_02","img_03","img_04","img_exif","img_watermark","img_join","img_adjust","img_target","img_compare","img_stitch" -> images.execute(request,context)
            "file_07","file_08","sha256" -> archives.execute(request,context)
            "dl_02","dl_03" -> downloads.execute(request,context)
            "dl_media" -> media.execute(request,context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
    }
}
