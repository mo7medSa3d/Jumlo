package com.jumlo.app.ui.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Requires an Android target. Ensures native scan-preview bitmaps are not retained after removal. */
class ScanPreviewBitmapOwnershipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thumbnailDisposalRecyclesItsOwnedBitmap() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        var visible by mutableStateOf(true)
        compose.setContent {
            if (visible) {
                DisposeOwnedPreviewBitmap(bitmap)
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
            }
        }
        compose.runOnIdle {
            assertFalse(bitmap.isRecycled)
            visible = false
        }
        compose.waitForIdle()
        assertTrue("Removed thumbnails must release their native bitmap", bitmap.isRecycled)
    }
}
