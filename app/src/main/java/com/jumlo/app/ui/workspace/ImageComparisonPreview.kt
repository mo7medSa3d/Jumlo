package com.jumlo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.theme.QuietTheme

/** Both layers are real decoded bitmaps; the divider changes only the clipping boundary. */
@Composable
internal fun ImageComparisonPreview(original:Bitmap,edited:Bitmap) {
    var split by rememberSaveable { mutableFloatStateOf(.5f) }
    val label=stringResource(R.string.studio_preview_comparison)
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    Column(Modifier.testTag("preview.bitmap"),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(320.dp).clip(QuietTheme.shapes.thumbnail)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .testTag("image.comparison").semantics {contentDescription=label}
            .pointerInput(original,edited,rtl) {
                detectHorizontalDragGestures {change,_ ->
                    val physical=(change.position.x/size.width).coerceIn(0f,1f)
                    change.consume();split=if(rtl) 1f-physical else physical
                }
            }) {
            Image(edited.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            Image(original.asImageBitmap(),null,Modifier.fillMaxSize().drawWithContent {
                val boundary=size.width*(if(rtl) 1f-split else split)
                clipRect(left=if(rtl) boundary else 0f,right=if(rtl) size.width else boundary) {this@drawWithContent.drawContent()}
            },contentScale=ContentScale.Fit)
            Canvas(Modifier.fillMaxSize()) {
                val x=size.width*(if(rtl) 1f-split else split)
                drawLine(Color.Black.copy(alpha=.35f),Offset(x,0f),Offset(x,size.height),5.dp.toPx())
                drawLine(Color.White,Offset(x,0f),Offset(x,size.height),2.dp.toPx())
                drawCircle(Color.White,14.dp.toPx(),Offset(x,size.height/2))
                drawLine(Color.Black,Offset(x-4.dp.toPx(),size.height/2),Offset(x+4.dp.toPx(),size.height/2),2.dp.toPx())
            }
            Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.studio_before),Modifier.background(Color.Black.copy(alpha=.7f),QuietTheme.shapes.thumbnail).padding(6.dp),color=Color.White,style=QuietTheme.typography.metadataStrong)
                Text(stringResource(R.string.studio_after),Modifier.background(Color.Black.copy(alpha=.7f),QuietTheme.shapes.thumbnail).padding(6.dp),color=Color.White,style=QuietTheme.typography.metadataStrong)
            }
        }
        Slider(split,{split=it},modifier=Modifier.testTag("image.comparison.slider").semantics {contentDescription=label})
    }
}
