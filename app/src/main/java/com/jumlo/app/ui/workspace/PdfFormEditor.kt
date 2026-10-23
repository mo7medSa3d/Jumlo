package com.jumlo.app.ui.workspace

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.pdf.FormEntry
import com.jumlo.app.data.pdf.FormInputParser
import com.jumlo.app.data.pdf.PdfFormFieldInfo
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.PreviewFrame
import com.jumlo.app.ui.components.QuietCard
import com.jumlo.app.ui.components.QuietStateCard
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Discover real widgets, project upright CropBox coordinates, and focus the corresponding field. */
@Composable
internal fun PdfFormEditor(vm:AppViewModel,sourceId:String,draft:WorkspaceDraft,
    edit:((WorkspaceDraft)->WorkspaceDraft)->Unit,onReady:(Boolean)->Unit,onImport:()->Unit={}) {
    var retry by remember(sourceId) {mutableIntStateOf(0)}
    var page by rememberSaveable(sourceId) {mutableIntStateOf(1)}
    var selectedName by rememberSaveable(sourceId) {mutableStateOf<String?>(null)}
    var requestFocus by remember {mutableIntStateOf(0)}
    val focus=remember {FocusRequester()}
    val result by produceState<Result<List<PdfFormFieldInfo>>?>(null,sourceId,retry) {
        value=null
        value=try {Result.success(vm.pdfFormFields(sourceId))} catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
    }
    val preview by produceState<Result<PreviewFrame>?>(null,sourceId,page,retry) {
        value=null
        value=try {Result.success(vm.preview(sourceId,page))} catch(cancel:CancellationException) {throw cancel} catch(error:Exception) {Result.failure(error)}
    }
    DisposeOwnedPreviewBitmap(preview?.getOrNull()?.bitmap)
    val fields=result?.getOrNull().orEmpty().filter {it.kind in setOf("text","checkbox","radio")}
    val values=remember(draft.text1) {runCatching {FormInputParser.parse(draft.text1).mapValues {(_,entry)->(entry as FormEntry.Input).value}}.getOrNull()}
    val valid=values!=null && values.keys.all {key->fields.any {it.name==key && !it.readOnly}}
    val ready=result?.isSuccess==true && fields.isNotEmpty() && valid
    val latestReady by rememberUpdatedState(onReady)
    LaunchedEffect(ready,sourceId) {latestReady(ready)}
    fun setValue(name:String,value:String) {
        edit {current->
            val currentValues=runCatching {FormInputParser.parse(current.text1).mapValues {(_,entry)->(entry as FormEntry.Input).value}}.getOrDefault(emptyMap())
            current.copy(text1=JsonObject((currentValues+(name to value)).mapValues {JsonPrimitive(it.value)}).toString())
        }
    }
    val active=fields.firstOrNull {it.name==selectedName} ?: fields.firstOrNull()
    LaunchedEffect(requestFocus,active?.name,valid) {
        if(requestFocus>0 && valid && active?.kind=="text" && !active.readOnly) focus.requestFocus()
    }
    QuietCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.interactive_form_title),style=QuietTheme.typography.sectionTitle)
        Text(stringResource(R.string.studio_form_tap),style=QuietTheme.typography.metadata)
        val frame=preview?.getOrNull()
        if(frame!=null) {
            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                val aspect=frame.width.toFloat()/frame.height
                val width=minOf(maxWidth,520.dp*aspect);val height=width/aspect
                Box(Modifier.size(width,height).testTag("form.page")) {
                    Image(frame.bitmap.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.FillBounds)
                    fields.forEach {field->field.widgets.filter {it.page==page}.forEach {widget->
                        val value=values?.get(field.name) ?: field.value
                        val label=stringResource(R.string.studio_form_field,field.name)
                        val text=when(field.kind) {
                            "checkbox"->if(values?.get(field.name)?.toBooleanStrictOrNull() ?: field.checked) "✓" else "□"
                            "radio"->if((values?.get(field.name)?.toIntOrNull() ?: field.selectedIndex)==widget.widgetIndex) "●" else "○"
                            else->value.ifBlank {field.name}
                        }
                        Surface(onClick={
                            selectedName=field.name
                            if(!field.readOnly) when(field.kind) {
                                "checkbox"->setValue(field.name,(!(values?.get(field.name)?.toBooleanStrictOrNull() ?: field.checked)).toString())
                                "radio"->setValue(field.name,widget.widgetIndex.toString())
                                "text"->requestFocus++
                            }
                        },shape=QuietTheme.shapes.thumbnail,
                            color=if(active?.name==field.name) MaterialTheme.colorScheme.primaryContainer.copy(alpha=.88f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.7f),
                            modifier=Modifier.absoluteOffset(width*widget.left.toFloat(),height*widget.top.toFloat())
                                .size((width*(widget.right-widget.left).toFloat()).coerceAtLeast(48.dp),(height*(widget.bottom-widget.top).toFloat()).coerceAtLeast(48.dp))
                                .semantics {contentDescription=label}.testTag("form.widget.${field.name}.${widget.widgetIndex}")) {
                            Box(Modifier.padding(4.dp),contentAlignment=Alignment.CenterStart) {Text(text,style=QuietTheme.typography.metadata,maxLines=2)}
                        }
                    }}
                }
            }
            DocumentPageControls(page,frame.count) {page=it}
        } else if(preview==null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else Text(stringResource(R.string.msg_preview_unavailable),color=MaterialTheme.colorScheme.error)
        when {
            result==null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            result?.isFailure==true -> QuietStateCard(stringResource(R.string.interactive_form_error),stringResource(R.string.msg_preview_unavailable),
                stringResource(R.string.print_preview_retry),{retry++},isError=true)
            fields.isEmpty() -> QuietStateCard(stringResource(R.string.studio_form_no_fields),stringResource(R.string.interactive_form_empty),stringResource(R.string.import_device_files),onImport)
            !valid -> QuietStateCard(stringResource(R.string.interactive_form_invalid_draft),stringResource(R.string.interactive_form_changes),
                stringResource(R.string.interactive_form_reset),{edit {it.copy(text1="{}")}},isError=true)
            else -> {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(fields,key={it.name}) {field->
                        FilterChip(border=null,selected=active?.name==field.name,onClick={selectedName=field.name;field.widgets.firstOrNull()?.let {page=it.page};requestFocus++},label={Text(field.name)})
                    }
                }
                active?.let {field->
                    val value=values?.get(field.name)
                    when(field.kind) {
                        "text"->TextField(value=value ?: field.value,onValueChange={setValue(field.name,it.take(8192))},label={Text(field.name)},
                            readOnly=field.readOnly,maxLines=5,modifier=Modifier.fillMaxWidth().focusRequester(focus).testTag("form.input.${field.name}"),
                            shape=QuietTheme.shapes.inputField,colors=borderlessFieldColors())
                        "checkbox"->if(!field.readOnly) InteractiveToggle(field.name,value?.equals("true",true) ?: field.checked) {checked->setValue(field.name,checked.toString())}
                        "radio"->if(!field.readOnly) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(field.choices) {index,option->FilterChip(border=null,selected=(value?.toIntOrNull() ?: field.selectedIndex)==index,
                                onClick={setValue(field.name,index.toString())},label={Text(option)})}
                        }
                    }
                    if(field.readOnly) Text(stringResource(R.string.interactive_form_read_only),style=QuietTheme.typography.metadata)
                }
                InteractiveToggle(stringResource(R.string.field_flatten_form),draft.flag1) {v->edit {it.copy(flag1=v)}}
                TextButton(onClick={edit {it.copy(text1="{}")}},enabled=values?.isNotEmpty()==true) {Text(stringResource(R.string.interactive_form_reset))}
            }
        }
    }
}
