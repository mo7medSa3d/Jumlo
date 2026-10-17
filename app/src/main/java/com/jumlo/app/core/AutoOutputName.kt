package com.jumlo.app.core

/** Filename is derived from the first source; users never need a naming prompt. */
object AutoOutputName {
    private val operations = mapOf(
        "pdf_02" to "PDF", "pdf_03" to "Merged", "pdf_04" to "Organized",
        "pdf_05" to "Split", "pdf_06" to "Extracted", "pdf_07" to "Images", "pdf_crop" to "Cropped",
        "pdf_compress" to "Compressed", "pdf_stamp" to "Stamped", "pdf_annotate" to "Annotated",
        "pdf_signature" to "Signed", "pdf_metadata" to "Updated", "pdf_forms" to "Filled",
        "pdf_protect" to "Protected", "pdf_redact" to "Redacted",
        "file_07" to "Archive", "file_encrypted_zip" to "Archive"
    )
    fun cleanBaseName(displayName:String, fallback:String="File"):String {
        return displayName.substringBeforeLast('.',displayName).replace(Regex("[\\/:*?\"<>|\\p{Cc}\\p{Cf}]")," ")
            .replace(Regex("\\s+")," ").trim(' ','.').take(112).ifBlank { fallback }
    }
    fun fromSource(toolId:String,displayName:String):String? {
        val operation=operations[toolId] ?: return null
        val base=cleanBaseName(displayName)
        if(toolId=="pdf_07") return SafeNames.basename(base)
        val extension=if(toolId.startsWith("file_")) "zip" else "pdf"
        return SafeNames.basename("$base - $operation.$extension")
    }
    fun pageImageName(baseName:String, pageNumber:Int, extension:String):String {
        val cleanBase=cleanBaseName(baseName,"Document")
        val padded=pageNumber.coerceAtLeast(1).toString().padStart(3,'0')
        val cleanExt=extension.trimStart('.').lowercase().ifBlank { "png" }
        return SafeNames.basename("$cleanBase - صفحة $padded.$cleanExt")
    }
    fun splitPartName(displayName:String, sourceDisplayName:String?, partNumber:Int):String {
        val raw=displayName.substringBeforeLast('.',displayName).trim()
        val isDefault=raw.isEmpty() || raw.equals("Split",ignoreCase=true) || raw.equals("Document",ignoreCase=true)
        val base=if(!isDefault) cleanBaseName(raw,"Split")
            else sourceDisplayName?.let { cleanBaseName(it,"Split") } ?: "Split"
        val padded=partNumber.coerceAtLeast(1).toString().padStart(3,'0')
        return SafeNames.basename("$base - $padded.pdf")
    }
}
