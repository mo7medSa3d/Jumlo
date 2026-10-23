package com.jumlo.app.ui.components

import kotlinx.coroutines.ensureActive

import kotlinx.coroutines.currentCoroutineContext

import com.jumlo.app.data.pdf.PdfRendererFactory

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowForward
import com.jumlo.app.ui.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.jumlo.app.ui.util.rememberAppHaptics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.OutputRecord
import com.jumlo.app.model.PillarCategory
import com.jumlo.app.model.UtilityTool
import com.jumlo.app.ui.theme.QuietTheme
import androidx.compose.ui.semantics.Role

import com.jumlo.app.ui.theme.ToolPalette
import com.jumlo.app.ui.theme.pressFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Shared Jumlo visual primitives. Real actions and content are supplied by screen owners;
 * these components never create sample user data or simulate unavailable features.
 */
@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = QuietTheme.shapes.card,
        color = accent?.let { ToolPalette.mix(colors.surfaceContainerLow, it, .04f) }
            ?: colors.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(QuietTheme.spacing.cardInteriorPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
fun NeonSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title, style = QuietTheme.typography.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.pressFeedback()) {
                Text(action, style = QuietTheme.typography.metadataStrong)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(17.dp))
            }
        }
    }
}

@Composable
fun NeonHero(
    title: String,
    description: String,
    button: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = colors.primary,
        shadowElevation = 3.dp
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))) {
            // Soft graphic accents remain decorative; only the real scan CTA is clickable.
            Box(Modifier.align(Alignment.TopEnd).offset(x = 65.dp, y = (-66).dp)
                .size(220.dp).background(colors.onPrimary.copy(alpha = 0.10f), CircleShape))
            Box(Modifier.align(Alignment.BottomEnd).offset(x = 45.dp, y = 70.dp)
                .size(138.dp).background(colors.onPrimary.copy(alpha = 0.08f), CircleShape))
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(Modifier.size(52.dp)
                    .background(colors.onPrimary.copy(alpha = 0.15f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center) {
                    ModernToolIcon("document_scanner", colors.onPrimary, Modifier.size(28.dp))
                }
                Text(title, style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold), color = colors.onPrimary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(description, style = QuietTheme.typography.bodyMedium,
                    color = colors.onPrimary.copy(alpha = 0.93f),
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onAction,
                    modifier = Modifier.pressFeedback().fillMaxWidth().heightIn(min = 54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.onPrimary, contentColor = colors.primary),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    ModernToolIcon("document_scanner", colors.primary, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(button, style = QuietTheme.typography.button, maxLines = 2)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun NeonToolTile(
    tool: UtilityTool,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val tint = ToolPalette.accentFor(tool.id, isDark)
    val iconSurface = ToolPalette.containerFor(tool.id, colors.surfaceContainerLow, isDark)
    val haptics = rememberAppHaptics()
    Surface(
        onClick = { haptics.actionClick(); onClick() },
        modifier = modifier.fillMaxWidth().pressFeedback()
            .heightIn(min = if (compact) 116.dp else 126.dp),
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerLow,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(if (compact) 52.dp else 58.dp)
                .background(iconSurface, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center) {
                ModernToolIcon(tool.iconName, tint,
                    Modifier.size(if (compact) 27.dp else 29.dp))
            }
            Text(
                if (language.isRtl) tool.titleAr else tool.titleEn,
                color = colors.onSurface,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Shows image and PDF previews from the actual persisted output URI.
 * PDF page 0 is rasterized off the UI thread with a strict bitmap bound.
 * Other file types display a semantic icon; there are no fake thumbnails.
 */
@Composable
fun NeonOutputPreview(
    output: OutputRecord,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val isPdf = output.mimeType.equals("application/pdf", ignoreCase = true)
    val isImage = output.mimeType.startsWith("image/")
    val pdfBitmap by produceState<Bitmap?>(null, output.uri, isPdf) {
        value = if (!isPdf) null else withContext(Dispatchers.IO) {
            val coroutine = currentCoroutineContext()
            try {
                context.contentResolver.openFileDescriptor(Uri.parse(output.uri), "r")?.use { fd ->
                    PdfRendererFactory.open(fd, context.cacheDir) { coroutine.ensureActive() }.use { pdf ->
                        if (pdf.pageCount < 1) null else pdf.openPage(0).use { page ->
                            val width = 240
                            val height = (width * (page.height.toFloat() / page.width.coerceAtLeast(1)))
                                .roundToInt().coerceIn(80, 360)
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                bitmap.eraseColor(AndroidColor.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                }
            } catch (_: Exception) { null }
        }
    }
    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        when {
            isImage -> {
                val request = remember(context, output.uri) {
                    ImageRequest.Builder(context).data(output.uri).size(480)
                        .crossfade(false).build()
                }
                AsyncImage(
                    model = request, contentDescription = output.displayName,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            }
            isPdf && pdfBitmap != null -> androidx.compose.foundation.Image(
                bitmap = pdfBitmap!!.asImageBitmap(),
                contentDescription = output.displayName,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
            else -> Icon(
                when {
                    isPdf -> Icons.Rounded.PictureAsPdf
                    output.mimeType.startsWith("video/") -> Icons.Rounded.Movie
                    output.mimeType.startsWith("audio/") -> Icons.Rounded.AudioFile
                    else -> Icons.Rounded.InsertDriveFile
                }, contentDescription = output.displayName,
                tint = colors.primary, modifier = Modifier.size(40.dp)
            )
        }
    }
}
