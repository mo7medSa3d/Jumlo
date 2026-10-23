package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.engine.PureDomainAlgorithms.formatBytes
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.model.SourceRef
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.components.userMessageResource
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Description
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException

@Composable
internal fun DuplicateResultsPanel(vm:AppViewModel,sources:List<SourceRef>,onImport:()->Unit) {
    var retry by remember {mutableIntStateOf(0)}
    val ids=sources.map {it.id}
    val result by produceState<Result<List<List<SourceRef>>>?>(null,ids,retry) {
        value=null
        value=try {Result.success(vm.duplicateSourceGroups(ids))} catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.studio_duplicates_title),style=QuietTheme.typography.sectionTitle)
        when {
            result==null->{LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.studio_duplicates_loading))}
            result?.isFailure==true->QuietStateCard(stringResource(userMessageResource(stableError(result!!.exceptionOrNull()!!))),stringResource(R.string.field_duplicates_note),
                stringResource(R.string.interactive_preview_retry),{retry++},isError=true)
            result?.getOrNull().isNullOrEmpty()->QuietStateCard(stringResource(R.string.studio_duplicates_none),stringResource(R.string.studio_duplicates_none_body),stringResource(R.string.import_device_files),onImport)
            else->LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                result!!.getOrThrow().forEachIndexed {index,group->
                    item("group-$index") {Text(stringResource(R.string.studio_duplicates_group,index+1,group.size),style=QuietTheme.typography.metadataStrong)}
                    items(group,key={it.id}) {source->
                        val frame by produceState<PreviewFrame?>(null,source.id) {
                            value=try {vm.thumbnail(source.id,1)} catch(cancel:CancellationException) {throw cancel} catch(_:Exception) {null}
                        }
                        Surface(shape=QuietTheme.shapes.card,color=MaterialTheme.colorScheme.surfaceContainerLow) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(68.dp),contentAlignment=Alignment.Center) {
                                    frame?.let {Image(it.bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
                                        ?: Icon(Icons.Rounded.Description,null)
                                }
                                Column(Modifier.weight(1f)) {Text(source.displayName,style=QuietTheme.typography.metadataStrong);Text(source.sizeBytes?.let(::formatBytes) ?: stringResource(R.string.media_size_unknown),style=QuietTheme.typography.metadata)}
                                TextButton(onClick={vm.removeSource(source.id)}) {Text(stringResource(R.string.action_remove))}
                            }
                        }
                    }
                }
            }
        }
    }
}
