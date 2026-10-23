package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.PdfCompressionPolicy
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.theme.QuietTheme
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

internal fun PdfCompressionPolicy.Preset.labelRes(): Int = when(this) {
    PdfCompressionPolicy.Preset.STRONG -> R.string.visual_compress_strong
    PdfCompressionPolicy.Preset.SMALL_FILE -> R.string.visual_compress_small
    PdfCompressionPolicy.Preset.BALANCED -> R.string.visual_compress_balanced
    PdfCompressionPolicy.Preset.BEST_QUALITY -> R.string.visual_compress_best
}

/** Primary controls write the same validated draft consumed by the worker. */
@Composable
internal fun VisualImageSettings(draft:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    val tool=draft.toolId
    if(!tool.startsWith("img_") && tool !in setOf("pdf_02","pdf_07","pdf_compress")) return
    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
        modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.visual_edit_title),style=QuietTheme.typography.sectionTitle)
            if(tool=="img_02") {
                val percentMode=draft.percentage.isNotBlank()
                val edgeMode=draft.longestEdge.isNotBlank()
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    item {FilterChip(percentMode,border=null,onClick={edit {it.copy(percentage="75",longestEdge="")}},
                        label={Text(stringResource(R.string.interactive_resize_scale))})}
                    item {FilterChip(edgeMode,border=null,onClick={edit {it.copy(percentage="",longestEdge="1920")}},
                        label={Text(stringResource(R.string.interactive_resize_edge))})}
                    item {FilterChip(!percentMode && !edgeMode,border=null,onClick={edit {it.copy(percentage="",longestEdge="")}},
                        label={Text(stringResource(R.string.interactive_resize_dimensions))})}
                }
                when {
                    percentMode -> {
                        InteractiveIntSlider(stringResource(R.string.interactive_scale_percent),draftNumber(draft.percentage,75),1,400,100) {
                            value -> edit {it.copy(percentage=value.toString(),longestEdge="")}
                        }
                        ChoiceValues(listOf(25,50,75,100,150,200),draftNumber(draft.percentage,75),{"$it%"}) {
                            value -> edit {it.copy(percentage=value.toString(),longestEdge="")}
                        }
                        Text(stringResource(R.string.visual_edit_preserve_ratio),style=QuietTheme.typography.metadata)
                    }
                    edgeMode -> {
                        InteractiveIntSlider(stringResource(R.string.field_longest_edge_opt),draftNumber(draft.longestEdge,1920),64,8192,1920) {
                            value -> edit {it.copy(longestEdge=value.toString(),percentage="")}
                        }
                        ChoiceValues(listOf(640,1280,1920,2560,4096),draftNumber(draft.longestEdge,1920),{"$it px"}) {
                            value -> edit {it.copy(longestEdge=value.toString(),percentage="")}
                        }
                    }
                    else -> {
                        InteractiveIntSlider(stringResource(R.string.field_target_width),draftNumber(draft.width,1600),1,8192,1600) {v->edit {it.copy(width=v.toString())}}
                        InteractiveIntSlider(stringResource(R.string.field_target_height),draftNumber(draft.height,1600),1,8192,1600) {v->edit {it.copy(height=v.toString())}}
                        PresetChoices(stringResource(R.string.field_fit_strategy),listOf(
                            "FIT" to R.string.ui_fit_contain,"FILL" to R.string.ui_fit_cover,
                            "PAD" to R.string.ui_fit_pad,"STRETCH" to R.string.ui_fit_stretch),draft.fit) {v->edit {it.copy(fit=v)}}
                    }
                }
                if(!percentMode) InteractiveToggle(stringResource(R.string.field_allow_upscaling),draft.allowUpscaling) {v->edit {it.copy(allowUpscaling=v)}}
            }
            if(tool=="img_adjust") {
                val filters=listOf(
                    listOf(R.string.visual_filter_original,0,100,100),
                    listOf(R.string.visual_filter_document,10,150,0),
                    listOf(R.string.visual_filter_vivid,5,115,135),
                    listOf(R.string.visual_filter_grayscale,0,110,0),
                    listOf(R.string.visual_filter_contrast,12,185,0))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(filters,key={it[0]}) { preset ->
                        FilterChip(draftNumber(draft.num1,0)==preset[1] && draftNumber(draft.num2,100)==preset[2] && draftNumber(draft.num3,100)==preset[3],
                            border=null,
                            onClick={edit {it.copy(num1=preset[1].toString(),num2=preset[2].toString(),num3=preset[3].toString())}},
                            label={Text(stringResource(preset[0]))})
                    }
                }
                InteractiveIntSlider(stringResource(R.string.field_brightness),draftNumber(draft.num1,0),-100,100,0) {v->edit {it.copy(num1=v.toString())}}
                InteractiveIntSlider(stringResource(R.string.field_contrast),draftNumber(draft.num2,100),0,300,100) {v->edit {it.copy(num2=v.toString())}}
                InteractiveIntSlider(stringResource(R.string.field_saturation),draftNumber(draft.num3,100),0,300,100) {v->edit {it.copy(num3=v.toString())}}
                InteractiveIntSlider(stringResource(R.string.field_padding_px),draftNumber(draft.num4,0),0,2000,0) {v->edit {it.copy(num4=v.toString())}}
            }
            if(tool in setOf("img_join","img_stitch")) {
                if(tool=="img_join") VisualChoiceCards(listOf(
                    "GRID2" to R.string.ui_layout_grid2,"HSTRIP" to R.string.ui_layout_hstrip,"VSTRIP" to R.string.ui_layout_vstrip),
                    draft.text1.ifBlank {"GRID2"},paper=false) {v->edit {it.copy(text1=v)}}
                InteractiveIntSlider(stringResource(R.string.field_gap_px),draftNumber(draft.num1,8),0,500,8) {v->edit {it.copy(num1=v.toString())}}
                if(tool=="img_stitch") InteractiveIntSlider(stringResource(R.string.field_overlap_px),draftNumber(draft.num2,0),0,400,0) {v->edit {it.copy(num2=v.toString())}}
            }
            if(tool=="img_watermark") InteractiveIntSlider(stringResource(R.string.field_opacity_percent),draftNumber(draft.num1,40),1,100,40) {v->edit {it.copy(num1=v.toString())}}
            if(tool=="img_target") ImageTargetControls(draft,edit)
            if(tool=="pdf_compress") {
                Text(stringResource(R.string.visual_compression_note),style=QuietTheme.typography.metadata)
                InteractiveToggle(stringResource(R.string.interactive_compression_target),draft.num2.isNotBlank()) {enabled->
                    edit {it.copy(num2=if(enabled) "5" else "")}
                }
                if(draft.num2.isNotBlank()) {
                    InteractiveIntSlider(stringResource(R.string.field_target_size_mb),draftNumber(draft.num2,5),1,100,5) {v->edit {it.copy(num2=v.toString())}}
                    Text(stringResource(R.string.field_target_size_note),style=QuietTheme.typography.metadata)
                }
            }
            if(tool=="pdf_07") {
                PresetChoices(stringResource(R.string.field_output_format),listOf("PNG" to R.string.interactive_png,"JPEG" to R.string.interactive_jpeg),draft.format) {v->edit {it.copy(format=v)}}
                InteractiveIntSlider(stringResource(R.string.field_dpi),draftNumber(draft.dpi,144),36,600,144) {v->edit {it.copy(dpi=v.toString())}}
                ChoiceValues(listOf(72,144,300,600),draftNumber(draft.dpi,144),{"$it DPI"}) {v->edit {it.copy(dpi=v.toString())}}
                InteractiveToggle(stringResource(R.string.field_grayscale),draft.grayscale) {v->edit {it.copy(grayscale=v)}}
            }
            if(tool=="pdf_02") VisualChoiceCards(listOf(
                "AUTO" to R.string.visual_auto,"A4" to R.string.visual_a4,"LETTER" to R.string.visual_letter,
                "CUSTOM" to R.string.jumlo_custom_paper),draft.paperMode,paper=true) {v->edit {it.copy(paperMode=v)}}
            if(tool=="pdf_02" && draft.paperMode=="CUSTOM") {
                InteractiveIntSlider(stringResource(R.string.field_paper_width),draftNumber(draft.paperWidth,595),72,1440,595) {v->edit {it.copy(paperWidth=v.toString())}}
                InteractiveIntSlider(stringResource(R.string.field_paper_height),draftNumber(draft.paperHeight,842),72,1440,842) {v->edit {it.copy(paperHeight=v.toString())}}
            }
            val imageOutput=tool.startsWith("img_") && tool !in setOf("img_target","img_compare")
            if(imageOutput && !(tool=="img_exif" && !draft.flag1)) {
                Text(stringResource(R.string.field_output_format),style=QuietTheme.typography.metadataStrong)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(listOf("JPEG","PNG","WEBP")) {format ->
                        FilterChip(draft.format==format,border=null,onClick={edit {it.copy(format=format)}},label={Text(format)})
                    }
                }
            }
            if((imageOutput || tool=="pdf_07") && (draft.format!="PNG" || tool=="img_exif" && !draft.flag1))
                InteractiveIntSlider(stringResource(R.string.field_quality),draftNumber(draft.quality,85),1,100,85) {v->edit {it.copy(quality=v.toString())}}
            if(tool=="img_04") Text(stringResource(R.string.interactive_compression_format_note),style=QuietTheme.typography.metadata)
        }
    }
}

