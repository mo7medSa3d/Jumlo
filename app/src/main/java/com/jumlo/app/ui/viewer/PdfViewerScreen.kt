package com.jumlo.app.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jumlo.app.R
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.pdf.PdfPageText
import com.jumlo.app.data.pdf.PdfReaderSession
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** In-memory last-read page per document URI; survives leaving and reopening the reader. */
private val readerLastPage = mutableMapOf<String, Int>()

/**
 * Immersive, content-first reading surface.
 *
 * A single slim top bar carries the only persistent chrome; tapping the page or scrolling
 * reveals it and it auto-hides after two seconds of stillness so the document fills the screen.
 * Thumbnails, search, file info and the jump-to-page control live behind the overflow menu as
 * on-demand surfaces, keeping the reading canvas uncluttered.
 */
@Composable
fun PdfViewerScreen(
    uri: String,
    title: String,
    onBack: () -> Unit,
    onOpenFile: () -> Unit,
    onPrint: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    key(uri) { PdfViewerContent(uri, title, onBack, onOpenFile, onPrint, modifier) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewerContent(
    uri: String,
    title: String,
    onBack: () -> Unit,
    onOpenFile: () -> Unit,
    onPrint: (String, String) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var session by remember { mutableStateOf<PdfReaderSession?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    var chromeVisible by remember { mutableStateOf(true) }
    var nightMode by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var thumbsOpen by rememberSaveable { mutableStateOf(false) }
    var jumpOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }

    var searchText by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var infoError by remember { mutableStateOf(false) }

    val activity = remember { context.findActivity() }

    var hideJob by remember { mutableStateOf<Job?>(null) }
    fun wake() {
        chromeVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(2000)
            if (!thumbsOpen && !jumpOpen && !menuOpen && !searchOpen && !infoOpen) chromeVisible = false
        }
    }
    fun toggleChrome() {
        chromeVisible = !chromeVisible
        if (chromeVisible) wake() else hideJob?.cancel()
    }

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

    LaunchedEffect(uri) { wake() }
    LaunchedEffect(list.firstVisibleItemIndex) {
        session?.let { readerLastPage[uri] = list.firstVisibleItemIndex }
        wake()
    }

    LaunchedEffect(infoOpen, uri) {
        if (infoOpen && info.isEmpty() && !infoError) {
            info = pdfInfo(context, uri)
            if (info.isEmpty()) infoError = true
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(chromeVisible, activity) {
        val controller = activity?.let { WindowInsetsControllerCompat(it.window, it.window.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
    }

    val document = session
    LaunchedEffect(document) {
        document?.let { doc ->
            val saved = readerLastPage[uri]
            if (saved != null && saved in 0 until doc.pageCount) list.scrollToItem(saved)
        }
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("pdf.viewer")) {
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Surface(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onBack(); menuOpen = false }, modifier = Modifier.testTag("pdf.back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.reader_back))
                    }
                    Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    document?.let { doc ->
                        TextButton(onClick = { jumpOpen = true; wake() }, modifier = Modifier.testTag("pdf.page.indicator")) {
                            Text(stringResource(R.string.reader_page_indicator, list.firstVisibleItemIndex + 1, doc.pageCount))
                        }
                        IconButton(onClick = { menuOpen = !menuOpen; wake() }, modifier = Modifier.testTag("pdf.menu")) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.reader_menu))
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_menu_open)) }, leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) }, onClick = { menuOpen = false; onOpenFile(); wake() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_menu_night)) }, leadingIcon = { Icon(Icons.Rounded.DarkMode, null) }, onClick = { menuOpen = false; nightMode = !nightMode; wake() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_menu_thumbnails)) }, leadingIcon = { Icon(Icons.Rounded.Image, null) }, onClick = { menuOpen = false; thumbsOpen = true; wake() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_menu_search)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = { menuOpen = false; searchOpen = true; wake() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_menu_share)) }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menuOpen = false; shareDocument(context, uri, title) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_menu_print)) }, leadingIcon = { Icon(Icons.Rounded.Print, null) }, onClick = { menuOpen = false; onPrint(uri, title) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.reader_menu_info)) }, leadingIcon = { Icon(Icons.Rounded.Info, null) }, onClick = { menuOpen = false; infoOpen = true; wake() })
                            }
                        }
                    }
                }
            }
        }
        if (document == null) {
            Box(Modifier.weight(1f).fillMaxWidth().clickable { wake() }) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    if (failure == null) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.reader_loading))
                    } else {
                        Text(stringResource(userMessageResource(failure!!)), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { retry++ }) { Text(stringResource(R.string.reader_retry)) }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("pdf.pages"), state = list, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(document.pageCount, key = { it }) { page -> ReaderPage(document, page, nightMode, ::toggleChrome) }
            }
        }
    }

    if (document != null) {
        if (thumbsOpen) {
            ThumbnailsSheet(document, list.firstVisibleItemIndex, { page -> scope.launch { list.animateScrollToItem(page) } }, { thumbsOpen = false })
        }
        if (jumpOpen) {
            JumpDialog(document.pageCount, list.firstVisibleItemIndex + 1, { page -> scope.launch { list.animateScrollToItem(page - 1) }; jumpOpen = false }, { jumpOpen = false })
        }
        if (searchOpen) {
            SearchSheet(
                searchText = searchText,
                onSearchText = { searchText = it },
                results = searchResults,
                busy = searchBusy,
                onSearch = { query ->
                    if (query.isBlank()) {
                        searchResults = emptyList()
                    } else {
                        searchBusy = true
                        scope.launch { searchResults = searchPdf(context, uri, query); searchBusy = false }
                    }
                },
                onJump = { page -> scope.launch { list.animateScrollToItem(page - 1) } },
                onDismiss = { searchOpen = false }
            )
        }
        if (infoOpen) {
            InfoSheet(info = info, error = infoError, title = title, onDismiss = { infoOpen = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailsSheet(document: PdfReaderSession, selected: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, content = {
        Column(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_thumbnails), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.reader_close)) }
            }
            LazyRow(Modifier.fillMaxWidth().heightIn(max = 256.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(document.pageCount, key = { it }) { page -> ReaderThumbnail(document, page, page == selected, { onJump(page) }) }
            }
        }
    })
}

