package com.jumlo.app.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jumlo.app.R
import com.jumlo.app.core.office.OfficeBlock
import com.jumlo.app.core.office.OfficeCell
import com.jumlo.app.core.office.OfficeParagraph
import com.jumlo.app.data.office.DocumentViewerFormats
import com.jumlo.app.data.office.DocumentViewerKind
import com.jumlo.app.data.office.DocumentViewerLoader
import com.jumlo.app.data.office.LoadedDocument
import com.jumlo.app.data.office.LoadedSheet
import com.jumlo.app.data.office.LoadedSlide
import com.jumlo.app.data.office.OutlineHeading
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.automirrored.rounded.ChevronLeft
import com.jumlo.app.ui.icons.automirrored.rounded.ChevronRight
import com.jumlo.app.ui.icons.automirrored.rounded.ViewList
import com.jumlo.app.ui.icons.rounded.BrokenImage
import com.jumlo.app.ui.icons.rounded.Check
import com.jumlo.app.ui.icons.rounded.Close
import com.jumlo.app.ui.icons.rounded.FolderOpen
import com.jumlo.app.ui.icons.rounded.FormatSize
import com.jumlo.app.ui.icons.rounded.Info
import com.jumlo.app.ui.icons.rounded.MoreVert
import com.jumlo.app.ui.icons.rounded.PictureAsPdf
import com.jumlo.app.ui.icons.rounded.PlayArrow
import com.jumlo.app.ui.icons.rounded.Search
import com.jumlo.app.ui.icons.rounded.Share
import com.jumlo.app.ui.icons.rounded.Warning
import com.jumlo.app.ui.theme.JumloMotion
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val WHITESPACE_REGEX = Regex("\\s+")

private fun officeBlockContentType(block: OfficeBlock): String = when (block) {
    is OfficeBlock.ParagraphBlock -> "paragraph"
    is OfficeBlock.TableBlock -> "table"
    is OfficeBlock.ImageBlock -> "image"
    OfficeBlock.PageBreak -> "page-break"
    is OfficeBlock.Section -> "section"
}

private enum class ReadingTheme { AUTO, PAPER, SEPIA, DARK }

private data class ReaderPalette(
    val backdrop: Color,
    val pageSurface: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val headerTint: Color,
    val zebraTint: Color,
    val highlight: Color,
    val highlightText: Color
)

@Composable
private fun rememberReaderPalette(theme: ReadingTheme): ReaderPalette {
    val scheme: ColorScheme = MaterialTheme.colorScheme
    val cardSurface = QuietTheme.pillars.cardSurface
    val cardBorder = QuietTheme.pillars.cardBorder
    val systemDark = isSystemInDarkTheme()
    return remember(theme, scheme, cardSurface, cardBorder, systemDark) {
        when (theme) {
            ReadingTheme.AUTO -> ReaderPalette(
                backdrop = scheme.background,
                pageSurface = cardSurface,
                text = scheme.onSurface,
                muted = scheme.onSurfaceVariant,
                border = cardBorder,
                headerTint = scheme.primaryContainer.copy(alpha = 0.55f),
                zebraTint = scheme.surfaceContainer.copy(alpha = 0.45f),
                highlight = Color(0xFFFFE082),
                highlightText = Color(0xFF1A1400)
            )
            ReadingTheme.PAPER -> ReaderPalette(
                backdrop = Color(0xFFF2F4F8),
                pageSurface = Color(0xFFFFFFFF),
                text = Color(0xFF14171F),
                muted = Color(0xFF5A6270),
                border = Color(0xFFDFE3EC),
                headerTint = Color(0xFFEAF0FB),
                zebraTint = Color(0xFFF7F9FC),
                highlight = Color(0xFFFFE082),
                highlightText = Color(0xFF1A1400)
            )
            ReadingTheme.SEPIA -> ReaderPalette(
                backdrop = Color(0xFFECE2CE),
                pageSurface = Color(0xFFF8F1E3),
                text = Color(0xFF3A2E22),
                muted = Color(0xFF776652),
                border = Color(0xFFDCCEB5),
                headerTint = Color(0xFFEADEC8),
                zebraTint = Color(0xFFF2E8D5),
                highlight = Color(0xFFFFD54F),
                highlightText = Color(0xFF231800)
            )
            ReadingTheme.DARK -> ReaderPalette(
                backdrop = Color(0xFF0A0B0E),
                pageSurface = Color(0xFF14161B),
                text = Color(0xFFECEEF2),
                muted = Color(0xFF9BA3B2),
                border = Color(0xFF272B35),
                headerTint = Color(0xFF1F2636),
                zebraTint = Color(0xFF1A1D24),
                highlight = Color(0xFFFFCA28),
                highlightText = Color(0xFF171200)
            )
        }
    }
}

private val SlidePaperPalette = ReaderPalette(
    backdrop = Color(0xFFE7EBF2),
    pageSurface = Color(0xFFFFFFFF),
    text = Color(0xFF14171F),
    muted = Color(0xFF5A6270),
    border = Color(0xFFDCE1EC),
    headerTint = Color(0xFFEAF0FB),
    zebraTint = Color(0xFFF7F9FC),
    highlight = Color(0xFFFFE082),
    highlightText = Color(0xFF1A1400)
)

