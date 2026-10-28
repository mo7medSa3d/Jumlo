package com.jumlo.app.core

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invisible direction overrides must not disguise a filename's real suffix. */
class SafeNamesBidiControlTest {
    private fun rejectsName(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue("Expected INVALID_FILENAME, received ${failure?.javaClass?.simpleName}: ${failure?.message}",
            failure is IllegalArgumentException && failure.message == "INVALID_FILENAME")
    }

    @Test fun unsafeDirectionalFormattingIsRejected() {
        val controls = listOf('\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
            '\u2066', '\u2067', '\u2068', '\u2069', '\u200E', '\u200F', '\u061C')
        controls.forEach { control ->
            val spoofed = "safe_${control}fdp.exe"
            rejectsName { SafeNames.basename(spoofed) }
            rejectsName { DocumentNamePolicy.rename("original.pdf", spoofed) }
            rejectsName { SafeNames.fitUtf8(spoofed) }
        }
    }

    @Test fun aSpoofMarkerBeyondTheTruncationLimitIsNotSilentlyAccepted() {
        rejectsName { SafeNames.fitUtf8("a".repeat(300) + '\u202E' + ".pdf", 140) }
    }

    @Test fun nestedArchiveEntriesCannotIntroduceBidiControlNames() {
        val dir = Files.createTempDirectory("safe-name-test-").toFile()
        try {
            rejectsName { SafeNames.entry(dir, "folder/unsafe\u202Epdf.exe") }
            assertTrue(SafeNames.entry(dir, "folder/ملف.pdf").path.startsWith(dir.canonicalPath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun legitimateRtlArabicHebrewAndEmojiNamesRemainValid() {
        val names = listOf("ملف عربي.pdf", "שלום.pdf", "تقرير-نهائي ٢٠٢٦.pdf", "😀 ملف.png", "می\u200Cرسم.pdf")
        names.forEach { name ->
            assertEquals(name, SafeNames.basename(name))
            assertEquals(name, SafeNames.fitUtf8(name))
        }
    }
}
