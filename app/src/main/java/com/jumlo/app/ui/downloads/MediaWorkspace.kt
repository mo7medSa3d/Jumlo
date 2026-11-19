package com.jumlo.app.ui.downloads

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jumlo.app.BuildConfig
import com.jumlo.app.R
import com.jumlo.app.core.DirectFileKind
import com.jumlo.app.core.media.*
import com.jumlo.app.ui.components.QuietBadge
import com.jumlo.app.ui.components.QuietShimmerSkeleton
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val UrlKeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go)
private val RESTRICTED_ERROR_CODES = setOf("MEDIA_SESSION_REQUIRED", "MEDIA_PRIVATE_CONTENT", "MEDIA_AGE_RESTRICTED", "MEDIA_SOURCE_REJECTED", "HTTP_401", "HTTP_403")
private val UNSUPPORTED_ERROR_CODES = setOf("MEDIA_SITE_UNSUPPORTED", "MEDIA_CONVERSION_UNAVAILABLE")
private val AUDIO_BITRATES = listOf(320, 256, 192, 128, 96, 64)
private val PDF_DIRECT_MODES = listOf(
    "ORIGINAL" to R.string.media_pdf_original,
    "PDF_RECOMMENDED" to R.string.media_pdf_recommended,
    "PDF_EXTREME" to R.string.media_pdf_extreme,
    "PDF_TO_IMAGES" to R.string.media_pdf_to_images
)
private val IMAGE_DIRECT_MODES = listOf(
    "ORIGINAL" to R.string.media_img_original,
    "IMAGE_COMPRESSED" to R.string.media_img_compressed,
    "IMAGE_TO_PDF" to R.string.media_img_to_pdf
)

