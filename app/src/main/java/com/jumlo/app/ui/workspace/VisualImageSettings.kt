package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.PdfCompressionPolicy
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.theme.QuietTheme

private val IMAGE_OUTPUT_FORMATS = listOf("JPEG","PNG","WEBP")
private val VISUAL_PDF_TOOLS = setOf("pdf_02","pdf_07","pdf_long_image","pdf_compress")
private val SCALE_PRESETS = listOf(25,50,75,100,150,200)
private val EDGE_PRESETS = listOf(640,1280,1920,2560,4096)
private val FIT_STRATEGIES = listOf("FIT" to R.string.ui_fit_contain,"FILL" to R.string.ui_fit_cover,"PAD" to R.string.ui_fit_pad,"STRETCH" to R.string.ui_fit_stretch)
private val JOIN_LAYOUTS = listOf("GRID2" to R.string.ui_layout_grid2,"HSTRIP" to R.string.ui_layout_hstrip,"VSTRIP" to R.string.ui_layout_vstrip)
private val PDF_IMAGE_FORMATS = listOf("PNG" to R.string.interactive_png,"JPEG" to R.string.interactive_jpeg)
private val DPI_PRESETS = listOf(72,144,300,600)
private val PAPER_MODES = listOf("AUTO" to R.string.visual_auto,"A4" to R.string.visual_a4,"LETTER" to R.string.visual_letter,"CUSTOM" to R.string.jumlo_custom_paper)

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
    if(!tool.startsWith("img_") && tool !in VISUAL_PDF_TOOLS) return
    Surface(shape=QuietTheme.shapes.card,color=MaterialTheme.colorScheme.surfaceContainerLow,
        modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.visual_edit_title),style=QuietTheme.typography.sectionTitle,modifier=Modifier.semantics { heading() })
            if(tool=="img_02") {
                val percentMode=draft.percentage.isNotBlank()
                val edgeMode=draft.longestEdge.isNotBlank()
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    item(key="scale",contentType="mode-chip") {FilterChip(percentMode,shape=QuietTheme.shapes.pill,border=null,onClick={edit {it.copy(percentage="75",longestEdge="")}},
                        label={Text(stringResource(R.string.interactive_resize_scale))})}
                    item(key="edge",contentType="mode-chip") {FilterChip(edgeMode,shape=QuietTheme.shapes.pill,border=null,onClick={edit {it.copy(percentage="",longestEdge="1920")}},
                        label={Text(stringResource(R.string.interactive_resize_edge))})}
                    item(key="dimensions",contentType="mode-chip") {FilterChip(!percentMode && !edgeMode,shape=QuietTheme.shapes.pill,border=null,onClick={edit {it.copy(percentage="",longestEdge="")}},
                        label={Text(stringResource(R.string.interactive_resize_dimensions))})}
                }
                when {
                    percentMode -> {
                        InteractiveIntSlider(stringResource(R.string.interactive_scale_percent),draftNumber(draft.percentage,75),1,400,100) {
                            value -> edit {it.copy(percentage=value.toString(),longestEdge="")}
                        }
                        ChoiceValues(SCALE_PRESETS,draftNumber(draft.percentage,75),{"$it%"}) {
                            value -> edit {it.copy(percentage=value.toString(),longestEdge="")}
                        }
                        Text(stringResource(R.string.visual_edit_preserve_ratio),style=QuietTheme.typography.metadata)
                    }
                    edgeMode -> {
                        InteractiveIntSlider(stringResource(R.string.field_longest_edge_opt),draftNumber(draft.longestEdge,1920),64,8192,1920) {
                            value -> edit {it.copy(longestEdge=value.toString(),percentage="")}
                        }
                        ChoiceValues(EDGE_PRESETS,draftNumber(draft.longestEdge,1920),{"$it px"}) {
                            value -> edit {it.copy(longestEdge=value.toString(),percentage="")}
                        }
                    }
                    else -> {
                        InteractiveIntSlider(stringResource(R.string.field_target_width),draftNumber(draft.width,1600),1,8192,1600) {v->edit {it.copy(width=v.toString())}}
                        InteractiveIntSlider(stringResource(R.string.field_target_height),draftNumber(draft.height,1600),1,8192,1600) {v->edit {it.copy(height=v.toString())}}
                        PresetChoices(stringResource(R.string.field_fit_strategy),FIT_STRATEGIES,draft.fit) {v->edit {it.copy(fit=v)}}
                    }
                }
                if(!percentMode) InteractiveToggle(stringResource(R.string.field_allow_upscaling),draft.allowUpscaling) {v->edit {it.copy(allowUpscaling=v)}}
            }
            if(tool=="img_join") {
                VisualChoiceCards(JOIN_LAYOUTS,draft.text1.ifBlank {"GRID2"},paper=false) {v->edit {it.copy(text1=v)}}
                InteractiveIntSlider(stringResource(R.string.field_gap_px),draftNumber(draft.num1,8),0,500,8) {v->edit {it.copy(num1=v.toString())}}
            }
            // img_watermark opacity is already controlled in VisualWatermarkEditor.
            if(tool=="pdf_compress") {
                val quality = draft.quality.toIntOrNull() ?: 52
                val strength = (((95 - quality.coerceIn(8, 95)) * 99.0 / 87.0).toInt() + 1).coerceIn(1, 100)
                InteractiveIntSlider(
                    stringResource(R.string.field_compress_slider, strength),
                    strength,
                    1,
                    100,
                    50
                ) { v ->
                    val targetDpi = (300 - ((v - 1) * 264.0 / 99.0)).toInt().coerceIn(36, 300)
                    val targetQuality = (95 - ((v - 1) * 87.0 / 99.0)).toInt().coerceIn(8, 95)
                    edit { it.copy(num1 = targetDpi.toString(), quality = targetQuality.toString(), num2 = "") }
                }
                Text(stringResource(R.string.field_compress_note), style = QuietTheme.typography.metadata)
            }
            if(tool=="pdf_07" || tool=="pdf_long_image") {
                PresetChoices(stringResource(R.string.field_output_format),PDF_IMAGE_FORMATS,draft.format) {v->edit {it.copy(format=v)}}
                InteractiveIntSlider(stringResource(R.string.field_dpi),draftNumber(draft.dpi,144),36,600,144) {v->edit {it.copy(dpi=v.toString())}}
                ChoiceValues(DPI_PRESETS,draftNumber(draft.dpi,144),{"$it DPI"}) {v->edit {it.copy(dpi=v.toString())}}
                InteractiveToggle(stringResource(R.string.field_grayscale),draft.grayscale) {v->edit {it.copy(grayscale=v)}}
            }
            if(tool=="pdf_02") VisualChoiceCards(PAPER_MODES,draft.paperMode,paper=true) {v->edit {it.copy(paperMode=v)}}
            if(tool=="pdf_02" && draft.paperMode=="CUSTOM") {
                InteractiveIntSlider(stringResource(R.string.field_paper_width),draftNumber(draft.paperWidth,595),72,1440,595) {v->edit {it.copy(paperWidth=v.toString())}}
                InteractiveIntSlider(stringResource(R.string.field_paper_height),draftNumber(draft.paperHeight,842),72,1440,842) {v->edit {it.copy(paperHeight=v.toString())}}
            }
            if(tool=="pdf_02") {
                InteractiveToggle(stringResource(R.string.field_pdf_lossless),draft.flag1) {v->edit {it.copy(flag1=v)}}
                if(draft.flag1) Text(stringResource(R.string.field_pdf_lossless_note),style=QuietTheme.typography.metadata)
                else InteractiveIntSlider(stringResource(R.string.field_pdf_quality),draftNumber(draft.quality,85),1,100,85) {v->edit {it.copy(quality=v.toString())}}
            }
            val imageOutput=tool.startsWith("img_")
            if(imageOutput) {
                Text(stringResource(R.string.field_output_format),style=QuietTheme.typography.metadataStrong)
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(IMAGE_OUTPUT_FORMATS,key={it},contentType={"format-chip"}) {format ->
                        FilterChip(draft.format==format,shape=QuietTheme.shapes.pill,border=null,onClick={edit {it.copy(format=format)}},label={Text(format)})
                    }
                }
            }
            if((imageOutput || tool=="pdf_07" || tool=="pdf_long_image") && draft.format!="PNG")
                InteractiveIntSlider(stringResource(R.string.field_quality),draftNumber(draft.quality,85),1,100,85) {v->edit {it.copy(quality=v.toString())}}
            if(tool=="img_04") Text(stringResource(R.string.interactive_compression_format_note),style=QuietTheme.typography.metadata)
        }
    }
}

