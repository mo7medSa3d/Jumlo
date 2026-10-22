package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jumlo.app.R
import com.jumlo.app.data.pdf.FormEntry
import com.jumlo.app.data.pdf.FormInputParser
import com.jumlo.app.data.pdf.PdfFormFieldInfo
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import com.jumlo.app.ui.components.QuietCard
import com.jumlo.app.ui.theme.QuietTheme
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Discover real AcroForm fields; JSON is an internal serialization detail. */
@Composable
internal fun PdfFormEditor(vm:AppViewModel,sourceId:String,draft:WorkspaceDraft,
    edit:((WorkspaceDraft)->WorkspaceDraft)->Unit,onReady:(Boolean)->Unit) {
    var retry by remember(sourceId) {mutableIntStateOf(0)}
    val result by produceState<Result<List<PdfFormFieldInfo>>?>(null,sourceId,retry) {
        value=null
        value=try {Result.success(vm.pdfFormFields(sourceId))}
            catch(cancel:CancellationException) {throw cancel}
            catch(error:Exception) {Result.failure(error)}
    }
    val fields=result?.getOrNull().orEmpty().filter {it.kind in setOf("text","checkbox","radio")}
    val draftValues=remember(draft.text1) {
        runCatching {FormInputParser.parse(draft.text1).mapValues {(_,entry)->(entry as FormEntry.Input).value}}.getOrNull()
    }
    val valid=draftValues!=null && draftValues.keys.all {key->fields.any {it.name==key && !it.readOnly}}
    val ready=result?.isSuccess==true && fields.isNotEmpty() && valid
    val latestReady by rememberUpdatedState(onReady)
    LaunchedEffect(ready,sourceId) {latestReady(ready)}
    fun setValue(name:String,value:String) {
        edit {current ->
            val values=runCatching {FormInputParser.parse(current.text1).mapValues {(_,entry)->(entry as FormEntry.Input).value}}.getOrDefault(emptyMap())
            current.copy(text1=JsonObject((values+(name to value)).mapValues {JsonPrimitive(it.value)}).toString())
        }
    }
    QuietCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.interactive_form_title),style=QuietTheme.typography.sectionTitle)
        when {
            result==null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            result?.isFailure==true -> {
                Text(stringResource(R.string.interactive_form_error),color=MaterialTheme.colorScheme.error)
                TextButton(onClick={retry++}) {Text(stringResource(R.string.print_preview_retry))}
            }
            fields.isEmpty() -> Text(stringResource(R.string.interactive_form_empty))
            !valid -> {
                Text(stringResource(R.string.interactive_form_invalid_draft),color=MaterialTheme.colorScheme.error)
                TextButton(onClick={edit {it.copy(text1="{}")}}) {Text(stringResource(R.string.interactive_form_reset))}
            }
            else -> {
                Text(stringResource(R.string.interactive_form_changes),style=QuietTheme.typography.metadata)
                fields.forEach {field ->
                    key(field.name) {
                        val value=draftValues?.get(field.name)
                        when(field.kind) {
                            "text" -> OutlinedTextField(value=value ?: field.value,
                                onValueChange={setValue(field.name,it.take(8192))},
                                label={Text(field.name)},readOnly=field.readOnly,
                                supportingText=if(field.readOnly) ({Text(stringResource(R.string.interactive_form_read_only))}) else null,
                                maxLines=5,modifier=Modifier.fillMaxWidth())
                            "checkbox" -> {
                                if(field.readOnly) Text("${field.name} · ${stringResource(R.string.interactive_form_read_only)}",style=QuietTheme.typography.metadata)
                                else InteractiveToggle(field.name,value?.equals("true",true) ?: field.checked) {
                                    checked->setValue(field.name,checked.toString())
                                }
                            }
                            "radio" -> {
                                Text(field.name,style=QuietTheme.typography.metadataStrong)
                                if(field.readOnly) Text(field.value,style=QuietTheme.typography.metadata)
                                else LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(field.choices) {index,option ->
                                        FilterChip(selected=(value?.toIntOrNull() ?: field.selectedIndex)==index,
                                            onClick={setValue(field.name,index.toString())},label={Text(option)})
                                    }
                                }
                            }
                        }
                    }
                }
                InteractiveToggle(stringResource(R.string.field_flatten_form),draft.flag1) {v->edit {it.copy(flag1=v)}}
                TextButton(onClick={edit {it.copy(text1="{}")}},enabled=draftValues?.isNotEmpty()==true) {
                    Text(stringResource(R.string.interactive_form_reset))
                }
            }
        }
    }
}
