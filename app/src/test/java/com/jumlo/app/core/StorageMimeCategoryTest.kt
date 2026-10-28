package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** MIME providers can differ in letter case and add parameters. */
class StorageMimeCategoryTest {
    @Test fun canonicalAndMixedCaseMimeTypesGroupInTheSameFilesSection() {
        val cases = mapOf(
            "application/pdf" to "PDF",
            "APPLICATION/PDF" to "PDF",
            "Application/Pdf; charset=BINARY" to "PDF",
            "image/png" to "Images",
            "IMAGE/JPEG; name=scan.jpg" to "Images",
            "Video/mp4" to "Video",
            "VIDEO/WEBM; codecs=vp9" to "Video",
            "audio/mpeg" to "Audio",
            "AUDIO/MPEG; bitrate=320" to "Audio",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "Documents",
            "application/pdfx" to "Documents",
            "application/octet-stream" to "Documents",
            "" to "Documents"
        )
        cases.forEach { (mime, expected) ->
            assertEquals("Files category should not depend on provider spelling for $mime",
                expected, StoragePaths.category(mime))
        }
    }

    @Test fun publishedFolderUsesTheSameCategoryAsFilesFilter() {
        val mime = "APPLICATION/PDF; charset=binary"
        assertEquals("PDF", StoragePaths.category(mime))
        assertEquals("Download/Jumlo/PDF/", StoragePaths.relativePath(mime))
        assertEquals("Download/Jumlo/PDF/Receipts/", StoragePaths.relativePath(mime, "Receipts"))
    }
}