@Composable
private fun ChoiceValues(values:List<Int>,selected:Int,label:(Int)->String,onSelect:(Int)->Unit) {
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(values,key={it},contentType={"value-chip"}) {value->FilterChip(selected==value,shape=QuietTheme.shapes.pill,border=null,onClick={onSelect(value)},label={Text(label(value))})}
    }
}

@Composable
internal fun PresetChoices(label:String,values:List<Pair<String,Int>>,selected:String,onSelect:(String)->Unit) {
    Text(label,style=QuietTheme.typography.metadataStrong,modifier=Modifier.semantics { heading() })
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(values,key={it.first},contentType={"preset-chip"}) {(value,labelId)->FilterChip(selected==value,shape=QuietTheme.shapes.pill,border=null,onClick={onSelect(value)},label={Text(stringResource(labelId))})}
    }
}

/** Visual paper/layout choices keep their aspect ratios visible before a real input is loaded. */
@Composable
private fun VisualChoiceCards(options:List<Pair<String,Int>>,selected:String,paper:Boolean,onSelect:(String)->Unit) {
    val accent=MaterialTheme.colorScheme.primary
    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        items(options,key={it.first},contentType={"choice-card"}) {(value,label) ->
            Surface(onClick={onSelect(value)},shape=QuietTheme.shapes.inputField,
                color=if(selected==value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier=Modifier.width(104.dp).heightIn(min=124.dp).semantics { this.selected = selected==value }) {
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
