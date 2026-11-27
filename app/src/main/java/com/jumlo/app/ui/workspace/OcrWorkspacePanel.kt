package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.DocumentContentAnalyzer
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.ocr.ArabicTesseractOcrEngine
import com.jumlo.app.data.ocr.OcrScript
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException

/** Clean iOS-style per-page OCR preview with automatic Arabic/English detection and copy/export actions. */
@Composable
internal fun OcrWorkspacePanel(
    vm: AppViewModel,
    sourceId: String,
    onExport: () -> Unit,
    exportEnabled: Boolean,
    selectedScript: String = "AUTO",
    onSelectScript: (String) -> Unit = {}
) {
    var page by rememberSaveable(sourceId) { mutableIntStateOf(1) }
    var attempts by rememberSaveable(sourceId) { mutableIntStateOf(0) }
    val script = remember(selectedScript) { OcrScript.fromId(selectedScript) }
    val downloadProgress by ArabicTesseractOcrEngine.downloadProgress.collectAsState()
    val clipboard = LocalClipboardManager.current
    val colors = MaterialTheme.colorScheme
    val pillars = QuietTheme.pillars
    val frame by produceState<Result<PreviewFrame>?>(null, sourceId, page) {
        value = null
        value = try { Result.success(vm.preview(sourceId, page)) } catch (cancel: CancellationException) { throw cancel } catch (error: Exception) { Result.failure(error) }
    }
    DisposeOwnedPreviewBitmap(frame?.getOrNull()?.bitmap)
    val recognized by produceState<Result<String>?>(null, sourceId, page, attempts, script) {
        value = null
        if (attempts > 0) value = try { Result.success(vm.recognizePage(sourceId, page, script)) } catch (cancel: CancellationException) { throw cancel } catch (error: Exception) { Result.failure(error) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            @Composable fun Page(modifier: Modifier) {
                Surface(modifier, shape = QuietTheme.shapes.card, color = colors.surfaceContainerLow) {
                    Box(Modifier.height(300.dp), contentAlignment = Alignment.Center) {
                        frame?.getOrNull()?.takeUnless { it.bitmap.isRecycled }?.let { current ->
                            val imageBitmap = remember(current.bitmap) { current.bitmap.asImageBitmap() }
                            Image(imageBitmap, stringResource(R.string.jumlo_page_label, page), Modifier.fillMaxSize().testTag("ocr.page"), contentScale = ContentScale.Fit)
                        } ?: if (frame?.isFailure == true) {
                            Text(stringResource(R.string.msg_preview_unavailable), style = QuietTheme.typography.metadata, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        } else {
                            CircularProgressIndicator(strokeWidth = 2.5.dp)
                        }
                    }
                }
            }
            @Composable fun TextPanel(modifier: Modifier) {
                val extractedText = recognized?.getOrNull().orEmpty()
                val isRtlUi = LocalLayoutDirection.current == LayoutDirection.Rtl
                val docAnalysis = remember(extractedText, isRtlUi) {
                    if (extractedText.isBlank()) null else DocumentContentAnalyzer.analyze(extractedText, isRtl = isRtlUi)
                }
                Surface(modifier, shape = QuietTheme.shapes.card, color = pillars.cardSurface) {
                    Column(Modifier.heightIn(min = 180.dp, max = 320.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.studio_ocr_text), style = QuietTheme.typography.sectionTitle, color = colors.onSurface, modifier = Modifier.semantics { heading() })
                            if (docAnalysis != null) {
                                val categoryName = if (isRtlUi) docAnalysis.category.labelAr else docAnalysis.category.labelEn
                                Text(
                                    stringResource(R.string.ocr_detected_doc_badge, categoryName, docAnalysis.suggestedBaseName),
                                    style = QuietTheme.typography.metadataStrong,
                                    color = colors.primary
                                )
                            } else {
                                Text(
                                    stringResource(R.string.ocr_auto_detect_status),
                                    style = QuietTheme.typography.metadata,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.testTag("ocr.auto-status")
                                )
                            }
                        }
                        Box(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            when {
                                attempts == 0 -> QuietPrimaryButton(
                                    stringResource(R.string.studio_ocr_recognize),
                                    onClick = { attempts++ },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                recognized == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val progress = downloadProgress
                                    if (progress != null && progress >= 0f) {
                                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                        Text(
                                            stringResource(R.string.ocr_downloading_arabic_model_pct, (progress * 100).toInt()),
                                            style = QuietTheme.typography.metadata,
                                            color = colors.onSurfaceVariant
                                        )
                                    } else {
                                        LinearProgressIndicator(Modifier.fillMaxWidth())
                                        Text(
                                            stringResource(if (progress != null) R.string.ocr_downloading_arabic_model else R.string.studio_ocr_wait),
                                            style = QuietTheme.typography.metadata,
                                            color = colors.onSurfaceVariant
                                        )
                                    }
                                }
                                recognized?.isFailure == true -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val errorCode = recognized?.exceptionOrNull()?.let(::stableError) ?: "OCR_FAILED"
                                    val errorMessage = if (errorCode == "OCR_ARABIC_MODEL_MISSING") {
                                        stringResource(R.string.ocr_arabic_model_missing_actionable)
                                    } else {
                                        stringResource(userMessageResource(errorCode))
                                    }
                                    Text(
                                        errorMessage,
                                        style = QuietTheme.typography.bodyMedium,
                                        color = colors.error
                                    )
                                    FilledTonalButton(onClick = { attempts++ }, shape = QuietTheme.shapes.pill) {
                                        Text(stringResource(R.string.interactive_preview_retry))
                                    }
                                }
                                extractedText.isBlank() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(R.string.studio_ocr_empty), style = QuietTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                    FilledTonalButton(onClick = { attempts++ }, shape = QuietTheme.shapes.pill) {
                                        Text(stringResource(R.string.interactive_preview_retry))
                                    }
                                }
                                else -> SelectionContainer {
                                    Text(
                                        extractedText,
                                        Modifier.testTag("ocr.text").fillMaxWidth(),
                                        style = QuietTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                                        color = colors.onSurface
                                    )
                                }
                            }
                        }
                        if (extractedText.isNotBlank() || exportEnabled) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (extractedText.isNotBlank()) {
                                    FilledTonalButton(
                                        onClick = {
                                            try { clipboard.setText(AnnotatedString(extractedText)) }
                                            catch (_: RuntimeException) { vm.notifyMessage("CLIPBOARD_UNAVAILABLE") }
                                        },
                                        shape = QuietTheme.shapes.pill,
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 36.dp).testTag("ocr.copy")
                                    ) {
                                        Text(stringResource(R.string.studio_ocr_copy), style = QuietTheme.typography.metadataStrong)
                                    }
                                }
                                if (exportEnabled) {
                                    Button(
                                        onClick = onExport,
                                        shape = QuietTheme.shapes.pill,
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 36.dp).testTag("ocr.export")
                                    ) {
                                        Text(stringResource(R.string.studio_ocr_export), style = QuietTheme.typography.metadataStrong)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (maxWidth >= 660.dp) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { Page(Modifier.weight(1f)); TextPanel(Modifier.weight(1f)) }
            else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Page(Modifier.fillMaxWidth()); TextPanel(Modifier.fillMaxWidth()) }
        }
        frame?.getOrNull()?.let { DocumentPageControls(page, it.count) { page = it } }
    }
}

/** Explain the installed model's exact script limit without guessing the language from an empty scan. */
@Composable
internal fun OcrLanguageNotice() {
    Text(
        text = stringResource(R.string.ocr_scanned_arabic_limit),
        modifier = Modifier.fillMaxWidth().testTag("ocr.script-limitation"),
        style = QuietTheme.typography.metadataStrong,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
