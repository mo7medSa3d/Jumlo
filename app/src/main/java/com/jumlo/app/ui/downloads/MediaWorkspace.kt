package com.jumlo.app.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jumlo.app.R
import com.jumlo.app.core.media.*
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.components.QuietShimmerSkeleton
import com.jumlo.app.ui.theme.pressFeedback
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlinx.coroutines.delay
import java.util.Locale

/** One input, a real probe, a preview, then one action. Preferences own the normal format choice. */
@Composable fun MediaWorkspace(
    initialUrl:String,
    sessionEpoch:Long,
    onQueued:(String)->Unit,
    onDirect:(String)->Unit,
    viewModel:MediaViewModel=hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard=LocalClipboardManager.current
    val haptics=rememberAppHaptics()
    var advanced by rememberSaveable { mutableStateOf(false) }
    var browser by rememberSaveable { mutableStateOf(false) }
    val clipboardSuggestion=remember { clipboard.getText()?.text?.toString()?.takeIf { it.trim().startsWith("https://",true) } }
    LaunchedEffect(sessionEpoch) { advanced=false; browser=false; viewModel.beginSession(initialUrl) }
    LaunchedEffect(state.url) {
        if(state.url.startsWith("https://",true)) {
            delay(500)
            viewModel.analyze()
        }
    }
    DisposableEffect(Unit) { onDispose { viewModel.cancelAnalysis() } }
    if(browser) {
        MediaBrowser(initialUrl=state.url,onClose={browser=false},onMedia={link->browser=false;viewModel.editUrl(link)},
            onFile={link->browser=false;onDirect(link)})
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier=Modifier.weight(1f).fillMaxWidth(),
            contentPadding=PaddingValues(horizontal=20.dp,vertical=22.dp),
            verticalArrangement=Arrangement.spacedBy(18.dp)
        ) {
            item("hero") {
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.redesign_download_hero),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text(stringResource(R.string.redesign_download_subtitle),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                }
            }
            item("input") {
                OutlinedTextField(
                    value=state.url,onValueChange=viewModel::editUrl,singleLine=true,
                    placeholder={Text(stringResource(R.string.redesign_link_hint))},
                    label={Text(stringResource(R.string.media_link))},
                    modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
                    textStyle=MaterialTheme.typography.bodyLarge.copy(textDirection=TextDirection.Ltr),
                    leadingIcon={Icon(Icons.Rounded.Link,null)},
                    trailingIcon={IconButton(onClick={clipboard.getText()?.text?.toString()?.let(viewModel::editUrl)}) {
                        Icon(Icons.Rounded.ContentPaste,stringResource(R.string.action_paste))
                    }}
                )
            }
            if(state.url.isEmpty() && clipboardSuggestion!=null) item("clipboard") {
                SuggestionChip(onClick={viewModel.editUrl(clipboardSuggestion)},
                    label={Text(stringResource(R.string.redesign_paste_suggestion))},icon={Icon(Icons.Rounded.ContentPaste,null)})
            }
            if(state.analyzing) item("loading") {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    QuietShimmerSkeleton(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.media_analyzing),style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error?.let { code -> item("error") {
                QuietStateCard(title=mediaError(code),subtitle=stringResource(R.string.redesign_download_retry_hint),isError=true)
            }}
            state.direct?.let { preview -> item("direct") {
                Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        Text(stringResource(R.string.redesign_ready_file),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
                        Text(preview.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        Text(listOfNotNull(preview.mime,preview.sizeBytes?.let(::mediaBytes)).joinToString(" · ").ifBlank { stringResource(R.string.media_size_unknown) },
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }}
            state.asset?.let { asset -> item("media") {
                Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        asset.thumbnailUrl?.let { thumb -> AsyncImage(model=thumb,contentDescription=asset.title,
                            modifier=Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(17.dp)),contentScale=ContentScale.Crop) }
                        Text(asset.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                        val choice=state.choices.firstOrNull { it.key==state.selectedKey }
                        val quality=choice?.expectedHeight?.let { "${it}p" } ?: choice?.container?.uppercase(Locale.ROOT)
                        val size=choice?.estimatedBytes?.let(::mediaBytes) ?: stringResource(R.string.media_size_unknown)
                        Text(listOfNotNull(quality,size).joinToString(" · "),style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }}
            item("advanced") {
                TextButton(onClick={advanced=!advanced}) {
                    Icon(Icons.Rounded.Tune,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_advanced))
                }
            }
            if(advanced) {
                item("mode") { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    FilterChip(state.mode==MediaMode.VIDEO,{viewModel.mode(MediaMode.VIDEO)},label={Text(stringResource(R.string.media_video))})
                    FilterChip(state.mode==MediaMode.AUDIO,{viewModel.mode(MediaMode.AUDIO)},label={Text(stringResource(R.string.media_audio_only))})
                    TextButton(onClick={browser=true}) {Text(stringResource(R.string.media_browser))}
                }}
                if(state.asset!=null) item("qualities") {
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(state.choices,key={it.key}) { choice ->
                            FilterChip(selected=state.selectedKey==choice.key,onClick={viewModel.select(choice.key)},
                                label={Text((choice.expectedHeight?.let { "${it}p" } ?: choice.container.uppercase(Locale.ROOT)) +
                                    (choice.estimatedBytes?.let { " · ${mediaBytes(it)}" } ?: ""))})
                        }
                    }
                }
            }
        }
        Surface(tonalElevation=3.dp,shadowElevation=7.dp) {
            Button(onClick={haptics.toolExecute();viewModel.submit(onQueued)},
                enabled=!state.analyzing && !state.submitting && (state.direct!=null || state.selectedKey!=null),
                modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp).pressFeedback().heightIn(min=56.dp),
                shape=RoundedCornerShape(18.dp)) {
                Icon(Icons.Rounded.Download,null);Spacer(Modifier.width(8.dp))
                Text(stringResource(if(state.submitting) R.string.media_queueing else R.string.redesign_download_now))
            }
        }
    }
}

private fun mediaBytes(bytes:Long):String=when {
    bytes>=1073741824L -> String.format(Locale.ROOT,"%.2f GiB",bytes/1073741824.0)
    bytes>=1048576L -> String.format(Locale.ROOT,"%.1f MiB",bytes/1048576.0)
    bytes>=1024L -> String.format(Locale.ROOT,"%.0f KiB",bytes/1024.0)
    else -> "$bytes B"
}

@Composable private fun mediaError(code:String):String=stringResource(when(code) {
    "DOWNLOAD_NOT_A_FILE" -> R.string.redesign_download_not_file
    "MEDIA_PRIVATE_CONTENT","LOGIN_REQUIRED","MEDIA_SESSION_REQUIRED" -> R.string.media_error_private
    "NETWORK_UNAVAILABLE","MEDIA_NETWORK_ERROR" -> R.string.media_error_network
    "MEDIA_NO_USABLE_FORMAT" -> R.string.media_error_format
    "INVALID_URL","MULTIPLE_MEDIA_LINKS" -> R.string.media_error_url
    "MEDIA_RUNTIME_INITIALIZATION_FAILED","MEDIA_NATIVE_ABI_UNAVAILABLE" -> R.string.media_error_runtime
    else -> R.string.media_error_generic
})
