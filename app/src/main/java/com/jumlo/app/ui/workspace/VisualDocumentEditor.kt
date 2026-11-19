package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietShimmerSkeleton
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.RotateRight
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * Direct-manipulation crop editor for images (`img_03`) and PDF pages (`pdf_crop`).
 * Uses actual Android-rendered PDF/image frames. Coordinates are validated normalized
 * top-left fractions consumed by the image crop and PDF crop engines.
 *
 * Strictly UI/gesture logic: the underlying document remains unchanged until
 * the user explicitly runs the processing job in WorkspaceScreen.
 */
private fun WorkspaceDraft.visualRect(): VisualRect {
    fun read(raw: String, fallback: Float): Float =
        WorkspaceDraft.normalizeDigits(raw).toFloatOrNull()?.takeIf { it.isFinite() } ?: fallback
    return VisualRect(
        read(cropLeft, VisualRect.WHOLE.left), read(cropTop, VisualRect.WHOLE.top),
        read(cropRight, VisualRect.WHOLE.right), read(cropBottom, VisualRect.WHOLE.bottom)
    ).valid()
}

private fun WorkspaceDraft.cropRectFor(page: Int, perPage: Boolean): VisualRect {
    val box = cropOverrides.firstOrNull { it.page == page }?.takeIf { perPage && toolId == "pdf_crop" }
    return if (box == null) visualRect()
    else VisualRect(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()).valid()
}

private fun Float.serialized(): String = String.format(Locale.US, "%.5f", this)