@Composable
private fun JumpDialog(count: Int, current: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(current.coerceIn(1, count)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_jump_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(value = page.toFloat(), valueRange = 1f..count.toFloat(), onValueChange = { page = it.toInt() }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(page.toString(), { value -> page = value.toIntOrNull()?.coerceIn(1, count) ?: page },
                    label = { Text(stringResource(R.string.reader_jump_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.reader_page_indicator, page, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onJump(page) }) { Text(stringResource(R.string.reader_jump_go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_close)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSheet(
    searchText: String,
    onSearchText: (String) -> Unit,
    results: List<Int>,
    busy: Boolean,
    onSearch: (String) -> Unit,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, content = {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_menu_search), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.reader_close)) }
            }
            OutlinedTextField(searchText, onSearchText, label = { Text(stringResource(R.string.reader_search_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                TextButton(onClick = { onSearch(searchText) }, enabled = !busy) { Text(if (busy) stringResource(R.string.reader_loading) else stringResource(R.string.reader_menu_search)) }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(horizontal = 20.dp, vertical = 4.dp)) {
                if (results.isEmpty() && !busy) Text(stringResource(R.string.reader_search_no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                results.forEach { page ->
                    TextButton(onClick = { onJump(page) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reader_page, page)) }
                }
            }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(info: Map<String, String>, error: Boolean, title: String, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, content = {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_menu_info), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.reader_close)) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (error && info.isEmpty()) Text(stringResource(R.string.reader_info_error), color = MaterialTheme.colorScheme.error)
                info.forEach { (label, value) -> InfoRow(label, value) }
            }
        }
    })
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label + ": ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReaderPage(document: PdfReaderSession, page: Int, dark: Boolean, onTap: () -> Unit) {
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
            .pointerInput(page) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { if (scale > 1.01f) reset() else scale = 2.5f }) }
            .semantics {
                contentDescription = pageLabel
                stateDescription = "${(scale * 100).toInt()}%"
                customActions = listOf(CustomAccessibilityAction(fitLabel) { reset(); true })
            }
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

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun shareDocument(context: Context, uri: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyToCache(context: Context, uri: String, prefix: String): File? {
    val file = File.createTempFile("$prefix-", ".pdf", context.cacheDir)
    return try {
        val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: run { file.delete(); return null }
        try { input.use { source -> file.outputStream().use { destination -> source.copyTo(destination) } } } finally { input.close() }
        file
    } catch (_: Exception) { file.delete(); null }
}

private suspend fun pdfInfo(context: Context, uri: String): Map<String, String> = withContext(Dispatchers.IO) {
    val file = copyToCache(context, uri, "pdf-info") ?: return@withContext emptyMap()
    try {
        PdfPageText.open(file).use { doc ->
            val info = doc.documentInformation
            mutableMapOf<String, String>().apply {
                info?.title?.takeIf { it.isNotBlank() }?.let { put("Title", it) }
                info?.author?.takeIf { it.isNotBlank() }?.let { put("Author", it) }
                info?.subject?.takeIf { it.isNotBlank() }?.let { put("Subject", it) }
                info?.keywords?.takeIf { it.isNotBlank() }?.let { put("Keywords", it) }
                info?.creator?.takeIf { it.isNotBlank() }?.let { put("Creator", it) }
                info?.producer?.takeIf { it.isNotBlank() }?.let { put("Producer", it) }
                info?.creationDate?.let { put("Created", it.toString()) }
                info?.modificationDate?.let { put("Modified", it.toString()) }
                put("Pages", doc.numberOfPages.toString())
            }
        }
    } catch (_: Exception) { emptyMap() } finally { file.delete() }
}

private suspend fun searchPdf(context: Context, uri: String, query: String): List<Int> = withContext(Dispatchers.IO) {
    val file = copyToCache(context, uri, "pdf-search") ?: return@withContext emptyList()
    try {
        PdfPageText.open(file).use { doc ->
            val needle = query.lowercase()
            val matches = mutableListOf<Int>()
            for (page in 0 until doc.numberOfPages) {
                val text = PdfPageText.extract(doc, page).lowercase()
                if (text.contains(needle)) matches.add(page + 1)
                if (matches.size >= 50) break
            }
            matches
        }
    } catch (_: Exception) { emptyList() } finally { file.delete() }
}
