package com.jumlo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ManagedFileItem
import com.jumlo.app.ui.theme.QuietTheme

@Composable
private fun fileTint(extension: String): Pair<Color, Color> {
    val p = QuietTheme.pillars
    return when (extension.lowercase()) {
        "pdf" -> p.pdfPrimary to p.pdfContainer
        "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp", "avif" -> p.imagesPrimary to p.imagesContainer
        "zip", "tar", "gz", "tgz", "7z", "rar" -> p.filesPrimary to p.filesContainer
        else -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
    }
}

@Composable
fun QuietFileRow(
    file: ManagedFileItem,
    language: AppLanguage,
    showCheckbox: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    onClick: () -> Unit,
    trailingActionText: String? = null,
    onTrailingAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (tint, container) = fileTint(file.extension)
    val isRtl = language.isRtl
    val modified = if (isRtl) file.modifiedLabelAr else file.modifiedLabelEn
    val details = buildList {
        add(file.formattedSize)
        file.pageCount?.let { add(if (isRtl) "$it صفحة" else "$it pages") }
        file.itemCount?.let { add(if (isRtl) "$it عنصر" else "$it items") }
        file.dimensions?.let { add(it) }
        add(modified)
    }.joinToString("  ·  ")
    val selected = file.isSelected

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(26.dp),
        color = if (selected) QuietTheme.pillars.activePillContainer else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = QuietTheme.spacing.fileRowStandardHeight)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showCheckbox) {
                QuietCheckbox(checked = selected, onCheckedChange = { onToggleSelect?.invoke() })
                Spacer(modifier = Modifier.width(8.dp))
            }
            QuietIconTile(
                icon = fileIconFor(file.extension),
                tint = tint,
                container = container,
                size = 48.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = QuietTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = details,
                    style = QuietTheme.typography.metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailingActionText != null && onTrailingAction != null) {
                TextButton(onClick = onTrailingAction, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                    Text(trailingActionText, style = QuietTheme.typography.metadataStrong, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