@Composable
internal fun ImageTargetControls(draft:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    val target=draftNumber(draft.num1,200000).coerceIn(1000,100000000)
    val label=stringResource(R.string.interactive_target_size,PureDomainAlgorithms.formatBytes(target.toLong()))
    Text(stringResource(R.string.ui_target_jpeg_policy),style=QuietTheme.typography.metadata)
    Text(label,style=QuietTheme.typography.metadataStrong)
    Slider(value=log10(target.toFloat()),valueRange=3f..8f,
        onValueChange={v->edit {it.copy(num1=10.0.pow(v.toDouble()).roundToInt().coerceIn(1000,100000000).toString())}},
        modifier=Modifier.semantics {contentDescription=label;stateDescription=label})
    ChoiceValues(listOf(100000,200000,500000,1000000,2000000,5000000),target,{PureDomainAlgorithms.formatBytes(it.toLong())}) {v->edit {it.copy(num1=v.toString())}}
}

@Composable
private fun ChoiceValues(values:List<Int>,selected:Int,label:(Int)->String,onSelect:(Int)->Unit) {
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(values) {value->FilterChip(selected==value,border=null,onClick={onSelect(value)},label={Text(label(value))})}
    }
}

@Composable
internal fun PresetChoices(label:String,values:List<Pair<String,Int>>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=QuietTheme.typography.metadataStrong)
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(values,key={it.first}) {(value,labelId)->FilterChip(selected==value,border=null,onClick={onSelect(value)},label={Text(stringResource(labelId))})}
    }
}

