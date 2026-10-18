package com.jumlo.app.ui.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
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
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/** Direct media by default; optionally uses the APK-packaged multi-site extractor. */
@Composable fun MediaWorkspace(
    initialUrl:String,
    sessionEpoch:Long,
    onQueued:(String)->Unit,
    onDirect:(String)->Unit,
    viewModel:MediaViewModel=hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard=LocalClipboardManager.current
    val haptics=rememberAppHaptics()
    var advanced by rememberSaveable { mutableStateOf(false) }
    val clipboardSuggestion=remember { clipboard.getText()?.text?.toString()?.takeIf { it.trim().startsWith("https://",true) } }
    LaunchedEffect(sessionEpoch) { advanced=false; viewModel.beginSession(initialUrl) }
    // Also re-inspect on a fresh entry with the exact same shared URL.
    LaunchedEffect(sessionEpoch, state.url) {
        if(state.url.startsWith("https://",true) || (state.allowHttp && state.url.startsWith("http://",true))) {
            delay(400)
            viewModel.analyze()
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.cancelAnalysis() } }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier=Modifier.weight(1f).fillMaxWidth(),
            contentPadding=PaddingValues(horizontal=20.dp,vertical=20.dp),
            verticalArrangement=Arrangement.spacedBy(16.dp)
        ) {
            item("hero") {
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.redesign_download_hero),style=QuietTheme.typography.screenTitle,fontWeight=FontWeight.Bold)
                    Text(
                        stringResource(if (BuildConfig.EXTENDED_MEDIA) R.string.media_supported_platforms_extended else R.string.media_supported_platforms),
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        style=QuietTheme.typography.metadata
                    )
                }
            }
            item("input") {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value=state.url,onValueChange=viewModel::editUrl,singleLine=true,
                        placeholder={Text(stringResource(R.string.redesign_link_hint))},
                        label={Text(stringResource(R.string.media_link))},
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),
                        textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr),
                        leadingIcon={Icon(Icons.Rounded.Link,null)},
                        trailingIcon={IconButton(onClick={clipboard.getText()?.text?.toString()?.let(viewModel::editUrl)}) {
                            Icon(Icons.Rounded.ContentPaste,stringResource(R.string.action_paste))
                        }}
                    )
                    Row(
                        modifier=Modifier.fillMaxWidth(),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            state.platformBadge?.let { badge ->
                                QuietBadge(
                                    text=badge,
                                    containerColor=MaterialTheme.colorScheme.primaryContainer,
                                    contentColor=MaterialTheme.colorScheme.onPrimaryContainer,
                                    leadingIcon=Icons.Rounded.AutoAwesome
                                )
                            }
                        }
                        if(state.url.isNotBlank() && !state.analyzing) {
                            TextButton(onClick={haptics.actionClick();viewModel.analyze()}) {
                                Icon(Icons.Rounded.Refresh,null,Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.media_analyze),style=QuietTheme.typography.metadataStrong)
                            }
                        }
                    }
                }
            }
            if(state.url.isEmpty() && clipboardSuggestion!=null) item("clipboard") {
                SuggestionChip(onClick={viewModel.editUrl(clipboardSuggestion)},
                    label={Text(stringResource(R.string.redesign_paste_suggestion))},icon={Icon(Icons.Rounded.ContentPaste,null)})
            }
            if(state.analyzing) item("loading") {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    QuietShimmerSkeleton(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.media_analyzing),style=QuietTheme.typography.metadata,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error?.let { code -> item("error") {
                val restricted = code in setOf("MEDIA_SESSION_REQUIRED","MEDIA_PRIVATE_CONTENT","MEDIA_SOURCE_REJECTED","HTTP_401","HTTP_403")
                val unsupported = code in setOf("MEDIA_SITE_UNSUPPORTED", "MEDIA_CONVERSION_UNAVAILABLE")
                QuietStateCard(
                    title=mediaError(code),
                    subtitle=stringResource(when {
                        restricted -> R.string.media_restricted_hint
                        unsupported -> R.string.media_direct_url_hint
                        else -> R.string.redesign_download_retry_hint
                    }),
                    actionLabel=if(restricted || unsupported) null else stringResource(R.string.media_analyze),
                    onAction=if(restricted || unsupported) null else ({ viewModel.analyze() }),
                    isError=true
                )
            }}

            // =========================================================================
            // CASE 1: DIRECT FILE / PDF / IMAGE / GOOGLE DRIVE LINK
            // =========================================================================
            state.direct?.let { preview ->
                item("direct-preview") {
                    Surface(
                        shape=RoundedCornerShape(24.dp),
                        color=MaterialTheme.colorScheme.surfaceContainerLow,
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
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
                            Text(state.name.ifBlank { preview.title },style=QuietTheme.typography.sectionTitle,fontWeight=FontWeight.Bold)
                            Text(
                                text=listOfNotNull(preview.mime,preview.sizeBytes?.let(::mediaBytes)).joinToString(" · ").ifBlank { stringResource(R.string.media_size_unknown) },
                                style=QuietTheme.typography.metadata,
                                color=MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        }
                    }
                }
                if (preview.kind == DirectFileKind.PDF) {
                    item("direct-pdf-options") {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.media_select_quality),style=QuietTheme.typography.sectionTitle)
                            val pdfModes = listOf(
                                "ORIGINAL" to stringResource(R.string.media_pdf_original),
                                "PDF_RECOMMENDED" to stringResource(R.string.media_pdf_recommended),
                                "PDF_EXTREME" to stringResource(R.string.media_pdf_extreme),
                                "PDF_TO_IMAGES" to stringResource(R.string.media_pdf_to_images)
                            )
                            pdfModes.forEach { (modeKey, modeLabel) ->
                                val selected = state.directMode == modeKey
                                Surface(
                                    onClick={haptics.selectionToggle();viewModel.directMode(modeKey)},
                                    shape=RoundedCornerShape(18.dp),
                                    color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border=BorderStroke(if(selected) 2.dp else 1.dp, if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    modifier=Modifier.fillMaxWidth().pressFeedback()
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        RadioButton(selected=selected,onClick=null)
                                        Text(modeLabel,style=QuietTheme.typography.bodyMedium.copy(fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium),
                                            color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                } else if (preview.kind == DirectFileKind.IMAGE) {
                    item("direct-image-options") {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.media_select_quality),style=QuietTheme.typography.sectionTitle)
                            val imgModes = listOf(
                                "ORIGINAL" to stringResource(R.string.media_img_original),
                                "IMAGE_COMPRESSED" to stringResource(R.string.media_img_compressed),
                                "IMAGE_TO_PDF" to stringResource(R.string.media_img_to_pdf)
                            )
                            imgModes.forEach { (modeKey, modeLabel) ->
                                val selected = state.directMode == modeKey
                                Surface(
                                    onClick={haptics.selectionToggle();viewModel.directMode(modeKey)},
                                    shape=RoundedCornerShape(18.dp),
                                    color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border=BorderStroke(if(selected) 2.dp else 1.dp, if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    modifier=Modifier.fillMaxWidth().pressFeedback()
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        RadioButton(selected=selected,onClick=null)
                                        Text(modeLabel,style=QuietTheme.typography.bodyMedium.copy(fontWeight=if(selected) FontWeight.Bold else FontWeight.Medium),
                                            color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                } else if (preview.kind in setOf(DirectFileKind.VIDEO, DirectFileKind.AUDIO)) {
                    item("direct-media-switch") {
                        QuietTonalButton(
                            text=stringResource(R.string.media_switch_to_extractor),
                            onClick={haptics.actionClick();viewModel.analyzeAsMediaStream()},
                            modifier=Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // =========================================================================
            // CASE 2: DIRECT VIDEO / AUDIO SOURCE (ORIGINAL FORMAT ONLY)
            // =========================================================================
            state.asset?.let { asset ->
                item("media-preview") {
                    Surface(
                        shape=RoundedCornerShape(24.dp),
                        color=MaterialTheme.colorScheme.surfaceContainerLow,
                        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            asset.thumbnailUrl?.let { thumb ->
                                AsyncImage(
                                    model=thumb,
                                    contentDescription=asset.title,
                                    modifier=Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(18.dp)),
                                    contentScale=ContentScale.Crop
                                )
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
                                    Text(up,style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                                }
                            }
                            Text(state.name.ifBlank { asset.title },style=QuietTheme.typography.sectionTitle,fontWeight=FontWeight.Bold,maxLines=3,overflow=TextOverflow.Ellipsis)

                        }
                    }
                }

                // 1. Download Type Selector (Video vs Audio Only) — Visible directly!
                item("media-type-selector") {
                    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.media_select_type),style=QuietTheme.typography.sectionTitle)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
                            val hasVideo = asset.formats.any { it.hasVideo }
                            if (hasVideo) {
                                ModeSelectionCard(
                                    title=stringResource(if (asset.formats.any { it.hasAudio })
                                        R.string.media_video else R.string.media_video_only),
                                    icon=Icons.Rounded.Videocam,
                                    selected=state.mode==MediaMode.VIDEO,
                                    onClick={haptics.selectionToggle();viewModel.mode(MediaMode.VIDEO)},
                                    modifier=Modifier.weight(1f)
                                )
                            }
                            if (asset.formats.any { it.hasAudio }) {
                                ModeSelectionCard(
                                    title=stringResource(R.string.media_audio_only),
                                    icon=Icons.Rounded.Audiotrack,
                                    selected=state.mode==MediaMode.AUDIO,
                                    onClick={haptics.selectionToggle();viewModel.mode(MediaMode.AUDIO)},
                                    modifier=Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 2. If Audio Mode is active: Audio Format (MP3, M4A, OPUS, ORIGINAL) + Bitrate (320..64 kbps)
                if (state.mode == MediaMode.AUDIO) {
                    item("audio-format-controls") {
                        Surface(
                            shape=RoundedCornerShape(22.dp),
                            color=MaterialTheme.colorScheme.surfaceContainerLow,
                            border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.media_audio_format),style=QuietTheme.typography.metadataStrong)
                                val originalAudioLabel = stringResource(R.string.media_original_audio)
                                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    val audioFormats = listOf(AudioOutput.ORIGINAL to originalAudioLabel)
                                    items(audioFormats, key={it.first.name}) { (fmt, label) ->
                                        FilterChip(
                                            selected=state.audio==fmt,
                                            onClick={haptics.selectionToggle();viewModel.audio(fmt)},
                                            label={Text(label,style=QuietTheme.typography.metadataStrong)}
                                        )
                                    }
                                }
                                if (state.audio != AudioOutput.ORIGINAL) {
                                    Text(stringResource(R.string.media_encoding_bitrate),style=QuietTheme.typography.metadataStrong)
                                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                        items(listOf(320, 256, 192, 128, 96, 64), key={it}) { kbps ->
                                            FilterChip(
                                                selected=state.bitrate==kbps,
                                                onClick={haptics.selectionToggle();viewModel.bitrate(kbps)},
                                                label={Text("$kbps kbps",style=QuietTheme.typography.metadataStrong)}
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Quality & Resolution Grid — Visible directly!
                item("media-quality-header") {
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.media_select_quality),style=QuietTheme.typography.sectionTitle)
                        Text(stringResource(R.string.media_actual_qualities),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                items(state.choices.chunked(2), key={row -> row.joinToString("-") { it.key }}) { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.fillMaxWidth()) {
                        row.forEach { choice ->
                            val selected = state.selectedKey == choice.key
                            val title = MediaCatalog.qualityTitle(choice)
                            val estBytes = MediaCatalog.displayBytes(choice, asset.durationSeconds)
                            val sizeLabel = estBytes?.let { (if(choice.sizeIsApproximate || choice.estimatedBytes == null) "~" else "") + mediaBytes(it) }
                                ?: stringResource(R.string.media_size_unknown)
                            val containerLabel = if (state.mode == MediaMode.AUDIO && state.audio != AudioOutput.ORIGINAL) {
                                state.audio.name
                            } else {
                                choice.container.uppercase(Locale.ROOT)
                            }
                            val subBadge = if (choice.mode == MediaMode.VIDEO) {
                                val audioLabel = if (asset.extractor in setOf("DirectMedia", "Html5"))
                                    R.string.media_audio_not_probed else if (choice.expectsAudio)
                                    R.string.media_with_audio else R.string.media_without_audio
                                "$containerLabel · ${stringResource(audioLabel)}"
                            } else {
                                containerLabel
                            }
                            Surface(
                                onClick={haptics.selectionToggle();viewModel.select(choice.key)},
                                shape=RoundedCornerShape(20.dp),
                                color=if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                border=BorderStroke(if(selected) 2.dp else 1.dp, if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier=Modifier.weight(1f).height(96.dp).pressFeedback()
                            ) {
                                Column(
                                    modifier=Modifier.fillMaxSize().padding(14.dp),
                                    verticalArrangement=Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier=Modifier.fillMaxWidth(),
                                        verticalAlignment=Alignment.CenterVertically,
                                        horizontalArrangement=Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text=title,
                                            style=QuietTheme.typography.bodyMedium.copy(fontWeight=FontWeight.Bold),
                                            color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines=1,
                                            overflow=TextOverflow.Ellipsis
                                        )
                                        if(selected) {
                                            Icon(Icons.Rounded.CheckCircle,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(18.dp))
                                        }
                                    }
                                    Row(
                                        modifier=Modifier.fillMaxWidth(),
                                        verticalAlignment=Alignment.CenterVertically,
                                        horizontalArrangement=Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text=subBadge,
                                            style=QuietTheme.typography.badge,
                                            color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text=sizeLabel,
                                            style=QuietTheme.typography.metadata,
                                            color=if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines=1
                                        )
                                    }
                                }
                            }
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

            item("advanced") {
                TextButton(onClick={advanced=!advanced}) {
                    Icon(Icons.Rounded.Tune,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_advanced),style=QuietTheme.typography.metadataStrong)
                }
            }
            if(advanced) {
                if (state.asset != null || state.direct != null) item("rename") {
                    OutlinedTextField(value=state.name,onValueChange=viewModel::name,
                        singleLine=true,label={Text(stringResource(R.string.media_filename))},
                        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp))
                }
                item("network-toggles") {
                    Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text(stringResource(R.string.media_wifi),Modifier.weight(1f))
                                Switch(checked=state.wifiOnly,onCheckedChange=viewModel::wifi)
                            }
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text(stringResource(R.string.media_unmetered),Modifier.weight(1f))
                                Switch(checked=state.unmetered,onCheckedChange=viewModel::unmetered)
                            }
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text(stringResource(R.string.media_http),Modifier.weight(1f))
                                Switch(checked=state.allowHttp,onCheckedChange=viewModel::allowHttp)
                            }
                        }
                    }
                }
            }
        }
        Surface(tonalElevation=3.dp,shadowElevation=7.dp) {
            Button(
                onClick={haptics.toolExecute();viewModel.submit(onQueued)},
                enabled=!state.analyzing && !state.submitting && (state.direct!=null || state.selectedKey!=null),
                modifier=Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp).pressFeedback().heightIn(min=56.dp),
                shape=RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Download,null);Spacer(Modifier.width(8.dp))
                Text(
                    text=when {
                        state.submitting -> stringResource(R.string.media_queueing)
                        state.asset != null -> stringResource(R.string.media_download_selected)
                        else -> stringResource(R.string.redesign_download_now)
                    },
                    style=QuietTheme.typography.button
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
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(64.dp).pressFeedback()
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
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
