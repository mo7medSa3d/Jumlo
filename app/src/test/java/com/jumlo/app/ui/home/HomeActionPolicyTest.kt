package com.jumlo.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Long-press pinning keeps the four-pin cap and drops the oldest pin to make room. */
class HomeActionPolicyTest {
    @Test fun pinAddsToEmpty() {
        assertEquals(listOf("scan_doc"), HomeActionPolicy.togglePin(emptyList(), "scan_doc"))
    }

    @Test fun pinAgainUnpins() {
        assertEquals(listOf("a", "c"), HomeActionPolicy.togglePin(listOf("a", "b", "c"), "b"))
    }

    @Test fun pinFifthDropsOldest() {
        assertEquals(listOf("b", "c", "d", "e"), HomeActionPolicy.togglePin(listOf("a", "b", "c", "d"), "e"))
    }
}
