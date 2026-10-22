package com.jumlo.app.ui.workspace

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/** Drawing histories can be large; keep them out of Android's saved-state Bundle. */
internal class VisualDocumentEditorSession:ViewModel() {
    private var key:String?=null
    private var active:VisualDocumentHistory?=null
    fun forDocument(document:String):VisualDocumentHistory {
        if(document==key) active?.let {return it}
        return VisualDocumentHistory().also {key=document;active=it}
    }
}

internal class VisualDocumentHistory {
    val timeline=mutableStateOf(VisualEditTimeline())
}
