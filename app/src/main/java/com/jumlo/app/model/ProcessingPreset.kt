package com.jumlo.app.model

import kotlinx.serialization.Serializable

/** A local, source-free template. Never contains the documents, passwords or text content. */
@Serializable data class ProcessingPreset(
    val name: String,
    val toolId: String,
    val draft: WorkspaceDraft,
    val version: Int = 1
) {
    companion object {
        fun capture(name: String, current: WorkspaceDraft): ProcessingPreset {
            val normalized = name.trim()
            require(normalized.length in 1..48 && normalized.none {it < ' '}) { "INVALID_PRESET_NAME" }
            return ProcessingPreset(normalized, current.toolId, current.copy(
                sourceIds=emptyList(), outputName="", pages="all", expectedHash="",
                text1="", text2="", text3="", text4="",
                secret1="", secret2="", cleartextConsent=false,
                pageEdits=emptyList()
            ))
        }
    }

    fun apply(current: WorkspaceDraft): WorkspaceDraft {
        require(version==1 && current.toolId == toolId) { "PRESET_TOOL_MISMATCH" }
        val d = draft
        return current.copy(
            format=d.format, quality=d.quality, prefix=d.prefix,
            width=d.width, height=d.height, percentage=d.percentage,
            longestEdge=d.longestEdge, fit=d.fit, allowUpscaling=d.allowUpscaling,
            cropLeft=d.cropLeft, cropTop=d.cropTop, cropRight=d.cropRight,
            cropBottom=d.cropBottom, turns=d.turns, flipX=d.flipX, flipY=d.flipY,
            paperWidth=d.paperWidth, paperHeight=d.paperHeight,
            orientation=d.orientation, margin=d.margin, fill=d.fill,
            paperMode=d.paperMode, dpi=d.dpi, grayscale=d.grayscale,
            zipLevel=d.zipLevel, wifiOnly=d.wifiOnly, unmeteredOnly=d.unmeteredOnly,
            num1=d.num1, num2=d.num2, num3=d.num3, num4=d.num4,
            flag1=d.flag1, flag2=d.flag2
            // Never overwrite sourceIds, outputName, pageEdits, text content,
            // passwords, grants, URLs, or per-source page ranges.
        )
    }
}
