package com.jumlo.app.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.pdf.PdfReaderSession
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/** Continuous reading, lazy thumbnails and per-page zoom; theme colors keep the canvas OLED black in dark mode. */
@Composable
fun PdfViewerScreen(uri: String, title: String, modifier: Modifier = Modifier) {
    key(uri) { PdfViewerContent(uri, title, modifier) }
}

@Composable
private fun PdfViewerContent(uri: String, title: String, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var session by remember { mutableStateOf<PdfReaderSession?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var thumbnails by rememberSaveable { mutableStateOf(true) }
    var darkPages by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uri, retry) {
        var opened: PdfReaderSession? = null
        try {
            failure = null
            session = null
            opened = PdfReaderSession.open(context, uri)
            session = opened
            awaitCancellation()
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { failure = stableError(error) }
        catch (_: OutOfMemoryError) { failure = "LOW_MEMORY" }
        finally { opened?.close() }
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("pdf.viewer")) {
        Text(title, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(thumbnails, { thumbnails = !thumbnails }, border = null, label = { Text(stringResource(R.string.reader_thumbnails)) },
                modifier = Modifier.testTag("pdf.thumbnails.toggle"))
            FilterChip(darkPages, { darkPages = !darkPages }, border = null, label = { Text(stringResource(R.string.reader_dark_pages)) },
                modifier = Modifier.testTag("pdf.dark.toggle"))
            session?.let { Text(stringResource(R.string.ui_page_indicator, list.firstVisibleItemIndex + 1, it.pageCount),
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("pdf.page.indicator")) }
        }
        val document = session
        if (document == null) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                if (failure == null) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.reader_loading))
                } else {
                    Text(stringResource(userMessageResource(failure!!)), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { retry++ }) { Text(stringResource(R.string.reader_retry)) }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("pdf.pages"), state = list, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(document.pageCount, key = { it }) { page -> ReaderPage(document, page, darkPages) }
            }
            if (thumbnails) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
                    LazyRow(Modifier.fillMaxWidth().height(132.dp).testTag("pdf.thumbnails"),
                        contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(document.pageCount, key = { it }) { page ->
                            ReaderThumbnail(document, page, page == list.firstVisibleItemIndex) {
                                scope.launch { list.animateScrollToItem(page) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderPage(document: PdfReaderSession, page: Int, dark: Boolean) {
    var frame by remember(document, page) { mutableStateOf<PdfReaderSession.Frame?>(null) }
    var failure by remember(document, page) { mutableStateOf<String?>(null) }
    var scale by rememberSaveable(page) { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val defaultEdge = with(LocalDensity.current) { 960.dp.roundToPx() }.coerceIn(1280, 1800)
    val rasterEdge = if (scale > 1.5f) 2400 else defaultEdge
    LaunchedEffect(document, page, rasterEdge) {
        try { frame = document.render(page, rasterEdge); failure = null }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { failure = stableError(error) }
        catch (_: OutOfMemoryError) { failure = "LOW_MEMORY" }
    }
    fun reset() { scale = 1f; pan = Offset.Zero }
    val transform = rememberTransformableState { zoom, offset, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        val maxX = viewport.width * (scale - 1) / 2
        val maxY = viewport.height * (scale - 1) / 2
        pan = if (scale <= 1.01f) Offset.Zero else Offset((pan.x + offset.x).coerceIn(-maxX, maxX), (pan.y + offset.y).coerceIn(-maxY, maxY))
    }
    val pageLabel = stringResource(R.string.reader_page, page + 1)
    val fitLabel = stringResource(R.string.ui_preview_reset_zoom)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(pageLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = ::reset, enabled = scale > 1.01f, modifier = Modifier.heightIn(min = 48.dp)) { Text("${(scale * 100).toInt()}% · $fitLabel") }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(frame?.let { it.width.toFloat() / it.height } ?: document.firstPageRatio)
            .clip(QuietTheme.shapes.thumbnail).background(if (dark) Color.Black else Color.White).clipToBounds()
            .onSizeChanged { viewport = it }.testTag("pdf.page.$page")
            .semantics {
                contentDescription = pageLabel
                stateDescription = "${(scale * 100).toInt()}%"
                customActions = listOf(CustomAccessibilityAction(fitLabel) { reset(); true })
            }
            .pointerInput(page) { detectTapGestures(onDoubleTap = { if (scale > 1.01f) reset() else scale = 2.5f }) }
            .transformable(transform, canPan = { scale > 1f }), contentAlignment = Alignment.Center) {
            frame?.let { rendered ->
                val filter = remember(dark) { if (!dark) null else ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                    -1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))) }
                Image(rendered.bitmap.asImageBitmap(), null, Modifier.fillMaxSize().testTag("pdf.page.image.$page").graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
                }, contentScale = ContentScale.Fit, colorFilter = filter)
            }
            if (frame == null && failure == null) CircularProgressIndicator(Modifier.size(32.dp))
            failure?.let { Text(stringResource(userMessageResource(it)), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun ReaderThumbnail(document: PdfReaderSession, page: Int, selected: Boolean, onClick: () -> Unit) {
    var frame by remember(document, page) { mutableStateOf<PdfReaderSession.Frame?>(null) }
    LaunchedEffect(document, page) {
        try { frame = document.render(page, 180) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Full page displays the actionable error. */ }
        catch (_: OutOfMemoryError) { /* The full page can be retried after memory is reclaimed. */ }
    }
    val label = stringResource(R.string.reader_page, page + 1)
    Surface(onClick = onClick, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = QuietTheme.shapes.thumbnail, modifier = Modifier.width(76.dp).fillMaxHeight().testTag("pdf.thumbnail.$page")
            .semantics { this.selected = selected; contentDescription = label }) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                frame?.let { Image(it.bitmap.asImageBitmap(), label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            Text("${page + 1}", style = MaterialTheme.typography.labelMedium)
        }
    }
}
