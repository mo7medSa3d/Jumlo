package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.data.processing.EncodedImagePreview
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.QuietCard
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Owns even a delivered result that is disposed before Compose observes its success state. */
private class EncodedPreviewLoad {
    var result by mutableStateOf<Result<EncodedImagePreview>?>(null)
        private set
    private var disposed=false

    fun success(preview:EncodedImagePreview) {
        if(disposed) preview.close() else result=Result.success(preview)
    }

    fun failure(error:Exception) {
        if(!disposed) result=Result.failure(error)
    }

    fun dispose() {
        disposed=true
        result?.getOrNull()?.close()
        result=null
    }
}

@Composable
internal fun EncodedImagePreviewPanel(vm:AppViewModel,sourceId:String,draft:WorkspaceDraft) {
    val settings=remember(draft) {runCatching {draft.executionSettings()}}
    EncodedImagePreviewPanel(sourceId,draft.toolId,settings,vm::encodedImagePreview)
}

/** The worker and preview consume identical settings; the loader creates both real layers. */
@Composable
internal fun EncodedImagePreviewPanel(
    sourceId:String,toolId:String,settings:Result<String>,
    load:suspend (String,String,String)->EncodedImagePreview
) {
    var retry by remember(sourceId,toolId) {mutableIntStateOf(0)}
    val owner=remember(sourceId,toolId,settings,retry) {EncodedPreviewLoad()}
    val currentLoad by rememberUpdatedState(load)
    DisposableEffect(owner) {
        // The previous owner's layers are no longer part of the committed composition.
        onDispose {owner.dispose()}
    }
    LaunchedEffect(owner) {
        var pending:EncodedImagePreview?=null
        try {
            val exactSettings=settings.getOrThrow()
            delay(300) // Coalesce quality/target slider edits and cancel stale work.
            val ready=currentLoad(sourceId,toolId,exactSettings).also {pending=it}
            currentCoroutineContext().ensureActive()
            owner.success(ready)
            pending=null
        } catch(cancel:CancellationException) {
            throw cancel
        } catch(error:Exception) {
            owner.failure(error)
        } finally {
            // Includes a cancelled coroutine handoff before the result could be displayed.
            pending?.close()
        }
    }
    QuietCard(Modifier.fillMaxWidth().testTag("encoded.preview")) {
        Text(stringResource(R.string.encoded_preview_title),style=QuietTheme.typography.sectionTitle)
        val preview=owner.result?.getOrNull()
        when {
            preview!=null -> EncodedImagePreviewDetails(preview)
            owner.result?.isFailure==true -> {
                Text(stringResource(R.string.encoded_preview_failed),
                    color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("encoded.preview.error"))
                owner.result?.exceptionOrNull()?.let {error ->
                    Text(stringResource(userMessageResource(stableError(error))),style=QuietTheme.typography.metadata)
                }
                FilledTonalButton(onClick={retry++},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(stringResource(R.string.interactive_preview_retry))
                }
            }
            else -> {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("encoded.preview.loading"))
                Text(stringResource(R.string.encoded_preview_loading),style=QuietTheme.typography.metadata)
            }
        }
    }
}

@Composable
private fun EncodedImagePreviewDetails(preview:EncodedImagePreview) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(if(preview.sampled) R.string.encoded_preview_sample_notice else R.string.encoded_preview_exact_notice),
            style=QuietTheme.typography.metadata,
            modifier=Modifier.testTag(if(preview.sampled) "encoded.preview.sample" else "encoded.preview.exact"))
        ImageComparisonPreview(preview.before,preview.after)
        Text(stringResource(R.string.encoded_preview_source,
            PureDomainAlgorithms.formatBytes(preview.sourceBytes),preview.sourceWidth,preview.sourceHeight),
            style=QuietTheme.typography.metadata,modifier=Modifier.testTag("encoded.preview.source"))
        Text(stringResource(if(preview.sampled) R.string.encoded_preview_sample_output else R.string.encoded_preview_output,
            preview.format,PureDomainAlgorithms.formatBytes(preview.encodedBytes),preview.encodedWidth,preview.encodedHeight),
            style=QuietTheme.typography.metadataStrong,modifier=Modifier.testTag("encoded.preview.output"))
        preview.quality?.let {quality ->
            Text(stringResource(if(preview.sampled) R.string.encoded_preview_sample_quality else R.string.encoded_preview_quality,quality),
                style=QuietTheme.typography.metadata,modifier=Modifier.testTag("encoded.preview.quality"))
        }
        preview.requestedTargetBytes?.let {target ->
            Text(stringResource(R.string.encoded_preview_target,PureDomainAlgorithms.formatBytes(target)),
                style=QuietTheme.typography.metadata,modifier=Modifier.testTag("encoded.preview.target"))
        }
    }
}
