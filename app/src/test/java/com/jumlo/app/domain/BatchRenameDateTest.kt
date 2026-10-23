package com.jumlo.app.domain

import com.jumlo.app.data.engine.PureDomainAlgorithms
import org.junit.Assert.assertEquals
import org.junit.Test

class BatchRenameDateTest {
    @Test fun frozenDateIsTheSameInEveryPreviewedName() {
        assertEquals(listOf("2026-10-23_alpha_1.pdf", "2026-10-23_beta_2.pdf"),
            PureDomainAlgorithms.batchRenamePreview(listOf("alpha.pdf", "beta.pdf"), "{date}_{name}_{n}", 1, "2026-10-23"))
    }

    @Test fun dateDoesNotDependOnExecutionDay() {
        assertEquals(listOf("2001-01-01_7.jpg"),
            PureDomainAlgorithms.batchRenamePreview(listOf("image.jpg"), "{date}_{n}", 7, "2001-01-01"))
    }

    @Test(expected = IllegalArgumentException::class) fun invalidFrozenDateCannotBecomeAPath() {
        PureDomainAlgorithms.batchRenamePreview(listOf("image.jpg"), "{date}_{n}", 1, "../../elsewhere")
    }
}
