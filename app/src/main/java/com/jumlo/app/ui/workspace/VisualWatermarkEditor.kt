package com.jumlo.app.ui.workspace

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.OverlayStylePolicy
import com.jumlo.app.data.processing.BitmapExtras
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.icons.Icons
import com.jumlo.app.ui.icons.rounded.Check
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared interactive PDF/image watermark controls use the exact serialized production geometry. */
@Composable
internal fun VisualWatermarkEditor(vm:AppViewModel,sourceId:String,draft:WorkspaceDraft,
    edit:((WorkspaceDraft)->WorkspaceDraft)->Unit,overlayId:String?=null,onImportImage:()->Unit={}) {
    val imageTool=draft.toolId=="img_watermark"
    val useImage=if(imageTool) draft.flag1 else draft.watermarkUseImage
    var page by rememberSaveable(sourceId) {mutableIntStateOf(1)}
    var retry by remember {mutableIntStateOf(0)}
    var selected by rememberSaveable(sourceId) {mutableStateOf<String?>("watermark")}
    val result by produceState<Result<PreviewFrame>?>(null,sourceId,page,retry) {
        value=null
        value=try {Result.success(vm.preview(sourceId,if(imageTool) 1 else page))}
            catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
    }
    val logo by produceState<Result<PreviewFrame>?>(null,overlayId,retry) {
        value=null
        if(overlayId!=null) value=try {Result.success(vm.preview(overlayId,1))}
            catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
    }
    val textBitmap by produceState<Bitmap?>(null,imageTool,useImage,draft.text1,draft.watermarkFont,draft.text3) {
        value=null
        if(imageTool && !useImage) {
            var pending:Bitmap?=null
            try {
                val rendered=withContext(Dispatchers.Default) {
                    runCatching {BitmapExtras.watermarkTextBitmap(draft.text1.ifBlank {"Jumlo"},draft.watermarkFont,draft.text3.ifBlank {"#FFFFFF"})}
                        .getOrNull().also {pending=it}
                }
                value=rendered
                pending=null
            } finally {pending?.takeUnless {it.isRecycled}?.recycle()}
        }
    }
    DisposeOwnedPreviewBitmap(result?.getOrNull()?.bitmap)
    DisposeOwnedPreviewBitmap(logo?.getOrNull()?.bitmap)
    DisposeOwnedPreviewBitmap(textBitmap)
    val centerX=(WorkspaceDraft.normalizeDigits(draft.num2).toFloatOrNull() ?: .5f).coerceIn(0f,1f)
    val centerY=(WorkspaceDraft.normalizeDigits(draft.num3).toFloatOrNull() ?: .5f).coerceIn(0f,1f)
    val opacity=draftNumber(if(imageTool) draft.num1 else draft.num4,if(imageTool) 40 else 28).coerceIn(if(imageTool) 1 else 5,100)
    val rotation=draftNumber(draft.text4,if(imageTool) 0 else -35).coerceIn(-180,180)
    val tiled=draft.text2=="TILE"
    val color=draft.text3.ifBlank {if(imageTool) "#FFFFFF" else "#64748B"}
    fun position(x:Float,y:Float) {edit {it.copy(num2=x.toString(),num3=y.toString())}}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.watermark_title),style=QuietTheme.typography.sectionTitle)
        Text(stringResource(if(tiled) R.string.studio_watermark_tile_hint else R.string.studio_watermark_gesture),style=QuietTheme.typography.metadata)
        val frame=result?.getOrNull()
        when {
            frame!=null -> {
                val applies=imageTool || page in selectedPageNumbers(draft.pages,frame.count)
                if(!imageTool && !useImage) {
                    PdfWatermarkPreview(frame,draft,page,applies,centerX,centerY,::position,{},
                        onScaleRotation={size,angle->edit {it.copy(num1=size.toString(),text4=angle.toString())}})
                } else {
                    val bitmap=if(useImage) logo?.getOrNull()?.bitmap else textBitmap
                    val objects=if(bitmap==null || !applies) emptyList() else {
                        val dimensions=OverlayStylePolicy.size(frame.width.toDouble(),frame.height.toDouble(),bitmap.width.toDouble(),bitmap.height.toDouble(),draft.watermarkScale)
                        val w=(dimensions.first/frame.width).toFloat();val h=(dimensions.second/frame.height).toFloat()
                        OverlayStylePolicy.centers(centerX.toDouble(),centerY.toDouble(),tiled).mapIndexed {index,(x,y)->
                            PreviewObject(if(tiled) "tile-$index" else "watermark",bitmap,x.toFloat()-w/2,y.toFloat()-h/2,x.toFloat()+w/2,y.toFloat()+h/2,rotation.toFloat(),opacity/100f)
                        }
                    }
                    TransformablePagePreview(frame,objects,selected,{selected=it},onTransform={obj->
                        val oldWidth=objects.firstOrNull {it.id==obj.id}?.width ?: obj.width
                        val newScale=(draft.watermarkScale*obj.width/oldWidth.coerceAtLeast(.001f)).coerceIn(.1,3.0)
                        edit {it.copy(num2=if(tiled) it.num2 else obj.center.x.toString(),num3=if(tiled) it.num3 else obj.center.y.toString(),
                            watermarkScale=newScale,text4=obj.rotation.roundToInt().toString())}
                    },tag=if(imageTool) "image.watermark.canvas" else "pdf.logo.canvas",
                        pageNumber=page.takeIf {!imageTool && draft.flag1 && applies},allowTranslation=!tiled)
                    if(useImage && overlayId==null) QuietStateCard(stringResource(R.string.studio_watermark_import_logo),stringResource(R.string.ui_watermark_logo_required),
                        stringResource(R.string.import_photo_gallery),onImportImage)
                    if(useImage && logo?.isFailure==true) QuietStateCard(stringResource(R.string.msg_preview_unavailable),stringResource(R.string.ui_watermark_logo_required),
                        stringResource(R.string.interactive_preview_retry),{retry++},isError=true)
                }
                if(!applies) Text(stringResource(R.string.interactive_page_not_selected),style=QuietTheme.typography.metadata)
                if(!imageTool) DocumentPageControls(page,frame.count) {page=it}
            }
            result?.isFailure==true -> QuietStateCard(stringResource(R.string.watermark_preview_error),stringResource(R.string.msg_preview_unavailable),
                stringResource(R.string.interactive_preview_retry),{retry++},isError=true)
            else -> LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(false to R.string.studio_watermark_text_mode,true to R.string.studio_watermark_image_mode).forEach {(value,label)->
                FilterChip(border=null,selected=useImage==value,onClick={edit {current->
                    val ids=if(!value && overlayId!=null) current.sourceIds-overlayId else current.sourceIds
                    if(imageTool) current.copy(flag1=value,sourceIds=ids) else current.copy(watermarkUseImage=value,sourceIds=ids)
                }},label={Text(stringResource(label))})
            }
        }
        if(!useImage) {
            TextField(draft.text1,{text->edit {it.copy(text1=text.take(120))}},label={Text(stringResource(R.string.watermark_text))},
                modifier=Modifier.fillMaxWidth(),maxLines=2,shape=QuietTheme.shapes.inputField,colors=borderlessFieldColors())
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(listOf(R.string.watermark_preset_confidential,R.string.watermark_preset_draft,R.string.watermark_preset_copy)) {labelId->
                    val label=stringResource(labelId)
                    FilterChip(border=null,selected=draft.text1==label,onClick={edit {it.copy(text1=label)}},label={Text(label)})
                }
            }
            Text(stringResource(R.string.studio_font),style=QuietTheme.typography.metadataStrong)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(listOf("sans" to R.string.studio_font_sans,"serif" to R.string.studio_font_serif,"mono" to R.string.studio_font_mono)) {(font,label)->
                    FilterChip(border=null,selected=draft.watermarkFont==font,onClick={edit {it.copy(watermarkFont=font)}},label={Text(stringResource(label))})
                }
            }
            Text(stringResource(R.string.watermark_colors),style=QuietTheme.typography.metadataStrong)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                items(listOf("#FFFFFF" to R.string.studio_ink_white,"#111111" to R.string.studio_ink_black,"#64748B" to R.string.watermark_color_slate,
                    "#1746A2" to R.string.studio_ink_blue,"#B42332" to R.string.studio_ink_red,"#145D4C" to R.string.studio_ink_green)) {(hex,label)->
                    val name=stringResource(label);val chosen=color.equals(hex,true)
                    Surface(onClick={edit {it.copy(text3=hex)}},shape=CircleShape,color=Color(android.graphics.Color.parseColor(hex)),
                        modifier=Modifier.size(48.dp).semantics {contentDescription=name;this.selected=chosen},shadowElevation=2.dp) {
                        Box(contentAlignment=Alignment.Center) {if(chosen) Icon(Icons.Rounded.Check,null,tint=if(hex=="#FFFFFF") Color.Black else Color.White)}
                    }
                }
            }
        }
        InteractiveIntSlider(stringResource(R.string.watermark_opacity),opacity,if(imageTool) 1 else 5,100,if(imageTool) 40 else 28) {v->
            edit {if(imageTool) it.copy(num1=v.toString()) else it.copy(num4=v.toString())}
        }
        if(imageTool || useImage) InteractiveIntSlider(stringResource(R.string.studio_watermark_scale),(draft.watermarkScale*100).roundToInt(),10,300,100) {v->edit {it.copy(watermarkScale=v/100.0)}}
        else InteractiveIntSlider(stringResource(R.string.watermark_size),draftNumber(draft.num1,42),12,90,42) {v->edit {it.copy(num1=v.toString())}}
        InteractiveIntSlider(stringResource(R.string.watermark_rotation),rotation,-180,180,if(imageTool) 0 else -35) {v->edit {it.copy(text4=v.toString())}}
        LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(listOf(-90,-45,0,45,90)) {angle->FilterChip(border=null,selected=rotation==angle,onClick={edit {it.copy(text4=angle.toString())}},label={Text("$angle°")})}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(border=null,selected=!tiled,onClick={edit {it.copy(text2="SINGLE")}},label={Text(stringResource(R.string.watermark_single))})
            FilterChip(border=null,selected=tiled,onClick={edit {it.copy(text2="TILE")}},label={Text(stringResource(R.string.watermark_tile))})
        }
        if(!tiled) {
            Text(stringResource(R.string.studio_watermark_placement),style=QuietTheme.typography.metadataStrong)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(listOf(Triple(R.string.studio_top_left,.2f,.2f),Triple(R.string.studio_top_right,.8f,.2f),Triple(R.string.studio_center,.5f,.5f),
                    Triple(R.string.studio_bottom_left,.2f,.8f),Triple(R.string.studio_bottom_right,.8f,.8f))) {(label,x,y)->
                    FilterChip(border=null,selected=abs(centerX-x)<.01f && abs(centerY-y)<.01f,onClick={position(x,y)},label={Text(stringResource(label))})
                }
            }
            InteractiveIntSlider(stringResource(R.string.editor_horizontal_position),(centerX*100).roundToInt(),0,100,50) {v->position(v/100f,centerY)}
            InteractiveIntSlider(stringResource(R.string.editor_vertical_position),(centerY*100).roundToInt(),0,100,50) {v->position(centerX,v/100f)}
        }
        if(!imageTool) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(border=null,selected=draft.pages.equals("all",true),onClick={edit {it.copy(pages="all")}},label={Text(stringResource(R.string.watermark_all_pages))})
                FilterChip(border=null,selected=draft.pages==page.toString(),onClick={edit {it.copy(pages=page.toString())}},label={Text(stringResource(R.string.watermark_this_page))})
            }
            InteractiveToggle(stringResource(R.string.watermark_page_numbers),draft.flag1) {v->edit {it.copy(flag1=v)}}
        }
    }
}
