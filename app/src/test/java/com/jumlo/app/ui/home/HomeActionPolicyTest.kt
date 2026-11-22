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

    @Test fun togglePinCanonicalizesAndDeduplicatesLegacyIds() {
        // Pinning canonical img_04 when legacy img_01 is saved unpins the consolidated tool.
        assertEquals(emptyList<String>(), HomeActionPolicy.togglePin(listOf("img_01"), "img_04"))
        // Duplicate legacy + canonical entries collapse to a single canonical entry.
        assertEquals(
            listOf("img_04", "pdf_05"),
            HomeActionPolicy.togglePin(listOf("img_01", "img_04"), "pdf_06")
        )
        // Migrates pdf_06 -> pdf_05, pdf_07 -> pdf_long_image, file_encrypted_zip -> file_07, dl_02 -> dl_media.
        assertEquals(
            listOf("pdf_05", "pdf_long_image", "file_07", "dl_media", "scan_doc"),
            HomeActionPolicy.togglePin(
                listOf("pdf_06", "pdf_07", "file_encrypted_zip", "dl_02"),
                "scan_doc"
            )
        )
    }

    @Test fun resolveCanonicalizesLegacySavedPinsAndPreservesOrder() {
        val eligible = listOf("pdf_05", "pdf_long_image", "file_07", "img_04", "dl_media")
        val resolved = HomeActionPolicy.resolve(
            saved = listOf("img_01", "img_04", "pdf_06", "pdf_07", "file_encrypted_zip", "dl_03"),
            eligible = eligible
        )
        assertEquals(listOf("img_04", "pdf_05", "pdf_long_image", "file_07", "dl_media"), resolved)
    }
}
