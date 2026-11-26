package com.jumlo.app.ui.viewer

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jumlo.app.R
import com.jumlo.app.core.PdfMarkupItem
import com.jumlo.app.core.PdfMarkupTool
import com.jumlo.app.core.UndoRedoStack
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.pdf.PdfMarkupFlattener
import com.jumlo.app.data.pdf.PdfPageText
import com.jumlo.app.data.pdf.PdfReaderSession
import com.jumlo.app.data.pdf.ReaderPositionStore
import com.jumlo.app.data.pdf.SavedSignatureStore
import com.jumlo.app.ui.components.formatBytes
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.downloads.CompletedResultCard
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-bleed iOS 27 immersive PDF reader & annotation studio.
 *
 * Pages fill the viewport edge-to-edge; translucent floating top & bottom pill bars fade away
 * automatically while reading and stay accessible in markup mode with 2-finger pinch-zoom & pan.
 */
data class MarkupSaveResult(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val locationLabel: String?,
    val savedInPlace: Boolean
)

private val markupSaveResultSaver = listSaver<MarkupSaveResult?, Any?>(
    save = { r ->
        if (r == null) emptyList()
        else listOf(r.uri, r.displayName, r.sizeBytes, r.locationLabel ?: "", r.savedInPlace)
    },
    restore = { saved ->
        if (saved.size < 5) null else MarkupSaveResult(
            uri = saved[0] as String,
            displayName = saved[1] as String,
            sizeBytes = (saved[2] as Number).toLong(),
            locationLabel = (saved[3] as String).takeIf { it.isNotBlank() },
            savedInPlace = saved[4] as Boolean
        )
    }
)

