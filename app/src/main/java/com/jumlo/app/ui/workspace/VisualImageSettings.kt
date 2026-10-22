package com.jumlo.app.ui.workspace

import com.jumlo.app.core.PdfCompressionPolicy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.theme.QuietTheme
import com.jumlo.app.ui.util.rememberAppHaptics
import kotlin.math.roundToInt

/** The name shown for a compression preset. */
internal fun PdfCompressionPolicy.Preset.labelRes(): Int = when (this) {
    PdfCompressionPolicy.Preset.STRONG -> R.string.visual_compress_strong
    PdfCompressionPolicy.Preset.SMALL_FILE -> R.string.visual_compress_small
    PdfCompressionPolicy.Preset.BALANCED -> R.string.visual_compress_balanced
    PdfCompressionPolicy.Preset.BEST_QUALITY -> R.string.visual_compress_best
}

/** Preset/slider controls write directly to the existing validated execution draft. */
@Composable
internal fun VisualImageSettings(draft: WorkspaceDraft, edit: ((WorkspaceDraft) -> WorkspaceDraft) -> Unit) {
    val imageResize=draft.toolId=="img_02"
    val imageAdjust=draft.toolId=="img_adjust"
    val compress=draft.toolId in setOf("img_04","pdf_compress")
    val pdfToImage=draft.toolId=="pdf_07"
    val toPdf=draft.toolId=="pdf_02"
    val watermark=draft.toolId=="img_watermark"
    val haptics=rememberAppHaptics()
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
                Text(stringResource(R.string.visual_resize_hint),
                    style=QuietTheme.typography.metadata,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                val sizes=listOf(
                    50 to R.string.visual_scale_small,
                    75 to R.string.visual_scale_medium,
                    100 to R.string.visual_scale_original,
                    150 to R.string.visual_scale_large
                )
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(sizes,key={it.first}) { (value,label) ->
                        FilterChip(percent==value,
                            onClick={
                                haptics.selectionToggle()
                                edit { it.copy(percentage=value.toString(),longestEdge="") }
                            },
                            label={Text(stringResource(label))})
                    }
                }
                Text(stringResource(R.string.visual_edit_preserve_ratio),
                    style=QuietTheme.typography.metadata,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(imageAdjust) {
                val filters=listOf(
                    listOf(R.string.visual_filter_original,0,100,100),
                    listOf(R.string.visual_filter_document,10,150,0),
                    listOf(R.string.visual_filter_vivid,5,115,135),
                    listOf(R.string.visual_filter_grayscale,0,110,0),
                    listOf(R.string.visual_filter_contrast,12,185,0)
                )
                val brightness=draft.num1.toIntOrNull()?:0
                val contrast=draft.num2.toIntOrNull()?:100
                val saturation=draft.num3.toIntOrNull()?:100
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(filters,key={it[0]}) { preset ->
                        FilterChip(
                            brightness==preset[1] && contrast==preset[2] && saturation==preset[3],
                            onClick={
                                haptics.selectionToggle()
                                edit { it.copy(num1=preset[1].toString(),
                                    num2=preset[2].toString(),num3=preset[3].toString()) }
                            },
                            label={Text(stringResource(preset[0]))}
                        )
                    }
                }
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
            if(draft.toolId=="pdf_compress") {
                Text(stringResource(R.string.visual_compression_note),
                    style=QuietTheme.typography.metadata,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                // The presets come from the policy, so the panel and the policy
                // cannot drift apart. Each chip shows the reduction it reached on
                // the measured corpus.
                val presets=PdfCompressionPolicy.Preset.entries.toList()
                val actualDpi=draft.num1.toIntOrNull()?:150
                val actualQuality=draft.quality.toIntOrNull()?:85
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(presets,key={it.name}) { option ->
                        val range=option.measuredSavingPercent
                        FilterChip(actualDpi==option.dpi && actualQuality==option.quality,
                            onClick={
                                haptics.selectionToggle()
                                // A leftover target size would put the tool on the
                                // other ladder and ignore the preset, so it is
                                // cleared with the preset.
                                edit { it.copy(num1=option.dpi.toString(),
                                    quality=option.quality.toString(),num2="") }
                            },label={Text("${stringResource(option.labelRes())} · $range%")})
                    }
                }
                Text(stringResource(R.string.visual_edit_quality_note),
                    style=QuietTheme.typography.metadata,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else if(compress || pdfToImage) {
                IntensitySlider(stringResource(R.string.field_quality),
                    draft.quality.toIntOrNull()?:85,10,100,85) {
                    edit { f-> f.copy(quality=it.toString()) }
                }
                // Compression may store the result as WebP; say so instead of renaming silently.
                if(draft.toolId=="img_04") {
                    Text(stringResource(R.string.visual_compress_webp_note),
                        style=QuietTheme.typography.metadata,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
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
    val haptics=rememberAppHaptics()
    Slider(value=safe.toFloat(),valueRange=minimum.toFloat()..maximum.toFloat(),
        onValueChange={onChange(it.roundToInt())},
        onValueChangeFinished={haptics.selectionToggle()})
}
