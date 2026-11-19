package com.jumlo.app.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.jumlo.app.ui.theme.QuietTheme
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
        border = null,
        modifier = modifier.heightIn(min = QuietTheme.spacing.touchTarget),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
