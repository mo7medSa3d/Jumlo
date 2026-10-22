package com.jumlo.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics

/**
 * Selection controls with one house rule: every tappable target is at least 48dp.
 * Material 3 chips default to 32dp and buttons to 40dp, so every raw chip/button in
 * the app was below the accessible minimum. These wrappers are the supported way
 * to add them.
 */

@Composable
fun QuietChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val haptics = rememberAppHaptics()
    FilterChip(
        selected = selected,
        onClick = {
            haptics.selectionToggle()
            onClick()
        },
        enabled = enabled,
        label = { androidx.compose.material3.Text(label, style = QuietTheme.typography.metadataStrong) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = QuietTheme.shapes.button,
        modifier = modifier.heightIn(min = QuietTheme.spacing.touchTarget),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/** A switch row: the whole row toggles, and the thumb is never smaller than 48dp of travel. */
@Composable
fun QuietSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = rememberAppHaptics()
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.selectionToggle()
            onCheckedChange(it)
        },
        enabled = enabled,
        modifier = modifier
            .heightIn(min = QuietTheme.spacing.touchTarget)
            .semantics {
                contentDescription = label
                stateDescription = label
            }
    )
}

/** Slider with an announced value and a tactile tick when the drag settles. */
@Composable
fun QuietSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueDescription: String,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: () -> Unit = {}
) {
    val haptics = rememberAppHaptics()
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = QuietTheme.spacing.touchTarget)
            .semantics {
                contentDescription = valueDescription
                stateDescription = valueDescription
            },
        onValueChangeFinished = {
            haptics.sliderTick()
            onValueChangeFinished()
        }
    )
}

/** Destructive action. Deliberately tonal, not a filled red button: destructive taps stay reversible. */
@Composable
fun QuietDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = rememberAppHaptics()
    Button(
        onClick = {
            haptics.secondaryAction()
            onClick()
        },
        enabled = enabled,
        modifier = modifier.pressFeedback().heightIn(min = QuietTheme.spacing.tonalButtonHeight),
        shape = QuietTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.Text(text = text, style = QuietTheme.typography.metadataStrong)
    }
}

/** Bottom sheet using the house sheet radius instead of the Material default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = QuietTheme.shapes.modalSheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
        content = content
    )
}

/** Neutral surface used by rows that need a soft container without a full card border. */
@Composable
fun QuietSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = QuietTheme.shapes.card,
        color = color,
        content = content
    )
}
