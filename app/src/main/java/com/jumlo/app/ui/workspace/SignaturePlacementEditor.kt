package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.processing.PdfSignaturePlacement
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateBefore
import com.jumlo.app.ui.icons.automirrored.rounded.NavigateNext
import com.jumlo.app.ui.icons.rounded.*
import com.jumlo.app.ui.scan.SignaturePad
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.roundToInt

private val placementsHistorySaver=listSaver<List<List<PdfSignaturePlacement>>,String>(
    save={history->history.map {Json.encodeToString(it)}},
    restore={history->history.map {Json.decodeFromString<List<PdfSignaturePlacement>>(it)}})

/** Every visible instance maps to a serialized page/box/rotation consumed by the PDF engine. */
@Composable
internal fun SignaturePlacementEditor(vm:AppViewModel,sourceId:String,signatureId:String?,draft:WorkspaceDraft,
    edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    var page by rememberSaveable(sourceId) {mutableIntStateOf(draftNumber(draft.num1,1).coerceAtLeast(1))}
    var selectedId by rememberSaveable(sourceId) {mutableStateOf<String?>(null)}
    var past by rememberSaveable(sourceId,stateSaver=placementsHistorySaver) {mutableStateOf(emptyList<List<PdfSignaturePlacement>>())}
    var future by rememberSaveable(sourceId,stateSaver=placementsHistorySaver) {mutableStateOf(emptyList<List<PdfSignaturePlacement>>())}
    var gestureBefore by remember(sourceId) {mutableStateOf<List<PdfSignaturePlacement>?>(null)}
    var showPad by rememberSaveable(sourceId) {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    val frame by produceState<Result<PreviewFrame>?>(null,sourceId,page,retry) {
        value=null
        value=try {Result.success(vm.preview(sourceId,page))} catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
    }
    val signature by produceState<PreviewFrame?>(null,signatureId,retry) {
        value=null
        if(signatureId!=null) value=try {vm.preview(signatureId,1)} catch(cancel:CancellationException) {throw cancel} catch(_:Exception) {null}
    }
    DisposeOwnedPreviewBitmap(frame?.getOrNull()?.bitmap)
    DisposeOwnedPreviewBitmap(signature?.bitmap)
    val latestDraft by rememberUpdatedState(draft)
    val latestEdit by rememberUpdatedState(edit)
    fun record(before:List<PdfSignaturePlacement>) {past=(past+listOf(before)).takeLast(30);future=emptyList()}
    fun replace(placements:List<PdfSignaturePlacement>) {latestEdit {it.copy(signaturePlacements=placements)}}
    fun updatePlacement(id:String,change:(PdfSignaturePlacement)->PdfSignaturePlacement) {
        latestEdit {current->current.copy(signaturePlacements=current.signaturePlacements.map {if(it.id==id) change(it) else it})}
    }
    LaunchedEffect(sourceId,signatureId) {
        if(signatureId!=null && latestDraft.signaturePlacements.isEmpty()) {
            fun fraction(raw:String,fallback:Double)=WorkspaceDraft.normalizeDigits(raw).toDoubleOrNull()?.coerceIn(0.0,1.0) ?: fallback
            val current=latestDraft
            val placement=PdfSignaturePlacement(UUID.randomUUID().toString(),page,
                fraction(current.cropLeft,.6),fraction(current.cropTop,.75),fraction(current.cropRight,.95),fraction(current.cropBottom,.95),current.signatureRotationDegrees)
            replace(listOf(placement));selectedId=placement.id
        }
    }
    val placements=draft.signaturePlacements
    val selected=placements.firstOrNull {it.id==selectedId && it.page==page}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.studio_signature_place),style=QuietTheme.typography.sectionTitle,modifier=Modifier.weight(1f))
            IconButton(enabled=past.isNotEmpty(),onClick={
                future=(future+listOf(placements)).takeLast(30);replace(past.last());past=past.dropLast(1)
            },modifier=Modifier.testTag("signature.placement.undo")) {Icon(Icons.Rounded.Undo,stringResource(R.string.visual_edit_undo))}
            IconButton(enabled=future.isNotEmpty(),onClick={
                past=(past+listOf(placements)).takeLast(30);replace(future.last());future=future.dropLast(1)
            }) {Icon(Icons.Rounded.Redo,stringResource(R.string.visual_edit_redo))}
        }
        Text(stringResource(R.string.studio_signature_gesture),style=QuietTheme.typography.metadata)
        val current=frame?.getOrNull()
        when {
            current!=null -> {
                val image=signature?.bitmap
                val objects=if(image==null) emptyList() else placements.filter {it.page==page}.map {p->
                    PreviewObject(p.id,image,p.left.toFloat(),p.top.toFloat(),p.right.toFloat(),p.bottom.toFloat(),p.rotationDegrees)
                }
                TransformablePagePreview(current,objects,selectedId,{selectedId=it},onTransform={obj->
                    if(gestureBefore==null) gestureBefore=latestDraft.signaturePlacements
                    updatePlacement(obj.id) {it.copy(left=obj.left.toDouble(),top=obj.top.toDouble(),right=obj.right.toDouble(),bottom=obj.bottom.toDouble(),rotationDegrees=obj.rotation)}
                },onCommit={gestureBefore?.let(::record);gestureBefore=null},tag="signature.object.canvas",description=stringResource(R.string.studio_signature_place))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={page--;selectedId=null},enabled=page>1) {Icon(Icons.AutoMirrored.Rounded.NavigateBefore,stringResource(R.string.watermark_previous_page))}
                    Text(stringResource(R.string.watermark_page_counter,page,current.count))
                    IconButton(onClick={page++;selectedId=null},enabled=page<current.count) {Icon(Icons.AutoMirrored.Rounded.NavigateNext,stringResource(R.string.watermark_next_page))}
                }
            }
            frame?.isFailure==true -> QuietStateCard(stringResource(R.string.msg_preview_unavailable),stringResource(R.string.interactive_signature_failed),
                stringResource(R.string.interactive_preview_retry),{retry++},isError=true)
            else -> LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if(signatureId!=null) {
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                itemsIndexed(placements,key={_,it->it.id}) {index,placement->
                    FilterChip(border=null,selected=selectedId==placement.id,onClick={page=placement.page;selectedId=placement.id},
                        label={Text(stringResource(R.string.studio_signature_instance,index+1,placement.page))})
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled=placements.size<100 && signature!=null,onClick={
                    record(placements)
                    val next=PdfSignaturePlacement(UUID.randomUUID().toString(),page,.3,.4,.65,.6)
                    replace(placements+next);selectedId=next.id
                },modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("signature.placement.add")) {
                    Icon(Icons.Rounded.Add,null);Text(stringResource(R.string.studio_signature_add))
                }
                IconButton(enabled=selected!=null && placements.size>1,onClick={record(placements);replace(placements.filterNot {it.id==selectedId});selectedId=null}) {
                    Icon(Icons.Rounded.Delete,stringResource(R.string.action_delete))
                }
            }
            selected?.let {item ->
                InteractiveIntSlider(stringResource(R.string.watermark_rotation),item.rotationDegrees.roundToInt(),-180,180,0) {rotation ->
                    updatePlacement(item.id) {it.copy(rotationDegrees=rotation.toFloat())}
                }
                InteractiveIntSlider(stringResource(R.string.watermark_size),((item.right-item.left)*100).roundToInt(),5,90,35) {percent->
                    val width=percent/100.0;val ratio=(item.bottom-item.top)/(item.right-item.left)
                    val height=(width*ratio).coerceAtMost(.95);val actualWidth=height/ratio
                    val cx=((item.left+item.right)/2).coerceIn(actualWidth/2,1-actualWidth/2)
                    val cy=((item.top+item.bottom)/2).coerceIn(height/2,1-height/2)
                    updatePlacement(item.id) {it.copy(left=cx-actualWidth/2,right=cx+actualWidth/2,top=cy-height/2,bottom=cy+height/2)}
                }
            }
            TextButton(onClick={showPad=!showPad}) {Text(stringResource(R.string.studio_signature_replace))}
        }
        if(signatureId==null || showPad) SignaturePad {file->
            signatureId?.let(vm::removeSource)
            vm.intakeLocalFile(file,"signature.png","image/png");showPad=false
        }
    }
}
