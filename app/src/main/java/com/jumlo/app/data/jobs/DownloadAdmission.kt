package com.jumlo.app.data.jobs

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Process-wide admission gate for both direct and extracted media transfers. */
@Singleton class DownloadAdmission @Inject constructor() {
    private val mutex=Mutex()
    private var running=0
    suspend fun <T> withSlot(maxParallel:Int, checkCancel:()->Unit={}, block:suspend ()->T):T {
        require(maxParallel in 1..6)
        while(true) {
            checkCancel()
            val acquired=mutex.withLock {
                if(running<maxParallel) {running++;true} else false
            }
            if(acquired) break
            delay(150)
        }
        try {return block()}
        finally {mutex.withLock {running--}}
    }
}
