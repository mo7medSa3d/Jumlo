package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Add
import com.jumlo.app.ui.icons.rounded.Remove
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlin.math.roundToInt

internal fun draftNumber(value: String, fallback: Int): Int =
    WorkspaceDraft.normalizeDigits(value).toIntOrNull() ?: fallback

/**
 * iOS Photos-style precision tick-dial scrubber slider.
 * Supports direct finger scrubbing, tap-to-set, haptic tick feedback, RTL mirroring,
 * and full TalkBack progress semantics.
 */
@Composable
internal fun InteractiveIntSlider(
    label: String,
    current: Int,
    minimum: Int,
    maximum: Int,
    reset: Int,
    onChange: (Int) -> Unit
) {
    val upper = maximum.coerceAtLeast(minimum)
    val safe = current.coerceIn(minimum, upper)
    val clampedReset = reset.coerceIn(minimum, upper)
    val haptics = rememberAppHaptics()
    val colors = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestOnChange by rememberUpdatedState(onChange)
    var lastHapticValue by remember(minimum, upper) { mutableIntStateOf(safe) }

    fun emitValue(next: Int) {
        val clamped = next.coerceIn(minimum, upper)
        if (clamped != lastHapticValue) {
            lastHapticValue = clamped
            haptics.sliderTick()
        }
        latestOnChange(clamped)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = QuietTheme.typography.metadataStrong,
                color = colors.onSurface
            )
            if (safe != clampedReset) {
                TextButton(
                    onClick = { haptics.selectionToggle(); emitValue(clampedReset) },
                    modifier = Modifier.heightIn(min = 40.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.visual_edit_reset), style = QuietTheme.typography.metadata)
                }
            }
            Surface(
                shape = QuietTheme.shapes.pill,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            ) {
                Text(
                    text = safe.toString(),
                    style = QuietTheme.typography.metadataStrong.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        if (upper > minimum) {
            val fraction = ((safe - minimum).toFloat() / (upper - minimum).toFloat()).coerceIn(0f, 1f)
            Surface(
                shape = QuietTheme.shapes.inputField,
                color = colors.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics {
                        contentDescription = label
                        stateDescription = safe.toString()
                        setProgress { target ->
                            val value = minimum + (target.coerceIn(0f, 1f) * (upper - minimum)).roundToInt()
                            emitValue(value)
                            true
                        }
                    }
                    .pointerInput(minimum, upper, rtl) {
                        detectTapGestures { tap ->
                            val raw = (tap.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val directed = if (rtl) 1f - raw else raw
                            emitValue(minimum + (directed * (upper - minimum)).roundToInt())
                        }
                    }
                    .pointerInput(minimum, upper, rtl) {
                        detectHorizontalDragGestures(
                            onDragEnd = { haptics.selectionToggle() }
                        ) { change, _ ->
                            change.consume()
                            val raw = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            val directed = if (rtl) 1f - raw else raw
                            emitValue(minimum + (directed * (upper - minimum)).roundToInt())
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val tickCount = 29
                    val centerY = size.height / 2f
                    val activeX = (if (rtl) 1f - fraction else fraction) * size.width
                    val activeColor = colors.primary
                    val inactiveColor = colors.onSurfaceVariant.copy(alpha = 0.28f)

                    for (i in 0 until tickCount) {
                        val t = i.toFloat() / (tickCount - 1).toFloat()
                        val x = t * size.width
                        val isMajor = i % 7 == 0
                        val tickH = if (isMajor) size.height * 0.72f else size.height * 0.42f
                        val tickW = if (isMajor) 2.2.dp.toPx() else 1.5.dp.toPx()
                        val filled = if (rtl) x >= activeX else x <= activeX
                        drawRoundRect(
                            color = if (filled) activeColor else inactiveColor,
                            topLeft = Offset(x - tickW / 2f, centerY - tickH / 2f),
                            size = Size(tickW, tickH),
                            cornerRadius = CornerRadius(tickW / 2f, tickW / 2f)
                        )
                    }

                    val needleW = 3.5.dp.toPx()
                    val needleH = size.height
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset((activeX - needleW / 2f).coerceIn(0f, size.width - needleW), 0f),
                        size = Size(needleW, needleH),
                        cornerRadius = CornerRadius(needleW / 2f, needleW / 2f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun InteractiveToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = QuietTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange = null)
    }
}

/**
 * iOS-style compact segmented stepper pill for discrete counts.
 */
@Composable
internal fun InteractiveCounter(
    label: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    onChange: (Int) -> Unit
) {
    val upper = maximum.coerceAtLeast(minimum)
    val safe = value.coerceIn(minimum, upper)
    val decrease = stringResource(R.string.interactive_decrease, label)
    val increase = stringResource(R.string.interactive_increase, label)
    val haptics = rememberAppHaptics()
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, Modifier.weight(1f), style = QuietTheme.typography.metadataStrong)
        Surface(
            shape = QuietTheme.shapes.pill,
            color = colors.surfaceContainerHigh
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = { haptics.sliderTick(); onChange(safe - 1) },
                    enabled = safe > minimum,
                    modifier = Modifier.size(48.dp).pressFeedback()
                ) {
                    Icon(Icons.Rounded.Remove, decrease, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = safe.toString(),
                    style = QuietTheme.typography.metadataStrong.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .semantics { contentDescription = label; stateDescription = safe.toString() }
                )
                IconButton(
                    onClick = { haptics.sliderTick(); onChange(safe + 1) },
                    enabled = safe < upper,
                    modifier = Modifier.size(48.dp).pressFeedback()
                ) {
                    Icon(Icons.Rounded.Add, increase, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
