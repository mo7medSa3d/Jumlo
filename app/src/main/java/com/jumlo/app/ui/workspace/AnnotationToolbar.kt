package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.core.PdfAnnotationKind
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.theme.QuietTheme
import kotlin.math.roundToInt

@Composable
internal fun AnnotationToolbar(draft:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    val current=PdfAnnotationKind.from(draft.text2)
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=QuietTheme.shapes.card) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            LazyRow(Modifier.testTag("annotation.tools"),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                items(PdfAnnotationKind.entries.toList(),key={it.id}) {kind->
                    val label=stringResource(annotationKindLabel(kind))
                    Surface(onClick={edit {it.copy(text2=kind.id)}},shape=QuietTheme.shapes.thumbnail,
                        color=if(current==kind) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier=Modifier.widthIn(min=72.dp).heightIn(min=64.dp).testTag("annotation.kind.${kind.id}").semantics {contentDescription=label;selected=current==kind}) {
                        Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Icon(painterResource(annotationIcon(kind)),null,Modifier.size(24.dp))
                            Text(label,style=QuietTheme.typography.metadata)
                        }
                    }
                }
            }
            Text(stringResource(R.string.watermark_colors),style=QuietTheme.typography.metadataStrong)
            LazyRow(Modifier.testTag("annotation.colors"),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                items(listOf("#FFEB3B" to R.string.studio_ink_yellow,"#111111" to R.string.studio_ink_black,
                    "#1746A2" to R.string.studio_ink_blue,"#B42332" to R.string.studio_ink_red,"#145D4C" to R.string.studio_ink_green)) {(hex,label)->
                    val chosen=draft.annotationColorHex.equals(hex,true)
                    val name=stringResource(label)
                    Surface(onClick={edit {it.copy(annotationColorHex=hex)}},color=Color(android.graphics.Color.parseColor(hex)),shape=CircleShape,
                        modifier=Modifier.size(48.dp).testTag("annotation.color.$hex").semantics {contentDescription=name;selected=chosen}) {
                        Box(contentAlignment=Alignment.Center) {if(chosen) Text("✓",color=if(hex=="#FFEB3B") Color.Black else Color.White)}
                    }
                }
            }
            if(current in setOf(PdfAnnotationKind.UNDERLINE,PdfAnnotationKind.STRIKETHROUGH,PdfAnnotationKind.CIRCLE,PdfAnnotationKind.SQUARE,PdfAnnotationKind.INK)) InteractiveIntSlider(stringResource(R.string.studio_annotation_thickness),draft.annotationThicknessPt.roundToInt(),1,12,2) {v->edit {it.copy(annotationThicknessPt=v.toFloat())}}
            if(current.carriesText) TextField(draft.text1,{value->edit {it.copy(text1=value.take(8192))}},label={Text(stringResource(R.string.field_text))},
                modifier=Modifier.fillMaxWidth(),shape=QuietTheme.shapes.inputField,colors=borderlessFieldColors())
        }
    }
}

private fun annotationIcon(kind:PdfAnnotationKind):Int=when(kind) {
    PdfAnnotationKind.HIGHLIGHT->R.drawable.ic_annotation_highlight
    PdfAnnotationKind.UNDERLINE->R.drawable.ic_annotation_underline
    PdfAnnotationKind.STRIKETHROUGH->R.drawable.ic_annotation_strikethrough
    PdfAnnotationKind.NOTE->R.drawable.ic_annotation_note
    PdfAnnotationKind.SQUARE->R.drawable.ic_annotation_square
    PdfAnnotationKind.CIRCLE->R.drawable.ic_annotation_circle
    PdfAnnotationKind.TEXTBOX->R.drawable.ic_annotation_textbox
    PdfAnnotationKind.INK->R.drawable.ic_annotation_ink
    PdfAnnotationKind.TEXT->R.drawable.ic_annotation_text
}
