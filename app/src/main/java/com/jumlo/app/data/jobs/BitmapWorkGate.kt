package com.jumlo.app.data.jobs

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Image/PDF work and downloaded-file transforms share one process-wide work budget. */
object BitmapWorkGate {
    private val slot = Semaphore(1)
    suspend fun <T> run(block: suspend () -> T): T = slot.withPermit { block() }
}
