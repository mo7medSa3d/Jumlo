package com.jumlo.app.ui.scan

internal object ScannerFallbackPolicy {
    fun shouldFallback(playServicesAvailable: Boolean, failures: Int): Boolean =
        !playServicesAvailable || failures >= 3
}
