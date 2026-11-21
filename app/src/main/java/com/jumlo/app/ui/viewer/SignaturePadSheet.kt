package com.jumlo.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.pdf.SavedSignature
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.theme.QuietTheme
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignaturePadSheet(
    saved: List<SavedSignature>,
    onPickSaved: (SavedSignature) -> Unit,
    onSaveNew: (ByteArray) -> Unit,
    onDeleteSaved: (SavedSignature) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val inkColors = remember { listOf(Color(0xFF0E142B), Color(0xFF2E67FF), Color(0xFFD32F2F)) }
    var activeColor by remember { mutableStateOf(inkColors.first()) }
    val strokes = remember { mutableStateListOf<Pair<Int, List<Offset>>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = pillars.cardSurface,
        shape = QuietTheme.shapes.sheet
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.markup_signature_title), style = QuietTheme.typography.sectionTitle, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    inkColors.forEach { ink ->
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(ink)
                                .border(if (activeColor == ink) 2.5.dp else 1.dp, if (activeColor == ink) colors.primary else pillars.cardBorder, CircleShape)
                                .clickable { activeColor = ink }
                        )
                    }
                    TextButton(onClick = { strokes.clear(); currentStroke = emptyList() }) {
                        Text(stringResource(R.string.markup_clear))
                    }
                }
            }
            if (saved.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(saved, key = { it.id }) { sig ->
                        val preview = remember(sig.file.absolutePath) { runCatching { BitmapFactory.decodeFile(sig.file.absolutePath) }.getOrNull() }
                        Surface(
                            onClick = { onPickSaved(sig) },
                            shape = QuietTheme.shapes.card,
                            color = Color.White,
                            border = BorderStroke(1.dp, pillars.cardBorder),
                            modifier = Modifier.width(124.dp).height(58.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (preview != null) {
                                    Image(preview.asImageBitmap(), null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                                }
                                IconButton(onClick = { onDeleteSaved(sig) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) {
                                    Icon(Icons.Rounded.Close, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Surface(
                shape = QuietTheme.shapes.card,
                color = Color.White,
                border = BorderStroke(1.dp, pillars.cardBorder),
                modifier = Modifier.fillMaxWidth().height(190.dp)
            ) {
                Canvas(
                    Modifier.fillMaxSize().onSizeChanged { padSize = it }
                        .pointerInput(activeColor) {
                            detectDragGestures(
                                onDragStart = { offset -> currentStroke = listOf(offset) },
                                onDrag = { change, _ -> change.consume(); currentStroke = currentStroke + change.position },
                                onDragEnd = {
                                    if (currentStroke.size >= 2) strokes += activeColor.toArgb() to currentStroke
                                    currentStroke = emptyList()
                                }
                            )
                        }
                ) {
                    fun drawPts(pts: List<Offset>, argb: Int) {
                        if (pts.size < 2) return
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) {
                                val prev = pts[i - 1]
                                val cur = pts[i]
                                quadraticBezierTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                            }
                        }
                        drawPath(path, Color(argb), style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    strokes.forEach { (c, pts) -> drawPts(pts, c) }
                    drawPts(currentStroke, activeColor.toArgb())
                }
            }
            QuietPrimaryButton(
                text = stringResource(R.string.markup_use_signature),
                enabled = strokes.isNotEmpty() && padSize.width > 0 && padSize.height > 0,
                onClick = {
                    val bmp = Bitmap.createBitmap(padSize.width.coerceAtLeast(320), padSize.height.coerceAtLeast(160), Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bmp)
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = 7f
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                    }
                    strokes.forEach { (argb, pts) ->
                        paint.color = argb
                        val p = AndroidPath().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) {
                                val prev = pts[i - 1]
                                val cur = pts[i]
                                quadTo(prev.x, prev.y, (prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                            }
                        }
                        canvas.drawPath(p, paint)
                    }
                    val bytes = ByteArrayOutputStream().use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    }
                    bmp.recycle()
                    onSaveNew(bytes)
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
