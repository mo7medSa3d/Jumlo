package com.jumlo.app.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jumlo.app.R
import com.jumlo.app.core.media.*
import com.jumlo.app.ui.components.*
import java.util.Locale

@Composable fun MediaWorkspace(initialUrl:String,onQueued:(String)->Unit,onDirect:(String)->Unit,viewModel:MediaViewModel=hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    var browser by remember { mutableStateOf(false) }
    LaunchedEffect(initialUrl) { viewModel.acceptSharedUrl(initialUrl) }
    DisposableEffect(Unit) { onDispose { viewModel.cancelAnalysis() } }
    if(browser) {
        MediaBrowser(initialUrl=s.url,onClose={browser=false},onMedia={value->browser=false;viewModel.editUrl(value);viewModel.analyze()},onFile={value->browser=false;onDirect(value)})
        return
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item(key="media-intro") { Text(stringResource(R.string.media_intro),style=MaterialTheme.typography.bodyMedium) }
            item(key="media-tos") {
                QuietCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.media_tos_notice),style=MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        QuietCheckbox(checked=s.termsAccepted,onCheckedChange={viewModel.acceptTerms(it)})
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.media_tos_ack),style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f))
                    }
                }
            }
            item(key="media-input") {
                QuietCard(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value=s.url,onValueChange=viewModel::editUrl,enabled=!s.submitting,label={Text(stringResource(R.string.media_link))},modifier=Modifier.fillMaxWidth(),maxLines=4)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        QuietPrimaryButton(text=stringResource(R.string.media_analyze),onClick=viewModel::analyze,enabled=s.termsAccepted && s.url.isNotBlank() && !s.analyzing && !s.submitting)
                        QuietTonalButton(text=stringResource(R.string.media_browser),onClick={viewModel.cancelAnalysis();browser=true})
                        TextButton(onClick=viewModel::refreshExtractor,enabled=!s.updatingExtractor && !s.submitting) {Text(stringResource(R.string.media_update_extractor))}
                        if(s.analyzing) TextButton(onClick=viewModel::cancelAnalysis) {Text(stringResource(R.string.media_cancel))}
                    }
                }
            }
            if(s.analyzing) item(key="media-analyzing") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.media_analyzing))
                QuietShimmerSkeleton()
            }
            s.extractorStatus?.let { status -> item(key="media-ext-status") { Text(status.replace('_',' '),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) } }
            s.error?.let { code ->
                item(key="media-error") {
                    QuietStateCard(
                        title=mediaError(code),
                        subtitle=code,
                        isError=true
                    )
                }
            }
            item(key="media-mode") {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=s.mode==MediaMode.VIDEO,onClick={viewModel.mode(MediaMode.VIDEO)},label={Text(stringResource(R.string.media_video))},enabled=!s.submitting)
                    FilterChip(selected=s.mode==MediaMode.AUDIO,onClick={viewModel.mode(MediaMode.AUDIO)},label={Text(stringResource(R.string.media_audio_only))},enabled=!s.submitting)
                }
            }
            s.asset?.let { asset ->
                item(key="media-asset") {
                    QuietCard(Modifier.fillMaxWidth()) {
                        asset.thumbnailUrl?.let { thumb ->
                            AsyncImage(
                                model = thumb,
                                contentDescription = asset.title,
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(asset.title,style=MaterialTheme.typography.titleLarge)
                        Text(listOfNotNull(asset.extractor, asset.uploader).joinToString(" · "),style=MaterialTheme.typography.labelMedium)
                        asset.durationSeconds?.let { Text(stringResource(R.string.media_duration,it.toInt())) }
                        Text(stringResource(R.string.media_actual_qualities),style=MaterialTheme.typography.bodySmall)
                    }
                }
                items(s.choices,key={it.key}) { choice ->
                    QuietCard(onClick=if(!s.submitting) ({viewModel.select(choice.key)}) else null,modifier=Modifier.fillMaxWidth()) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            RadioButton(selected=choice.key==s.selectedKey,onClick={viewModel.select(choice.key)},enabled=!s.submitting)
                            Column(Modifier.weight(1f)) {
                                val video=choice.formats.firstOrNull { it.hasVideo }
                                val audio=choice.formats.firstOrNull { it.hasAudio }
                                val quality=if(s.mode==MediaMode.VIDEO) video?.height?.let { "${it}p" } ?: stringResource(R.string.media_resolution_unknown)
                                    else audio?.audioBitrateKbps?.let { "${it.toInt()} kb/s" } ?: stringResource(R.string.media_bitrate_unknown)
                                Text(quality,style=MaterialTheme.typography.titleMedium)
                                Text(choice.formats.joinToString(" + ") { f -> listOfNotNull(f.extension.uppercase(Locale.ROOT),f.videoCodec.takeIf { f.hasVideo },f.audioCodec.takeIf { f.hasAudio },f.language).joinToString(" / ") },style=MaterialTheme.typography.bodySmall)
                                video?.fps?.let { Text("${it} fps",style=MaterialTheme.typography.labelSmall) }
                                val size=choice.estimatedBytes?.let(::mediaBytes) ?: stringResource(R.string.media_size_unknown)
                                Text(if(choice.sizeIsApproximate) stringResource(R.string.media_approximate,size) else size,style=MaterialTheme.typography.bodySmall)
                                if(choice.requiresMux) Text(stringResource(R.string.media_mux,choice.container.uppercase(Locale.ROOT)),style=MaterialTheme.typography.bodySmall)
                                if(s.mode==MediaMode.VIDEO && !choice.expectsAudio) Text(stringResource(R.string.media_silent),style=MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if(s.choices.isEmpty()) item(key="media-no-choices") {
                    QuietStateCard(title=stringResource(R.string.media_no_choices),subtitle=stringResource(R.string.media_direct_instead),actionLabel=stringResource(R.string.media_direct_instead),onAction={onDirect(s.url)})
                }
                if(s.mode==MediaMode.AUDIO) {
                    item(key="media-audio-fmt") { Text(stringResource(R.string.media_audio_format));LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(AudioOutput.entries,key={it.name}) { output -> FilterChip(s.audio==output,{viewModel.audio(output)},label={Text(if(output==AudioOutput.ORIGINAL) stringResource(R.string.media_original_audio) else output.name)},enabled=!s.submitting) } } }
                    if(s.audio!=AudioOutput.ORIGINAL) item(key="media-audio-bitrate") {
                        Text(stringResource(R.string.media_encoding_bitrate))
                        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { items(listOf(64,96,128,160,192,256,320),key={it}) { rate -> FilterChip(s.bitrate==rate,{viewModel.bitrate(rate)},label={Text("$rate kb/s")},enabled=!s.submitting) } }
                        Text(stringResource(R.string.media_no_upscale),style=MaterialTheme.typography.bodySmall)
                    }
                }
                item(key="media-filename") {OutlinedTextField(s.name,viewModel::name,label={Text(stringResource(R.string.media_filename))},enabled=!s.submitting,modifier=Modifier.fillMaxWidth(),singleLine=true)}
            }
            item(key="media-network-toggles") {
                QuietCard(Modifier.fillMaxWidth()) {
                    MediaToggle(stringResource(R.string.media_wifi),s.wifiOnly,viewModel::wifi,!s.submitting)
                    MediaToggle(stringResource(R.string.media_unmetered),s.unmetered,viewModel::unmetered,!s.submitting)
                    MediaToggle(stringResource(R.string.media_http),s.allowHttp,viewModel::allowHttp,!s.submitting)
                }
            }
            item(key="media-matrix") {
                QuietSectionTitle(stringResource(R.string.media_platform_matrix_title))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(MediaPlatformMatrix.entries,key={it.id}) { entry ->
                        val statusText=when(entry.status) {
                            PlatformVerificationStatus.VERIFIED -> entry.displayName
                            PlatformVerificationStatus.HOST_VERIFIED -> "${entry.displayName}: ${stringResource(R.string.media_status_host_verified)}"
                            PlatformVerificationStatus.UNVERIFIED -> "${entry.displayName}: ${stringResource(R.string.media_status_unverified)}"
                        }
                        QuietBadge(
                            text=statusText,
                            containerColor=if(entry.status==PlatformVerificationStatus.UNVERIFIED) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor=if(entry.status==PlatformVerificationStatus.UNVERIFIED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            item(key="media-new-copy") { TextButton(onClick=viewModel::newCopy,enabled=!s.submitting) { Text(stringResource(R.string.media_new_copy)) } }
            item(key="media-limits") { Text(stringResource(R.string.media_limits),style=MaterialTheme.typography.bodySmall);TextButton(onClick={onDirect(s.url)}) {Text(stringResource(R.string.media_direct_instead))} }
        }
        Surface(tonalElevation=3.dp) {
            QuietPrimaryButton(
                text=stringResource(if(s.submitting) R.string.media_queueing else R.string.media_download_selected),
                onClick={viewModel.submit(onQueued)},
                enabled=s.termsAccepted && s.selectedKey!=null && !s.analyzing && !s.submitting,
                modifier=Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
            )
        }
    }
}
@Composable private fun MediaToggle(label:String,checked:Boolean,onChange:(Boolean)->Unit,enabled:Boolean) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(label,Modifier.weight(1f));Switch(checked,onChange,enabled=enabled) }
}
private fun mediaBytes(value:Long):String = when { value>=1073741824L -> String.format(Locale.ROOT,"%.2f GiB",value/1073741824.0);value>=1048576 -> String.format(Locale.ROOT,"%.1f MiB",value/1048576.0);else -> "$value B" }
@Composable private fun mediaError(code:String):String = stringResource(when(code) {
    "MEDIA_SITE_UNSUPPORTED","UNSUPPORTED" -> R.string.media_error_site
    "MEDIA_PRIVATE_CONTENT","PRIVATE_CONTENT" -> R.string.media_error_private
    "MEDIA_GEO_BLOCKED" -> R.string.media_error_geoblocked
    "MEDIA_REMOVED","REMOVED" -> R.string.media_error_removed
    "MEDIA_ENCRYPTED_SOURCE" -> R.string.media_error_drm
    "MEDIA_SESSION_REQUIRED","MEDIA_SOURCE_REJECTED","LOGIN_REQUIRED" -> R.string.media_error_session
    "MEDIA_RATE_LIMITED","RATE_LIMITED" -> R.string.media_error_rate
    "NETWORK_UNAVAILABLE","MEDIA_NETWORK_ERROR","MEDIA_TLS_FAILED","NETWORK_ERROR" -> R.string.media_error_network
    "MEDIA_NO_USABLE_FORMAT","MEDIA_REANALYSIS_REQUIRED" -> R.string.media_error_format
    "INVALID_URL","MULTIPLE_MEDIA_LINKS" -> R.string.media_error_url
    "MEDIA_BUILD_NOT_ENABLED","MEDIA_NATIVE_ABI_UNAVAILABLE","MEDIA_RUNTIME_INITIALIZATION_FAILED" -> R.string.media_error_runtime
    else -> R.string.media_error_generic
})
