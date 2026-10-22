package com.jumlo.app.core

import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.model.UrlValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Policy checks for download naming, resume decisions and image request geometry. */
class TransferAndNamingPolicyTest {

    @Test fun a_typed_file_name_with_spaces_is_not_lost() {
        val named = DownloadLinkPolicy.cleanName("My Report.pdf", null, "application/pdf")
        assertTrue(named, named.contains("My Report"))
        assertTrue(named, named.endsWith(".pdf"))
    }

    @Test fun a_non_ascii_file_name_is_not_lost() {
        val named = DownloadLinkPolicy.cleanName("تقرير.pdf", null, "application/pdf")
        assertTrue(named, named.contains("تقرير"))
    }

    @Test fun a_download_url_still_uses_its_path_name() {
        val named = DownloadLinkPolicy.cleanName("https://example.org/files/report.pdf", null, "application/pdf")
        assertEquals("report.pdf", named)
    }

    @Test fun a_fully_transferred_partial_is_recognised_as_complete() {
        assertTrue(DownloadRestartPolicy.isTransferComplete(2048L, 2048L))
        assertFalse(DownloadRestartPolicy.isTransferComplete(2047L, 2048L))
        assertFalse(DownloadRestartPolicy.isTransferComplete(2048L, null))
        assertFalse(DownloadRestartPolicy.isTransferComplete(0L, 0L))
    }

    @Test fun percentage_resize_honours_a_request_above_one_hundred_percent() {
        val dimensions = ImageResizePolicy.targetDimensions(1000, 800, 150, null, 1600, 1600)
        assertEquals(1500 to 1200, dimensions)
        assertTrue(ImageResizePolicy.allowsUpscaling(150, null, allowUpscaling = false))
    }

    @Test fun longest_edge_resize_keeps_the_aspect_ratio() {
        val dimensions = ImageResizePolicy.targetDimensions(1000, 800, null, 400, 1600, 1600)
        assertEquals(400 to 320, dimensions)
        assertFalse(ImageResizePolicy.allowsUpscaling(null, 400, allowUpscaling = false))
    }

    @Test fun a_pixel_target_still_respects_the_no_upscale_guard() {
        assertFalse(ImageResizePolicy.allowsUpscaling(null, null, allowUpscaling = false))
        assertTrue(ImageResizePolicy.allowsUpscaling(null, null, allowUpscaling = true))
    }

    @Test fun a_margin_larger_than_the_page_is_clamped_instead_of_failing() {
        val page = PdfPageSize(595f, 842f)
        val placement = ImagePdfLayout.resolve(1000, 1000, page, 200, automatic = false, fill = false)
        assertTrue("margin must fit the page", placement.marginPt * 2 < minOf(page.widthPt, page.heightPt))
        assertTrue(placement.widthPt > 0f && placement.heightPt > 0f)
    }

    @Test fun a_page_range_past_the_last_page_is_clamped() {
        assertEquals(listOf(1, 2, 3), PageSelection.parse("1-10", 3))
        assertEquals(listOf(2), PageSelection.parse("2", 3))
    }

    @Test fun contiguous_ranges_stay_one_group_for_splitting() {
        assertEquals(listOf(listOf(1, 2, 3), listOf(5)), PageSelection.groups("1-3,5", 6))
        assertEquals(listOf(listOf(1), listOf(2)), PageSelection.groups("1,2", 3))
    }

    @Test fun a_range_without_any_existing_page_still_fails() {
        var failed = false
        try {
            PageSelection.parse("7-9", 3)
        } catch (error: IllegalArgumentException) {
            failed = error.message == "INVALID_PAGE_RANGE"
        }
        assertTrue("a page that does not exist must be reported", failed)
    }

    @Test fun batch_url_parsing_reports_duplicates_and_unsupported_schemes() {
        val parsed = PureDomainAlgorithms.parseBatchUrls(
            "https://example.org/a.pdf\nhttps://example.org/a.pdf\nftp://example.org/b.pdf"
        )
        assertEquals(3, parsed.size)
        assertEquals(UrlValidationStatus.VALID, parsed[0].status)
        assertEquals(UrlValidationStatus.DUPLICATE, parsed[1].status)
        assertEquals(UrlValidationStatus.UNSUPPORTED_PROTOCOL, parsed[2].status)
    }

    @Test fun an_image_budget_allows_a_camera_photo_but_refuses_an_impossible_one() {
        val budget = 128L * 1024 * 1024
        // 12 MP decode fits, a 100 MP decode does not.
        ImageWorkPolicy.requireFits(4000, 3000, 0L, budget)
        var failed = false
        try {
            ImageWorkPolicy.requireFits(10000, 10000, 0L, budget)
        } catch (error: IllegalArgumentException) {
            failed = error.message == "IMAGE_MEMORY_BUDGET_EXCEEDED"
        }
        assertTrue("an impossible allocation must be refused", failed)
    }

    @Test fun the_published_output_budget_is_enforced() {
        ResourceBudgetPolicy.requireOutputCount(512)
        var failed = false
        try {
            ResourceBudgetPolicy.requireOutputCount(513)
        } catch (error: IllegalArgumentException) {
            failed = error.message == "TOO_MANY_OUTPUTS"
        }
        assertTrue("more outputs than the published limit must be refused", failed)
    }

    @Test fun a_workspace_temp_file_is_not_published_as_an_output() {
        val temp = File.createTempFile("jumlo-test", ".tmp")
        temp.delete()
        assertFalse(temp.exists())
    }
}
