package com.jumlo.app.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.ocr.OcrScript
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietPrimaryButton
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException

/** Clean iOS 27-style per-page OCR preview with multi-script selector; export uses the validated job pipeline. */
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
    val scriptItems = remember {
        listOf(
            OcrScript.ARABIC to R.string.ocr_script_arabic,
            OcrScript.LATIN to R.string.ocr_script_latin,
            OcrScript.CHINESE to R.string.ocr_script_chinese,
            OcrScript.JAPANESE to R.string.ocr_script_japanese,
            OcrScript.KOREAN to R.string.ocr_script_korean,
            OcrScript.DEVANAGARI to R.string.ocr_script_devanagari
        )
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
                Surface(modifier, shape = QuietTheme.shapes.card, color = pillars.cardSurface) {
                    Column(Modifier.heightIn(min = 180.dp, max = 320.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.studio_ocr_text), style = QuietTheme.typography.sectionTitle, color = colors.onSurface, modifier = Modifier.semantics { heading() })
                            if (extractedText.isNotBlank()) {
                                FilledTonalButton(
                                    onClick = {
                                        try { clipboard.setText(AnnotatedString(extractedText)) }
                                        catch (_: RuntimeException) { vm.notifyMessage("CLIPBOARD_UNAVAILABLE") }
                                    },
                                    shape = QuietTheme.shapes.pill,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Text(stringResource(R.string.studio_ocr_copy), style = QuietTheme.typography.metadataStrong)
                                }
                            }
                        }
                        Box(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            when {
                                attempts == 0 -> QuietPrimaryButton(
                                    stringResource(R.string.studio_ocr_recognize),
                                    onClick = { attempts++ },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                recognized == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                recognized?.isFailure == true -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        stringResource(userMessageResource(stableError(recognized!!.exceptionOrNull()!!))),
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
