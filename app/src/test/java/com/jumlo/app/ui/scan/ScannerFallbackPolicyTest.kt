package com.jumlo.app.ui.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerFallbackPolicyTest {
    @Test fun transientFailureRetries() {
        assertFalse(ScannerFallbackPolicy.shouldFallback(true, 0))
        assertFalse(ScannerFallbackPolicy.shouldFallback(true, 1))
        assertFalse(ScannerFallbackPolicy.shouldFallback(true, 2))
    }
    @Test fun repeatedFailureFallsBack() {
        assertTrue(ScannerFallbackPolicy.shouldFallback(true, 3))
        assertTrue(ScannerFallbackPolicy.shouldFallback(true, 9))
    }
    @Test fun missingPlayServicesFallsBackImmediately() {
        assertTrue(ScannerFallbackPolicy.shouldFallback(false, 0))
    }
}
