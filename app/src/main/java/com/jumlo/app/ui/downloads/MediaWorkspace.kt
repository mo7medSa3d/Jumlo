package com.jumlo.app.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jumlo.app.R
import com.jumlo.app.core.media.*
import com.jumlo.app.ui.components.*
import com.jumlo.app.ui.util.rememberAppHaptics
import java.util.Locale

@Composable
fun MediaWorkspace(
    initialUrl: String,
    onQueued: (String) -> Unit,
    onDirect: (String) -> Unit,
    viewModel: MediaViewModel = hiltViewModel()
) {
    val haptics = rememberAppHaptics()
    val s by viewModel.state.collectAsStateWithLifecycle()
    var browser by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            viewModel.acceptSharedUrl(initialUrl)
            if (s.termsAccepted) viewModel.analyze()
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.cancelAnalysis() } }

    if (browser) {
        MediaBrowser(
            initialUrl = s.url,
            onClose = { browser = false },
            onMedia = { value -> browser = false; viewModel.editUrl(value); viewModel.analyze() },
            onFile = { value -> browser = false; onDirect(value) }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reassuring privacy header card
            item(key = "media-privacy-banner") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.media_intro),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Streamlined URL Input Box with 1-Tap Paste & 1-Tap Analyze
            item(key = "media-input-card") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = s.url,
                            onValueChange = viewModel::editUrl,
                            enabled = !s.submitting,
                            label = { Text(stringResource(R.string.media_link)) },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        clipboardManager.getText()?.text?.let { text ->
                                            if (text.isNotBlank()) {
                                                viewModel.editUrl(text)
                                                viewModel.analyze()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = stringResource(R.string.action_paste),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    haptics.toolExecute()
                                    viewModel.analyze()
                                },
                                enabled = s.url.isNotBlank() && !s.analyzing && !s.submitting,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.media_analyze))
                            }

                            FilledTonalButton(
                                onClick = {
                                    haptics.actionClick()
                                    viewModel.cancelAnalysis()
                                    browser = true
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(stringResource(R.string.media_browser))
                            }

                            if (s.analyzing) {
                                OutlinedButton(
                                    onClick = {
                                        haptics.secondaryAction()
                                        viewModel.cancelAnalysis()
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(stringResource(R.string.media_cancel))
                                }
                            }
                        }
                    }
                }
            }

            // Progress indicator while analyzing
            if (s.analyzing) {
                item(key = "media-analyzing") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.media_analyzing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Error display if any
            s.error?.let { code ->
                item(key = "media-error") {
                    QuietStateCard(
                        title = mediaError(code),
                        subtitle = code,
                        isError = true
                    )
                }
            }

            // Mode Selector: Video vs Audio
            item(key = "media-mode") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = s.mode == MediaMode.VIDEO,
                        onClick = { viewModel.mode(MediaMode.VIDEO) },
                        label = { Text(stringResource(R.string.media_video)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(14.dp),
                        enabled = !s.submitting,
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = s.mode == MediaMode.AUDIO,
                        onClick = { viewModel.mode(MediaMode.AUDIO) },
                        label = { Text(stringResource(R.string.media_audio_only)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(14.dp),
                        enabled = !s.submitting,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Analyzed Media Asset Card
            s.asset?.let { asset ->
                item(key = "media-asset") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            asset.thumbnailUrl?.let { thumb ->
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = asset.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                            Text(
                                text = asset.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOfNotNull(asset.extractor, asset.uploader).joinToString(" • ").takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                asset.durationSeconds?.let {
                                    Text("• ${it.toInt()}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // Header for qualities
                item(key = "media-qualities-header") {
                    Text(
                        text = stringResource(R.string.media_actual_qualities),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Beautiful Quality Choice Cards
                items(s.choices, key = { it.key }) { choice ->
                    val isSelected = choice.key == s.selectedKey
                    val video = choice.formats.firstOrNull { it.hasVideo }
                    val audio = choice.formats.firstOrNull { it.hasAudio }
                    val quality = if (s.mode == MediaMode.VIDEO) {
                        video?.height?.let { "${it}p" } ?: stringResource(R.string.media_resolution_unknown)
                    } else {
                        audio?.audioBitrateKbps?.let { "${it.toInt()} kb/s" } ?: stringResource(R.string.media_bitrate_unknown)
                    }
                    val size = choice.estimatedBytes?.let(::mediaBytes) ?: stringResource(R.string.media_size_unknown)

                    Surface(
                        onClick = { if (!s.submitting) viewModel.select(choice.key) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        tonalElevation = if (isSelected) 3.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    haptics.selectionToggle()
                                    viewModel.select(choice.key)
                                },
                                enabled = !s.submitting
                            )
                            Column(Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = quality,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = if (choice.sizeIsApproximate) stringResource(R.string.media_approximate, size) else size,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = choice.container.uppercase(Locale.ROOT) + if (video?.fps != null) " • ${video.fps} fps" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (s.choices.isEmpty()) {
                    item(key = "media-no-choices") {
                        QuietStateCard(
                            title = stringResource(R.string.media_no_choices),
                            subtitle = stringResource(R.string.media_direct_instead),
                            actionLabel = stringResource(R.string.media_direct_instead),
                            onAction = { onDirect(s.url) }
                        )
                    }
                }

                // Audio Format options (only shown when Audio mode is selected)
                if (s.mode == MediaMode.AUDIO) {
                    item(key = "media-audio-fmt") {
                        Text(stringResource(R.string.media_audio_format), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AudioOutput.entries, key = { it.name }) { output ->
                                FilterChip(
                                    selected = s.audio == output,
                                    onClick = { viewModel.audio(output) },
                                    label = { Text(if (output == AudioOutput.ORIGINAL) stringResource(R.string.media_original_audio) else output.name) },
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !s.submitting
                                )
                            }
                        }
                    }
                }
            }
        }

        // Every first download requires explicit rights/terms consent; never pre-check this.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = s.termsAccepted,
                    onCheckedChange = viewModel::acceptTerms,
                    enabled = !s.submitting
                )
                Text(
                    text = stringResource(R.string.media_rights_confirmation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Prominent Sticky Bottom Bar with Direct Download Button
        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        haptics.toolExecute()
                        viewModel.submit(onQueued)
                    },
                    enabled = s.termsAccepted && s.selectedKey != null && !s.analyzing && !s.submitting,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (s.submitting) R.string.media_queueing else R.string.media_download_selected),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

private fun mediaBytes(value: Long): String = when {
    value >= 1073741824L -> String.format(Locale.ROOT, "%.2f GiB", value / 1073741824.0)
    value >= 1048576 -> String.format(Locale.ROOT, "%.1f MiB", value / 1048576.0)
    else -> "$value B"
}

@Composable
private fun mediaError(code: String): String = stringResource(when (code) {
    "MEDIA_SITE_UNSUPPORTED", "UNSUPPORTED" -> R.string.media_error_site
    "MEDIA_PRIVATE_CONTENT", "PRIVATE_CONTENT" -> R.string.media_error_private
    "MEDIA_GEO_BLOCKED" -> R.string.media_error_geoblocked
    "MEDIA_REMOVED", "REMOVED" -> R.string.media_error_removed
    "MEDIA_ENCRYPTED_SOURCE" -> R.string.media_error_drm
    "MEDIA_SESSION_REQUIRED", "MEDIA_SOURCE_REJECTED", "LOGIN_REQUIRED" -> R.string.media_error_session
    "MEDIA_RATE_LIMITED", "RATE_LIMITED" -> R.string.media_error_rate
    "NETWORK_UNAVAILABLE", "MEDIA_NETWORK_ERROR", "MEDIA_TLS_FAILED", "NETWORK_ERROR" -> R.string.media_error_network
    "MEDIA_NO_USABLE_FORMAT", "MEDIA_REANALYSIS_REQUIRED" -> R.string.media_error_format
    "INVALID_URL", "MULTIPLE_MEDIA_LINKS" -> R.string.media_error_url
    "MEDIA_TERMS_REQUIRED" -> R.string.media_rights_required
    "MEDIA_BUILD_NOT_ENABLED", "MEDIA_NATIVE_ABI_UNAVAILABLE", "MEDIA_RUNTIME_INITIALIZATION_FAILED" -> R.string.media_error_runtime
    else -> R.string.media_error_generic
})
