package com.jumlo.app.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** The document scanner opens directly; ML Kit owns capture, crop, filters and page ordering. */
@Composable
fun ScanScreen(onScannedPdf: (File) -> Unit, onBack: () -> Unit, externalBusy: Boolean = false) {
    SmartDocumentScanLauncher(onScannedPdf = onScannedPdf, onBack = onBack, externalBusy = externalBusy)
}

private val signatureSaver = listSaver<List<SignatureStroke>, String>(
    save = { strokes -> strokes.map { stroke ->
        "${stroke.colorArgb};${stroke.widthFraction};" + stroke.points.joinToString(";") { "${it.x},${it.y}" }
    } },
    restore = { saved -> saved.mapNotNull { value -> runCatching {
        val parts = value.split(';')
        SignatureStroke(parts.drop(2).map { point ->
            val pair = point.split(','); Offset(pair[0].toFloat(), pair[1].toFloat())
        }, parts[0].toInt(), parts[1].toFloat())
    }.getOrNull() } }
)

/** Styled ink, undo/redo and a private reusable PNG library; export never blocks the main thread. */
@Composable
fun SignaturePad(onSaved: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var strokes by rememberSaveable(stateSaver = signatureSaver) { mutableStateOf<List<SignatureStroke>>(emptyList()) }
    var undone by rememberSaveable(stateSaver = signatureSaver) { mutableStateOf<List<SignatureStroke>>(emptyList()) }
    val activePoints = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var inkArgb by rememberSaveable { mutableIntStateOf(android.graphics.Color.BLACK) }
    var penDp by rememberSaveable { mutableFloatStateOf(3f) }
    var busy by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var libraryVersion by remember { mutableIntStateOf(0) }
    val library by produceState<List<File>>(emptyList(), libraryVersion) {
        value = withContext(Dispatchers.IO) {
            File(context.filesDir, "signatures").listFiles()?.filter { it.extension == "png" }
                ?.sortedByDescending { it.lastModified() }.orEmpty()
        }
    }
    val latestInk by rememberUpdatedState(inkArgb)
    val latestPen by rememberUpdatedState(with(density) { penDp.dp.toPx() })
    val hasInk = strokes.isNotEmpty()
    val padDescription = stringResource(R.string.studio_signature_pad)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.signature_title), style = QuietTheme.typography.sectionTitle)
        Box(Modifier.fillMaxWidth().height(200.dp).clip(QuietTheme.shapes.thumbnail).background(Color(0xFFF4F4F4))) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = 14.dp.toPx()
                for (row in 0..(size.height / cell).toInt()) for (column in 0..(size.width / cell).toInt()) {
                    if ((row + column) % 2 == 0) drawRect(Color(0xFFE2E2E2), Offset(column * cell, row * cell), androidx.compose.ui.geometry.Size(cell, cell))
                }
            }
            Canvas(Modifier.fillMaxSize().testTag("signature.pad").semantics { contentDescription = padDescription }
                .onSizeChanged { canvasSize = it }
                .pointerInput(busy) {
                    if (!busy) detectDragGestures(
                        onDragStart = { point ->
                            activePoints.clear()
                            if (strokes.size < 300 && strokes.sumOf { it.points.size } < 59_800)
                                activePoints.add(Offset((point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f)))
                        },
                        onDrag = { change, _ ->
                            if (activePoints.isNotEmpty() && activePoints.size < 200) {
                                change.consume()
                                activePoints.add(Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f)))
                            }
                        },
                        onDragEnd = {
                            if (activePoints.size >= 2) {
                                strokes = strokes + SignatureStroke(activePoints.toList(), latestInk, latestPen / size.width)
                                undone = emptyList()
                            }
                            activePoints.clear()
                        },
                        onDragCancel = { activePoints.clear() }
                    )
                }) {
                fun drawStroke(points: List<Offset>, color: Int, width: Float) {
                    if (points.size < 2) return
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points.first().x * size.width, points.first().y * size.height)
                        points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                    }
                    drawPath(path, Color(color), style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                strokes.forEach { drawStroke(it.points, it.colorArgb, it.widthFraction * size.width) }
                drawStroke(activePoints, inkArgb, latestPen)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { undone = undone + strokes.last(); strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty() && !busy,
                modifier = Modifier.testTag("signature.undo")) { Icon(Icons.Rounded.Undo, stringResource(R.string.visual_edit_undo)) }
            IconButton(onClick = { strokes = strokes + undone.last(); undone = undone.dropLast(1) }, enabled = undone.isNotEmpty() && !busy,
                modifier = Modifier.testTag("signature.redo")) { Icon(Icons.Rounded.Redo, stringResource(R.string.visual_edit_redo)) }
            TextButton(onClick = { strokes = emptyList(); undone = emptyList(); activePoints.clear() }, enabled = !busy && hasInk) {
                Text(stringResource(R.string.signature_clear))
            }
        }
        Text(stringResource(R.string.studio_pen_thickness, penDp.toInt()), style = QuietTheme.typography.metadataStrong)
        Slider(penDp, { penDp = it }, valueRange = 1f..12f, steps = 10, enabled = !busy,
            modifier = Modifier.testTag("signature.thickness"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Color.Black, Color(0xFF1746A2), Color(0xFF8E2430), Color(0xFF145D4C)).forEachIndexed { index, ink ->
                val label = stringResource(listOf(R.string.studio_ink_black, R.string.studio_ink_blue, R.string.studio_ink_red, R.string.studio_ink_green)[index])
                Surface(onClick = { inkArgb = ink.toArgb() }, color = ink, shape = CircleShape,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = label; selected = inkArgb == ink.toArgb() }) {
                    Box(contentAlignment = Alignment.Center) { if (inkArgb == ink.toArgb()) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
            }
        }
        QuietPrimaryButton(stringResource(R.string.signature_save), enabled = hasInk && !busy && canvasSize.width > 0 && canvasSize.height > 0,
            modifier = Modifier.fillMaxWidth().testTag("signature.save"), onClick = {
                val savedStrokes = strokes.toList()
                val savedSize = canvasSize
                busy = true; saveFailed = false
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) {
                            val directory = File(context.filesDir, "signatures").apply { check(mkdirs() || isDirectory) }
                            val output = File(directory, "signature-${UUID.randomUUID()}.png")
                            val bitmap = renderSignature(savedStrokes, savedSize.width, savedSize.height)
                            try {
                                output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)); it.fd.sync() }
                                output
                            } catch (error: Throwable) { output.delete(); throw error }
                            finally { bitmap.recycle() }
                        }
                        libraryVersion++
                        onSaved(file)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { saveFailed = true }
                    finally { busy = false }
                }
            })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (saveFailed) Text(stringResource(R.string.interactive_signature_failed), color = MaterialTheme.colorScheme.error)
        if (library.isNotEmpty()) {
            Text(stringResource(R.string.studio_signature_library), style = QuietTheme.typography.metadataStrong)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library, key = { it.name }) { file ->
                    val bitmap by produceState<Bitmap?>(null, file) {
                        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() }
                    }
                    Column(Modifier.width(148.dp)) {
                        Surface(onClick = { if (!busy) onSaved(file) }, shape = QuietTheme.shapes.thumbnail, color = Color.White,
                            modifier = Modifier.fillMaxWidth().height(84.dp)) {
                            bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.studio_use_signature), Modifier.padding(8.dp), contentScale = ContentScale.Fit) }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try { withContext(Dispatchers.IO) { check(file.delete() || !file.exists()) }; libraryVersion++ }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { saveFailed = true }
                            }
                        }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
        Text(stringResource(R.string.signature_hint), style = QuietTheme.typography.metadata)
    }
}
