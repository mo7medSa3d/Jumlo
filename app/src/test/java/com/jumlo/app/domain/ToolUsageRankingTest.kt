package com.jumlo.app.domain

import com.jumlo.app.core.ToolUsagePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolUsageRankingTest {
    @Test fun fullRankingKeepsEveryUsedToolAndUsesDefaultOrderToBreakTies() {
        val catalogue = listOf("pdf_03", "pdf_02", "img_01", "img_02", "img_03", "img_04", "sha256")
        val counts = catalogue.associateWith { 2 } + ("sha256" to 4) + ("not_allowed" to 999)
        assertEquals(listOf("sha256", "pdf_03", "pdf_02", "img_01", "img_02", "img_03", "img_04"),
            ToolUsagePolicy.ranked(counts, catalogue))
    }

    @Test fun unseenToolsAreNotInventedAsUsage() {
        assertEquals(listOf("img_02"), ToolUsagePolicy.ranked(
            mapOf("pdf_03" to 0, "img_02" to 3, "sha256" to -1), listOf("pdf_03", "img_02", "sha256")))
    }

    @Test fun saturatedCounterCannotWrapAndChangeTheRanking() {
        assertEquals(1_000_000, ToolUsagePolicy.count(mapOf("pdf_03" to Int.MAX_VALUE), "pdf_03")["pdf_03"])
    }
}