@Composable
fun PdfViewerScreen(
    uri: String,
    title: String,
    onBack: () -> Unit,
    onOpenFile: () -> Unit,
    onPrint: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onSaveMarkup: ((String, String, List<PdfMarkupItem>, () -> Unit) -> Unit)? = null,
    onSaveInPlace: ((String, String, List<PdfMarkupItem>, () -> Unit) -> Unit)? = null,
    onSaveMarkupResult: ((String, String, List<PdfMarkupItem>, (MarkupSaveResult?) -> Unit) -> Unit)? = null,
    onSaveInPlaceResult: ((String, String, List<PdfMarkupItem>, (MarkupSaveResult?) -> Unit) -> Unit)? = null,
    onOpenSavedOutput: ((String, String) -> Unit)? = null,
    onBackInterceptChange: ((Boolean) -> Unit)? = null
) {
    key(uri) {
        PdfViewerContent(
            uri, title, onBack, onOpenFile, onPrint, modifier,
            onSaveMarkup, onSaveInPlace, onSaveMarkupResult, onSaveInPlaceResult, onOpenSavedOutput, onBackInterceptChange
        )
    }
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
    onSaveMarkup: ((String, String, List<PdfMarkupItem>, () -> Unit) -> Unit)? = null,
    onSaveInPlace: ((String, String, List<PdfMarkupItem>, () -> Unit) -> Unit)? = null,
    onSaveMarkupResult: ((String, String, List<PdfMarkupItem>, (MarkupSaveResult?) -> Unit) -> Unit)? = null,
    onSaveInPlaceResult: ((String, String, List<PdfMarkupItem>, (MarkupSaveResult?) -> Unit) -> Unit)? = null,
    onOpenSavedOutput: ((String, String) -> Unit)? = null,
    onBackInterceptChange: ((Boolean) -> Unit)? = null
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
    var unlockPassword by remember(uri) { mutableStateOf<String?>(null) }
    var passwordInput by remember(uri) { mutableStateOf("") }
    var passwordRevealed by remember(uri) { mutableStateOf(false) }

    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(session) { if (session == null) chromeVisible = true }
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
    var activeToolName by rememberSaveable { mutableStateOf(PdfMarkupTool.PEN.name) }
    val activeTool = PdfMarkupTool.entries.firstOrNull { it.name == activeToolName } ?: PdfMarkupTool.PEN
    var activeColorArgb by rememberSaveable { mutableIntStateOf(0xFF2E67FF.toInt()) }
    val activeColor = Color(activeColorArgb)
    var penWidthNorm by rememberSaveable { mutableFloatStateOf(0.0045f) }
    var highlighterWidthNorm by rememberSaveable { mutableFloatStateOf(0.024f) }
    var eraserRadiusNorm by rememberSaveable { mutableFloatStateOf(0.032f) }
    var textSizePt by rememberSaveable { mutableFloatStateOf(14f) }
    var checkSizeNorm by rememberSaveable { mutableFloatStateOf(0.042f) }
    var signatureScaleNorm by rememberSaveable { mutableFloatStateOf(0.28f) }
    var selectedItemId by remember(uri) { mutableStateOf<String?>(null) }
    var editingTextItemId by remember(uri) { mutableStateOf<String?>(null) }

    var markupItems by remember(uri) { mutableStateOf<List<PdfMarkupItem>>(emptyList()) }
    val history = remember(uri) { UndoRedoStack<PdfMarkupItem>() }
    var markupSaving by remember { mutableStateOf(false) }
    var confirmDiscardMarkup by rememberSaveable(uri) { mutableStateOf(false) }
    var pendingExitAfterSave by remember(uri) { mutableStateOf(false) }
    var completedMarkup by rememberSaveable(uri, stateSaver = markupSaveResultSaver) { mutableStateOf<MarkupSaveResult?>(null) }
    var saveFeedback by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var feedbackJob by remember { mutableStateOf<Job?>(null) }
    var signatureSheetOpen by remember { mutableStateOf(false) }
    var pendingSignaturePath by remember { mutableStateOf<String?>(null) }
    var pendingTextPos by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var textInput by remember { mutableStateOf("") }
    val sigStore = remember(context) { SavedSignatureStore(context) }
    var savedSignatures by remember(uri) { mutableStateOf<List<com.jumlo.app.data.pdf.SavedSignature>>(emptyList()) }
    LaunchedEffect(uri) { savedSignatures = withContext(Dispatchers.IO) { sigStore.list() } }

    val canAnnotate = onSaveMarkup != null || onSaveInPlace != null || onSaveMarkupResult != null || onSaveInPlaceResult != null

    fun showFeedback(message: String, isError: Boolean = false) {
        feedbackJob?.cancel()
        saveFeedback = message to isError
        feedbackJob = scope.launch {
            delay(2600)
            saveFeedback = null
        }
    }

    fun commitMarkup(next: List<PdfMarkupItem>) {
        history.push(markupItems)
        markupItems = next
    }

    fun executeSave(preferInPlace: Boolean) {
        if (markupItems.isEmpty() || markupSaving) return
        val snapshot = markupItems.toList()
        markupSaving = true

        fun finishSaved(label: String, reloadDocument: Boolean, result: MarkupSaveResult? = null) {
            markupSaving = false
            markupItems = emptyList()
            selectedItemId = null
            history.clear()
            markupMode = false
            if (reloadDocument) retry++
            if (pendingExitAfterSave) {
                pendingExitAfterSave = false
                onBack()
                return
            }
            if (result != null && !result.savedInPlace) {
                completedMarkup = result
            } else {
                showFeedback(label, isError = false)
            }
        }

        fun handleStructuredResult(result: MarkupSaveResult?) {
            if (result != null) {
                val switchedFromReadOnly = preferInPlace && !result.savedInPlace && onOpenSavedOutput != null
                if (switchedFromReadOnly) {
                    markupSaving = false
                    markupItems = emptyList()
                    selectedItemId = null
                    history.clear()
                    markupMode = false
                    onOpenSavedOutput?.invoke(result.uri, result.displayName)
                } else {
                    finishSaved(
                        context.getString(if (result.savedInPlace) R.string.s8_markup_saved else R.string.s8_markup_saved_copy),
                        reloadDocument = result.savedInPlace,
                        result = result
                    )
                }
            } else {
                pendingExitAfterSave = false
                markupSaving = false
                showFeedback(context.getString(R.string.s8_markup_save_failed), isError = true)
            }
        }

        if (preferInPlace && onSaveInPlaceResult != null) {
            onSaveInPlaceResult(uri, title, snapshot, ::handleStructuredResult)
            return
        }
        if (!preferInPlace && onSaveMarkupResult != null) {
            onSaveMarkupResult(uri, title, snapshot, ::handleStructuredResult)
            return
        }

        if (preferInPlace && onSaveInPlace != null) {
            onSaveInPlace(uri, title, snapshot) {
                finishSaved(context.getString(R.string.s8_markup_saved), reloadDocument = true)
            }
            return
        }

        if (preferInPlace) {
            scope.launch {
                val overwritten = try {
                    withContext(Dispatchers.IO) {
                        val coroutine = currentCoroutineContext()
                        PdfMarkupFlattener.flattenInPlace(context, Uri.parse(uri), snapshot) { coroutine.ensureActive() }
                    }
                } catch (cancel: CancellationException) {
                    markupSaving = false
                    throw cancel
                } catch (_: Exception) {
                    false
                }

                if (overwritten) {
                    val size = withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { it.length } }.getOrNull()?.takeIf { it > 0L } ?: 0L
                    }
                    finishSaved(
                        context.getString(R.string.s8_markup_saved),
                        reloadDocument = true,
                        result = MarkupSaveResult(uri, title, size, null, savedInPlace = true)
                    )
                } else if (onSaveMarkup != null) {
                    onSaveMarkup(uri, title, snapshot) {
                        finishSaved(context.getString(R.string.s8_markup_saved_copy), reloadDocument = false)
                    }
                } else {
                    markupSaving = false
                    showFeedback(context.getString(R.string.s8_markup_save_failed), isError = true)
                }
            }
        } else if (onSaveMarkup != null) {
            onSaveMarkup(uri, title, snapshot) {
                finishSaved(context.getString(R.string.s8_markup_saved_copy), reloadDocument = false)
            }
        } else {
            markupSaving = false
            showFeedback(context.getString(R.string.s8_markup_save_failed), isError = true)
        }
    }

    val activity = remember(context) { context.findActivity() }

    var hideJob by remember { mutableStateOf<Job?>(null) }
    fun wake() {
        chromeVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(2200)
            if (!thumbsOpen && !jumpOpen && !menuOpen && !searchOpen && !infoOpen && !markupMode) chromeVisible = false
        }
    }
    fun toggleChrome() {
        chromeVisible = !chromeVisible
        if (chromeVisible) wake() else hideJob?.cancel()
    }
    val viewerOwnsBack = searchOpen || markupMode || markupItems.isNotEmpty()
    fun handleBack() {
        menuOpen = false
        when {
            searchOpen -> { searchOpen = false; searchText = ""; searchResults = emptyList() }
            markupItems.isNotEmpty() -> confirmDiscardMarkup = true
            markupMode -> { markupMode = false; selectedItemId = null }
            else -> onBack()
        }
    }
    androidx.activity.compose.BackHandler(enabled = viewerOwnsBack) { handleBack() }
    DisposableEffect(viewerOwnsBack) {
        onBackInterceptChange?.invoke(viewerOwnsBack)
        onDispose { onBackInterceptChange?.invoke(false) }
    }

    LaunchedEffect(uri, retry, unlockPassword) {
        var opened: PdfReaderSession? = null
        try {
            failure = null
            session = null
            opened = PdfReaderSession.open(context, uri, unlockPassword)
            session = opened
            awaitCancellation()
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { failure = stableError(error) }
        catch (_: OutOfMemoryError) { failure = "LOW_MEMORY" }
        finally { if (session === opened) session = null; opened?.close() }
    }

    val currentPage by remember { derivedStateOf { list.firstVisibleItemIndex } }

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
    LaunchedEffect(document, currentPage) {
        val doc = document ?: return@LaunchedEffect
        delay(120)
        if (currentPage + 1 < doc.pageCount) doc.prefetch(currentPage + 1, defaultEdge)
        if (currentPage - 1 >= 0) doc.prefetch(currentPage - 1, defaultEdge)
    }
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

    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars

    Box(modifier.fillMaxSize().background(colors.surfaceContainerLowest).testTag("pdf.viewer")) {
        if (document == null) {
            Box(Modifier.fillMaxSize().clickable { wake() }, contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    val currentFailure = failure
                    if (currentFailure == null) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.reader_loading))
                    } else if (currentFailure == "PDF_ENCRYPTED" || currentFailure == "PDF_WRONG_PASSWORD") {
                        Icon(Icons.Rounded.Lock, null, tint = colors.primary, modifier = Modifier.size(36.dp))
                        Text(
                            stringResource(if (currentFailure == "PDF_WRONG_PASSWORD") R.string.msg_pdf_wrong_password else R.string.reader_password_prompt),
                            color = if (currentFailure == "PDF_WRONG_PASSWORD") colors.error else colors.onSurface,
                            style = QuietTheme.typography.sectionTitle,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it.take(256) },
                            label = { Text(stringResource(R.string.field_current_password)) },
                            singleLine = true,
                            visualTransformation = if (passwordRevealed) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordRevealed = !passwordRevealed }) {
                                    Icon(Icons.Rounded.Visibility, stringResource(if (passwordRevealed) R.string.studio_password_hide else R.string.studio_password_show))
                                }
                            },
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()
                        )
                        Button(
                            onClick = { if (passwordInput.isNotEmpty()) { unlockPassword = passwordInput; retry++ } },
                            enabled = passwordInput.isNotEmpty(),
                            shape = QuietTheme.shapes.pill,
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text(stringResource(R.string.reader_unlock_action)) }
                    } else {
                        Text(
                            stringResource(userMessageResource(currentFailure)),
                            color = colors.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                        Button(onClick = { retry++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.reader_retry))
                        }
                    }
                }
            }
        } else {
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // Full-bleed edge-to-edge PDF canvas (zero horizontal waste, tight 6dp page gap).
            LazyColumn(
                Modifier.fillMaxSize().testTag("pdf.pages").background(colors.surfaceContainer),
                state = list,
                contentPadding = PaddingValues(top = 0.dp, bottom = navBottom + if (markupMode) 124.dp else 72.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                                    penWidthNorm = penWidthNorm,
                                    highlighterWidthNorm = highlighterWidthNorm,
                                    eraserRadiusNorm = eraserRadiusNorm,
                                    textSizePt = textSizePt,
                                    checkSizeNorm = checkSizeNorm,
                                    signatureScaleNorm = signatureScaleNorm,
                                    pendingSignaturePath = pendingSignaturePath,
                                    selectedItemId = selectedItemId,
                                    onSelectItem = { selectedItemId = it },
                                    onAddItem = { item -> commitMarkup(markupItems + item) },
                                    onRequestTextAt = { p, x, y -> editingTextItemId = null; pendingTextPos = Triple(p, x, y); textInput = "" },
                                    onEditTextItem = { tb -> editingTextItemId = tb.id; pendingTextPos = Triple(tb.pageIndex, tb.normX, tb.normY); textInput = tb.text },
                                    onMoveItem = { id, dx, dy ->
                                        commitMarkup(markupItems.map { item ->
                                            if (item.id != id) item else when (item) {
                                                is PdfMarkupItem.TextBox -> item.copy(
                                                    normX = (item.normX + dx).coerceIn(0f, 0.92f),
                                                    normY = (item.normY + dy).coerceIn(0f, 0.94f)
                                                )
                                                is PdfMarkupItem.CheckMark -> item.copy(
                                                    normX = (item.normX + dx).coerceIn(0f, 0.94f),
                                                    normY = (item.normY + dy).coerceIn(0f, 0.94f)
                                                )
                                                is PdfMarkupItem.SavedSignatureStamp -> item.copy(
                                                    normX = (item.normX + dx).coerceIn(0f, (1f - item.normW).coerceAtLeast(0f)),
                                                    normY = (item.normY + dy).coerceIn(0f, (1f - item.normH).coerceAtLeast(0f))
                                                )
                                                is PdfMarkupItem.InkStroke -> {
                                                    val minX = item.points.minOfOrNull { it.x } ?: 0f
                                                    val maxX = item.points.maxOfOrNull { it.x } ?: 1f
                                                    val minY = item.points.minOfOrNull { it.y } ?: 0f
                                                    val maxY = item.points.maxOfOrNull { it.y } ?: 1f
                                                    val clampedDx = dx.coerceIn(-minX, 1f - maxX)
                                                    val clampedDy = dy.coerceIn(-minY, 1f - maxY)
                                                    item.copy(points = item.points.map { com.jumlo.app.core.NormPoint(it.x + clampedDx, it.y + clampedDy) })
                                                }
                                                is PdfMarkupItem.HighlightStroke -> {
                                                    val minX = item.points.minOfOrNull { it.x } ?: 0f
                                                    val maxX = item.points.maxOfOrNull { it.x } ?: 1f
                                                    val minY = item.points.minOfOrNull { it.y } ?: 0f
                                                    val maxY = item.points.maxOfOrNull { it.y } ?: 1f
                                                    val clampedDx = dx.coerceIn(-minX, 1f - maxX)
                                                    val clampedDy = dy.coerceIn(-minY, 1f - maxY)
                                                    item.copy(points = item.points.map { com.jumlo.app.core.NormPoint(it.x + clampedDx, it.y + clampedDy) })
                                                }
                                            }
                                        })
                                    },
                                    onResizeSignature = { id, dNormW, dNormH ->
                                        commitMarkup(markupItems.map { item ->
                                            if (item.id != id || item !is PdfMarkupItem.SavedSignatureStamp) item else {
                                                val nextW = (item.normW + dNormW).coerceIn(0.08f, (1f - item.normX).coerceAtMost(0.85f))
                                                val nextH = (item.normH + dNormH).coerceIn(0.04f, (1f - item.normY).coerceAtMost(0.55f))
                                                item.copy(normW = nextW, normH = nextH)
                                            }
                                        })
                                    },
                                    onDeleteItem = { id ->
                                        if (selectedItemId == id) selectedItemId = null
                                        commitMarkup(markupItems.filterNot { it.id == id })
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = JumloMotion.topChromeEnter(reducedMotion),
            exit = JumloMotion.topChromeExit(reducedMotion)
        ) {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Surface(
                    shape = QuietTheme.shapes.pill,
                    color = colors.surfaceContainerHigh.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, pillars.cardBorder),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = ::handleBack, modifier = Modifier.size(42.dp).testTag("pdf.back")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.reader_back), modifier = Modifier.size(21.dp))
                        }
                        Text(
                            title,
                            Modifier.weight(1f).padding(horizontal = 6.dp).semantics { heading() },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content)
                        )
                        document?.let { doc ->
                            TextButton(
                                onClick = { jumpOpen = true; wake() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 38.dp).testTag("pdf.page.indicator")
                            ) {
                                Text(
                                    stringResource(
                                        R.string.reader_page_indicator,
                                        if (doc.pageCount > 0) (currentPage + 1).coerceIn(1, doc.pageCount) else 0,
                                        doc.pageCount
                                    ),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (canAnnotate) {
                                IconButton(
                                    onClick = {
                                        markupMode = !markupMode
                                        if (markupMode) chromeVisible = true
                                    },
                                    modifier = Modifier.size(42.dp).testTag("pdf.markup.toggle")
                                ) {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        stringResource(R.string.markup_toggle),
                                        tint = if (markupMode) colors.primary else colors.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { menuOpen = !menuOpen; wake() }, modifier = Modifier.size(42.dp).testTag("pdf.menu")) {
                                Icon(Icons.Rounded.MoreVert, stringResource(R.string.reader_menu), modifier = Modifier.size(20.dp))
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
        }

        AnimatedVisibility(
            visible = saveFeedback != null,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 66.dp),
            enter = if (reducedMotion) EnterTransition.None else fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else fadeOut()
        ) {
            saveFeedback?.let { (message, isError) ->
                Surface(
                    shape = QuietTheme.shapes.pill,
                    color = if (isError) colors.errorContainer else colors.primaryContainer,
                    border = BorderStroke(1.dp, pillars.cardBorder),
                    shadowElevation = 6.dp,
                    modifier = Modifier.testTag("pdf.markup.feedback")
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            if (isError) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) colors.onErrorContainer else colors.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            message,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isError) colors.onErrorContainer else colors.onPrimaryContainer
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && !markupMode && document != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = JumloMotion.bottomChromeEnter(reducedMotion),
            exit = JumloMotion.bottomChromeExit(reducedMotion)
        ) {
            Surface(
                shape = QuietTheme.shapes.pill,
                color = colors.surfaceContainerHigh.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, pillars.cardBorder),
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = { thumbsOpen = true; wake() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Image, stringResource(R.string.reader_menu_thumbnails), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { searchOpen = true; wake() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Search, stringResource(R.string.reader_menu_search), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { nightMode = !nightMode; wake() }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.DarkMode,
                            stringResource(R.string.reader_menu_night),
                            tint = if (nightMode) colors.primary else colors.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = { shareDocument(context, uri, title) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.reader_menu_share), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onPrint(uri, title) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Print, stringResource(R.string.reader_menu_print), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (markupMode && document != null && canAnnotate) {
            PdfMarkupFloatingBar(
                activeTool = activeTool,
                activeColor = activeColor,
                penWidthNorm = penWidthNorm,
                highlighterWidthNorm = highlighterWidthNorm,
                eraserRadiusNorm = eraserRadiusNorm,
                textSizePt = textSizePt,
                checkSizeNorm = checkSizeNorm,
                signatureScaleNorm = signatureScaleNorm,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                hasEdits = markupItems.isNotEmpty(),
                saving = markupSaving,
                onSelectTool = { t ->
                    activeToolName = t.name
                    if (t != PdfMarkupTool.SELECT) selectedItemId = null
                    if (t == PdfMarkupTool.HIGHLIGHTER && activeColorArgb == 0xFF2E67FF.toInt()) activeColorArgb = 0xFFFFD60A.toInt()
                    if (t == PdfMarkupTool.SIGNATURE) signatureSheetOpen = true
                },
                onSelectColor = { activeColorArgb = it.toArgb() },
                onPenWidthChange = { penWidthNorm = it },
                onHighlighterWidthChange = { highlighterWidthNorm = it },
                onEraserRadiusChange = { eraserRadiusNorm = it },
                onTextSizeChange = { textSizePt = it },
                onCheckSizeChange = { checkSizeNorm = it },
                onSignatureScaleChange = { scale ->
                    signatureScaleNorm = scale
                    val selId = selectedItemId
                    if (selId != null) {
                        markupItems = markupItems.map { item ->
                            if (item.id != selId || item !is PdfMarkupItem.SavedSignatureStamp) item else {
                                val aspect = if (item.normW > 0.001f) item.normH / item.normW else 0.36f
                                val nextW = scale.coerceIn(0.08f, (1f - item.normX).coerceAtMost(0.85f))
                                val nextH = (nextW * aspect).coerceIn(0.04f, (1f - item.normY).coerceAtMost(0.55f))
                                item.copy(normW = nextW, normH = nextH)
                            }
                        }
                    }
                },
                onUndo = { history.undo(markupItems)?.let { markupItems = it } },
                onRedo = { history.redo(markupItems)?.let { markupItems = it } },
                onSaveInPlace = { executeSave(preferInPlace = true) },
                onSaveAsCopy = { executeSave(preferInPlace = false) },
                onClose = { markupMode = false; selectedItemId = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (document != null) {
        if (thumbsOpen) {
            ThumbnailsSheet(document, currentPage, { page -> scope.launch { list.animateScrollToItem(page) } }, { thumbsOpen = false })
        }
        if (jumpOpen) {
            JumpDialog(
                document.pageCount,
                currentPage + 1,
                { page -> if (document.pageCount > 0) scope.launch { list.animateScrollToItem((page - 1).coerceIn(0, document.pageCount - 1)) }; jumpOpen = false },
                { jumpOpen = false }
            )
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
                    signatureSheetOpen = false
                    scope.launch {
                        val (saved, updated) = withContext(Dispatchers.IO) {
                            val created = runCatching { sigStore.save(bytes) }.getOrNull()
                            created to sigStore.list()
                        }
                        savedSignatures = updated
                        if (saved != null) pendingSignaturePath = saved.file.absolutePath
                    }
                },
                onDeleteSaved = { sig ->
                    scope.launch {
                        savedSignatures = withContext(Dispatchers.IO) {
                            sigStore.delete(sig.id)
                            sigStore.list()
                        }
                    }
                },
                onDismiss = { signatureSheetOpen = false }
            )
        }
        completedMarkup?.let { saved ->
            val location = saved.locationLabel?.takeIf { it.isNotBlank() }?.let {
                stringResource(R.string.ui_saved_subfolder, it)
            } ?: stringResource(if (saved.savedInPlace) R.string.s10_saved_in_place else R.string.s10_saved_location_default)
            val meta = if (saved.sizeBytes > 0L) "${formatBytes(saved.sizeBytes)} · $location" else location
            ModalBottomSheet(
                onDismissRequest = { completedMarkup = null },
                containerColor = colors.surfaceContainerLow
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompletedResultCard(
                        title = stringResource(R.string.s10_saved_title),
                        displayName = saved.displayName,
                        metadataLine = meta,
                        onOpen = {
                            completedMarkup = null
                            if (!saved.savedInPlace) onOpenSavedOutput?.invoke(saved.uri, saved.displayName)
                        },
                        onShare = { shareDocument(context, saved.uri, saved.displayName) },
                        onNewRun = {
                            completedMarkup = null
                            markupMode = true
                            chromeVisible = true
                        },
                        newRunLabel = stringResource(R.string.t34_annotate_again)
                    )
                    TextButton(
                        onClick = { completedMarkup = null },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.t34_markup_done))
                    }
                }
            }
        }
        if (confirmDiscardMarkup) {
            AlertDialog(
                onDismissRequest = { confirmDiscardMarkup = false },
                title = { Text(stringResource(R.string.ui_unsaved_title)) },
                text = { Text(stringResource(R.string.ui_unsaved_body)) },
                confirmButton = {
                    TextButton(
                        enabled = !markupSaving,
                        onClick = {
                            confirmDiscardMarkup = false
                            pendingExitAfterSave = true
                            executeSave(preferInPlace = true)
                        }
                    ) { Text(stringResource(R.string.ui_save_leave)) }
                },
                dismissButton = {
                    Column {
                        TextButton(onClick = { confirmDiscardMarkup = false }) { Text(stringResource(R.string.ui_keep_editing)) }
                        TextButton(
                            enabled = !markupSaving,
                            onClick = {
                                confirmDiscardMarkup = false
                                markupItems = emptyList()
                                history.clear()
                                markupMode = false
                                selectedItemId = null
                                onBack()
                            }
                        ) { Text(stringResource(R.string.ui_discard_leave)) }
                    }
                }
            )
        }
        pendingTextPos?.let { (pIdx, nx, ny) ->
            AlertDialog(
                onDismissRequest = { editingTextItemId = null; pendingTextPos = null },
                title = { Text(stringResource(R.string.markup_tool_text)) },
                text = { OutlinedTextField(value = textInput, onValueChange = { textInput = it.take(300) }, singleLine = false, maxLines = 4) },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = textInput.trim()
                        val editId = editingTextItemId
                        if (trimmed.isNotEmpty()) {
                            if (editId != null) {
                                commitMarkup(markupItems.map { item ->
                                    if (item.id == editId && item is PdfMarkupItem.TextBox) item.copy(text = trimmed) else item
                                })
                            } else {
                                val draft = PdfMarkupItem.TextBox(
                                    java.util.UUID.randomUUID().toString(),
                                    pIdx,
                                    nx,
                                    ny,
                                    trimmed,
                                    activeColor.toArgb(),
                                    textSizePt
                                )
                                val b = com.jumlo.app.core.PdfMarkupPolicy.boundingBox(draft)
                                commitMarkup(
                                    markupItems + draft.copy(
                                        normX = (nx - b.width * 0.5f).coerceIn(0.02f, (1f - b.width).coerceAtLeast(0.02f)),
                                        normY = (ny - b.height * 0.5f).coerceIn(0.02f, (1f - b.height).coerceAtLeast(0.02f))
                                    )
                                )
                            }
                        }
                        editingTextItemId = null
                        pendingTextPos = null
                    }) { Text(stringResource(R.string.action_save)) }
                },
                dismissButton = { TextButton(onClick = { editingTextItemId = null; pendingTextPos = null }) { Text(stringResource(R.string.action_cancel)) } }
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
                OutlinedTextField(
                    draft,
                    { value ->
                        val digits = value.filter { it.isDigit() }
                        draft = digits
                        digits.toIntOrNull()?.let { page = it.coerceIn(1, safeCount) }
                    },
                    label = { Text(stringResource(R.string.reader_jump_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
        Text("$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val NightModeColorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f
)))

@Composable
private fun ReaderPage(
    document: PdfReaderSession,
    page: Int,
    dark: Boolean,
    defaultEdge: Int,
    onTap: () -> Unit,
    markupEnabled: Boolean = false,
    markupOverlay: @Composable () -> Unit = {}
) {
    var scale by rememberSaveable(page) { mutableFloatStateOf(1f) }
    val rasterEdge = if (scale > 1.5f) 2400 else defaultEdge
    var frame by remember(document, page) { mutableStateOf(document.peekCached(page, rasterEdge)) }
    var failure by remember(document, page) { mutableStateOf<String?>(null) }
    var pageRetry by remember(document, page) { mutableIntStateOf(0) }
    var pan by remember(document, page) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val latestOnTap by rememberUpdatedState(onTap)
    LaunchedEffect(document, page, rasterEdge, pageRetry) {
        val cached = if (pageRetry == 0) document.peekCached(page, rasterEdge) else null
        if (cached != null) {
            frame = cached
            failure = null
            return@LaunchedEffect
        }
        try { frame = document.render(page, rasterEdge); failure = null }
        catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) { failure = stableError(error) }
        catch (_: OutOfMemoryError) { failure = "LOW_MEMORY" }
    }
    fun reset() { scale = 1f; pan = Offset.Zero }
    fun applyTransform(zoomChange: Float, panChange: Offset) {
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        val maxX = viewport.width * (nextScale - 1f) * 0.5f
        val maxY = viewport.height * (nextScale - 1f) * 0.5f
        pan = if (nextScale <= 1.01f) {
            Offset.Zero
        } else {
            Offset((pan.x + panChange.x).coerceIn(-maxX, maxX), (pan.y + panChange.y).coerceIn(-maxY, maxY))
        }
    }

    val transform = rememberTransformableState { zoom, offset, _ -> applyTransform(zoom, offset) }
    val pageLabel = stringResource(R.string.reader_page, page + 1)
    val fitLabel = stringResource(R.string.ui_preview_reset_zoom)
    val pageRatio = (frame?.let { if (it.height > 0) it.width.toFloat() / it.height else null } ?: document.cachedRatio(page))
        .takeIf { it.isFinite() && it > 0f } ?: 0.707f

    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(pageRatio)
                .background(if (dark) Color.Black else Color.White)
                .clipToBounds()
                .onSizeChanged { viewport = it }
                .testTag("pdf.page.$page")
                .pointerInput(page, markupEnabled) {
                    if (!markupEnabled) {
                        detectTapGestures(
                            onTap = { latestOnTap() },
                            onDoubleTap = { if (scale > 1.01f) reset() else scale = 2.5f }
                        )
                    }
                }
                // Intercept 2-finger pinch-zoom on Initial pass in markup mode or at 1x scale so 1-finger drags always scroll LazyColumn cleanly.
                .pointerInput(page, markupEnabled) {
                    awaitEachGesture {
                        var zooming = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount == 0) break
                            if (pressedCount >= 2 && (markupEnabled || scale <= 1.01f || zooming)) {
                                zooming = true
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    applyTransform(zoomChange, panChange)
                                }
                                event.changes.forEach { it.consume() }
                            } else if (zooming) {
                                // Keep consuming remaining lift events after a pinch so lifting 1 finger doesn't leave a stray dot or jerk scroll.
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = pageLabel
                    stateDescription = "${(scale * 100).toInt()}%"
                    customActions = listOf(CustomAccessibilityAction(fitLabel) { reset(); true })
                }
                .then(
                    if (!markupEnabled && scale > 1.01f) {
                        Modifier.transformable(transform, enabled = true, canPan = { scale > 1.01f && !markupEnabled })
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Shared transform container so PDF bitmap and normalized [0..1] markup overlay zoom/pan in lockstep.
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                },
                contentAlignment = Alignment.Center
            ) {
                frame?.takeUnless { it.bitmap.isRecycled }?.let { rendered ->
                    val imageBitmap = remember(rendered.bitmap) { rendered.bitmap.asImageBitmap() }
                    Image(
                        imageBitmap,
                        null,
                        Modifier.fillMaxSize().testTag("pdf.page.image.$page"),
                        contentScale = ContentScale.Fit,
                        colorFilter = if (dark) NightModeColorFilter else null
                    )
                }
                markupOverlay()
            }
            if (frame == null && failure == null) CircularProgressIndicator(Modifier.size(32.dp))
            failure?.let {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(userMessageResource(it)), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = { pageRetry++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reader_retry)) }
                }
            }
        }
        if (scale > 1.01f) {
            FilledTonalButton(
                onClick = ::reset,
                shape = QuietTheme.shapes.pill,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).heightIn(min = 34.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("${(scale * 100).toInt()}% · $fitLabel", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ReaderThumbnail(document: PdfReaderSession, page: Int, selected: Boolean, onClick: () -> Unit) {
    var frame by remember(document, page) { mutableStateOf(document.peekCached(page, 180)) }
    LaunchedEffect(document, page) {
        try { frame = document.render(page, 180) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { /* Full page displays the actionable error. */ }
        catch (_: OutOfMemoryError) { /* The full page can be retried after memory is reclaimed. */ }
    }
    val label = stringResource(R.string.reader_page, page + 1)
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = QuietTheme.shapes.thumbnail,
        modifier = Modifier.width(76.dp).fillMaxHeight().testTag("pdf.thumbnail.$page")
            .semantics { this.selected = selected; contentDescription = label }
    ) {
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
