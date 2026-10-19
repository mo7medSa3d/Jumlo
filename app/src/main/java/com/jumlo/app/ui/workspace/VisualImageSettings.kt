package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.theme.QuietTheme
import kotlin.math.roundToInt

/** Preset/slider controls write directly to the existing validated execution draft. */
@Composable
internal fun VisualImageSettings(draft: WorkspaceDraft, edit: ((WorkspaceDraft) -> WorkspaceDraft) -> Unit) {
    val imageResize=draft.toolId=="img_02"
    val imageAdjust=draft.toolId=="img_adjust"
    val compress=draft.toolId in setOf("img_04","pdf_compress")
    val pdfToImage=draft.toolId=="pdf_07"
    val toPdf=draft.toolId=="pdf_02"
    val watermark=draft.toolId=="img_watermark"
    if(!(imageResize||imageAdjust||compress||pdfToImage||toPdf||watermark)) return
    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLow,
        modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.visual_edit_title),style=QuietTheme.typography.sectionTitle)
            if(imageResize) {
                val percent=(draft.percentage.toIntOrNull() ?: 100).coerceIn(10,300)
                Text(stringResource(R.string.visual_edit_resize),style=QuietTheme.typography.metadataStrong)
                Slider(value=percent.toFloat(),onValueChange={value->
                    edit { it.copy(percentage=value.roundToInt().toString(),longestEdge="") }
                },valueRange=10f..300f)
                Text("$percent%",style=QuietTheme.typography.metadata)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf(50,75,100,150).forEach { value ->
                        FilterChip(percent==value,
                            onClick={edit {it.copy(percentage=value.toString(),longestEdge="")}},
                            label={Text("$value%")})
                    }
                }
                Text(stringResource(R.string.visual_edit_preserve_ratio),
                    style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(imageAdjust) {
                IntensitySlider(stringResource(R.string.field_brightness),draft.num1.toIntOrNull()?:0,-100,100,0) {
                    edit { f-> f.copy(num1=it.toString()) }
                }
                IntensitySlider(stringResource(R.string.field_contrast),draft.num2.toIntOrNull()?:100,0,300,100) {
                    edit { f-> f.copy(num2=it.toString()) }
                }
                IntensitySlider(stringResource(R.string.field_saturation),draft.num3.toIntOrNull()?:100,0,300,100) {
                    edit { f-> f.copy(num3=it.toString()) }
                }
            }
            if(compress || pdfToImage) {
                IntensitySlider(stringResource(R.string.field_quality),
                    draft.quality.toIntOrNull()?:85,10,100,85) {
                    edit { f-> f.copy(quality=it.toString()) }
                }
                if(draft.toolId=="pdf_compress") {
                    Text(stringResource(R.string.visual_edit_quality_note),
                        style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(toPdf) {
                Text(stringResource(R.string.jumlo_paper_mode),style=QuietTheme.typography.metadataStrong)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("AUTO","A4","LETTER").forEach { option ->
                        FilterChip(draft.paperMode==option,onClick={
                            edit { it.copy(paperMode=option) }
                        },label={Text(option)})
                    }
                }
                Text(stringResource(R.string.field_fill_page),style=QuietTheme.typography.metadataStrong)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!draft.fill,onClick={edit {it.copy(fill=false)}},
                        label={Text(stringResource(R.string.ui_fit_contain))})
                    FilterChip(draft.fill,onClick={edit {it.copy(fill=true)}},
                        label={Text(stringResource(R.string.ui_fit_cover))})
                }
            }
            if(watermark) {
                IntensitySlider(stringResource(R.string.field_opacity_percent),
                    draft.num1.toIntOrNull()?:40,1,100,40) {
                    edit {f->f.copy(num1=it.toString())}
                }
            }
        }
    }
}
@Composable
private fun IntensitySlider(label:String,current:Int,minimum:Int,maximum:Int,reset:Int,onChange:(Int)->Unit) {
    val safe=current.coerceIn(minimum,maximum)
    Row {
        Text(label,modifier=Modifier.weight(1f),style=QuietTheme.typography.metadataStrong)
        TextButton(onClick={onChange(reset)}) { Text(stringResource(R.string.visual_edit_reset)) }
    }
    Slider(value=safe.toFloat(),valueRange=minimum.toFloat()..maximum.toFloat(),
        onValueChange={onChange(it.roundToInt())})
    Text("$safe",style=QuietTheme.typography.metadata)
}
