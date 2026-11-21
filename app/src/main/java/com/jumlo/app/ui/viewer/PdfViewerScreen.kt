package com.jumlo.app.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jumlo.app.R
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.pdf.PdfPageText
import com.jumlo.app.data.pdf.PdfReaderSession
import com.jumlo.app.core.PdfMarkupItem
import com.jumlo.app.core.PdfMarkupTool
import com.jumlo.app.core.UndoRedoStack
import com.jumlo.app.data.pdf.ReaderPositionStore
import com.jumlo.app.data.pdf.SavedSignature
import com.jumlo.app.data.pdf.SavedSignatureStore
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.io.File

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
    modifier: Modifier = Modifier,
    onSaveMarkup: ((String, String, List<PdfMarkupItem>, () -> Unit) -> Unit)? = null
) {
    key(uri) { PdfViewerContent(uri, title, onBack, onOpenFile, onPrint, modifier, onSaveMarkup) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewerContent(
    uri: String,
    title: String,
    onBack: () -> Unit,
    onOpenFile: () -> Unit,
    onPrint: (String, String) -> Unit,
    modifier: Modifier,
    onSaveMarkup: ((String, String, List<PdfMarkupItem>, () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val defaultEdge = with(LocalDensity.current) { 960.dp.roundToPx() }.coerceIn(1280, 1800)
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var restoredPage by remember(uri) { mutableStateOf(false) }
    var session by remember { mutableStateOf<PdfReaderSession?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    var chromeVisible by remember { mutableStateOf(false) }
    var pageObserved by remember { mutableStateOf(false) }
    var nightMode by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var thumbsOpen by rememberSaveable { mutableStateOf(false) }
    var jumpOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var infoOpen by rememberSaveable { mutableStateOf(false) }

    var searchText by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Int>>(emptyList()) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var info by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var infoError by remember { mutableStateOf(false) }
    var markupMode by rememberSaveable { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf(PdfMarkupTool.PEN) }
    var activeColor by remember { mutableStateOf(Color(0xFF2E67FF)) }
    var markupItems by remember(uri) { mutableStateOf<List<PdfMarkupItem>>(emptyList()) }
    val history = remember(uri) { UndoRedoStack<PdfMarkupItem>() }
    var markupSaving by remember { mutableStateOf(false) }
    var signatureSheetOpen by remember { mutableStateOf(false) }
    var pendingSignaturePath by remember { mutableStateOf<String?>(null) }
    var pendingTextPos by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var textInput by remember { mutableStateOf("") }
    val sigStore = remember(context) { SavedSignatureStore(context) }
    var savedSignatures by remember(uri) { mutableStateOf(sigStore.list()) }
    fun commitMarkup(next: List<PdfMarkupItem>) {
        history.push(markupItems)
        markupItems = next
    }

    val activity = remember(context) { context.findActivity() }

    var hideJob by remember { mutableStateOf<Job?>(null) }
    fun wake() {
        chromeVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(2000)
            if (!thumbsOpen && !jumpOpen && !menuOpen && !searchOpen && !infoOpen && !markupMode) chromeVisible = false
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
        finally { if (session === opened) session = null; opened?.close() }
    }

    val currentPage by remember { derivedStateOf { list.firstVisibleItemIndex } }
    // Skip the first emission so the document opens full-screen; real scrolling still reveals chrome.
    LaunchedEffect(currentPage) { if (pageObserved) wake() else pageObserved = true }

    LaunchedEffect(infoOpen, uri) {
        if (infoOpen && info.isEmpty() && !infoError) {
            info = pdfInfo(context, uri)
            if (info.isEmpty()) infoError = true
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.let { WindowInsetsControllerCompat(it.window, it.window.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    LaunchedEffect(chromeVisible, activity) {
        val controller = activity?.let { WindowInsetsControllerCompat(it.window, it.window.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (chromeVisible) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
    }

    val document = session
    LaunchedEffect(uri, document) {
        restoredPage = false
        document?.let { doc ->
            val saved = withContext(Dispatchers.IO) { ReaderPositionStore.read(context, uri) }
            if (saved != null && saved in 0 until doc.pageCount) list.scrollToItem(saved)
            restoredPage = true
        }
    }
    // Never write an initial page-0 position before the saved bookmark was restored.
    LaunchedEffect(uri, restoredPage) {
        if (restoredPage) snapshotFlow { list.firstVisibleItemIndex }.collectLatest { page ->
            delay(350)
            withContext(Dispatchers.IO) { ReaderPositionStore.save(context, uri, page) }
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("pdf.viewer")) {
        if (document == null) {
            Box(Modifier.fillMaxSize().clickable { wake() }, contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                    val currentFailure = failure
                    if (currentFailure == null) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.reader_loading))
                    } else {
                        Text(stringResource(userMessageResource(currentFailure)), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Button(onClick = { retry++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_retry)) }
                    }
                }
            }
        } else {
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(Modifier.fillMaxSize().testTag("pdf.pages").background(MaterialTheme.colorScheme.surfaceContainer), state = list,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(28.dp)) {
                items(document.pageCount, key = { it }, contentType = { "pdf-page" }) { page ->
                    val pageMarkup = remember(markupItems, page) { markupItems.filter { it.pageIndex == page } }
                    ReaderPage(
                        document = document,
                        page = page,
                        dark = nightMode,
                        defaultEdge = defaultEdge,
                        onTap = { if (!markupMode) toggleChrome() },
                        markupEnabled = markupMode,
                        markupOverlay = {
                            if (markupMode || pageMarkup.isNotEmpty()) {
                                PdfMarkupPageLayer(
                                    pageIndex = page,
                                    items = pageMarkup,
                                    activeTool = if (markupMode) activeTool else PdfMarkupTool.NONE,
                                    activeColor = activeColor,
                                    pendingSignaturePath = pendingSignaturePath,
                                    onAddItem = { item -> commitMarkup(markupItems + item) },
                                    onRequestTextAt = { p, x, y -> pendingTextPos = Triple(p, x, y); textInput = "" },
                                    onMoveItem = { id, dx, dy ->
                                        markupItems = markupItems.map { item ->
                                            if (item.id != id) item else when (item) {
                                                is PdfMarkupItem.TextBox -> item.copy(normX = (item.normX + dx).coerceIn(0f, 0.92f), normY = (item.normY + dy).coerceIn(0f, 0.94f))
                                                is PdfMarkupItem.CheckMark -> item.copy(normX = (item.normX + dx).coerceIn(0f, 0.94f), normY = (item.normY + dy).coerceIn(0f, 0.94f))
                                                is PdfMarkupItem.SavedSignatureStamp -> item.copy(normX = (item.normX + dx).coerceIn(0f, 0.85f), normY = (item.normY + dy).coerceIn(0f, 0.90f))
                                                else -> item
                                            }
                                        }
                                    },
                                    onDeleteItem = { id -> commitMarkup(markupItems.filterNot { it.id == id }) }
                                )
                            }
                        }
                    )
                }
            }
        }
        // Chrome overlays the document instead of consuming layout space, so pages never get cut off.
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = if (reducedMotion) EnterTransition.None else fadeIn() + slideInVertically { -it },
            exit = if (reducedMotion) ExitTransition.None else fadeOut() + slideOutVertically { -it }
        ) {
            Surface(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onBack(); menuOpen = false }, modifier = Modifier.size(48.dp).testTag("pdf.back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.reader_back))
                    }
                    Text(title, Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content))
                    document?.let { doc ->
                        TextButton(onClick = { jumpOpen = true; wake() }, modifier = Modifier.heightIn(min = 48.dp).testTag("pdf.page.indicator")) {
                            Text(stringResource(R.string.reader_page_indicator, if (doc.pageCount > 0) (currentPage + 1).coerceIn(1, doc.pageCount) else 0, doc.pageCount))
                        }
                        if (onSaveMarkup != null) {
                            IconButton(
                                onClick = { markupMode = !markupMode; if (markupMode) chromeVisible = true },
                                modifier = Modifier.size(48.dp).testTag("pdf.markup.toggle")
                            ) {
                                Icon(Icons.Rounded.Edit, stringResource(R.string.markup_toggle), tint = if (markupMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        IconButton(onClick = { menuOpen = !menuOpen; wake() }, modifier = Modifier.size(48.dp).testTag("pdf.menu")) {
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
        if (markupMode && document != null && onSaveMarkup != null) {
            PdfMarkupFloatingBar(
                activeTool = activeTool,
                activeColor = activeColor,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                hasEdits = markupItems.isNotEmpty(),
                saving = markupSaving,
                onSelectTool = { t ->
                    activeTool = t
                    if (t == PdfMarkupTool.HIGHLIGHTER && activeColor == Color(0xFF2E67FF)) activeColor = Color(0xFFFFD60A)
                    if (t == PdfMarkupTool.SIGNATURE) signatureSheetOpen = true
                },
                onSelectColor = { activeColor = it },
                onUndo = { history.undo(markupItems)?.let { markupItems = it } },
                onRedo = { history.redo(markupItems)?.let { markupItems = it } },
                onSave = {
                    if (markupItems.isNotEmpty() && !markupSaving) {
                        markupSaving = true
                        onSaveMarkup(uri, title, markupItems) {
                            markupSaving = false
                            markupItems = emptyList()
                            history.clear()
                            markupMode = false
                        }
                    }
                },
                onClose = { markupMode = false },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (document != null) {
        if (thumbsOpen) {
            ThumbnailsSheet(document, currentPage, { page -> scope.launch { list.animateScrollToItem(page) } }, { thumbsOpen = false })
        }
        if (jumpOpen) {
            JumpDialog(document.pageCount, currentPage + 1, { page -> if (document.pageCount > 0) scope.launch { list.animateScrollToItem((page - 1).coerceIn(0, document.pageCount - 1)) }; jumpOpen = false }, { jumpOpen = false })
        }
        if (searchOpen) {
            SearchSheet(
                searchText = searchText,
                onSearchText = { searchText = it },
                results = searchResults,
                busy = searchBusy,
                onSearch = { query ->
                    searchJob?.cancel()
                    if (query.isBlank()) {
                        searchBusy = false
                        searchResults = emptyList()
                    } else {
                        searchBusy = true
                        var launched: Job? = null
                        launched = scope.launch {
                            try { searchResults = searchPdf(context, uri, query) }
                            finally { if (searchJob === launched) searchBusy = false }
                        }
                        searchJob = launched
                    }
                },
                onJump = { page -> if (document.pageCount > 0) scope.launch { list.animateScrollToItem((page - 1).coerceIn(0, document.pageCount - 1)) } },
                onDismiss = { searchOpen = false }
            )
        }
        if (infoOpen) {
            InfoSheet(info = info, error = infoError, title = title, onDismiss = { infoOpen = false })
        }
        if (signatureSheetOpen) {
            SignaturePadSheet(
                saved = savedSignatures,
                onPickSaved = { sig -> pendingSignaturePath = sig.file.absolutePath; signatureSheetOpen = false },
                onSaveNew = { bytes ->
                    val saved = runCatching { sigStore.save(bytes) }.getOrNull()
                    if (saved != null) {
                        savedSignatures = sigStore.list()
                        pendingSignaturePath = saved.file.absolutePath
                    }
                    signatureSheetOpen = false
                },
                onDeleteSaved = { sig -> sigStore.delete(sig.id); savedSignatures = sigStore.list() },
                onDismiss = { signatureSheetOpen = false }
            )
        }
        pendingTextPos?.let { (pIdx, nx, ny) ->
            AlertDialog(
                onDismissRequest = { pendingTextPos = null },
                title = { Text(stringResource(R.string.markup_tool_text)) },
                text = { OutlinedTextField(value = textInput, onValueChange = { textInput = it.take(300) }, singleLine = false, maxLines = 4) },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = textInput.trim()
                        if (trimmed.isNotEmpty()) {
                            commitMarkup(markupItems + PdfMarkupItem.TextBox(java.util.UUID.randomUUID().toString(), pIdx, nx, ny, trimmed, activeColor.toArgb()))
                        }
                        pendingTextPos = null
                    }) { Text(stringResource(R.string.action_save)) }
                },
                dismissButton = { TextButton(onClick = { pendingTextPos = null }) { Text(stringResource(R.string.action_cancel)) } }
            )
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
                Text(stringResource(R.string.reader_thumbnails), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.reader_close)) }
            }
            LazyRow(Modifier.fillMaxWidth().heightIn(max = 256.dp), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(document.pageCount, key = { it }, contentType = { "pdf-thumb" }) { page -> ReaderThumbnail(document, page, page == selected, { onJump(page) }) }
            }
        }
    })
}

@Composable
private fun JumpDialog(count: Int, current: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    val safeCount = count.coerceAtLeast(1)
    var page by rememberSaveable { mutableIntStateOf(current.coerceIn(1, safeCount)) }
    var draft by rememberSaveable { mutableStateOf(page.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_jump_title), modifier = Modifier.semantics { heading() }) },
        text = {
            val jumpLabel = stringResource(R.string.reader_jump_title)
            val jumpState = stringResource(R.string.reader_page_indicator, page, safeCount)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (safeCount > 1) Slider(value = page.toFloat(), valueRange = 1f..safeCount.toFloat(), onValueChange = { page = it.toInt(); draft = page.toString() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = jumpLabel; stateDescription = jumpState })
                OutlinedTextField(draft, { value ->
                    val digits = value.filter { it.isDigit() }
                    draft = digits
                    digits.toIntOrNull()?.let { page = it.coerceIn(1, safeCount) }
                },
                    label = { Text(stringResource(R.string.reader_jump_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.reader_page_indicator, page, safeCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onJump(draft.toIntOrNull()?.coerceIn(1, safeCount) ?: page) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_jump_go)) } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_close)) } }
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
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_menu_search), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.reader_close)) }
            }
            OutlinedTextField(searchText, onSearchText, label = { Text(stringResource(R.string.reader_search_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                TextButton(onClick = { onSearch(searchText) }, enabled = !busy && (searchText.isNotBlank() || results.isNotEmpty()), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) stringResource(R.string.reader_loading) else stringResource(R.string.reader_menu_search)) }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(horizontal = 20.dp, vertical = 4.dp)) {
                if (results.isEmpty() && !busy && searchText.isNotBlank()) item("empty", contentType = "empty") {
                    Text(stringResource(R.string.reader_search_no_results), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                items(results, key = { it }, contentType = { "search-result" }) { page ->
                    TextButton(onClick = { onJump(page) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_page, page)) }
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
                Text(stringResource(R.string.reader_menu_info), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.reader_close)) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
                if (error && info.isEmpty()) Text(stringResource(R.string.reader_info_error), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
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

private val NightModeColorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f
)))

@Composable
private fun ReaderPage(document: PdfReaderSession, page: Int, dark: Boolean, defaultEdge: Int, onTap: () -> Unit, markupEnabled: Boolean = false, markupOverlay: @Composable () -> Unit = {}) {
    var frame by remember(document, page) { mutableStateOf<PdfReaderSession.Frame?>(null) }
    var failure by remember(document, page) { mutableStateOf<String?>(null) }
    var pageRetry by remember(document, page) { mutableIntStateOf(0) }
    var scale by rememberSaveable(page) { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val latestOnTap by rememberUpdatedState(onTap)
    val rasterEdge = if (scale > 1.5f) 2400 else defaultEdge
    LaunchedEffect(document, page, rasterEdge, pageRetry) {
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
    val pageRatio = (frame?.let { if (it.height > 0) it.width.toFloat() / it.height else null } ?: document.firstPageRatio).takeIf { it.isFinite() && it > 0f } ?: 0.707f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().aspectRatio(pageRatio)
                .clip(QuietTheme.shapes.thumbnail).background(if (dark) Color.Black else Color.White).clipToBounds()
                .onSizeChanged { viewport = it }.testTag("pdf.page.$page")
                .pointerInput(page, markupEnabled) { detectTapGestures(onTap = { latestOnTap() }, onDoubleTap = { if (!markupEnabled) { if (scale > 1.01f) reset() else scale = 2.5f } }) }
                .semantics {
                    contentDescription = pageLabel
                    stateDescription = "${(scale * 100).toInt()}%"
                    customActions = listOf(CustomAccessibilityAction(fitLabel) { reset(); true })
                }
                .transformable(transform, enabled = !markupEnabled, canPan = { scale > 1f && !markupEnabled }), contentAlignment = Alignment.Center) {
                frame?.takeUnless { it.bitmap.isRecycled }?.let { rendered ->
                    val imageBitmap = remember(rendered.bitmap) { rendered.bitmap.asImageBitmap() }
                    Image(imageBitmap, null, Modifier.fillMaxSize().testTag("pdf.page.image.$page").graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
                    }, contentScale = ContentScale.Fit, colorFilter = if (dark) NightModeColorFilter else null)
                }
                markupOverlay()
                if (frame == null && failure == null) CircularProgressIndicator(Modifier.size(32.dp))
                failure?.let {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(userMessageResource(it)), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        TextButton(onClick = { pageRetry++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_retry)) }
                    }
                }
            }
            if (scale > 1.01f) {
                FilledTonalButton(onClick = ::reset, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("${(scale * 100).toInt()}% · $fitLabel", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text(pageLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
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
                frame?.takeUnless { it.bitmap.isRecycled }?.let { rendered ->
                    val imageBitmap = remember(rendered.bitmap) { rendered.bitmap.asImageBitmap() }
                    Image(imageBitmap, label, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            Text("${page + 1}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return null
}

private fun shareDocument(context: Context, uri: String, title: String) {
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, parsed)
        clipData = ClipData.newRawUri(title, parsed)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private suspend fun copyToCache(context: Context, uri: String, prefix: String): File? {
    var file: File? = null
    return try {
        val target = File.createTempFile("$prefix-", ".pdf", context.cacheDir).also { file = it }
        val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: run { target.delete(); return null }
        input.use { source ->
            target.outputStream().use { destination ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    if (context.cacheDir.usableSpace < 32L * 1024 * 1024) { target.delete(); return null }
                    if (read > 0) destination.write(buffer, 0, read)
                }
            }
        }
        target
    } catch (cancel: CancellationException) { file?.delete(); throw cancel }
    catch (_: Exception) { file?.delete(); null }
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
                val fmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                info?.creationDate?.let { put("Created", fmt.format(it.time)) }
                info?.modificationDate?.let { put("Modified", fmt.format(it.time)) }
                put("Pages", doc.numberOfPages.toString())
            }
        }
    } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { emptyMap() } finally { file.delete() }
}

private suspend fun searchPdf(context: Context, uri: String, query: String): List<Int> = withContext(Dispatchers.IO) {
    val file = copyToCache(context, uri, "pdf-search") ?: return@withContext emptyList()
    try {
        PdfPageText.open(file).use { doc ->
            val needle = query.lowercase()
            val matches = mutableListOf<Int>()
            for (page in 0 until doc.numberOfPages) {
                currentCoroutineContext().ensureActive()
                val text = PdfPageText.extract(doc, page).lowercase()
                if (text.contains(needle)) matches.add(page + 1)
                if (matches.size >= 50) break
            }
            matches
        }
    } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { emptyList() } finally { file.delete() }
}