@Composable
fun OfficeDocumentViewerScreen(
    uri: String,
    title: String,
    mimeType: String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onConvertToPdf: (() -> Unit)? = null,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
    onBackInterceptChange: ((Boolean) -> Unit)? = null
) {
    key(uri) {
        OfficeDocumentViewerContent(
            uri = uri,
            title = title,
            mimeType = mimeType,
            onBack = onBack,
            onShare = onShare,
            onConvertToPdf = onConvertToPdf,
            onOpenExternal = onOpenExternal,
            modifier = modifier,
            onBackInterceptChange = onBackInterceptChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficeDocumentViewerContent(
    uri: String,
    title: String,
    mimeType: String?,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onConvertToPdf: (() -> Unit)?,
    onOpenExternal: () -> Unit,
    modifier: Modifier,
    onBackInterceptChange: ((Boolean) -> Unit)?
) {
    val context = LocalContext.current
    var loaded by remember(uri) { mutableStateOf<LoadedDocument?>(null) }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    var reloadNonce by remember(uri) { mutableIntStateOf(0) }

    var searchOpen by rememberSaveable(uri) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(uri) { mutableStateOf("") }
    var activeMatchIndex by rememberSaveable(uri) { mutableIntStateOf(0) }

    var presenting by rememberSaveable(uri) { mutableStateOf(false) }
    var outlineOpen by rememberSaveable(uri) { mutableStateOf(false) }
    var appearanceOpen by rememberSaveable(uri) { mutableStateOf(false) }
    var fontScale by rememberSaveable(uri) { mutableFloatStateOf(1.0f) }
    var themeName by rememberSaveable(uri) { mutableStateOf(ReadingTheme.AUTO.name) }
    val readingTheme = ReadingTheme.entries.firstOrNull { it.name == themeName } ?: ReadingTheme.AUTO
    val readerPalette = rememberReaderPalette(readingTheme)

    LaunchedEffect(uri, title, mimeType, reloadNonce) {
        loadFailed = false
        loaded = null
        try {
            loaded = DocumentViewerLoader.load(context, uri, title, mimeType)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            loadFailed = true
        }
    }

    LaunchedEffect(searchQuery) {
        activeMatchIndex = 0
    }

    val detectedKind = remember(mimeType, title) { DocumentViewerFormats.detect(mimeType, title) }
    val badgeLabel = when (val doc = loaded) {
        is LoadedDocument.Word -> stringResource(R.string.viewer_office_badge_word)
        is LoadedDocument.Presentation -> stringResource(R.string.viewer_office_badge_presentation)
        is LoadedDocument.Spreadsheet -> stringResource(R.string.viewer_office_badge_spreadsheet)
        is LoadedDocument.PlainText -> stringResource(R.string.viewer_office_badge_text)
        null -> when (detectedKind) {
            DocumentViewerKind.WORD -> stringResource(R.string.viewer_office_badge_word)
            DocumentViewerKind.PRESENTATION -> stringResource(R.string.viewer_office_badge_presentation)
            DocumentViewerKind.SPREADSHEET -> stringResource(R.string.viewer_office_badge_spreadsheet)
            DocumentViewerKind.TEXT -> stringResource(R.string.viewer_office_badge_text)
            else -> stringResource(R.string.viewer_office_badge_text)
        }
    }

    val unsupportedList = when (val doc = loaded) {
        is LoadedDocument.Presentation -> doc.unsupported
        is LoadedDocument.Word -> doc.unsupported
        is LoadedDocument.Spreadsheet -> doc.unsupported
        is LoadedDocument.PlainText, null -> emptyList()
    }

    val doc = loaded
    val officeOwnsBack = (doc is LoadedDocument.Presentation && presenting) || searchOpen
    DisposableEffect(officeOwnsBack) {
        onBackInterceptChange?.invoke(officeOwnsBack)
        onDispose { onBackInterceptChange?.invoke(false) }
    }
    BackHandler(enabled = searchOpen && !(doc is LoadedDocument.Presentation && presenting)) {
        searchOpen = false
        searchQuery = ""
    }
    if (doc is LoadedDocument.Presentation && presenting) {
        BackHandler(enabled = true) { presenting = false }
        FullscreenPresentationStage(
            doc = doc,
            searchQuery = if (searchOpen) searchQuery else "",
            onExit = { presenting = false },
            modifier = modifier
        )
        return
    }

    val showAppearanceAction = doc is LoadedDocument.Word || doc is LoadedDocument.PlainText
    val showOutlineAction = doc is LoadedDocument.Word && doc.headings.isNotEmpty()
    val showPresentAction = doc is LoadedDocument.Presentation && doc.slides.isNotEmpty()
    val effectiveBackdrop = when (doc) {
        is LoadedDocument.Word, is LoadedDocument.PlainText -> readerPalette.backdrop
        is LoadedDocument.Presentation -> if (isSystemInDarkTheme()) Color(0xFF0E1015) else SlidePaperPalette.backdrop
        else -> MaterialTheme.colorScheme.background
    }

    var searchMatchCount by remember(uri, searchQuery, doc) { mutableIntStateOf(0) }
    var outlineRequestBlockIndex by remember(uri) { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(effectiveBackdrop)
    ) {
        ViewerTopBar(
            title = title,
            badge = badgeLabel,
            showSearch = doc != null,
            searchActive = searchOpen,
            showPresent = showPresentAction,
            showOutline = showOutlineAction,
            showAppearance = showAppearanceAction,
            onBack = onBack,
            onToggleSearch = {
                searchOpen = !searchOpen
                if (!searchOpen) searchQuery = ""
            },
            onPresent = { presenting = true },
            onOutline = { outlineOpen = true },
            onAppearance = { appearanceOpen = true },
            onShare = onShare,
            onConvertToPdf = onConvertToPdf,
            onOpenExternal = onOpenExternal
        )

        val reducedMotion = LocalReducedMotion.current
        AnimatedVisibility(visible = searchOpen && doc != null, enter = JumloMotion.topChromeEnter(reducedMotion), exit = JumloMotion.topChromeExit(reducedMotion)) {
            ViewerSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                matchIndex = activeMatchIndex,
                matchCount = searchMatchCount,
                onPrev = {
                    if (searchMatchCount > 0) {
                        activeMatchIndex = (activeMatchIndex - 1 + searchMatchCount) % searchMatchCount
                    }
                },
                onNext = {
                    if (searchMatchCount > 0) {
                        activeMatchIndex = (activeMatchIndex + 1) % searchMatchCount
                    }
                },
                onClose = {
                    searchOpen = false
                    searchQuery = ""
                }
            )
        }

        if (unsupportedList.isNotEmpty()) {
            PartialSupportNotice()
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                loadFailed -> ViewerErrorState(
                    onOpenExternal = onOpenExternal,
                    onRetry = { reloadNonce++ }
                )
                doc == null -> ViewerLoadingState()
                doc is LoadedDocument.Presentation -> PresentationViewerBody(
                    doc = doc,
                    searchQuery = if (searchOpen) searchQuery else "",
                    activeMatchIndex = activeMatchIndex,
                    onMatchCountChange = { searchMatchCount = it }
                )
                doc is LoadedDocument.Word -> WordViewerBody(
                    doc = doc,
                    palette = readerPalette,
                    fontScale = fontScale,
                    searchQuery = if (searchOpen) searchQuery else "",
                    activeMatchIndex = activeMatchIndex,
                    jumpToBlockIndex = outlineRequestBlockIndex,
                    onJumpConsumed = { outlineRequestBlockIndex = null },
                    onMatchCountChange = { searchMatchCount = it }
                )
                doc is LoadedDocument.Spreadsheet -> SpreadsheetViewerBody(
                    doc = doc,
                    palette = rememberReaderPalette(ReadingTheme.AUTO),
                    searchQuery = if (searchOpen) searchQuery else "",
                    activeMatchIndex = activeMatchIndex,
                    onMatchCountChange = { searchMatchCount = it }
                )
                doc is LoadedDocument.PlainText -> PlainTextViewerBody(
                    doc = doc,
                    palette = readerPalette,
                    fontScale = fontScale,
                    searchQuery = if (searchOpen) searchQuery else "",
                    activeMatchIndex = activeMatchIndex,
                    onMatchCountChange = { searchMatchCount = it }
                )
            }
        }
    }

    if (outlineOpen && doc is LoadedDocument.Word && doc.headings.isNotEmpty()) {
        OutlineBottomSheet(
            headings = doc.headings,
            onSelect = { heading ->
                outlineRequestBlockIndex = heading.blockIndex
                outlineOpen = false
            },
            onDismiss = { outlineOpen = false }
        )
    }

    if (appearanceOpen && (doc is LoadedDocument.Word || doc is LoadedDocument.PlainText)) {
        val wordCount = remember(doc) {
            when (doc) {
                is LoadedDocument.Word -> doc.wordCount
                is LoadedDocument.PlainText -> doc.lines.sumOf { line ->
                    line.trim().split(WHITESPACE_REGEX).count { it.isNotEmpty() }
                }
                else -> 0
            }
        }
        AppearanceBottomSheet(
            fontScale = fontScale,
            onFontScaleChange = { fontScale = it },
            selectedTheme = readingTheme,
            onThemeSelect = { themeName = it.name },
            wordCount = wordCount,
            onDismiss = { appearanceOpen = false }
        )
    }
}

@Composable
private fun ViewerTopBar(
    title: String,
    badge: String,
    showSearch: Boolean,
    searchActive: Boolean,
    showPresent: Boolean,
    showOutline: Boolean,
    showAppearance: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onPresent: () -> Unit,
    onOutline: () -> Unit,
    onAppearance: () -> Unit,
    onShare: () -> Unit,
    onConvertToPdf: (() -> Unit)?,
    onOpenExternal: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val barBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)

    Surface(
        color = barBg,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = title,
                        style = QuietTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDirection = TextDirection.Content
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = QuietTheme.shapes.pill,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = badge,
                            style = QuietTheme.typography.badge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                if (showPresent) {
                    IconButton(onClick = onPresent) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.viewer_office_present),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (showOutline) {
                    IconButton(onClick = onOutline) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ViewList,
                            contentDescription = stringResource(R.string.viewer_office_outline)
                        )
                    }
                }
                if (showAppearance) {
                    IconButton(onClick = onAppearance) {
                        Icon(
                            imageVector = Icons.Rounded.FormatSize,
                            contentDescription = stringResource(R.string.viewer_office_appearance)
                        )
                    }
                }
                if (showSearch) {
                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.reader_menu_search),
                            tint = if (searchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.reader_menu)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reader_menu_share)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        if (onConvertToPdf != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.viewer_office_convert_pdf)) },
                                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onConvertToPdf()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.viewer_office_open_external)) },
                            leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenExternal()
                            }
                        )
                    }
                }
            }
            HorizontalDivider(color = QuietTheme.pillars.cardBorder, thickness = 0.75.dp)
        }
    }
}

