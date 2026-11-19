package com.jumlo.app.ui.components

import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import com.jumlo.app.data.pdf.PdfRendererFactory
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
        border = null,
        shadowElevation = 0.dp
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
            TextButton(onClick = onAction, modifier = Modifier.pressFeedback().sizeIn(minHeight = 48.dp)) {
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
        shape = QuietTheme.shapes.innerPanel,
        color = pillars.cardSurface,
        border = null,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(if (compact) 48.dp else 54.dp)
                    .background(iconSurface, QuietTheme.shapes.iconTile),
                contentAlignment = Alignment.Center
            ) {
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
 * Shows image and PDF previews from the actual persisted output URI, paired with
 * crisp iOS-style layered file type tiles and extension badges for every format.
 */
@Composable
fun NeonOutputPreview(
    output: OutputRecord,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val isPdf = output.mimeType.equals("application/pdf", ignoreCase = true)
    val isImage = output.mimeType.startsWith("image/")
    val isVideo = output.mimeType.startsWith("video/")
    val isAudio = output.mimeType.startsWith("audio/")
    val isZip = output.mimeType.contains("zip", ignoreCase = true)
    val extBadge = remember(output.displayName, output.mimeType) {
        val ext = output.displayName.substringAfterLast('.', "").uppercase().take(4)
        when {
            ext.isNotBlank() && ext.all { it.isLetterOrDigit() } -> ext
            isPdf -> "PDF"
            isZip -> "ZIP"
            isVideo -> "VID"
            isAudio -> "AUD"
            isImage -> "IMG"
            else -> "DOC"
        }
    }
    val (tileAccent, tileBg) = when {
        isPdf -> pillars.pdfPrimary to pillars.pdfContainer
        isImage -> pillars.imagesPrimary to pillars.imagesContainer
        isZip -> pillars.filesPrimary to pillars.filesContainer
        isVideo || isAudio -> pillars.downloadsPrimary to pillars.downloadsContainer
        else -> colors.primary to colors.primaryContainer
    }

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
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(AndroidColor.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                coroutine.ensureActive()
                                bitmap
                            } catch (t: Throwable) {
                                bitmap.recycle()
                                throw t
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
        modifier = modifier
            .clip(QuietTheme.shapes.activeIndicatorPill)
            .background(tileBg),
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
            isPdf && renderedPdf != null && !renderedPdf.isRecycled -> {
                val imageBitmap = remember(renderedPdf) { renderedPdf.asImageBitmap() }
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = output.displayName,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                )
            }
            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        when {
                            isPdf -> Icons.Rounded.PictureAsPdf
                            isVideo -> Icons.Rounded.Movie
                            isAudio -> Icons.Rounded.AudioFile
                            isZip -> Icons.Rounded.FolderZip
                            else -> Icons.Rounded.InsertDriveFile
                        },
                        contentDescription = output.displayName,
                        tint = tileAccent,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(5.dp),
            shape = QuietTheme.shapes.badge,
            color = tileAccent.copy(alpha = 0.92f),
            contentColor = ToolIconColors.glyphOn(tileAccent)
        ) {
            Text(
                text = extBadge,
                style = QuietTheme.typography.badge,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