@Composable
internal fun VisualDocumentEditor(
    vm: AppViewModel,
    sourceId: String,
    draft: WorkspaceDraft,
    edit: ((WorkspaceDraft) -> WorkspaceDraft) -> Unit
) {
    val tool = draft.toolId
    val sourceIsPdf = tool == "pdf_crop"
    val haptics = rememberAppHaptics()
    var pageCount by remember(sourceId) { mutableIntStateOf(0) }
    var currentPage by rememberSaveable(sourceId, tool) {
        mutableIntStateOf(if (sourceIsPdf) (WorkspaceDraft.normalizeDigits(draft.num1).toIntOrNull() ?: 1).coerceAtLeast(1) else 1)
    }
    var preview by remember(sourceId, currentPage) { mutableStateOf<PreviewFrame?>(null) }
    var failed by remember(sourceId, currentPage) { mutableStateOf(false) }
    var retry by remember(sourceId) { mutableIntStateOf(0) }
    LaunchedEffect(sourceId, currentPage, retry) {
        preview = null
        failed = false
        try {
            val frame = vm.preview(sourceId, if (sourceIsPdf) currentPage else 1)
            preview = frame
            pageCount = frame.count.coerceAtLeast(1)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            failed = true
        }
    }
    var perPageCrop by rememberSaveable(sourceId, tool) { mutableStateOf(draft.cropOverrides.isNotEmpty()) }
    var rect by remember(sourceId, tool) { mutableStateOf(draft.cropRectFor(currentPage, perPageCrop)) }
    val historyModel: VisualDocumentEditorSession = androidx.lifecycle.viewmodel.compose.viewModel(key = "visual-document-editor-session")
    val session = remember(historyModel, sourceId, tool) { historyModel.forDocument("$tool:$sourceId") }
    var timeline by session.timeline
    val undoHistory = timeline.past
    val redoHistory = timeline.future
    var lockedAspect by rememberSaveable(sourceId, tool) { mutableFloatStateOf(0f) }
    var zoom by rememberSaveable(sourceId, tool) { mutableFloatStateOf(1f) }
    var pan by remember(sourceId, tool) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(1f, 4f)
        pan = if (zoom == 1f) Offset.Zero else pan + panChange
    }
    val currentDraft by rememberUpdatedState(draft)
    var beforeDrag by remember(sourceId, tool) { mutableStateOf<VisualRect?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var snapGuide by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(
        draft.cropLeft, draft.cropTop, draft.cropRight, draft.cropBottom,
        draft.cropOverrides, currentPage, perPageCrop, sourceId, tool
    ) {
        if (!dragging) {
            val fromDraft = draft.cropRectFor(currentPage, perPageCrop)
            if (fromDraft != rect) rect = fromDraft
        }
    }
    val latestEdit by rememberUpdatedState(edit)
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val handlePx = with(density) { 34.dp.toPx() }

    fun snapshot(box: VisualRect = rect) = VisualEditSnapshot(
        rect = box,
        turns = currentDraft.turns,
        flipX = currentDraft.flipX,
        flipY = currentDraft.flipY,
        aspect = lockedAspect,
        cropOverrides = currentDraft.cropOverrides,
        cropPage = currentPage,
        perPageCrop = perPageCrop,
        cropDefaultRect = currentDraft.visualRect()
    )

    fun recordSnapshot() {
        timeline = timeline.record(snapshot())
    }

    fun recordEdit(previous: VisualRect, updated: VisualRect) {
        if (previous != updated) timeline = timeline.record(snapshot(previous))
    }

    fun restoreSnapshot(snapshot: VisualEditSnapshot) {
        rect = snapshot.rect
        lockedAspect = snapshot.aspect
        if (tool == "pdf_crop") {
            perPageCrop = snapshot.perPageCrop
            currentPage = snapshot.cropPage
        }
        val draftRect = if (tool == "pdf_crop") snapshot.cropDefaultRect else rect
        latestEdit {
            it.copy(
                cropLeft = draftRect.left.serialized(),
                cropTop = draftRect.top.serialized(),
                cropRight = draftRect.right.serialized(),
                cropBottom = draftRect.bottom.serialized(),
                cropOverrides = if (tool == "pdf_crop") snapshot.cropOverrides else it.cropOverrides,
                turns = snapshot.turns,
                flipX = snapshot.flipX,
                flipY = snapshot.flipY
            )
        }
    }

    fun saveRect(box: VisualRect) {
        val safe = box.valid()
        latestEdit { current ->
            if (tool == "pdf_crop" && perPageCrop) {
                current.copy(
                    cropOverrides = current.cropOverrides.filterNot { it.page == currentPage } +
                        com.jumlo.app.data.processing.PdfCropOverride(
                            currentPage, safe.left.toDouble(), safe.top.toDouble(),
                            safe.right.toDouble(), safe.bottom.toDouble()
                        )
                )
            } else {
                current.copy(
                    cropLeft = safe.left.serialized(),
                    cropTop = safe.top.serialized(),
                    cropRight = safe.right.serialized(),
                    cropBottom = safe.bottom.serialized()
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.visual_edit_title),
                style = QuietTheme.typography.sectionTitle,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                val reset = VisualRect.WHOLE
                if (rect != reset || lockedAspect != 0f || draft.turns != 0 || draft.flipX || draft.flipY) recordSnapshot()
                lockedAspect = 0f
                rect = reset
                if (tool == "pdf_crop" && perPageCrop) saveRect(reset)
                else restoreSnapshot(VisualEditSnapshot(reset, 0, false, false, 0f, cropPage = currentPage, cropDefaultRect = reset))
                zoom = 1f
                pan = Offset.Zero
                haptics.selectionToggle()
            }) {
                Icon(Icons.Rounded.RestartAlt, null)
                Text(stringResource(R.string.visual_edit_reset))
            }
            IconButton(enabled = undoHistory.isNotEmpty(), onClick = {
                timeline.undo(snapshot())?.let { restored ->
                    timeline = restored.timeline
                    restoreSnapshot(restored.snapshot)
                    haptics.selectionToggle()
                }
            }) { Icon(Icons.Rounded.Undo, stringResource(R.string.visual_edit_undo)) }
            IconButton(enabled = redoHistory.isNotEmpty(), onClick = {
                timeline.redo(snapshot())?.let { restored ->
                    timeline = restored.timeline
                    restoreSnapshot(restored.snapshot)
                    haptics.selectionToggle()
                }
            }) { Icon(Icons.Rounded.Redo, stringResource(R.string.visual_edit_redo)) }
        }
        var historyOpen by rememberSaveable(sourceId, tool) { mutableStateOf(false) }
        if (undoHistory.isNotEmpty()) {
            TextButton(onClick = { historyOpen = !historyOpen }) {
                Text(stringResource(R.string.visual_edit_history, undoHistory.size))
            }
        }
        if (historyOpen && undoHistory.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                undoHistory.asReversed().forEachIndexed { index, _ ->
                    val step = undoHistory.size - index
                    TextButton(onClick = {
                        timeline.restore(undoHistory.size - index - 1)?.let { restored ->
                            timeline = restored.timeline
                            restoreSnapshot(restored.snapshot)
                        }
                        haptics.selectionToggle()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.visual_edit_history_step, step),
                            style = QuietTheme.typography.metadata
                        )
                    }
                }
            }
        }
        val frame = preview
        if (frame != null && !frame.bitmap.isRecycled) {
            val sourceAspect = frame.bitmap.width.toFloat() / frame.bitmap.height.coerceAtLeast(1)
            val rotated = tool == "img_03" && draft.turns % 2 != 0
            val aspect = if (rotated) 1f / sourceAspect else sourceAspect
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val canvasWidth = minOf(maxWidth, 450.dp, (440.dp * aspect).coerceAtLeast(75.dp))
                val canvasHeight = canvasWidth / aspect
                Box(
                    Modifier.width(canvasWidth).height(canvasHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceContainerLowest)
                        .pointerInput(sourceId, currentPage) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }) }
                        .transformable(transformState, canPan = { zoom > 1f }),
                    contentAlignment = Alignment.Center
                ) {
                    val sourceWidth = if (rotated) canvasHeight else canvasWidth
                    val sourceHeight = if (rotated) canvasWidth else canvasHeight
                    Box(
                        Modifier.requiredSize(sourceWidth, sourceHeight).graphicsLayer {
                            rotationZ = if (tool == "img_03") (draft.turns % 4) * 90f else 0f
                            scaleX = zoom * (if (tool == "img_03" && draft.flipX) -1f else 1f)
                            scaleY = zoom * (if (tool == "img_03" && draft.flipY) -1f else 1f)
                            val maxX = size.width * (zoom - 1f) / 2f
                            val maxY = size.height * (zoom - 1f) / 2f
                            translationX = pan.x.coerceIn(-maxX, maxX)
                            translationY = pan.y.coerceIn(-maxY, maxY)
                        },
                        contentAlignment = AbsoluteAlignment.TopLeft
                    ) {
                        Image(
                            frame.bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.visual_editor_title),
                            modifier = Modifier.fillMaxSize().testTag("preview.bitmap"),
                            contentScale = ContentScale.FillBounds
                        )

                        val active by rememberUpdatedState(rect)
                        var handle by remember { mutableStateOf(VisualHandle.NONE) }
                        Canvas(Modifier.fillMaxSize().pointerInput(tool, sourceId, currentPage, handlePx, draft.turns) {
                            detectDragGestures(
                                onDragStart = { tap ->
                                    if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                                    beforeDrag = active
                                    handle = active.hitHandle(
                                        tap.x / size.width, tap.y / size.height,
                                        size.width.toFloat(), size.height.toFloat(), handlePx
                                    )
                                    dragging = handle != VisualHandle.NONE
                                },
                                onDrag = { change, delta ->
                                    if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                                    if (handle != VisualHandle.NONE) {
                                        change.consume()
                                        val snapped = active.draggedWithSnap(
                                            handle, delta.x / size.width, delta.y / size.height,
                                            if (lockedAspect > 0f) sourceCropAspect(lockedAspect, if (tool == "img_03") currentDraft.turns else 0) / sourceAspect else 0f
                                        )
                                        rect = snapped.rect
                                        if (snapped.snapped) {
                                            snapGuide = Offset(snapped.guideX ?: -1f, snapped.guideY ?: -1f)
                                            haptics.snap()
                                        } else snapGuide = null
                                    }
                                },
                                onDragEnd = {
                                    if (handle != VisualHandle.NONE) {
                                        beforeDrag?.let { recordEdit(it, active) }
                                        saveRect(active)
                                        haptics.selectionToggle()
                                    }
                                    beforeDrag = null
                                    handle = VisualHandle.NONE
                                    dragging = false
                                    snapGuide = null
                                },
                                onDragCancel = {
                                    dragging = false
                                    handle = VisualHandle.NONE
                                    snapGuide = null
                                    rect = beforeDrag ?: draft.visualRect()
                                    beforeDrag = null
                                }
                            )
                        }) {
                            val x0 = rect.left * size.width
                            val y0 = rect.top * size.height
                            val x1 = rect.right * size.width
                            val y1 = rect.bottom * size.height
                            val rectSize = androidx.compose.ui.geometry.Size(x1 - x0, y1 - y0)
                            val shade = Color.Black.copy(alpha = .48f)
                            drawRect(shade, size = androidx.compose.ui.geometry.Size(size.width, y0))
                            drawRect(shade, Offset(0f, y1), androidx.compose.ui.geometry.Size(size.width, size.height - y1))
                            drawRect(shade, Offset(0f, y0), androidx.compose.ui.geometry.Size(x0, y1 - y0))
                            drawRect(shade, Offset(x1, y0), androidx.compose.ui.geometry.Size(size.width - x1, y1 - y0))
                            for (i in 1..2) {
                                val position = i / 3f
                                drawLine(
                                    Color.White.copy(alpha = .5f),
                                    Offset(x0 + (x1 - x0) * position, y0),
                                    Offset(x0 + (x1 - x0) * position, y1), 1.dp.toPx()
                                )
                                drawLine(
                                    Color.White.copy(alpha = .5f),
                                    Offset(x0, y0 + (y1 - y0) * position),
                                    Offset(x1, y0 + (y1 - y0) * position), 1.dp.toPx()
                                )
                            }
                            drawRect(colors.primary, Offset(x0, y0), rectSize, style = Stroke(3.dp.toPx()))
                            snapGuide?.let { guide ->
                                if (guide.x >= 0f) drawLine(
                                    colors.tertiary, Offset(guide.x * size.width, 0f),
                                    Offset(guide.x * size.width, size.height), 1.5.dp.toPx()
                                )
                                if (guide.y >= 0f) drawLine(
                                    colors.tertiary, Offset(0f, guide.y * size.height),
                                    Offset(size.width, guide.y * size.height), 1.5.dp.toPx()
                                )
                            }
                            listOf(
                                Offset(x0, y0), Offset(x1, y0), Offset(x0, y1), Offset(x1, y1)
                            ).forEach { corner ->
                                drawCircle(Color.White, 11.dp.toPx(), center = corner)
                                drawCircle(colors.primary, 8.dp.toPx(), center = corner)
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text(stringResource(R.string.editor_fit_preview)) }
            Text(
                stringResource(R.string.visual_edit_drag_hint),
                style = QuietTheme.typography.metadata,
                color = colors.onSurfaceVariant
            )
            if (sourceIsPdf && pageCount > 1) {
                DocumentPageControls(currentPage, pageCount) { page -> currentPage = page }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.pages == "all", border = null, onClick = {
                        latestEdit { it.copy(pages = "all") }
                    }, label = { Text(stringResource(R.string.visual_apply_all)) })
                    FilterChip(selected = draft.pages == currentPage.toString(), border = null, onClick = {
                        latestEdit { it.copy(pages = currentPage.toString()) }
                    }, label = { Text(stringResource(R.string.visual_apply_page)) })
                }
            }
            if (tool == "pdf_crop") {
                InteractiveToggle(stringResource(R.string.studio_crop_per_page), perPageCrop) { enabled ->
                    recordSnapshot()
                    perPageCrop = enabled
                    if (!enabled) latestEdit { it.copy(cropOverrides = emptyList()) }
                    rect = currentDraft.cropRectFor(currentPage, enabled)
                }
                Text(stringResource(R.string.studio_crop_override_count, draft.cropOverrides.size), style = QuietTheme.typography.metadata)
                if (draft.cropOverrides.isNotEmpty()) TextButton(onClick = {
                    recordSnapshot()
                    latestEdit { it.copy(cropOverrides = emptyList()) }
                    perPageCrop = false
                }) {
                    Text(stringResource(R.string.studio_crop_clear_overrides))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.visual_edit_free to 0f,
                    R.string.visual_edit_square to 1f,
                    R.string.visual_edit_document to (210f / 297f),
                    R.string.visual_landscape to (16f / 9f)
                ).forEach { (label, ratio) ->
                    FilterChip(selected = lockedAspect == ratio, border = null, onClick = {
                        val box = if (ratio == 0f) VisualRect.WHOLE
                        else VisualRect.fitAspect(frame.width, frame.height, sourceCropAspect(ratio, if (tool == "img_03") draft.turns else 0))
                        if (rect != box || lockedAspect != ratio) recordSnapshot()
                        lockedAspect = ratio
                        rect = box
                        saveRect(box)
                        haptics.selectionToggle()
                    }, label = { Text(stringResource(label)) })
                }
            }
            if (tool == "img_03") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        recordSnapshot()
                        if (lockedAspect > 0f) lockedAspect = 1f / lockedAspect
                        latestEdit { it.copy(turns = (it.turns + 1) % 4) }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Rounded.RotateRight, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.visual_rotate))
                    }
                    FilledTonalButton(onClick = {
                        recordSnapshot()
                        latestEdit { it.copy(flipX = !it.flipX) }
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Flip, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.visual_flip))
                    }
                }
                FilledTonalButton(onClick = { recordSnapshot(); latestEdit { it.copy(flipY = !it.flipY) } }) {
                    Text(stringResource(R.string.editor_flip_vertical))
                }
            }
            Text(stringResource(R.string.editor_adjust_region), style = QuietTheme.typography.metadataStrong)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    R.string.editor_move_left to (-.02f to 0f), R.string.editor_move_right to (.02f to 0f),
                    R.string.editor_move_up to (0f to -.02f), R.string.editor_move_down to (0f to .02f)
                ).forEach { (label, delta) ->
                    FilledTonalButton(onClick = {
                        val next = rect.dragged(VisualHandle.BODY, delta.first, delta.second)
                        recordEdit(rect, next)
                        rect = next
                        saveRect(next)
                    }) { Text(stringResource(label)) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(R.string.editor_smaller to -.02f, R.string.editor_larger to .02f).forEach { (label, delta) ->
                    FilledTonalButton(onClick = {
                        val next = rect.draggedLocked(
                            VisualHandle.BOTTOM_RIGHT, delta, delta,
                            if (lockedAspect > 0f) sourceCropAspect(lockedAspect, if (tool == "img_03") currentDraft.turns else 0) / sourceAspect else 0f
                        )
                        recordEdit(rect, next)
                        rect = next
                        saveRect(next)
                    }) { Text(stringResource(label)) }
                }
            }
        } else if (failed) {
            Text(stringResource(R.string.msg_preview_unavailable), color = colors.error)
            TextButton(onClick = { retry++ }) { Text(stringResource(R.string.interactive_preview_retry)) }
            if (sourceIsPdf && currentPage > 1) TextButton(onClick = {
                currentPage = 1
                retry++
            }) { Text(stringResource(R.string.interactive_first_page)) }
        } else QuietShimmerSkeleton()
    }
}

/**
 * Numeric mirror of a crop edge. The handle is the primary control; this field is the
 * exact-value input for the same number and never disagrees with it.
 */
@Composable
internal fun CropNumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        val typed = text.replace(',', '.').toFloatOrNull()
        val external = value.replace(',', '.').toFloatOrNull()
        if (typed == null || external == null || typed != external) text = value
    }
    val parsed = text.replace(',', '.').toFloatOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next.take(8)
            val candidate = text.replace(',', '.').toFloatOrNull()
            if (candidate != null && candidate.isFinite() && candidate in 0f..1f) onChange(candidate)
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = text.isNotEmpty() && (parsed == null || parsed < 0f || parsed > 1f),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
        modifier = modifier.heightIn(min = 64.dp)
    )
}
