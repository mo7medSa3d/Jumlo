package com.jumlo.app.core

/** Resolve generic SAF content types from small file signatures, not from filename alone.
 * No network access, full-file decoding or unbounded allocations.
 */
object SourceMediaType {
    private val generic = setOf("", "application/octet-stream", "binary/octet-stream", "application/unknown")

    fun resolve(declared: String?, name: String, header: ByteArray): String {
        val supplied=declared?.trim()?.lowercase().orEmpty()
        if (supplied !in generic) return supplied
        fun prefix(vararg bytes: Int): Boolean = header.size >= bytes.size &&
            bytes.indices.all { index -> (header[index].toInt() and 0xff)==bytes[index] }
        fun ascii(offset: Int, word: String): Boolean = header.size >= offset + word.length &&
            word.indices.all { index -> header[offset+index].toInt()==word[index].code }
        val brand = if (header.size >= 12 && ascii(4,"ftyp"))
            String(header,8,4,Charsets.ISO_8859_1).lowercase() else ""
        return when {
            prefix(0x25,0x50,0x44,0x46,0x2d) -> "application/pdf"
            prefix(0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) -> "image/png"
            prefix(0xff,0xd8,0xff) -> "image/jpeg"
            ascii(0,"GIF87a") || ascii(0,"GIF89a") -> "image/gif"
            ascii(0,"RIFF") && ascii(8,"WEBP") -> "image/webp"
            prefix(0x50,0x4b,0x03,0x04) || prefix(0x50,0x4b,0x05,0x06) || prefix(0x50,0x4b,0x07,0x08) -> "application/zip"
            brand in setOf("heic","heix","hevc","hevx","mif1","msf1") -> "image/heic"
            brand in setOf("avif","avis") -> "image/avif"
            brand in setOf("isom","iso2","mp41","mp42","avc1","dash","m4a ","m4v ") ->
                if(name.endsWith(".m4a",ignoreCase=true) || brand=="m4a ") "audio/mp4" else "video/mp4"
            prefix(0x49,0x44,0x33) -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }
}
