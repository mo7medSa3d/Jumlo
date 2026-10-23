package com.jumlo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Only use for a bitmap owned by this composable: AppViewModel.preview creates a fresh result. */
@Composable
internal fun DisposeOwnedPreviewBitmap(bitmap: Bitmap?) {
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeUnless { it.isRecycled }?.recycle() }
    }
}
