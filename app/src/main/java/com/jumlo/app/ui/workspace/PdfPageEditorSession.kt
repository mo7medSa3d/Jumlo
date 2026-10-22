package com.jumlo.app.ui.workspace

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.jumlo.app.data.processing.PdfPageEdit

/**
 * WorkspaceRoute owns this ViewModel. Large document/history/selection lists stay in memory
 * across configuration changes and never enter the Activity saved-state Bundle. After process
 * death the current document is seeded from the durable WorkspaceDraft; undo history starts fresh.
 */
internal class PdfPageEditorSession : ViewModel() {
    private var documentKey:String?=null
    private var active:PdfPageEditorSessionState?=null

    fun forDocument(key:String,initialPages:List<PdfPageEdit>):PdfPageEditorSessionState {
        if(documentKey==key) active?.let { return it }
        return PdfPageEditorSessionState(initialPages).also {
            documentKey=key
            active=it // One active document per route: switching releases the previous large history.
        }
    }
}

internal class PdfPageEditorSessionState(initialPages:List<PdfPageEdit>) {
    val history=mutableStateOf(PageEditHistory(initialPages))
    val selection=mutableStateOf<List<String>>(emptyList())
}
