package com.jumlo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.theme.QuietTheme

/** Hit regions come from the same ImageJoinPolicy plan used to encode the collage. */
@Composable
internal fun CollagePlacementPreview(bitmap:Bitmap,cells:List<Rect>,onMove:(Int,Int)->Unit) {
    var dragged by remember {mutableIntStateOf(-1)}
    var hovered by remember {mutableIntStateOf(-1)}
    val latestCells by rememberUpdatedState(cells)
    val latestMove by rememberUpdatedState(onMove)
    val accent=MaterialTheme.colorScheme.primary
    val label=stringResource(R.string.studio_collage_drag)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(320.dp).testTag("preview.bitmap"),contentAlignment=Alignment.Center) {
            val aspect=bitmap.width.toFloat()/bitmap.height
            val width=minOf(maxWidth,maxHeight*aspect)
            Box(Modifier.size(width,width/aspect)) {
                Image(bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                Canvas(Modifier.fillMaxSize().testTag("collage.canvas").semantics {contentDescription=label}
                    .pointerInput(Unit) {
                        fun cell(point:Offset):Int {
                            val normalized=Offset(point.x/size.width,point.y/size.height)
                            return latestCells.indexOfFirst {it.contains(normalized)}
                        }
                        detectDragGesturesAfterLongPress(onDragStart={point->dragged=cell(point);hovered=dragged},
                            onDrag={change,_->if(dragged>=0) {change.consume();hovered=cell(change.position)}},
                            onDragEnd={if(dragged>=0 && hovered>=0 && dragged!=hovered) latestMove(dragged,hovered);dragged=-1;hovered=-1},
                            onDragCancel={dragged=-1;hovered=-1})
                    }) {
                    listOf(dragged,hovered).filter {it in cells.indices}.distinct().forEach {index->
                        val rect=cells[index]
                        val origin=Offset(rect.left*size.width,rect.top*size.height)
                        val extent=Size(rect.width*size.width,rect.height*size.height)
                        drawRect(accent.copy(alpha=.18f),origin,extent)
                        drawRect(accent,origin,extent,style=Stroke(3.dp.toPx()))
                    }
                }
            }
        }
        Text(label,style=QuietTheme.typography.metadata)
    }
}
