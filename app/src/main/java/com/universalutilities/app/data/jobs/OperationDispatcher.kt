package com.universalutilities.app.data.jobs

import com.universalutilities.app.data.source.SourceStore
import com.universalutilities.app.data.output.OutputStore
import com.universalutilities.app.model.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

interface OperationHandler { suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> }
data class ExecutionContext(val sources:SourceStore,val outputs:OutputStore,val budget:WorkBudget,val report:suspend (JobProgress)->Unit,val checkCancellation:()->Unit)

@Singleton class OperationDispatcher @Inject constructor(
    private val images:com.universalutilities.app.data.processing.ImageOperationHandler,
    private val pdf:com.universalutilities.app.data.processing.PdfOperationHandler,
    private val archives:com.universalutilities.app.data.processing.ArchiveOperationHandler,
    private val downloads:com.universalutilities.app.data.processing.DownloadOperationHandler,
    private val media:com.universalutilities.app.data.media.MediaOperationHandler
) : OperationHandler {
    private val bitmapSlot=Semaphore(1)
    private val downloadSlots=Semaphore(2)
    override suspend fun execute(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        if(!CapabilityRegistry.enabled(request.toolId)) throw OperationFailure("CAPABILITY_UNAVAILABLE")
        return if(request.toolId.startsWith("img_") || request.toolId in setOf("pdf_02","pdf_07")) {
            bitmapSlot.withPermit { dispatch(request,context) }
        } else if(request.toolId.startsWith("dl_")) downloadSlots.withPermit { dispatch(request,context) } else dispatch(request,context)
    }
    private suspend fun dispatch(request:JobRequest,context:ExecutionContext):List<ItemOutcome> {
        if(request.schemaVersion!=1) throw OperationFailure("UNSUPPORTED_SETTINGS_VERSION")
        return when(request.toolId) {
            "pdf_02","pdf_07" -> pdf.execute(request,context)
            "img_01","img_02","img_03","img_04" -> images.execute(request,context)
            "file_07","file_08","sha256" -> archives.execute(request,context)
            "dl_02","dl_03" -> downloads.execute(request,context)
            "dl_media" -> media.execute(request,context)
            else -> throw OperationFailure("CAPABILITY_UNAVAILABLE")
        }
    }
}
