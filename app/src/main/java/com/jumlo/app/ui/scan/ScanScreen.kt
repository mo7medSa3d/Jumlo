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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val SIGNATURE_INKS = listOf(
    Color.Black to R.string.studio_ink_black,
    Color(0xFF1746A2) to R.string.studio_ink_blue,
    Color(0xFF8E2430) to R.string.studio_ink_red,
    Color(0xFF145D4C) to R.string.studio_ink_green
)

/** The scanner first asks for the output format, because the Play-services result
 *  carries both a PDF and JPEG pages and the old code silently discarded the JPEGs.
 *  ML Kit owns capture, crop, filters and page ordering. */
@Composable
fun ScanScreen(
    onScannedPdf: (File) -> Unit, onScannedJpegs: (List<File>) -> Unit = {},
    onBack: () -> Unit, externalBusy: Boolean = false
) {
    var format by rememberSaveable { mutableStateOf<String?>(null) }
    var useAutoScanner by rememberSaveable { mutableStateOf(false) }
    if (format == null) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.scan_back))
            }
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                Text(stringResource(R.string.scan_choose_format), style = QuietTheme.typography.sectionTitle, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.scan_smart_hint), style = QuietTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (externalBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                QuietPrimaryButton(stringResource(R.string.scan_save_pdf), onClick = { useAutoScanner = false; format = "pdf" },
                    enabled = !externalBusy, modifier = Modifier.fillMaxWidth().testTag("scan.format.pdf"))
                QuietTonalButton(stringResource(R.string.scan_save_jpg), onClick = { useAutoScanner = false; format = "jpg" },
                    enabled = !externalBusy, modifier = Modifier.fillMaxWidth().testTag("scan.format.jpg"))
            }
        }
    } else if (useAutoScanner) {
        SmartDocumentScanLauncher(
            onScannedPdf = onScannedPdf, onScannedJpegs = onScannedJpegs,
            jpegOutput = format == "jpg", onBack = onBack, externalBusy = externalBusy
        )
    } else {
        CameraXScanFallback(
            onScannedPdf = onScannedPdf, onScannedJpegs = onScannedJpegs,
            jpegOutput = format == "jpg", onBack = onBack, externalBusy = externalBusy
        )
    }
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
    val strokePath = remember { androidx.compose.ui.graphics.Path() }
    val hasInk = strokes.isNotEmpty()
    val padDescription = stringResource(R.string.studio_signature_pad)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.signature_title), style = QuietTheme.typography.sectionTitle, modifier = Modifier.semantics { heading() })
        // Signature-pad checkerboard follows the theme instead of fixed light
        // grays, so it stays readable in dark mode too.
        val padScheme = MaterialTheme.colorScheme
        Box(Modifier.fillMaxWidth().height(200.dp).clip(QuietTheme.shapes.thumbnail).background(padScheme.surfaceContainerHigh)) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = 14.dp.toPx()
                for (row in 0..(size.height / cell).toInt()) for (column in 0..(size.width / cell).toInt()) {
                    if ((row + column) % 2 == 0) drawRect(padScheme.surfaceContainerHighest, Offset(column * cell, row * cell), androidx.compose.ui.geometry.Size(cell, cell))
                }
            }
            Canvas(Modifier.fillMaxSize().testTag("signature.pad").semantics { contentDescription = padDescription }
                .onSizeChanged { canvasSize = it }
                .pointerInput(busy) {
                    if (!busy) detectDragGestures(
                        onDragStart = { point ->
                            activePoints.clear()
                            if (size.width > 0 && size.height > 0 && strokes.size < 300 && strokes.sumOf { it.points.size } < 59_800)
                                activePoints.add(Offset((point.x / size.width).coerceIn(0f, 1f), (point.y / size.height).coerceIn(0f, 1f)))
                        },
                        onDrag = { change, _ ->
                            if (size.width > 0 && size.height > 0 && activePoints.isNotEmpty() && activePoints.size < 2000) {
                                change.consume()
                                val next = Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                val last = activePoints.last()
                                val dx = next.x - last.x
                                val dy = next.y - last.y
                                if (dx * dx + dy * dy >= 0.000016f) {
                                    activePoints.add(next)
                                }
                            }
                        },
                        onDragEnd = {
                            if (size.width > 0 && activePoints.size >= 2) {
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
                    strokePath.reset()
                    strokePath.moveTo(points[0].x * size.width, points[0].y * size.height)
                    for (i in 1 until points.size) {
                        strokePath.lineTo(points[i].x * size.width, points[i].y * size.height)
                    }
                    drawPath(strokePath, Color(color), style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                strokes.forEach { drawStroke(it.points, it.colorArgb, it.widthFraction * size.width) }
                drawStroke(activePoints, inkArgb, latestPen)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { strokes.lastOrNull()?.let { last -> undone = undone + last; strokes = strokes.dropLast(1) } }, enabled = strokes.isNotEmpty() && !busy,
                modifier = Modifier.size(48.dp).testTag("signature.undo")) { Icon(Icons.Rounded.Undo, stringResource(R.string.visual_edit_undo)) }
            IconButton(onClick = { undone.lastOrNull()?.let { last -> strokes = strokes + last; undone = undone.dropLast(1) } }, enabled = undone.isNotEmpty() && !busy,
                modifier = Modifier.size(48.dp).testTag("signature.redo")) { Icon(Icons.Rounded.Redo, stringResource(R.string.visual_edit_redo)) }
            TextButton(onClick = { strokes = emptyList(); undone = emptyList(); activePoints.clear() }, enabled = !busy && hasInk, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.signature_clear))
            }
        }
        val penLabel = stringResource(R.string.studio_pen_thickness, penDp.toInt())
        Text(penLabel, style = QuietTheme.typography.metadataStrong)
        Slider(penDp, { penDp = it }, valueRange = 1f..12f, steps = 10, enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("signature.thickness").semantics { contentDescription = penLabel; stateDescription = penLabel })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SIGNATURE_INKS.forEach { (ink, labelRes) ->
                val label = stringResource(labelRes)
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
        if (saveFailed) Text(stringResource(R.string.interactive_signature_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (library.isNotEmpty()) {
            Text(stringResource(R.string.studio_signature_library), style = QuietTheme.typography.metadataStrong, modifier = Modifier.semantics { heading() })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(library, key = { it.name }, contentType = { "signature" }) { file ->
                    val bitmap by produceState<Bitmap?>(null, file) {
                        value = withContext(Dispatchers.IO) { try { BitmapFactory.decodeFile(file.path) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null } }
                    }
                    DisposeOwnedPreviewBitmap(bitmap)
                    Column(Modifier.width(148.dp)) {
                        Surface(onClick = { if (!busy) onSaved(file) }, shape = QuietTheme.shapes.thumbnail, color = Color.White,
                            modifier = Modifier.fillMaxWidth().height(84.dp)) {
                            bitmap?.takeUnless { it.isRecycled }?.let { decoded ->
                                val imageBitmap = remember(decoded) { decoded.asImageBitmap() }
                                Image(imageBitmap, stringResource(R.string.studio_use_signature), Modifier.padding(8.dp), contentScale = ContentScale.Fit)
                            }
                        }
                        TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
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
