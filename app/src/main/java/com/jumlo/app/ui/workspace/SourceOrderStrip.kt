package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateBefore
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateNext
import com.jumlo.app.ui.icons.rounded.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.jumlo.app.ui.icons.rounded.SwapHoriz
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.SourceRef
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException

/** Input order is the export order; controls stay visible beside the actual source thumbnail. */
@Composable
internal fun SourceOrderStrip(vm:AppViewModel,sources:List<SourceRef>,selectedId:String?,logoId:String?,
    onSelect:(String)->Unit,onMove:(Int,Int)->Unit,renamed:List<String>?=null) {
    val latestSources by rememberUpdatedState(sources)
    val latestMove by rememberUpdatedState(onMove)
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val pageCount by produceState<Result<Int>?>(null,sources.map {it.id}) {
        value=null
        if(sources.isNotEmpty() && sources.all {it.mimeType=="application/pdf"}) {
            value=try {Result.success(sources.sumOf {source ->
                val thumbnail=vm.thumbnail(source.id,1)
                try {thumbnail.count} finally {thumbnail.bitmap.recycle()}
            })} catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {Result.failure(error)}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.interactive_input_order),style=QuietTheme.typography.sectionTitle)
        Text(stringResource(R.string.interactive_input_order_help),style=QuietTheme.typography.metadata)
        if(sources.all {it.mimeType=="application/pdf"}) {
            when {
                pageCount==null -> Text(stringResource(R.string.studio_merge_counting),style=QuietTheme.typography.metadata)
                pageCount?.isSuccess==true -> Text(stringResource(R.string.studio_merge_pages,sources.size,pageCount!!.getOrThrow()),style=QuietTheme.typography.metadataStrong)
                else -> Text(stringResource(R.string.msg_preview_unavailable),style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.error)
            }
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            itemsIndexed(sources,key={_,source->source.id}) {index,source ->
                val description=stringResource(R.string.interactive_source_position,index+1,sources.size,source.displayName)
                val frame by produceState<PreviewFrame?>(null,source.id) {
                    value=null
                    if(source.mimeType=="application/pdf" || source.mimeType?.startsWith("image/")==true)
                        value=try {vm.thumbnail(source.id,1)}
                            catch(cancel:CancellationException) {throw cancel}
                            catch(_:Exception) {null}
                }
                var displacement by remember(source.id) {mutableFloatStateOf(0f)}
                val dragDescription=stringResource(R.string.studio_drag_reorder,source.displayName)
                Column(Modifier.width(148.dp).animateItem().graphicsLayer {translationX=displacement},horizontalAlignment=Alignment.CenterHorizontally) {
                    Surface(onClick={onSelect(source.id)},shape=RoundedCornerShape(16.dp),
                        color=if(selectedId==source.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier=Modifier.fillMaxWidth().semantics {contentDescription=description;selected=selectedId==source.id}) {
                        Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth().height(94.dp),contentAlignment=Alignment.Center) {
                                val image=frame
                                if(image!=null && !image.bitmap.isRecycled) Image(image.bitmap.asImageBitmap(),null,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize())
                                else Icon(Icons.Rounded.Description,null,Modifier.size(32.dp))
                            }
                            Text("${index+1}. ${source.displayName}",maxLines=2,overflow=TextOverflow.Ellipsis,style=QuietTheme.typography.metadata)
                            renamed?.getOrNull(index)?.let { name ->
                                Text(name,style=QuietTheme.typography.metadataStrong,color=MaterialTheme.colorScheme.primary)
                            }
                            if(source.id==logoId) Text(stringResource(R.string.interactive_logo_source),style=QuietTheme.typography.metadataStrong)
                        }
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={onMove(index,-1)},enabled=index>0) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateBefore,stringResource(R.string.jumlo_move_earlier))
                        }
                        Icon(Icons.Rounded.SwapHoriz,dragDescription,Modifier.size(48.dp)
                            .pointerInput(source.id,rtl) {
                                detectDragGesturesAfterLongPress(
                                    onDragEnd={displacement=0f},onDragCancel={displacement=0f},
                                    onDrag={change,delta ->
                                        change.consume();displacement+=delta.x
                                        val steps=(displacement/158.dp.toPx()).toInt()
                                        if(steps!=0) {
                                            val current=latestSources.indexOfFirst {it.id==source.id}
                                            if(current>=0) latestMove(current,if(rtl) -steps else steps)
                                            displacement-=steps*158.dp.toPx()
                                        }
                                    })
                            }.padding(12.dp))
                        IconButton(onClick={onMove(index,1)},enabled=index<sources.lastIndex) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateNext,stringResource(R.string.jumlo_move_later))
                        }
                    }
                }
            }
        }
    }
}
