package com.jumlo.app.ui.workspace

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.SourceRef
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.theme.LocalReducedMotion
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.CancellationException

/**
 * Direct-manipulation iOS-style source order strip: long-press and drag any card
 * directly to reorder inputs without cluttered arrow buttons.
 */
@Composable
internal fun SourceOrderStrip(
    vm: AppViewModel,
    sources: List<SourceRef>,
    selectedId: String?,
    logoId: String?,
    onSelect: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    renamed: List<String>? = null
) {
    val latestSources by rememberUpdatedState(sources)
    val latestMove by rememberUpdatedState(onMove)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = rememberAppHaptics()
    val reduced = LocalReducedMotion.current
    val sourceIds = remember(sources) { sources.map { it.id } }
    val pageCount by produceState<Result<Int>?>(null, sourceIds) {
        value = null
        if (sources.isNotEmpty() && sources.all { it.mimeType == "application/pdf" }) {
            value = try {
                Result.success(sources.sumOf { source ->
                    val thumbnail = vm.thumbnail(source.id, 1)
                    try { thumbnail.count } finally { thumbnail.bitmap.recycle() }
                })
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { Result.failure(error) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.interactive_input_order), style = QuietTheme.typography.sectionTitle, modifier = Modifier.semantics { heading() })
            if (sources.isNotEmpty() && sources.all { it.mimeType == "application/pdf" }) {
                when {
                    pageCount == null -> Text(stringResource(R.string.studio_merge_counting), style = QuietTheme.typography.metadata)
                    pageCount?.isSuccess == true -> Text(stringResource(R.string.studio_merge_pages, sources.size, pageCount!!.getOrThrow()), style = QuietTheme.typography.metadataStrong, color = MaterialTheme.colorScheme.primary)
                    else -> Text(stringResource(R.string.msg_preview_unavailable), style = QuietTheme.typography.metadata, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(stringResource(R.string.interactive_input_order_help), style = QuietTheme.typography.metadata, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(sources, key = { _, source -> source.id }, contentType = { _, _ -> "source-card" }) { index, source ->
                val description = stringResource(R.string.interactive_source_position, index + 1, sources.size, source.displayName)
                val earlierLabel = stringResource(R.string.jumlo_move_earlier)
                val laterLabel = stringResource(R.string.jumlo_move_later)
                val frame by produceState<PreviewFrame?>(null, source.id) {
                    value = null
                    if (source.mimeType == "application/pdf" || source.mimeType?.startsWith("image/") == true) {
                        value = try { vm.thumbnail(source.id, 1) }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { null }
                    }
                }
                DisposeOwnedPreviewBitmap(frame?.bitmap)
                var displacement by remember(source.id) { mutableFloatStateOf(0f) }
                var dragging by remember(source.id) { mutableStateOf(false) }
                val scaleState = animateFloatAsState(
                    targetValue = if (dragging && !reduced) 1.05f else 1f,
                    animationSpec = if (reduced) snap() else spring(dampingRatio = 0.72f, stiffness = 420f),
                    label = "source-drag-scale"
                )

                Surface(
                    onClick = { onSelect(source.id) },
                    shape = QuietTheme.shapes.inputField,
                    color = if (selectedId == source.id) MaterialTheme.colorScheme.primaryContainer else QuietTheme.pillars.cardSurface,
                    shadowElevation = if (dragging) 8.dp else 0.dp,
                    modifier = Modifier
                        .width(136.dp)
                        .animateItem()
                        .graphicsLayer {
                            translationX = displacement
                            val s = scaleState.value
                            scaleX = s
                            scaleY = s
                        }
                        .pointerInput(source.id, rtl) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = true
                                    haptics.dragStart()
                                },
                                onDragEnd = {
                                    dragging = false
                                    displacement = 0f
                                    haptics.dragEnd()
                                },
                                onDragCancel = {
                                    dragging = false
                                    displacement = 0f
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    displacement += delta.x
                                    val steps = (displacement / 146.dp.toPx()).toInt()
                                    if (steps != 0) {
                                        val current = latestSources.indexOfFirst { it.id == source.id }
                                        if (current >= 0) {
                                            latestMove(current, if (rtl) -steps else steps)
                                            haptics.snap()
                                        }
                                        displacement -= steps * 146.dp.toPx()
                                    }
                                }
                            )
                        }
                        .semantics {
                            contentDescription = description
                            selected = selectedId == source.id
                            customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction(earlierLabel) { latestMove(index, -1); true })
                                if (index < sources.lastIndex) add(CustomAccessibilityAction(laterLabel) { latestMove(index, 1); true })
                            }
                        }
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
                            val image = frame
                            if (image != null && !image.bitmap.isRecycled) {
                                val imageBitmap = remember(image.bitmap) { image.bitmap.asImageBitmap() }
                                Image(imageBitmap, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            } else {
                                Icon(Icons.Rounded.Description, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = QuietTheme.typography.badge,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            source.displayName,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = QuietTheme.typography.metadata.copy(textDirection = TextDirection.Content)
                        )
                        renamed?.getOrNull(index)?.let { name ->
                            Text(name, style = QuietTheme.typography.metadataStrong.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.primary)
                        }
                        if (source.id == logoId) Text(stringResource(R.string.interactive_logo_source), style = QuietTheme.typography.metadataStrong)
                    }
                }
            }
        }
    }
}
