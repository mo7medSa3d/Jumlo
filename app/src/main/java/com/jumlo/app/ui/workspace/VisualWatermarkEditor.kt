package com.jumlo.app.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * Direct-manipulation PDF watermark studio. All editor values serialize to the
 * existing workspace draft and are consumed by PdfOperationHandler.
 *
 * The bitmap is a real preview of the imported document; no fake PDF assets.
 * Changing controls NEVER changes the source bytes until Apply is selected.
 */
@Composable
internal fun VisualWatermarkEditor(
    vm: AppViewModel,
    sourceId: String,
    draft: WorkspaceDraft,
    edit: ((WorkspaceDraft) -> WorkspaceDraft) -> Unit
) {
    val haptics = rememberAppHaptics()
    var page by rememberSaveable(sourceId) { mutableIntStateOf(1) }
    var count by remember(sourceId) { mutableIntStateOf(1) }
    var frame by remember(sourceId,page) { mutableStateOf<PreviewFrame?>(null) }
    var error by remember(sourceId,page) { mutableStateOf(false) }
    LaunchedEffect(sourceId,page) {
        frame = null
        error = false
        try {
            val loaded = vm.preview(sourceId,page)
            frame = loaded
            count = loaded.count.coerceAtLeast(1)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            error = true
        }
    }
    DisposableEffect(frame) {
        val bitmap = frame?.bitmap
        onDispose { bitmap?.takeUnless { it.isRecycled }?.recycle() }
    }

    val tiled = draft.text2 == "TILE"
    val colorHex = draft.text3.takeIf { Regex("^#[0-9A-Fa-f]{6}$").matches(it) } ?: "#64748B"
    val watermarkColor = Color(android.graphics.Color.parseColor(colorHex))
    val opacity = (draft.num4.toIntOrNull() ?: 28).coerceIn(5,100)
    val fontSize = (draft.num1.toIntOrNull() ?: 42).coerceIn(12,90)
    val rotation = (draft.text4.toIntOrNull() ?: -35).coerceIn(-180,180)
    var centerX by remember(sourceId) { mutableFloatStateOf((draft.num2.toFloatOrNull() ?: .5f).coerceIn(0f,1f)) }
    var centerY by remember(sourceId) { mutableFloatStateOf((draft.num3.toFloatOrNull() ?: .5f).coerceIn(0f,1f)) }
    LaunchedEffect(draft.num2,draft.num3) {
        centerX = (draft.num2.toFloatOrNull() ?: .5f).coerceIn(0f,1f)
        centerY = (draft.num3.toFloatOrNull() ?: .5f).coerceIn(0f,1f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.watermark_title), style = QuietTheme.typography.sectionTitle)
        Text(stringResource(R.string.watermark_help), style = QuietTheme.typography.metadata,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val currentFrame = frame
                if (currentFrame != null && !currentFrame.bitmap.isRecycled) {
                    val aspect = currentFrame.bitmap.width.toFloat() /
                        currentFrame.bitmap.height.coerceAtLeast(1)
                    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment = Alignment.Center) {
                        val canvasWidth = minOf(maxWidth, 440.dp, (460.dp * aspect).coerceAtLeast(80.dp))
                        val canvasHeight = canvasWidth / aspect
                        val density = LocalDensity.current
                        val canvasWidthPx = with(density) { canvasWidth.toPx() }
                        val canvasHeightPx = with(density) { canvasHeight.toPx() }
                        val labelWidth = canvasWidth * (if(tiled) .38f else .78f)
                        val labelHeight = 55.dp
                        val previewText = draft.text1.trim()
                        Box(Modifier.width(canvasWidth).height(canvasHeight)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)) {
                            Image(currentFrame.bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.watermark_pdf_preview),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds)
                            if (previewText.isNotEmpty()) {
                                val positions = if(tiled) {
                                    listOf(.18f,.5f,.82f).flatMap { y ->
                                        listOf(.18f,.5f,.82f).map { x -> x to y }
                                    }
                                } else listOf(centerX to centerY)
                                positions.forEachIndexed { index,(x,y) ->
                                    Box(
                                        modifier = Modifier
                                            .absoluteOffset(
                                                x = canvasWidth*x-labelWidth/2f,
                                                y = canvasHeight*y-labelHeight/2f)
                                            .size(labelWidth,labelHeight)
                                            .graphicsLayer {
                                                rotationZ = rotation.toFloat()
                                                alpha = opacity / 100f
                                            }
                                            .then(
                                                if(!tiled && index==0) {
                                                    Modifier.pointerInput(
                                                        sourceId,canvasWidthPx,canvasHeightPx
                                                    ) {
                                                        detectDragGestures(onDragEnd = {
                                                            haptics.selectionToggle()
                                                        }) { change, delta ->
                                                            change.consume()
                                                            val nx = (centerX + delta.x /
                                                                canvasWidthPx).coerceIn(.08f,.92f)
                                                            val ny = (centerY + delta.y /
                                                                canvasHeightPx).coerceIn(.08f,.92f)
                                                            centerX = nx
                                                            centerY = ny
                                                            edit { it.copy(num2=nx.toString(),
                                                                num3=ny.toString()) }
                                                        }
                                                    }
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(previewText,
                                            color = watermarkColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (fontSize * (if(tiled) .26f else .48f)).sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page = (page-1).coerceAtLeast(1) },
                            enabled = page > 1) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateBefore,
                                stringResource(R.string.watermark_previous_page))
                        }
                        Text(stringResource(R.string.watermark_page_counter,page,count),
                            style = QuietTheme.typography.metadataStrong)
                        IconButton(onClick = { page = (page+1).coerceAtMost(count) },
                            enabled = page < count) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateNext,
                                stringResource(R.string.watermark_next_page))
                        }
                    }
                } else if(error) {
                    Text(stringResource(R.string.watermark_preview_error),
                        color = MaterialTheme.colorScheme.error,
                        style = QuietTheme.typography.metadata)
                } else {
                    CircularProgressIndicator(Modifier.padding(24.dp))
                }
            }
        }

        OutlinedTextField(
            value = draft.text1,
            onValueChange = { value -> edit { it.copy(text1=value.take(120)) } },
            label = { Text(stringResource(R.string.watermark_text)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                R.string.watermark_preset_confidential,
                R.string.watermark_preset_draft,
                R.string.watermark_preset_copy
            ).forEach { nameId ->
                val label = stringResource(nameId)
                FilterChip(selected = draft.text1 == label,
                    onClick = { edit { it.copy(text1=label) }; haptics.selectionToggle() },
                    label = { Text(label, maxLines = 1) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !tiled,
                onClick = { edit { it.copy(text2="SINGLE") }; haptics.selectionToggle() },
                label = { Text(stringResource(R.string.watermark_single)) })
            FilterChip(selected = tiled,
                onClick = { edit { it.copy(text2="TILE") }; haptics.selectionToggle() },
                label = { Text(stringResource(R.string.watermark_tile)) })
        }
        Text(stringResource(R.string.watermark_colors),style = QuietTheme.typography.metadataStrong)
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween) {
            val swatches = listOf(
                "#64748B" to R.string.watermark_color_slate,
                "#0F766E" to R.string.watermark_color_teal,
                "#2563EB" to R.string.watermark_color_blue,
                "#EA580C" to R.string.watermark_color_orange,
                "#DC2626" to R.string.watermark_color_red,
                "#111827" to R.string.watermark_color_dark
            )
            swatches.forEach { (hex,labelId) ->
                val selected = colorHex.equals(hex,ignoreCase=true)
                val colorLabel = stringResource(labelId)
                Surface(
                    onClick = {
                        edit { it.copy(text3=hex) }
                        haptics.selectionToggle()
                    },
                    modifier = Modifier.size(42.dp)
                        .semantics { contentDescription = colorLabel },
                    shape = CircleShape,
                    color = Color(android.graphics.Color.parseColor(hex)),
                    border = BorderStroke(if(selected) 3.dp else 1.dp,
                        if(selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if(selected) Icon(Icons.Rounded.Check,null,
                            tint=Color.White, modifier=Modifier.size(19.dp))
                    }
                }
            }
        }
        WatermarkSlider(
            stringResource(R.string.watermark_opacity),
            opacity.toFloat(),5f,100f,
            onChange = { value -> edit { it.copy(num4=value.roundToInt().toString()) } },
            onFinished = { haptics.selectionToggle() }
        )
        WatermarkSlider(
            stringResource(R.string.watermark_size),
            fontSize.toFloat(),12f,90f,
            onChange = { value -> edit { it.copy(num1=value.roundToInt().toString()) } },
            onFinished = { haptics.selectionToggle() }
        )
        WatermarkSlider(
            stringResource(R.string.watermark_rotation),
            rotation.toFloat(),-90f,90f,
            onChange = { value -> edit { it.copy(text4=value.roundToInt().toString()) } },
            onFinished = { haptics.selectionToggle() }
        )
        Text(stringResource(R.string.watermark_page_scope),style=QuietTheme.typography.metadataStrong)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = draft.pages.equals("all",ignoreCase=true),
                onClick = { edit { it.copy(pages="all") } },
                label = { Text(stringResource(R.string.watermark_all_pages)) })
            FilterChip(selected = draft.pages == page.toString(),
                onClick = { edit { it.copy(pages=page.toString()) } },
                label = { Text(stringResource(R.string.watermark_this_page)) })
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.watermark_page_numbers),
                modifier = Modifier.weight(1f), style = QuietTheme.typography.metadataStrong)
            Switch(checked = draft.flag1,
                onCheckedChange = { enabled ->
                    edit { it.copy(flag1=enabled) }
                    haptics.selectionToggle()
                })
        }
    }
}

@Composable
private fun WatermarkSlider(
    label: String,
    current: Float,
    minimum: Float,
    maximum: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Text(label,style=QuietTheme.typography.metadataStrong)
    Slider(value = current.coerceIn(minimum,maximum),
        valueRange = minimum..maximum,
        onValueChange = onChange,
        onValueChangeFinished = onFinished)
}
