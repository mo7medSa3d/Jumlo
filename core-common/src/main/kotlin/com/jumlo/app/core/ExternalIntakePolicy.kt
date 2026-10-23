package com.jumlo.app.core

/** External shares start a compatible workspace; in-workspace pickers keep their existing tool. */
object ExternalIntakePolicy {
    fun toolFor(mimeTypes: List<String?>): String? = when {
        mimeTypes.isEmpty() -> null
        mimeTypes.all { it.equals("application/pdf", true) } -> if (mimeTypes.size == 1) "pdf_04" else "pdf_03"
        mimeTypes.all { it?.startsWith("image/", true) == true } -> "pdf_02"
        mimeTypes.size == 1 -> OutputWorkflowPolicy.reuseTool(mimeTypes.single().orEmpty())
        else -> "file_07"
    }

    fun jobId(value: String?): String? = value?.takeIf(OutputWorkflowPolicy::isId)
}
