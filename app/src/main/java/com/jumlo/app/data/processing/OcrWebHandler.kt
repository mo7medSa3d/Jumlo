package com.jumlo.app.data.processing

import com.jumlo.app.data.jobs.ExecutionContext
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.data.jobs.OperationHandler
import com.jumlo.app.model.ItemOutcome
import com.jumlo.app.model.JobRequest
import javax.inject.Inject
import javax.inject.Singleton

/** OCR and webpage capture are already blocked by CapabilityPolicy. The old
 * implementation is retained outside the APK under docs/deferred for future work.
 */
@Singleton class OcrWebHandler @Inject constructor() : OperationHandler {
    override suspend fun execute(request: JobRequest, context: ExecutionContext): List<ItemOutcome> =
        throw OperationFailure("CAPABILITY_UNAVAILABLE")
}