/** Visual paper/layout choices keep their aspect ratios visible before a real input is loaded. */
@Composable
private fun VisualChoiceCards(options:List<Pair<String,Int>>,selected:String,paper:Boolean,onSelect:(String)->Unit) {
    val accent=MaterialTheme.colorScheme.primary
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        items(options,key={it.first}) {(value,label) ->
            Surface(onClick={onSelect(value)},shape=QuietTheme.shapes.inputField,
                color=if(selected==value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier=Modifier.width(104.dp).heightIn(min=124.dp)) {
                Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                        if(paper) {
                            val ratio=when(value) {"A4"->.707f;"LETTER"->.773f;"AUTO"->1f;else->.85f}
                            val h=size.height;val w=h*ratio
                            drawRoundRect(Color.White,Offset((size.width-w)/2,0f),Size(w,h),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                            drawRoundRect(accent.copy(alpha=.22f),Offset((size.width-w)/2+w*.12f,h*.12f),Size(w*.76f,h*.76f),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        } else {
                            val columns=if(value=="VSTRIP") 1 else 2
                            val rows=if(value=="HSTRIP") 1 else 2
                            val gap=4.dp.toPx()
                            val w=(size.width-gap*(columns-1))/columns;val h=(size.height-gap*(rows-1))/rows
                            for(row in 0 until rows) for(column in 0 until columns) {
                                drawRoundRect(accent.copy(alpha=.55f+.1f*((row+column)%2)),Offset(column*(w+gap),row*(h+gap)),Size(w,h),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                            }
                        }
                    }
                    Text(stringResource(label),style=QuietTheme.typography.metadataStrong)
                }
            }
        }
    }
}
