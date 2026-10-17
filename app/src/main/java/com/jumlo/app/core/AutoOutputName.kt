package com.jumlo.app.core

/** Filename is derived from the first source; users never need a naming prompt. */
object AutoOutputName {
    private val operations = mapOf(
        "pdf_02" to "PDF", "pdf_03" to "Merged", "pdf_04" to "Organized",
        "pdf_05" to "Split", "pdf_06" to "Extracted", "pdf_crop" to "Cropped",
        "pdf_compress" to "Compressed", "pdf_stamp" to "Stamped", "pdf_annotate" to "Annotated",
        "pdf_signature" to "Signed", "pdf_metadata" to "Updated", "pdf_forms" to "Filled",
        "pdf_protect" to "Protected", "pdf_redact" to "Redacted",
        "file_07" to "Archive", "file_encrypted_zip" to "Archive"
    )
    fun fromSource(toolId:String,displayName:String):String? {
        val operation=operations[toolId] ?: return null
        val base=displayName.substringBeforeLast('.',displayName).replace(Regex("[\\/:*?\"<>|\\p{Cc}\\p{Cf}]")," ")
            .replace(Regex("\\s+")," ").trim(' ','.').take(112).ifBlank { "File" }
        val extension=if(toolId.startsWith("file_")) "zip" else "pdf"
        return SafeNames.basename("$base - $operation.$extension")
    }
}
