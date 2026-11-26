package com.jumlo.app.ui.viewer

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jumlo.app.R
import com.jumlo.app.data.office.DocumentViewerKind
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.QuietTonalButton
import com.jumlo.app.ui.components.formatBytes
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowBack
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.zip.ZipInputStream

private data class ZipPreviewEntry(val name: String, val sizeBytes: Long, val isDirectory: Boolean)

private fun formatDurationMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.ROOT, "%02d:%02d", min, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAndArchiveViewerScreen(
    uri: String,
    title: String,
    kind: DocumentViewerKind,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onImagesToPdf: (() -> Unit)? = null,
    onEditImage: (() -> Unit)? = null,
    onExtractZip: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = QuietTheme.typography.sectionTitle.copy(textDirection = TextDirection.Content)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.scan_back))
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.action_share))
                    }
                    TextButton(onClick = onOpenExternal) {
                        Text(stringResource(R.string.action_open))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (kind) {
                DocumentViewerKind.IMAGE -> ImageInAppViewer(
                    uri = uri,
                    title = title,
                    onImagesToPdf = onImagesToPdf,
                    onEditImage = onEditImage
                )
                DocumentViewerKind.AUDIO -> AudioInAppPlayer(uri = uri, title = title)
                DocumentViewerKind.VIDEO -> VideoInAppPlayer(uri = uri)
                DocumentViewerKind.ARCHIVE -> ZipArchiveInspector(uri = uri, onExtractZip = onExtractZip)
                else -> Unit
            }
        }
    }
}

@Composable
private fun ImageInAppViewer(
    uri: String,
    title: String,
    onImagesToPdf: (() -> Unit)?,
    onEditImage: (() -> Unit)?
) {
    val context = LocalContext.current
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clipToBounds()
                .pointerInput(uri) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
                .pointerInput(uri) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = nextScale
                        offset = if (nextScale <= 1.02f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(Uri.parse(uri)).crossfade(true).build(),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
        if (onImagesToPdf != null || onEditImage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onEditImage != null) {
                    QuietTonalButton(
                        text = stringResource(R.string.workspace_action_compress_images),
                        onClick = onEditImage,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (onImagesToPdf != null) {
                    QuietPrimaryButton(
                        text = stringResource(R.string.ui_create_pdf),
                        onClick = onImagesToPdf,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioInAppPlayer(uri: String, title: String) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var playing by remember(uri) { mutableStateOf(false) }
    var ready by remember(uri) { mutableStateOf(false) }
    var failed by remember(uri) { mutableStateOf(false) }
    var durationMs by remember(uri) { mutableIntStateOf(0) }
    var positionMs by remember(uri) { mutableIntStateOf(0) }
    val playerState = remember(uri) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(uri) {
        val mp = MediaPlayer()
        playerState.value = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(context, Uri.parse(uri))
            mp.setOnPreparedListener {
                durationMs = it.duration.coerceAtLeast(0)
                ready = true
            }
            mp.setOnCompletionListener {
                playing = false
                positionMs = 0
                runCatching { it.seekTo(0) }
            }
            mp.setOnErrorListener { _, _, _ ->
                failed = true
                playing = false
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            failed = true
        }
        onDispose {
            playerState.value = null
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
    }

    LaunchedEffect(playing, ready) {
        while (playing && ready && isActive) {
            playerState.value?.let { mp ->
                runCatching { positionMs = mp.currentPosition.coerceAtLeast(0) }
            }
            delay(250L)
        }
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = QuietTheme.shapes.heroCard,
            color = QuietTheme.pillars.cardSurface,
            border = BorderStroke(1.dp, QuietTheme.pillars.cardBorder),
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.primaryContainer,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AudioFile, null, tint = colors.primary, modifier = Modifier.size(36.dp))
                    }
                }
                Text(
                    text = title,
                    style = QuietTheme.typography.sectionTitle.copy(textDirection = TextDirection.Content),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (failed) {
                    Text(stringResource(R.string.msg_preview_unavailable), color = colors.error, style = QuietTheme.typography.metadata)
                } else {
                    val maxDur = durationMs.coerceAtLeast(1)
                    Slider(
                        value = positionMs.coerceIn(0, maxDur).toFloat(),
                        onValueChange = { pos ->
                            positionMs = pos.toInt()
                            playerState.value?.let { runCatching { it.seekTo(positionMs) } }
                        },
                        valueRange = 0f..maxDur.toFloat(),
                        enabled = ready,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDurationMs(positionMs), style = QuietTheme.typography.metadata, color = colors.onSurfaceVariant)
                        Text(formatDurationMs(durationMs), style = QuietTheme.typography.metadata, color = colors.onSurfaceVariant)
                    }
                    Surface(
                        onClick = {
                            val mp = playerState.value ?: return@Surface
                            if (!ready) return@Surface
                            runCatching {
                                if (mp.isPlaying) {
                                    mp.pause()
                                    playing = false
                                } else {
                                    mp.start()
                                    playing = true
                                }
                            }
                        },
                        shape = CircleShape,
                        color = colors.primary,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = colors.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoInAppPlayer(uri: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(Uri.parse(uri))
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ZipArchiveInspector(uri: String, onExtractZip: (() -> Unit)?) {
    val context = LocalContext.current
    val entriesState by produceState<Result<List<ZipPreviewEntry>>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val list = mutableListOf<ZipPreviewEntry>()
                val input = context.contentResolver.openInputStream(Uri.parse(uri))
                    ?: error("ARCHIVE_UNREADABLE")
                input.use { stream ->
                    ZipInputStream(stream).use { zip ->
                        while (list.size < 1000) {
                            val entry = zip.nextEntry ?: break
                            list += ZipPreviewEntry(entry.name, entry.size.coerceAtLeast(0L), entry.isDirectory)
                            zip.closeEntry()
                        }
                    }
                }
                list
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        when (val state = entriesState) {
            null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> if (state.isFailure) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_preview_unavailable), color = MaterialTheme.colorScheme.error)
                }
            } else {
                val itemsList = state.getOrDefault(emptyList())
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(itemsList) { entry ->
                        Surface(
                            shape = QuietTheme.shapes.card,
                            color = QuietTheme.pillars.cardSurface,
                            border = BorderStroke(1.dp, QuietTheme.pillars.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = entry.name,
                                    style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, textDirection = TextDirection.Content),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!entry.isDirectory && entry.sizeBytes > 0L) {
                                    Text(
                                        text = formatBytes(entry.sizeBytes),
                                        style = QuietTheme.typography.metadata,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (onExtractZip != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                QuietPrimaryButton(
                    text = stringResource(R.string.workspace_action_extract_zip),
                    onClick = onExtractZip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