@Composable
private fun ViewerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchIndex: Int,
    matchCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 1.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.viewer_office_search_hint),
                            style = QuietTheme.typography.metadata
                        )
                    },
                    singleLine = true,
                    shape = QuietTheme.shapes.inputField,
                    textStyle = QuietTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotBlank()) {
                    Text(
                        text = if (matchCount > 0) {
                            stringResource(R.string.viewer_office_search_matches, matchIndex + 1, matchCount)
                        } else {
                            stringResource(R.string.viewer_office_search_none)
                        },
                        style = QuietTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
                IconButton(onClick = onPrev, enabled = matchCount > 0) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ChevronLeft,
                        contentDescription = stringResource(R.string.action_previous)
                    )
                }
                IconButton(onClick = onNext, enabled = matchCount > 0) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ChevronRight,
                        contentDescription = stringResource(R.string.action_next)
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.reader_close)
                    )
                }
            }
            HorizontalDivider(color = QuietTheme.pillars.cardBorder, thickness = 0.75.dp)
        }
    }
}

@Composable
private fun PartialSupportNotice() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.viewer_office_partial_notice),
                style = QuietTheme.typography.metadata,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun ViewerLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Text(
                text = stringResource(R.string.viewer_office_loading),
                style = QuietTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ViewerErrorState(
    onOpenExternal: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(R.string.viewer_office_error_open),
                style = QuietTheme.typography.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onOpenExternal,
                shape = QuietTheme.shapes.button
            ) {
                Text(stringResource(R.string.viewer_office_open_external))
            }
            OutlinedButton(
                onClick = onRetry,
                shape = QuietTheme.shapes.button
            ) {
                Text(stringResource(R.string.reader_retry))
            }
        }
    }
}

