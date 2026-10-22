package com.jumlo.app.ui.workspace

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.jumlo.app.R
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.AppViewModel
import kotlinx.coroutines.CancellationException

/** Insertion positions include the slot after the last page. */
@Composable
internal fun PdfInsertionControls(vm:AppViewModel,draft:WorkspaceDraft,edit:((WorkspaceDraft)->WorkspaceDraft)->Unit) {
    val source=draft.sourceIds.firstOrNull() ?: return
    val count by produceState<Int?>(null,source) {
        value=try {
            val frame=vm.thumbnail(source,1)
            try {frame.count} finally {frame.bitmap.recycle()}
        } catch(cancel:CancellationException) {throw cancel}
          catch(_:Exception) {0}
    }
    Column {
        when(val pages=count) {
            null -> LinearProgressIndicator()
            0 -> Text(stringResource(R.string.msg_preview_unavailable))
            else -> InteractiveIntSlider(stringResource(R.string.field_insert_at_page),draftNumber(draft.num1,1),1,pages+1,1) {v->
                edit {it.copy(num1=v.toString())}
            }
        }
    }
}
