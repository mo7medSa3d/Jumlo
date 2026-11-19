package com.jumlo.app.ui.components

import kotlinx.coroutines.ensureActive

import kotlinx.coroutines.currentCoroutineContext

import com.jumlo.app.data.pdf.PdfRendererFactory

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.ArrowForward
import com.jumlo.app.ui.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.jumlo.app.ui.util.rememberAppHaptics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.OutputRecord
import com.jumlo.app.model.UtilityTool
import com.jumlo.app.ui.theme.JumloRadii
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.theme.ToolIconColors
import com.jumlo.app.ui.theme.ToolPalette
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.workspace.DisposeOwnedPreviewBitmap
import kotlinx.coroutines.CancellationException
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
    val pillars = QuietTheme.pillars
    Surface(
        modifier = modifier,
        shape = QuietTheme.shapes.card,
        color = accent?.let { ToolPalette.mix(pillars.cardSurface, it, .04f) }
            ?: pillars.cardSurface,
        border = BorderStroke(1.dp, pillars.cardBorder),
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
            modifier = Modifier.weight(1f).semantics { heading() },
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
fun NeonToolTile(
    tool: UtilityTool,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val iconSurface = ToolIconColors.iconColorFor(tool.id)
    val tint = ToolIconColors.glyphOn(iconSurface)
    val haptics = rememberAppHaptics()
    Surface(
        onClick = { haptics.actionClick(); onClick() },
        modifier = modifier.fillMaxWidth().pressFeedback()
            .heightIn(min = if (compact) 116.dp else 126.dp),
        shape = RoundedCornerShape(18.dp),
        color = pillars.cardSurface,
        border = BorderStroke(1.dp, pillars.cardBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(if (compact) 48.dp else 54.dp)
                .background(iconSurface, RoundedCornerShape(JumloRadii.indicator)),
                contentAlignment = Alignment.Center) {
                ModernToolIcon(tool.iconName, tint,
                    Modifier.size(if (compact) 25.dp else 27.dp))
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
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null }
            catch (_: OutOfMemoryError) { null }
        }
    }
    DisposeOwnedPreviewBitmap(pdfBitmap)
    val renderedPdf = pdfBitmap
    Box(
        modifier = modifier.clip(RoundedCornerShape(JumloRadii.indicator))
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
            isPdf && renderedPdf != null -> androidx.compose.foundation.Image(
                bitmap = renderedPdf.asImageBitmap(),
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