/** Inspect a link, select actual source qualities and queue a durable download. */
@Composable fun MediaWorkspace(
    initialUrl:String,
    sessionEpoch:Long,
    onQueued:(String)->Unit,
    onDirect:(String)->Unit,
    @Suppress("UNUSED_PARAMETER") defaultWifiOnly:Boolean=false,
    defaultVideoQuality:String="BEST",
    defaultAudioQuality:Int=192,
    onEditedUrl:(String)->Unit = {},
    viewModel:MediaViewModel=hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Keep the parent workspace's draft URL in sync with the media-specific VM.
    // Navigation's unsaved-changes guard observes the parent URL, not media state.
    val currentOnEditedUrl by rememberUpdatedState(onEditedUrl)
    val currentOnQueued by rememberUpdatedState(onQueued)
    val editUrl = remember(viewModel) {
        { value: String ->
            viewModel.editUrl(value)
            currentOnEditedUrl(value)
        }
    }
    val clipboard=LocalClipboardManager.current
    val haptics=rememberAppHaptics()
    val reducedMotion=LocalReducedMotion.current
    var clipboardEmpty by remember { mutableStateOf(false) }
    val readClipboard = remember(clipboard) {
        { try { clipboard.getText()?.text?.take(32768) } catch (_:RuntimeException) { null } }
    }
    val clipboardSuggestion=remember {
        readClipboard()?.let { text ->
            runCatching { MediaLinks.normalize(text,allowHttp=true) }.getOrNull()
        }
    }
    val onAnalyze = remember(viewModel) { { viewModel.analyze() } }
    val onAnalyzeWithHaptics = remember(haptics, viewModel) { { haptics.actionClick(); viewModel.analyze() } }
    val onUrlChange = remember(editUrl) { { value: String -> clipboardEmpty = false; editUrl(value) } }
    val onClearUrl = remember(haptics, editUrl) { { haptics.selectionToggle(); editUrl("") } }
    val onPasteUrl = remember(readClipboard, haptics, editUrl, viewModel) {
        {
            val clip = readClipboard()?.takeIf { it.isNotBlank() }
            clipboardEmpty = clip == null
            if (clip != null) {
                haptics.actionClick()
                editUrl(clip)
                viewModel.analyze()
            }
        }
    }
    val onSwitchToExtractor = remember(haptics, viewModel) { { haptics.actionClick(); viewModel.analyzeAsMediaStream() } }
    val onSelectVideo = remember(haptics, viewModel) { { haptics.selectionToggle(); viewModel.mode(MediaMode.VIDEO) } }
    val onSelectAudio = remember(haptics, viewModel) { { haptics.selectionToggle(); viewModel.mode(MediaMode.AUDIO) } }
    val onSubmit = remember(haptics, viewModel) { { haptics.toolExecute(); viewModel.submit(currentOnQueued) } }
    val urlKeyboardActions = remember(onAnalyze) { KeyboardActions(onGo = { onAnalyze() }) }
    LaunchedEffect(sessionEpoch) {
        viewModel.beginSession(initialUrl,sessionEpoch,defaultVideoQuality,defaultAudioQuality)
    }
    // Also re-inspect on a fresh entry with the exact same shared URL.
    LaunchedEffect(sessionEpoch, state.url, state.allowHttp) {
        if(runCatching { MediaLinks.normalize(state.url,state.allowHttp) }.isSuccess) {
            delay(200)
            viewModel.analyze(automatic=true)
        }
    }
    LaunchedEffect(state.error) { if(state.error!=null) haptics.error() }
    DisposableEffect(Unit) { onDispose { viewModel.cancelAnalysis() } }
    val choiceRows = remember(state.choices) { state.choices.chunked(2) }
    val selectedChoice = remember(state.choices, state.selectedKey) {
        state.choices.firstOrNull { it.key == state.selectedKey }
    }
    val unknownSizeLabel = stringResource(R.string.media_size_unknown)
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier=Modifier.weight(1f).widthIn(max=840.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            contentPadding=PaddingValues(horizontal=20.dp,vertical=20.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)
        ) {
            // Top bar already displays the tool title and subtitle; start directly with link input.
            item("input") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = QuietTheme.shapes.card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, if (state.platformBadge != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)),
                        modifier = Modifier.fillMaxWidth().then(if (reducedMotion) Modifier else Modifier.animateContentSize())
                    ) {
                        Column(Modifier.padding(6.dp)) {
                            TextField(
                                value = state.url,
                                onValueChange = onUrlChange,
                                singleLine = true,
                                enabled = !state.submitting,
                                isError = clipboardEmpty,
                                supportingText = if (clipboardEmpty) ({ Text(stringResource(R.string.studio_clipboard_empty)) }) else null,
                                placeholder = { Text(stringResource(R.string.redesign_link_hint), style = QuietTheme.typography.metadata) },
                                label = { Text(stringResource(R.string.media_link)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = QuietTheme.shapes.innerPanel,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                keyboardOptions = UrlKeyboardOptions,
                                keyboardActions = urlKeyboardActions,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                                leadingIcon = { Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (state.url.isNotEmpty()) {
                                            IconButton(onClick = onClearUrl) {
                                                Icon(Icons.Rounded.Close, stringResource(R.string.signature_clear))
                                            }
                                        }
                                        FilledTonalIconButton(
                                            onClick = onPasteUrl,
                                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        ) {
                                            Icon(Icons.Rounded.ContentPaste, stringResource(R.string.action_paste), Modifier.size(18.dp))
                                        }
                                    }
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnimatedVisibility(
                            visible = state.platformBadge != null,
                            enter = if (reducedMotion) EnterTransition.None else fadeIn(),
                            exit = if (reducedMotion) ExitTransition.None else fadeOut()
                        ) {
                            state.platformBadge?.let { badge ->
                                QuietBadge(
                                    text = badge,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    leadingIcon = Icons.Rounded.AutoAwesome
                                )
                            }
                        }
                        if (state.url.isNotBlank() && !state.analyzing) {
                            FilledTonalButton(
                                onClick = onAnalyzeWithHaptics,
                                shape = QuietTheme.shapes.activeIndicatorPill,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.media_analyze), style = QuietTheme.typography.metadataStrong)
                            }
                        }
                    }
                }
            }
            if(state.url.isEmpty() && clipboardSuggestion!=null) item("clipboard") {
                val onUseSuggestion = remember(clipboardSuggestion, haptics, editUrl, viewModel) {
                    { haptics.actionClick(); editUrl(clipboardSuggestion); viewModel.analyze() }
                }
                SuggestionChip(onClick=onUseSuggestion,
                    label={Text(stringResource(R.string.redesign_paste_suggestion))},icon={Icon(Icons.Rounded.ContentPaste,null)})
            }
            if(state.analyzing) item("loading") {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    QuietShimmerSkeleton(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.media_analyzing),style=QuietTheme.typography.metadata,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier=Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick=viewModel::cancelAnalysis,modifier=Modifier.heightIn(min=48.dp)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
            state.error?.let { code -> item("error") {
                val restricted = code in RESTRICTED_ERROR_CODES
                val unsupported = code in UNSUPPORTED_ERROR_CODES
                QuietStateCard(
                    title=mediaError(code),
                    subtitle=stringResource(when {
                        restricted -> R.string.media_restricted_hint
                        unsupported -> R.string.media_direct_url_hint
                        else -> R.string.redesign_download_retry_hint
                    }),
                    actionLabel=if(restricted || unsupported) null else stringResource(R.string.media_analyze),
                    onAction=if(restricted || unsupported) null else onAnalyze,
                    isError=true,
                    modifier=Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }}


            state.direct?.let { preview ->
                item("direct-preview") {
                    val directMeta = remember(preview.mime, preview.sizeBytes) {
                        listOfNotNull(preview.mime, preview.sizeBytes?.let(::mediaBytes)).joinToString(" · ")
                    }
                    Surface(
                        shape=QuietTheme.shapes.heroCard,
                        color=MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Icon(
                                    imageVector=when(preview.kind) {
                                        DirectFileKind.PDF -> Icons.Rounded.PictureAsPdf
                                        DirectFileKind.VIDEO -> Icons.Rounded.Movie
                                        DirectFileKind.AUDIO -> Icons.Rounded.AudioFile
                                        DirectFileKind.IMAGE -> Icons.Rounded.Image
                                        DirectFileKind.ARCHIVE -> Icons.Rounded.FolderZip
                                        DirectFileKind.OTHER -> Icons.Rounded.InsertDriveFile
                                    },
                                    contentDescription=null,
                                    tint=MaterialTheme.colorScheme.primary,
                                    modifier=Modifier.size(28.dp)
                                )
                                QuietBadge(
                                    text=state.platformBadge ?: preview.kind.name,
                                    containerColor=MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor=MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(stringResource(R.string.redesign_ready_file),color=MaterialTheme.colorScheme.primary,style=QuietTheme.typography.metadataStrong)
                            }
                            Text(state.name.ifBlank { preview.title },style=QuietTheme.typography.sectionTitle.copy(fontWeight=FontWeight.Bold,textDirection=TextDirection.Content))
                            Text(
                                text=directMeta.ifBlank { stringResource(R.string.media_size_unknown) },
                                style=QuietTheme.typography.metadata,
                                color=MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        }
                    }
                }
                if (preview.kind == DirectFileKind.PDF) {
                    item("direct-pdf-options") {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.media_select_quality),style=QuietTheme.typography.sectionTitle,modifier=Modifier.semantics { heading() })
                            PDF_DIRECT_MODES.forEach { (modeKey, modeLabelRes) ->
                                val selected = state.directMode == modeKey
                                val onModeClick = remember(modeKey, haptics, viewModel) {
                                    { haptics.selectionToggle(); viewModel.directMode(modeKey) }
                                }
                                Surface(
                                    shape=QuietTheme.shapes.innerPanel,
                                    color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).selectable(selected=selected,role=Role.RadioButton,onClick=onModeClick).pressFeedback()
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        RadioButton(selected=selected,onClick=null)
                                        Text(stringResource(modeLabelRes),style=QuietTheme.typography.bodyMedium.copy(fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium),
                                            color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                } else if (preview.kind == DirectFileKind.IMAGE) {
                    item("direct-image-options") {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.media_select_quality),style=QuietTheme.typography.sectionTitle,modifier=Modifier.semantics { heading() })
                            IMAGE_DIRECT_MODES.forEach { (modeKey, modeLabelRes) ->
                                val selected = state.directMode == modeKey
                                val onModeClick = remember(modeKey, haptics, viewModel) {
                                    { haptics.selectionToggle(); viewModel.directMode(modeKey) }
                                }
                                Surface(
                                    shape=QuietTheme.shapes.innerPanel,
                                    color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).selectable(selected=selected,role=Role.RadioButton,onClick=onModeClick).pressFeedback()
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        RadioButton(selected=selected,onClick=null)
                                        Text(stringResource(modeLabelRes),style=QuietTheme.typography.bodyMedium.copy(fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium),
                                            color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                } else if (preview.kind == DirectFileKind.VIDEO || preview.kind == DirectFileKind.AUDIO) {
                    item("direct-media-switch") {
                        QuietTonalButton(
                            text=stringResource(R.string.media_switch_to_extractor),
                            onClick=onSwitchToExtractor,
                            modifier=Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            state.asset?.let { asset ->
                item("media-preview") {
                    Surface(
                        shape=QuietTheme.shapes.heroCard,
                        color=MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            asset.thumbnailUrl?.let { thumb ->
                                MediaArtwork(thumb,asset.title)
                            }
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                QuietBadge(
                                    text=state.platformBadge ?: asset.extractor,
                                    containerColor=MaterialTheme.colorScheme.primaryContainer,
                                    contentColor=MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                asset.durationSeconds?.takeIf { it > 0 }?.let { sec ->
                                    QuietBadge(
                                        text=formatDuration(sec.roundToInt()),
                                        containerColor=MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor=MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                asset.uploader?.let { up ->
                                    Text(up,style=QuietTheme.typography.metadata.copy(textDirection=TextDirection.Content),color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            }
                            Text(state.name.ifBlank { asset.title },style=QuietTheme.typography.sectionTitle.copy(fontWeight=FontWeight.Bold,textDirection=TextDirection.Content),maxLines=3,overflow=TextOverflow.Ellipsis)

                        }
                    }
                }

                item("media-type-selector") {
                    val hasVideo = remember(asset.formats) { asset.formats.any { it.hasVideo } }
                    val hasAudio = remember(asset.formats) { asset.formats.any { it.hasAudio } }
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.media_select_type),style=QuietTheme.typography.sectionTitle,modifier=Modifier.semantics { heading() })
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                            if (hasVideo) {
                                ModeSelectionCard(
                                    title=stringResource(if (hasAudio)
                                        R.string.media_video else R.string.media_video_only),
                                    icon=Icons.Rounded.Videocam,
                                    selected=state.mode==MediaMode.VIDEO,
                                    onClick=onSelectVideo,
                                    modifier=Modifier.weight(1f)
                                )
                            }
                            if (hasAudio) {
                                ModeSelectionCard(
                                    title=stringResource(R.string.media_audio_only),
                                    icon=Icons.Rounded.Audiotrack,
                                    selected=state.mode==MediaMode.AUDIO,
                                    onClick=onSelectAudio,
                                    modifier=Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (state.mode == MediaMode.AUDIO) {
                    item("audio-format-controls") {
                        Surface(
                            shape=QuietTheme.shapes.card,
                            color=MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.media_audio_format),style=QuietTheme.typography.metadataStrong)
                                val originalAudioLabel = stringResource(R.string.media_original_audio)
                                val hasNativeOnlyChoice = remember(state.choices) {
                                    state.choices.any { MediaEngineRouting.isNativeChoice(it) }
                                }
                                LaunchedEffect(hasNativeOnlyChoice, state.audio) {
                                    if ((!BuildConfig.EXTENDED_MEDIA || hasNativeOnlyChoice) && state.audio != AudioOutput.ORIGINAL) {
                                        viewModel.audio(AudioOutput.ORIGINAL)
                                    }
                                }
                                val audioFormats = remember(hasNativeOnlyChoice, originalAudioLabel) {
                                    if (BuildConfig.EXTENDED_MEDIA && !hasNativeOnlyChoice) {
                                        listOf(
                                            AudioOutput.ORIGINAL to originalAudioLabel,
                                            AudioOutput.MP3 to "MP3",
                                            AudioOutput.M4A to "M4A",
                                            AudioOutput.OPUS to "OPUS"
                                        )
                                    } else {
                                        listOf(AudioOutput.ORIGINAL to originalAudioLabel)
                                    }
                                }
                                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    items(audioFormats, key={it.first.name}, contentType={"audio-format-chip"}) { (fmt, label) ->
                                        val onFormatClick = remember(fmt, haptics, viewModel) {
                                            { haptics.selectionToggle(); viewModel.audio(fmt) }
                                        }
                                        FilterChip(
                                            border=null,
                                            selected=state.audio==fmt,
                                            onClick=onFormatClick,
                                            label={Text(label,style=QuietTheme.typography.metadataStrong)}
                                        )
                                    }
                                }
                                if (state.audio != AudioOutput.ORIGINAL) {
                                    Text(stringResource(R.string.media_encoding_bitrate),style=QuietTheme.typography.metadataStrong)
                                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        items(AUDIO_BITRATES, key={it}, contentType={"bitrate-chip"}) { kbps ->
                                            val onBitrateClick = remember(kbps, haptics, viewModel) {
                                                { haptics.selectionToggle(); viewModel.bitrate(kbps) }
                                            }
                                            FilterChip(
                                                border=null,
                                                selected=state.bitrate==kbps,
                                                onClick=onBitrateClick,
                                                label={Text(stringResource(R.string.modern_audio_bitrate, kbps),style=QuietTheme.typography.metadataStrong)}
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (choiceRows.isNotEmpty()) item("media-quality-header") {
                    Text(stringResource(R.string.media_select_quality),style=QuietTheme.typography.sectionTitle,modifier=Modifier.semantics { heading() })
                }
                val isUnprobedExtractor = asset.extractor in setOf("DirectMedia", "Html5")

                items(
                    choiceRows,
                    key={row -> if (row.size == 1) row[0].key else "${row[0].key}-${row[1].key}"},
                    contentType={"quality-row"}
                ) { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) {
                        row.forEach { choice ->
                            val selected = state.selectedKey == choice.key
                            val title = remember(choice) { MediaCatalog.qualityTitle(choice) }
                            val sizeLabel = remember(choice, asset.durationSeconds, unknownSizeLabel) {
                                MediaCatalog.displayBytes(choice, asset.durationSeconds)
                                    ?.let { (if(choice.sizeIsApproximate || choice.estimatedBytes == null) "~" else "") + mediaBytes(it) }
                                    ?: unknownSizeLabel
                            }
                            val containerLabel = remember(state.mode, state.audio, choice.container) {
                                if (state.mode == MediaMode.AUDIO && state.audio != AudioOutput.ORIGINAL) {
                                    state.audio.name
                                } else {
                                    choice.container.uppercase(Locale.ROOT)
                                }
                            }
                            val subBadge = if (choice.mode == MediaMode.VIDEO) {
                                val audioLabel = if (isUnprobedExtractor)
                                    R.string.media_audio_not_probed else if (choice.expectsAudio)
                                    R.string.media_with_audio else R.string.media_without_audio
                                "$containerLabel · ${stringResource(audioLabel)}"
                            } else {
                                containerLabel
                            }
                            val onChoiceClick = remember(choice.key, haptics, viewModel) {
                                { haptics.selectionToggle(); viewModel.select(choice.key) }
                            }
                            QualityChoiceCard(
                                title=title,
                                subBadge=subBadge,
                                sizeLabel=sizeLabel,
                                selected=selected,
                                onClick=onChoiceClick,
                                modifier=Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (state.asset != null && state.choices.isNotEmpty() && state.selectedKey == null) {
                item("choose-quality-hint") {
                    Text(stringResource(R.string.media_choose_quality_hint),
                        style=QuietTheme.typography.bodyMedium,
                        color=MaterialTheme.colorScheme.primary)
                }
            }
        }
        Surface(tonalElevation=2.dp) {
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            val selectedQualityTitle = remember(selectedChoice) {
                selectedChoice?.let(MediaCatalog::qualityTitle)
            }
            val downloadButtonLabel = when {
                state.submitting -> stringResource(R.string.media_queueing)
                selectedQualityTitle != null -> stringResource(R.string.media_download_with_quality, selectedQualityTitle)
                state.asset != null -> stringResource(R.string.media_download_selected)
                else -> stringResource(R.string.redesign_download_now)
            }
            Button(
                onClick=onSubmit,
                enabled=!state.analyzing && !state.submitting && (state.direct!=null || state.selectedKey!=null),
                modifier=Modifier.widthIn(max=840.dp).fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp).pressFeedback().heightIn(min=56.dp),
                shape=QuietTheme.shapes.button
            ) {
                Icon(Icons.Rounded.Download,null);Spacer(Modifier.width(8.dp))
                Text(
                    text=downloadButtonLabel,
                    style=QuietTheme.typography.button
                )
            }
            }
        }
    }


}

@Composable
private fun QualityChoiceCard(
    title: String,
    subBadge: String,
    sizeLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = QuietTheme.shapes.card,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.heightIn(min = 96.dp).pressFeedback()
            .semantics { this.selected = selected; role = Role.RadioButton }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = subBadge,
                    style = QuietTheme.typography.badge,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = sizeLabel,
                    style = QuietTheme.typography.metadata,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ModeSelectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = QuietTheme.shapes.card,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.heightIn(min = 64.dp).pressFeedback()
            .semantics {this.selected=selected;role=Role.RadioButton}
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = title,
                style = QuietTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MediaArtwork(url:String,title:String) {
    val context = LocalContext.current
    val request = remember(url, context) {
        val referer = when {
            url.contains("tiktok", ignoreCase = true) -> "https://www.tiktok.com/"
            url.contains("instagram", ignoreCase = true) || url.contains("cdninstagram", ignoreCase = true) -> "https://www.instagram.com/"
            url.contains("fbcdn", ignoreCase = true) || url.contains("facebook", ignoreCase = true) -> "https://www.facebook.com/"
            url.contains("twimg", ignoreCase = true) -> "https://x.com/"
            else -> null
        }
        ImageRequest.Builder(context)
            .data(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            .apply { if (referer != null) addHeader("Referer", referer) }
            .crossfade(true)
            .build()
    }
    var loading by remember(url) {mutableStateOf(true)}
    var failed by remember(url) {mutableStateOf(false)}
    val onLoading = remember(url) { { _: Any -> loading = true; failed = false } }
    val onSuccess = remember(url) { { _: Any -> loading = false; failed = false } }
    val onError = remember(url) { { _: Any -> loading = false; failed = true } }
    Box(Modifier.fillMaxWidth().height(190.dp).clip(QuietTheme.shapes.thumbnail).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment=Alignment.Center) {
        AsyncImage(model=request,contentDescription=title,modifier=Modifier.fillMaxSize(),
            contentScale=ContentScale.Crop,
            onLoading=onLoading,onSuccess=onSuccess,
            onError=onError)
        if(loading) QuietShimmerSkeleton(Modifier.fillMaxSize())
        if(failed) Column(horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(QuietTheme.spacing.sm)) {
            Icon(Icons.Rounded.BrokenImage,null,Modifier.size(32.dp),MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.msg_preview_unavailable),style=QuietTheme.typography.metadata,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hrs, mins, secs)
    else String.format(Locale.ROOT, "%02d:%02d", mins, secs)
}

private fun mediaBytes(bytes:Long):String=when {
    bytes>=1073741824L -> String.format(Locale.ROOT,"%.2f GiB",bytes/1073741824.0)
    bytes>=1048576L -> String.format(Locale.ROOT,"%.1f MiB",bytes/1048576.0)
    bytes>=1024L -> String.format(Locale.ROOT,"%.0f KiB",bytes/1024.0)
    else -> "$bytes B"
}

@Composable private fun mediaError(code:String):String=stringResource(userMessageResource(code))
