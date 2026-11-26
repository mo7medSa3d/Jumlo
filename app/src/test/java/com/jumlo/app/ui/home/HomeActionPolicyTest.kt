package com.jumlo.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** Long-press pinning supports unlimited pins in insertion order and canonicalizes legacy tool IDs. */
class HomeActionPolicyTest {
    @Test fun pinAddsToEmpty() {
        assertEquals(listOf("scan_doc"), HomeActionPolicy.togglePin(emptyList(), "scan_doc"))
    }

    @Test fun pinAgainUnpins() {
        assertEquals(listOf("pdf_02", "img_02"), HomeActionPolicy.togglePin(listOf("pdf_02", "pdf_03", "img_02"), "pdf_03"))
    }

    @Test fun pinningFiveOrMoreToolsKeepsAllInOrderWithoutCap() {
        val afterFifth = HomeActionPolicy.togglePin(
            listOf("pdf_02", "pdf_03", "img_02", "dl_media"),
            "pdf_05"
        )
        assertEquals(listOf("pdf_02", "pdf_03", "img_02", "dl_media", "pdf_05"), afterFifth)

        val afterSixth = HomeActionPolicy.togglePin(afterFifth, "pdf_long_image")
        assertEquals(listOf("pdf_02", "pdf_03", "img_02", "dl_media", "pdf_05", "pdf_long_image"), afterSixth)
    }

    @Test fun togglePinDeduplicatesDistinctIds() {
        assertEquals(listOf("img_01", "img_04"), HomeActionPolicy.togglePin(listOf("img_01"), "img_04"))
        assertEquals(
            listOf("img_01", "img_04", "pdf_06"),
            HomeActionPolicy.togglePin(listOf("img_01", "img_04"), "pdf_06")
        )
        assertEquals(
            listOf("pdf_06", "pdf_07", "file_07", "dl_media", "scan_doc"),
            HomeActionPolicy.togglePin(
                listOf("pdf_06", "pdf_07", "file_encrypted_zip", "dl_02"),
                "scan_doc"
            )
        )
    }

    @Test fun resolveFiltersToEligibleAndPreservesOrder() {
        val eligible = listOf("pdf_05", "pdf_07", "pdf_long_image", "file_07", "img_04", "dl_media")
        val resolved = HomeActionPolicy.resolve(
            saved = listOf("img_01", "img_04", "pdf_07", "pdf_long_image", "file_07", "dl_media"),
            eligible = eligible
        )
        assertEquals(listOf("img_04", "pdf_07", "pdf_long_image", "file_07", "dl_media"), resolved)
    }
}