@Composable
private fun ViewerEmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = QuietTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun PresentationViewerBody(
    doc: LoadedDocument.Presentation,
    searchQuery: String,
    activeMatchIndex: Int,
    onMatchCountChange: (Int) -> Unit
) {
    if (doc.slides.isEmpty()) {
        LaunchedEffect(Unit) { onMatchCountChange(0) }
        ViewerEmptyState(stringResource(R.string.viewer_office_empty_slides))
        return
    }

    val pagerState = rememberPagerState(pageCount = { doc.slides.size })
    val filmstripState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val slideAspect = doc.aspectRatio.coerceIn(0.6f, 2.4f)

    val matchingSlides = remember(doc.slides, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else doc.slides.indices.filter { idx -> slideMatchesQuery(doc.slides[idx], searchQuery) }
    }

    LaunchedEffect(matchingSlides) {
        onMatchCountChange(matchingSlides.size)
    }

    LaunchedEffect(matchingSlides, activeMatchIndex) {
        if (matchingSlides.isNotEmpty()) {
            val target = matchingSlides[activeMatchIndex.coerceIn(0, matchingSlides.lastIndex)]
            if (pagerState.currentPage != target) {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        filmstripState.animateScrollToItem(pagerState.currentPage.coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    KeynoteSlideCard(
                        slide = doc.slides[page],
                        aspectRatio = slideAspect,
                        searchQuery = searchQuery,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                    scope.launch { pagerState.animateScrollToPage(prev) }
                },
                enabled = pagerState.currentPage > 0
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.viewer_office_prev_slide)
                )
            }

            Surface(
                shape = QuietTheme.shapes.pill,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                border = BorderStroke(0.75.dp, QuietTheme.pillars.cardBorder)
            ) {
                Text(
                    text = stringResource(
                        R.string.viewer_office_slide_counter,
                        pagerState.currentPage + 1,
                        doc.slides.size
                    ),
                    style = QuietTheme.typography.metadataStrong,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            IconButton(
                onClick = {
                    val next = (pagerState.currentPage + 1).coerceAtMost(doc.slides.lastIndex)
                    scope.launch { pagerState.animateScrollToPage(next) }
                },
                enabled = pagerState.currentPage < doc.slides.lastIndex
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.viewer_office_next_slide)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                state = filmstripState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(doc.slides, key = { index, _ -> "slide-$index" }, contentType = { _, _ -> "slide-thumb" }) { index, slide ->
                    val selected = index == pagerState.currentPage
                    val hasSearchHit = index in matchingSlides
                    val snippet = remember(slide) { slideTitleSnippet(slide) }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SlidePaperPalette.pageSurface,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = when {
                                selected -> MaterialTheme.colorScheme.primary
                                hasSearchHit -> SlidePaperPalette.highlight
                                else -> QuietTheme.pillars.cardBorder
                            }
                        ),
                        shadowElevation = if (selected) 4.dp else 1.dp,
                        modifier = Modifier
                            .width(114.dp)
                            .height(68.dp)
                            .pressFeedback().clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(7.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = QuietTheme.typography.badge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else SlidePaperPalette.muted
                                )
                                if (hasSearchHit) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                            Text(
                                text = snippet,
                                style = QuietTheme.typography.badge.copy(
                                    fontWeight = FontWeight.Medium,
                                    textDirection = TextDirection.Content
                                ),
                                color = SlidePaperPalette.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenPresentationStage(
    doc: LoadedDocument.Presentation,
    searchQuery: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { doc.slides.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()
    val slideAspect = doc.aspectRatio.coerceIn(0.6f, 2.4f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (doc.slides.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    KeynoteSlideCard(
                        slide = doc.slides[page],
                        aspectRatio = slideAspect,
                        searchQuery = searchQuery,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Edge tap zones: CenterStart retreats and CenterEnd advances, mirroring automatically in RTL.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight(0.75f)
                    .fillMaxWidth(0.20f)
                    .clickable(enabled = pagerState.currentPage > 0) {
                        val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                        scope.launch { pagerState.animateScrollToPage(prev) }
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.75f)
                    .fillMaxWidth(0.20f)
                    .clickable(enabled = pagerState.currentPage < doc.slides.lastIndex) {
                        val next = (pagerState.currentPage + 1).coerceAtMost(doc.slides.lastIndex)
                        scope.launch { pagerState.animateScrollToPage(next) }
                    }
            )
        }

        Surface(
            shape = QuietTheme.shapes.pill,
            color = Color(0xCC1C1D22),
            border = BorderStroke(0.75.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.viewer_office_slide_counter,
                        (pagerState.currentPage + 1).coerceAtMost(doc.slides.size.coerceAtLeast(1)),
                        doc.slides.size
                    ),
                    style = QuietTheme.typography.metadataStrong,
                    color = Color.White
                )
                Surface(
                    shape = QuietTheme.shapes.pill,
                    color = Color(0x33FFFFFF),
                    modifier = Modifier.clickable(onClick = onExit)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.viewer_office_exit_present),
                            style = QuietTheme.typography.badge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeynoteSlideCard(
    slide: LoadedSlide,
    aspectRatio: Float,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SlidePaperPalette.pageSurface,
        shadowElevation = 8.dp,
        border = BorderStroke(0.75.dp, SlidePaperPalette.border),
        modifier = modifier.aspectRatio(aspectRatio)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slide.blocks.forEach { block ->
                OfficeBlockView(
                    block = block,
                    palette = SlidePaperPalette,
                    fontScale = 0.92f,
                    searchQuery = searchQuery,
                    pageBreakNumber = null
                )
            }
        }
    }
}


@Composable
private fun WordViewerBody(
    doc: LoadedDocument.Word,
    palette: ReaderPalette,
    fontScale: Float,
    searchQuery: String,
    activeMatchIndex: Int,
    jumpToBlockIndex: Int?,
    onJumpConsumed: () -> Unit,
    onMatchCountChange: (Int) -> Unit
) {
    if (doc.blocks.isEmpty()) {
        LaunchedEffect(Unit) { onMatchCountChange(0) }
        ViewerEmptyState(stringResource(R.string.viewer_office_empty_doc))
        return
    }

    val listState = rememberLazyListState()

    val pageNumbers = remember(doc.blocks) {
        var page = 1
        buildMap {
            doc.blocks.forEachIndexed { index, block ->
                if (block is OfficeBlock.PageBreak) {
                    page += 1
                    put(index, page)
                }
            }
        }
    }

    val matchingBlocks = remember(doc.blocks, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else doc.blocks.indices.filter { idx -> blockMatchesQuery(doc.blocks[idx], searchQuery) }
    }

    LaunchedEffect(matchingBlocks) {
        onMatchCountChange(matchingBlocks.size)
    }

    LaunchedEffect(matchingBlocks, activeMatchIndex) {
        if (matchingBlocks.isNotEmpty()) {
            val target = matchingBlocks[activeMatchIndex.coerceIn(0, matchingBlocks.lastIndex)]
            listState.animateScrollToItem(target)
        }
    }

    LaunchedEffect(jumpToBlockIndex) {
        if (jumpToBlockIndex != null) {
            listState.animateScrollToItem(jumpToBlockIndex.coerceIn(0, doc.blocks.lastIndex))
            onJumpConsumed()
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        itemsIndexed(doc.blocks, key = { index, _ -> "word-block-$index" }, contentType = { _, block -> officeBlockContentType(block) }) { index, block ->
            val isFirst = index == 0
            val isLast = index == doc.blocks.lastIndex
            val cardShape = RoundedCornerShape(
                topStart = if (isFirst) 18.dp else 0.dp,
                topEnd = if (isFirst) 18.dp else 0.dp,
                bottomStart = if (isLast) 18.dp else 0.dp,
                bottomEnd = if (isLast) 18.dp else 0.dp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(palette.pageSurface)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (isFirst) 22.dp else 6.dp,
                        bottom = if (isLast) 26.dp else 6.dp
                    )
            ) {
                OfficeBlockView(
                    block = block,
                    palette = palette,
                    fontScale = fontScale,
                    searchQuery = searchQuery,
                    pageBreakNumber = pageNumbers[index]
                )
            }
        }
    }
}


@Composable
private fun SpreadsheetViewerBody(
    doc: LoadedDocument.Spreadsheet,
    palette: ReaderPalette,
    searchQuery: String,
    activeMatchIndex: Int,
    onMatchCountChange: (Int) -> Unit
) {
    if (doc.sheets.isEmpty()) {
        LaunchedEffect(Unit) { onMatchCountChange(0) }
        ViewerEmptyState(stringResource(R.string.viewer_office_empty_doc))
        return
    }

    var selectedSheetIndex by rememberSaveable(doc.displayName) { mutableIntStateOf(0) }
    val safeIndex = selectedSheetIndex.coerceIn(0, doc.sheets.lastIndex)
    val activeSheet: LoadedSheet = doc.sheets[safeIndex]
    val listState = rememberLazyListState()

    val matchingBlocks = remember(activeSheet.blocks, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else activeSheet.blocks.indices.filter { idx -> blockMatchesQuery(activeSheet.blocks[idx], searchQuery) }
    }

    LaunchedEffect(matchingBlocks) {
        onMatchCountChange(matchingBlocks.size)
    }

    LaunchedEffect(matchingBlocks, activeMatchIndex) {
        if (matchingBlocks.isNotEmpty()) {
            val target = matchingBlocks[activeMatchIndex.coerceIn(0, matchingBlocks.lastIndex)]
            listState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        if (doc.sheets.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(doc.sheets, key = { index, _ -> "sheet-tab-$index" }, contentType = { _, _ -> "sheet-tab" }) { index, sheet ->
                    val selected = index == safeIndex
                    Surface(
                        shape = QuietTheme.shapes.pill,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else QuietTheme.pillars.cardBorder
                        ),
                        modifier = Modifier.pressFeedback().clickable { selectedSheetIndex = index }
                    ) {
                        Text(
                            text = sheet.title,
                            style = QuietTheme.typography.metadataStrong.copy(textDirection = TextDirection.Content),
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        if (activeSheet.blocks.isEmpty()) {
            ViewerEmptyState(stringResource(R.string.viewer_office_empty_doc))
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(activeSheet.blocks, key = { index, _ -> "sheet-$safeIndex-block-$index" }, contentType = { _, block -> officeBlockContentType(block) }) { _, block ->
                    OfficeBlockView(
                        block = block,
                        palette = palette,
                        fontScale = 0.95f,
                        searchQuery = searchQuery,
                        pageBreakNumber = null
                    )
                }
            }
        }
    }
}


@Composable
private fun PlainTextViewerBody(
    doc: LoadedDocument.PlainText,
    palette: ReaderPalette,
    fontScale: Float,
    searchQuery: String,
    activeMatchIndex: Int,
    onMatchCountChange: (Int) -> Unit
) {
    val csvRows = doc.csvRows
    if (csvRows != null) {
        if (csvRows.isEmpty()) {
            LaunchedEffect(Unit) { onMatchCountChange(0) }
            ViewerEmptyState(stringResource(R.string.viewer_office_empty_doc))
            return
        }
        val tableRows = remember(csvRows) {
            csvRows.mapIndexed { rowIndex, cols ->
                cols.map { cell -> OfficeCell(text = cell, bold = rowIndex == 0) }
            }
        }
        val matchingRowCount = remember(csvRows, searchQuery) {
            if (searchQuery.isBlank()) 0
            else csvRows.count { row -> row.any { it.contains(searchQuery, ignoreCase = true) } }
        }
        LaunchedEffect(matchingRowCount) { onMatchCountChange(matchingRowCount) }

        LazyColumn(
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            item {
                OfficeTableView(
                    rows = tableRows,
                    headerRow = true,
                    palette = palette,
                    fontScale = fontScale,
                    searchQuery = searchQuery
                )
            }
        }
        return
    }

    if (doc.lines.isEmpty()) {
        LaunchedEffect(Unit) { onMatchCountChange(0) }
        ViewerEmptyState(stringResource(R.string.viewer_office_empty_doc))
        return
    }

    val listState = rememberLazyListState()
    val matchingLines = remember(doc.lines, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else doc.lines.indices.filter { doc.lines[it].contains(searchQuery, ignoreCase = true) }
    }

    LaunchedEffect(matchingLines) {
        onMatchCountChange(matchingLines.size)
    }

    LaunchedEffect(matchingLines, activeMatchIndex) {
        if (matchingLines.isNotEmpty()) {
            val target = matchingLines[activeMatchIndex.coerceIn(0, matchingLines.lastIndex)]
            listState.animateScrollToItem(target)
        }
    }

    val isCodeLike = doc.extension.equals("json", ignoreCase = true) ||
        doc.extension.equals("log", ignoreCase = true)
    val textStyle = TextStyle(
        fontFamily = if (isCodeLike) FontFamily.Monospace else MaterialTheme.typography.bodyLarge.fontFamily,
        fontSize = ((if (isCodeLike) 13.5f else 15.5f) * fontScale).sp,
        lineHeight = ((if (isCodeLike) 20f else 24f) * fontScale).sp,
        color = palette.text,
        textDirection = TextDirection.Content
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        itemsIndexed(doc.lines, key = { index, _ -> "line-$index" }, contentType = { _, _ -> "text-line" }) { index, line ->
            val isFirst = index == 0
            val isLast = index == doc.lines.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (isFirst) 18.dp else 0.dp,
                topEnd = if (isFirst) 18.dp else 0.dp,
                bottomStart = if (isLast) 18.dp else 0.dp,
                bottomEnd = if (isLast) 18.dp else 0.dp
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(palette.pageSurface)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = if (isFirst) 20.dp else 2.dp,
                        bottom = if (isLast) 24.dp else 2.dp
                    )
            ) {
                Text(
                    text = highlightQuery(line, searchQuery, palette),
                    style = textStyle
                )
            }
        }
    }
}


@Composable
private fun OfficeBlockView(
    block: OfficeBlock,
    palette: ReaderPalette,
    fontScale: Float,
    searchQuery: String,
    pageBreakNumber: Int?
) {
    when (block) {
        is OfficeBlock.ParagraphBlock -> OfficeParagraphView(
            paragraph = block.paragraph,
            palette = palette,
            fontScale = fontScale,
            searchQuery = searchQuery
        )
        is OfficeBlock.TableBlock -> OfficeTableView(
            rows = block.rows,
            headerRow = block.headerRow,
            palette = palette,
            fontScale = fontScale,
            searchQuery = searchQuery
        )
        is OfficeBlock.ImageBlock -> OfficeImageView(
            bytes = block.bytes,
            borderColor = palette.border
        )
        OfficeBlock.PageBreak -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = palette.border)
                if (pageBreakNumber != null) {
                    Text(
                        text = stringResource(R.string.viewer_office_page_break, pageBreakNumber),
                        style = QuietTheme.typography.badge,
                        color = palette.muted
                    )
                }
                HorizontalDivider(modifier = Modifier.weight(1f), color = palette.border)
            }
        }
        is OfficeBlock.Section -> {
            Text(
                text = highlightQuery(block.title, searchQuery, palette),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (18f * fontScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    textDirection = TextDirection.Content
                ),
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .semantics { heading() }
            )
        }
    }
}

@Composable
private fun OfficeParagraphView(
    paragraph: OfficeParagraph,
    palette: ReaderPalette,
    fontScale: Float,
    searchQuery: String
) {
    if (paragraph.isBlank) {
        Spacer(modifier = Modifier.height(6.dp))
        return
    }

    val (baseSp, lineHeightMult, weight, topPad) = when (paragraph.level) {
        1 -> Quad(26f, 1.28f, FontWeight.Bold, 6.dp)
        2 -> Quad(21f, 1.32f, FontWeight.Bold, 10.dp)
        3 -> Quad(18f, 1.36f, FontWeight.SemiBold, 8.dp)
        4 -> Quad(16f, 1.40f, FontWeight.SemiBold, 6.dp)
        else -> Quad(15.5f, 1.55f, FontWeight.Normal, 0.dp)
    }

    val alignment = when (paragraph.align.lowercase()) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        "justify" -> TextAlign.Justify
        else -> TextAlign.Start
    }

    val style = TextStyle(
        fontFamily = MaterialTheme.typography.bodyLarge.fontFamily,
        fontSize = (baseSp * fontScale).sp,
        lineHeight = (baseSp * lineHeightMult * fontScale).sp,
        fontWeight = weight,
        color = palette.text,
        textAlign = alignment,
        textDirection = TextDirection.Content
    )

    val annotated = remember(paragraph, searchQuery, palette) {
        buildParagraphAnnotatedString(paragraph, searchQuery, palette)
    }

    val headingModifier = if (paragraph.level in 1..4) Modifier.semantics { heading() } else Modifier
    val indentDp = (paragraph.listIndent.coerceIn(0, 6) * 16).dp

    val marker = paragraph.listMarker
    if (marker != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indentDp, top = topPad),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = marker.trim(),
                style = style.copy(fontWeight = FontWeight.SemiBold, color = palette.muted)
            )
            Text(
                text = annotated,
                style = style,
                modifier = Modifier.weight(1f).then(headingModifier)
            )
        }
    } else {
        Text(
            text = annotated,
            style = style,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topPad)
                .then(headingModifier)
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun OfficeTableView(
    rows: List<List<OfficeCell>>,
    headerRow: Boolean,
    palette: ReaderPalette,
    fontScale: Float,
    searchQuery: String
) {
    if (rows.isEmpty()) return
    val cellStyle = TextStyle(
        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
        fontSize = (13.5f * fontScale).sp,
        lineHeight = (19f * fontScale).sp,
        color = palette.text,
        textDirection = TextDirection.Content
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
    ) {
        Column {
            rows.forEachIndexed { rowIndex, row ->
                val isHeader = headerRow && rowIndex == 0
                val rowBg = when {
                    isHeader -> palette.headerTint
                    rowIndex % 2 == 1 -> palette.zebraTint
                    else -> palette.pageSurface
                }
                Row(
                    modifier = Modifier.background(rowBg)
                ) {
                    row.forEach { cell ->
                        val minColWidth = (112 * cell.colSpan.coerceIn(1, 4)).dp
                        Box(
                            modifier = Modifier
                                .widthIn(min = minColWidth, max = 280.dp)
                                .border(0.5.dp, palette.border)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = highlightQuery(cell.text, searchQuery, palette),
                                style = if (isHeader || cell.bold) {
                                    cellStyle.copy(fontWeight = FontWeight.SemiBold)
                                } else {
                                    cellStyle
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficeImageView(
    bytes: ByteArray,
    borderColor: Color
) {
    var bitmap by remember(bytes) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(bytes) { mutableStateOf(false) }
    DisposeOwnedPreviewBitmap(bitmap)

    LaunchedEffect(bytes) {
        failed = false
        bitmap = withContext(Dispatchers.Default) {
            decodeSafeBitmap(bytes, maxEdgePx = 1400)
        }
        if (bitmap == null) failed = true
    }

    val decoded = bitmap
    if (decoded != null && !decoded.isRecycled) {
        val imageBitmap = remember(decoded) { decoded.asImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(0.75.dp, borderColor, RoundedCornerShape(10.dp))
        )
    } else if (failed) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(0.75.dp, borderColor, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlineBottomSheet(
    headings: List<OutlineHeading>,
    onSelect: (OutlineHeading) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = QuietTheme.shapes.modalSheetTop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.viewer_office_outline),
                style = QuietTheme.typography.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(headings, key = { index, heading -> "heading-${heading.blockIndex}-$index" }, contentType = { _, _ -> "outline-heading" }) { _, heading ->
                    val indent = ((heading.level - 1).coerceIn(0, 3) * 14).dp
                    Surface(
                        shape = QuietTheme.shapes.thumbnail,
                        color = Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(heading) }
                    ) {
                        Text(
                            text = heading.text,
                            style = QuietTheme.typography.bodyMedium.copy(
                                fontWeight = if (heading.level <= 2) FontWeight.SemiBold else FontWeight.Normal,
                                textDirection = TextDirection.Content
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = indent + 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceBottomSheet(
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    selectedTheme: ReadingTheme,
    onThemeSelect: (ReadingTheme) -> Unit,
    wordCount: Int,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = QuietTheme.shapes.modalSheetTop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.viewer_office_appearance),
                    style = QuietTheme.typography.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (wordCount > 0) {
                    Surface(
                        shape = QuietTheme.shapes.pill,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.viewer_office_word_count, wordCount),
                            style = QuietTheme.typography.badge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.viewer_office_font_size),
                        style = QuietTheme.typography.metadataStrong,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(fontScale * 100).toInt()}%",
                        style = QuietTheme.typography.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = fontScale,
                    onValueChange = onFontScaleChange,
                    valueRange = 0.85f..1.40f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    Triple(ReadingTheme.AUTO, R.string.viewer_office_theme_auto, MaterialTheme.colorScheme.surfaceContainerHigh),
                    Triple(ReadingTheme.PAPER, R.string.viewer_office_theme_paper, Color(0xFFFFFFFF)),
                    Triple(ReadingTheme.SEPIA, R.string.viewer_office_theme_sepia, Color(0xFFF8F1E3)),
                    Triple(ReadingTheme.DARK, R.string.viewer_office_theme_dark, Color(0xFF14161B))
                )
                options.forEach { (theme, labelRes, swatch) ->
                    val selected = theme == selectedTheme
                    Surface(
                        shape = QuietTheme.shapes.thumbnail,
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else QuietTheme.pillars.cardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onThemeSelect(theme) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(1.dp, QuietTheme.pillars.cardBorder, CircleShape)
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = if (theme == ReadingTheme.DARK) Color.White else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(labelRes),
                                style = QuietTheme.typography.badge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}


private fun buildParagraphAnnotatedString(
    paragraph: OfficeParagraph,
    searchQuery: String,
    palette: ReaderPalette
): AnnotatedString {
    val needle = searchQuery.trim()
    return buildAnnotatedString {
        paragraph.runs.forEach { run ->
            val runStyle = SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null
            )
            withStyle(runStyle) {
                appendHighlighted(run.text, needle, palette)
            }
        }
    }
}

private fun highlightQuery(
    text: String,
    searchQuery: String,
    palette: ReaderPalette
): AnnotatedString {
    val needle = searchQuery.trim()
    if (needle.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        appendHighlighted(text, needle, palette)
    }
}

private fun AnnotatedString.Builder.appendHighlighted(
    text: String,
    needle: String,
    palette: ReaderPalette
) {
    if (needle.isEmpty() || text.isEmpty()) {
        append(text)
        return
    }
    var cursor = 0
    while (cursor < text.length) {
        val hit = text.indexOf(needle, startIndex = cursor, ignoreCase = true)
        if (hit < 0) {
            append(text.substring(cursor))
            break
        }
        if (hit > cursor) append(text.substring(cursor, hit))
        withStyle(SpanStyle(background = palette.highlight, color = palette.highlightText)) {
            append(text.substring(hit, hit + needle.length))
        }
        cursor = hit + needle.length
    }
}

private fun slideMatchesQuery(slide: LoadedSlide, query: String): Boolean {
    if (query.isBlank()) return false
    if (slide.title?.contains(query, ignoreCase = true) == true) return true
    return slide.blocks.any { blockMatchesQuery(it, query) }
}

private fun blockMatchesQuery(block: OfficeBlock, query: String): Boolean {
    if (query.isBlank()) return false
    return when (block) {
        is OfficeBlock.ParagraphBlock -> block.paragraph.text.contains(query, ignoreCase = true)
        is OfficeBlock.TableBlock -> block.rows.any { row ->
            row.any { cell -> cell.text.contains(query, ignoreCase = true) }
        }
        is OfficeBlock.Section -> block.title.contains(query, ignoreCase = true)
        is OfficeBlock.ImageBlock, OfficeBlock.PageBreak -> false
    }
}

private fun slideTitleSnippet(slide: LoadedSlide): String {
    val explicit = slide.title?.trim().orEmpty()
    if (explicit.isNotEmpty()) return explicit
    val firstParagraph = slide.blocks
        .filterIsInstance<OfficeBlock.ParagraphBlock>()
        .firstOrNull { !it.paragraph.isBlank }
        ?.paragraph
        ?.text
        ?.trim()
        .orEmpty()
    return firstParagraph.ifEmpty { "#${slide.index + 1}" }
}

private fun decodeSafeBitmap(bytes: ByteArray, maxEdgePx: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val rawMax = maxOf(bounds.outWidth, bounds.outHeight)
        if (rawMax <= 0) return null
        var sample = 1
        while (rawMax / sample > maxEdgePx) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}
