package com.jumlo.app.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument

object PdfBoxBackend {
    fun scrubMetadata(doc: PDDocument, keepTitle: Boolean = true): List<String> {
        val removed = mutableListOf<String>()
        val cos = doc.documentInformation.cosObject
        val contentKeys = setOf("title", "subject", "keywords")
        val keys = cos.keySet().toList()
        keys.forEach { key ->
            val name = key.name
            if (name.equals("Trapped", ignoreCase = true)) return@forEach
            if (keepTitle && name.lowercase() in contentKeys) return@forEach
            removed += if (name.lowercase() in contentKeys) "content:$name" else name
        }
        keys.forEach { key ->
            if (!(keepTitle && key.name.lowercase() in contentKeys)) cos.removeItem(key)
        }
        val catalog = doc.documentCatalog
        val metadataStream = catalog.getCOSObject().getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.METADATA)
        if (metadataStream != null) {
            catalog.getCOSObject().removeItem(com.tom_roush.pdfbox.cos.COSName.METADATA)
            removed += "xmp"
        }
        return removed.distinct().sorted()
    }
}
