package com.jumlo.app.core

import org.junit.Assert.*
import org.junit.Test

class ImageWorkPolicyTest {
    @Test fun neverRoundsAnImpossibleTargetUpTo512Bytes() {
        assertFalse(ImageWorkPolicy.meetsStrictBound(512, 100))
        assertFalse(ImageWorkPolicy.meetsStrictBound(100, 100))
        assertTrue(ImageWorkPolicy.meetsStrictBound(99, 100))
        assertFalse(ImageWorkPolicy.meetsStrictBound(0, 100))
    }
    @Test fun qualityLadderNeverRaisesCallerQuality() {
        assertEquals(listOf(7, 1), ImageWorkPolicy.qualities(7))
        assertTrue(ImageWorkPolicy.qualities(35).all { it <= 35 })
        assertEquals(95, ImageWorkPolicy.qualities(95).first())
    }
    @Test(expected = IllegalArgumentException::class) fun tinyBudgetDoesNotReceiveAnArtificialFloor() {
        ImageWorkPolicy.requireFits(20, 10, 800, 1000)
    }
    @Test fun joinGeometryAndResidentBudgetAreExact() {
        val plan = ImageJoinPolicy.plan(listOf(10 to 10, 10 to 10), "HSTRIP", 0)
        assertEquals(20, plan.width); assertEquals(10, plan.height)
        assertEquals(1600L, ImageWorkPolicy.requireFits(plan.width, plan.height, 800, 1600) + 800)
        val stitched = ImageJoinPolicy.plan(listOf(100 to 50, 100 to 40), "VSTRIP", 3, 10)
        assertEquals(83, stitched.height); assertEquals(53, stitched.placements[1].y)
    }
    @Test(expected = IllegalArgumentException::class) fun dimensionsDoNotOverflowBeforeAllocation() {
        ImageWorkPolicy.bytes(Int.MAX_VALUE, Int.MAX_VALUE)
    }
}
