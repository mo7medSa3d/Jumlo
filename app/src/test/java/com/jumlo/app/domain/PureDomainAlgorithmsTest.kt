package com.jumlo.app.domain

import com.jumlo.app.core.ArchiveLimits
import com.jumlo.app.data.files.EncryptedZip
import com.jumlo.app.data.files.EncryptedZipEntrySource
import com.jumlo.app.core.HttpPolicy
import com.jumlo.app.core.HttpTransport
import com.jumlo.app.core.PageSelection
import com.jumlo.app.core.SafeNames
import com.jumlo.app.core.TransportResponse
import com.jumlo.app.data.engine.PureDomainAlgorithms
import com.jumlo.app.data.files.GoogleDriveClient
import com.jumlo.app.data.jobs.OperationFailure
import com.jumlo.app.model.FittingStrategy
import com.jumlo.app.model.ResizeMode
import com.jumlo.app.model.UrlValidationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PureDomainAlgorithmsTest {

    @Test
    fun `parsePageRangeSyntax parses inclusive ranges and single pages accurately`() {
        val result = PureDomainAlgorithms.parsePageRangeSyntax("1-3, 7, 10-12", maxPages = 24)
        assertTrue(result.isSuccess)
        assertEquals(listOf(1, 2, 3, 7, 10, 11, 12), result.getOrThrow())
    }

    @Test
    fun `parsePageRangeSyntax rejects out-of-bounds pages`() {
        val result = PureDomainAlgorithms.parsePageRangeSyntax("1-3, 28", maxPages = 24)
        assertTrue(result.isFailure)
    }

    @Test
    fun `formatPagesToRangeSyntax round-trips sorted page list`() {
        val formatted = PureDomainAlgorithms.formatPagesToRangeSyntax(listOf(1, 2, 3, 7, 10, 11, 12))
        assertEquals("1-3, 7, 10-12", formatted)
    }

    @Test
    fun `parseBatchUrls identifies valid links, duplicates, and unsupported ftp protocol`() {
        val input = """
            https://data.research.org/datasets/climate_2024_q1.csv
            https://data.research.org/datasets/climate_2024_q2.csv
            https://data.research.org/datasets/climate_2024_q1.csv
            ftp://broken.resource/sample.bin
        """.trimIndent()

        val parsed = PureDomainAlgorithms.parseBatchUrls(input)
        assertEquals(4, parsed.size)
        assertEquals(UrlValidationStatus.VALID, parsed[0].status)
        assertEquals(UrlValidationStatus.VALID, parsed[1].status)
        assertEquals(UrlValidationStatus.DUPLICATE, parsed[2].status)
        assertEquals(UrlValidationStatus.UNSUPPORTED_PROTOCOL, parsed[3].status)
    }

    @Test(expected = SecurityException::class)
    fun `validateZipEntryPath blocks Zip-Slip directory traversal attack`() {
        val sandboxDir = File("/tmp/universal_utilities_test_sandbox").apply { mkdirs() }
        PureDomainAlgorithms.validateZipEntryPath(sandboxDir, "../../etc/passwd")
    }

    @Test
    fun `validateZipEntryPath allows clean relative paths inside sandbox`() {
        val sandboxDir = File("/tmp/universal_utilities_test_sandbox").apply { mkdirs() }
        val resolved = PureDomainAlgorithms.validateZipEntryPath(sandboxDir, "Photos_Compressed_Batch/landscape_01.webp")
        assertTrue(resolved.canonicalPath.startsWith(sandboxDir.canonicalPath))
    }

    @Test
    fun `resolveUniqueFileName appends incrementing counter on collision`() {
        val existing = setOf("Lecture_Notes_Bundle.zip", "Lecture_Notes_Bundle (1).zip")
        val resolved = PureDomainAlgorithms.resolveUniqueFileName(existing, "Lecture_Notes_Bundle.zip")
        assertEquals("Lecture_Notes_Bundle (2).zip", resolved)
    }

    @Test
    fun `calculateTargetDimensions preserves 16-9 aspect ratio and respects upscaling guard`() {
        val (w, h) = PureDomainAlgorithms.calculateTargetDimensions(
            sourceWidth = 3840,
            sourceHeight = 2160,
            mode = ResizeMode.BOUNDING_BOX,
            strategy = FittingStrategy.FIT_WITHIN,
            targetWidth = 1920,
            targetHeight = 1080,
            allowUpscaling = false
        )
        assertEquals(1920, w)
        assertEquals(1080, h)

        val (smallW, smallH) = PureDomainAlgorithms.calculateTargetDimensions(
            sourceWidth = 800,
            sourceHeight = 600,
            mode = ResizeMode.BOUNDING_BOX,
            strategy = FittingStrategy.FIT_WITHIN,
            targetWidth = 1920,
            targetHeight = 1080,
            allowUpscaling = false
        )
        assertEquals(800, smallW)
        assertEquals(600, smallH)
    }
}

class ExpansionLogicTest {
    @Test fun batchRename_applies_pattern_and_uniquifies() {
        val out = PureDomainAlgorithms.batchRenamePreview(listOf("a.jpg","b.jpg"), "file-{n}", 1)
        assertEquals(listOf("file-1.jpg","file-2.jpg"), out)
    }
    @Test fun duplicateGroups_finds_exact_groups() {
        val groups = PureDomainAlgorithms.duplicateGroups(listOf("x","y","x","x","z"))
        assertEquals(listOf(listOf(0,2,3)), groups)
    }
}

class ZipSlipTest {
    @Test fun malicious_entry_names_are_rejected() {
        val root = java.nio.file.Files.createTempDirectory("zipslip").toFile()
        for (bad in listOf("../evil.txt", "..\\evil.txt", "/abs.txt", "a/../../evil.txt", "a//b.txt", "C:/evil.txt")) {
            var rejected = false
            try { PureDomainAlgorithms.validateZipEntryPath(root, bad) } catch (e: Exception) { rejected = true }
            assertTrue("must reject: $bad", rejected)
        }
        val ok = PureDomainAlgorithms.validateZipEntryPath(root, "docs/readme.txt")
        assertTrue(ok.path.startsWith(root.canonicalPath))
    }

    @Test fun page_range_fuzzish_inputs_never_throw_unexpectedly() {
        val inputs = listOf("", " ", "-", "1-", "-3", "1,,2", "0", "99999999999999999999", "١-٣", "1-3,7", "5-2")
        for (input in inputs) {
            val result = PureDomainAlgorithms.parsePageRangeSyntax(input, 10)
            if (result.isSuccess) assertTrue(result.getOrThrow().all { it in 1..10 })
        }
    }

    @Test fun property_fuzz_parsers_zip_page_range_and_url() {
        val rng = Random(42L)
        val root = java.nio.file.Files.createTempDirectory("fuzz-zip").toFile()
        try {
            val alphabet = "abc123.-_,/\\:;\u0000\u0661\u0665?#@"
            repeat(250) {
                val len = rng.nextInt(48)
                val randomStr = buildString(len) { repeat(len) { append(alphabet[rng.nextInt(alphabet.length)]) } }
                val pageRes = runCatching { PageSelection.parse(randomStr, 20) }
                if (pageRes.isSuccess) {
                    val pages = pageRes.getOrThrow()
                    assertTrue(pages.isNotEmpty() && pages.all { it in 1..20 } && pages.distinct().size == pages.size)
                } else {
                    assertTrue(pageRes.exceptionOrNull() is IllegalArgumentException)
                }
                val zipRes = runCatching { SafeNames.entry(root, randomStr) }
                if (zipRes.isSuccess) {
                    val f = zipRes.getOrThrow().canonicalFile
                    assertTrue(f.path.startsWith(root.canonicalFile.path + File.separator))
                } else {
                    assertTrue(zipRes.exceptionOrNull() is IllegalArgumentException)
                }
                val urlRes = runCatching { HttpPolicy.url(randomStr, cleartext = false) }
                if (urlRes.isSuccess) {
                    val u = urlRes.getOrThrow()
                    assertEquals("https", u.scheme)
                    assertTrue(u.rawUserInfo == null && u.fragment == null && !u.host.isNullOrBlank())
                } else {
                    assertTrue(urlRes.exceptionOrNull() is IllegalArgumentException)
                }
            }
        } finally {
            root.deleteRecursively()
        }
    }
}

class EncryptedZipTest {
    @Test fun zip4j_aes_roundtrip_and_wrong_password_rejected() {
        val dir = java.nio.file.Files.createTempDirectory("ezip").toFile()
        try {
            val src = File(dir, "hello.txt").apply { writeText("secret contents") }
            val zipFile = File(dir, "out.zip")
            EncryptedZip.create(zipFile, listOf(EncryptedZipEntrySource("hello.txt", src)), "correct horse".toCharArray())
            var rejected = false
            try {
                EncryptedZip.extract(zipFile, File(dir, "wrong"), "wrong".toCharArray())
                rejected = !File(dir, "wrong/hello.txt").exists()
            } catch (_: Exception) {
                rejected = true
            }
            assertTrue("wrong password must not yield plaintext", rejected)
            val extracted = EncryptedZip.extract(zipFile, File(dir, "ok"), "correct horse".toCharArray())
            assertEquals(1, extracted.size)
            assertEquals("secret contents", extracted.single().readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun encrypted_zip_extract_rejects_malicious_zip_slip_entries_and_enforces_limits() {
        val dir = java.nio.file.Files.createTempDirectory("ezip-slip").toFile()
        try {
            val maliciousNames = listOf(
                "../outside.txt",
                "/abs/outside.txt",
                "nested/../../outside.txt",
                "..\\backslash.txt",
                "C:drive.txt"
            )
            for (badName in maliciousNames) {
                val badZip = File(dir, "bad.zip")
                ZipOutputStream(badZip.outputStream()).use { zos ->
                    zos.putNextEntry(ZipEntry(badName))
                    zos.write("payload".toByteArray())
                    zos.closeEntry()
                }
                val target = File(dir, "target")
                val res = runCatching {
                    EncryptedZip.extract(badZip, target, "pw".toCharArray(), ArchiveLimits(1024 * 1024, 10, 32))
                }
                assertTrue("Malicious entry '$badName' must be rejected", res.isFailure)
                assertEquals("UNSAFE_ARCHIVE_PATH", res.exceptionOrNull()?.message)
                assertFalse("No file may escape target", File(dir, "outside.txt").exists())
                badZip.delete()
            }

            // Enforce entry count limit and total byte limit
            val s1 = File(dir, "a.txt").apply { writeText("1234567890") }
            val s2 = File(dir, "b.txt").apply { writeText("1234567890") }
            val validZip = File(dir, "valid.zip")
            EncryptedZip.create(
                validZip,
                listOf(EncryptedZipEntrySource("a.txt", s1), EncryptedZipEntrySource("b.txt", s2)),
                "pw".toCharArray()
            )
            val countLimited = runCatching {
                EncryptedZip.extract(validZip, File(dir, "out-count"), "pw".toCharArray(), ArchiveLimits(1024, maxEntries = 1, maxDepth = 8))
            }
            assertTrue(countLimited.isFailure)
            assertEquals("ARCHIVE_ENTRY_LIMIT", countLimited.exceptionOrNull()?.message)

            val byteLimited = runCatching {
                EncryptedZip.extract(validZip, File(dir, "out-bytes"), "pw".toCharArray(), ArchiveLimits(maxBytes = 15, maxEntries = 10, maxDepth = 8))
            }
            assertTrue(byteLimited.isFailure)
            assertEquals("ARCHIVE_SIZE_LIMIT", byteLimited.exceptionOrNull()?.message)
        } finally {
            dir.deleteRecursively()
        }
    }
}

class GoogleDriveClientTest {
    @Test fun extractFileId_parses_official_drive_urls_and_rejects_invalid_ids() {
        assertEquals(
            "1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345",
            GoogleDriveClient.extractFileId("https://drive.google.com/file/d/1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345/view?usp=sharing")
        )
        assertEquals(
            "1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345",
            GoogleDriveClient.extractFileId("https://drive.google.com/open?id=1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345")
        )
        assertEquals(
            "1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345",
            GoogleDriveClient.extractFileId("1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345")
        )
        assertTrue(runCatching { GoogleDriveClient.extractFileId("../bad") }.isFailure)
    }

    @Test fun download_and_upload_use_official_drive_v3_endpoints_and_map_errors() {
        val dir = java.nio.file.Files.createTempDirectory("gdrive-test").toFile()
        try {
            val transport = object : HttpTransport {
                override fun open(url: String, headers: Map<String, String>): TransportResponse {
                    val auth = headers["Authorization"]
                    if (auth != "Bearer valid-session-token") {
                        return TransportResponse(401, emptyMap(), ByteArrayInputStream("{\"error\":\"unauth\"}".toByteArray()))
                    }
                    return when {
                        url.contains("/drive/v3/files/privateFileId123") ->
                            TransportResponse(403, emptyMap(), ByteArrayInputStream("{\"error\":\"forbidden\"}".toByteArray()))
                        url.contains("/drive/v3/files/missingFileId123") ->
                            TransportResponse(404, emptyMap(), ByteArrayInputStream("{\"error\":\"notFound\"}".toByteArray()))
                        url.contains("/drive/v3/files/goodFileId12345?fields=") ->
                            TransportResponse(
                                200,
                                emptyMap(),
                                ByteArrayInputStream("{\"id\":\"goodFileId12345\",\"name\":\"report.pdf\",\"mimeType\":\"application/pdf\",\"size\":\"11\"}".toByteArray())
                            )
                        url.contains("/drive/v3/files/goodFileId12345?alt=media") ->
                            TransportResponse(200, emptyMap(), ByteArrayInputStream("%PDF-1.4-ok".toByteArray()))
                        url.startsWith("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart") ->
                            TransportResponse(
                                200,
                                emptyMap(),
                                ByteArrayInputStream("{\"id\":\"uploadedId12345\",\"name\":\"notes.txt\",\"mimeType\":\"text/plain\",\"size\":\"5\"}".toByteArray())
                            )
                        else -> TransportResponse(500, emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                }
            }
            val client = GoogleDriveClient(transport)
            val target = File(dir, "downloaded.pdf")
            val meta = client.downloadFile("goodFileId12345", "valid-session-token".toCharArray(), target, 1024 * 1024, 0)
            assertEquals("report.pdf", meta.name)
            assertEquals("%PDF-1.4-ok", target.readText())

            // Upload test
            val toUpload = File(dir, "notes.txt").apply { writeText("hello") }
            val uploaded = client.uploadFile(toUpload, "notes.txt", "text/plain", null, "valid-session-token".toCharArray())
            assertEquals("uploadedId12345", uploaded.id)

            // Error category mapping
            val unauth = runCatching { client.fetchMetadata("goodFileId12345", "wrong-token".toCharArray()) }
            assertEquals("LOGIN_REQUIRED", (unauth.exceptionOrNull() as? OperationFailure)?.code)

            val priv = runCatching { client.fetchMetadata("privateFileId123", "valid-session-token".toCharArray()) }
            assertEquals("PRIVATE_CONTENT", (priv.exceptionOrNull() as? OperationFailure)?.code)

            val removed = runCatching { client.fetchMetadata("missingFileId123", "valid-session-token".toCharArray()) }
            assertEquals("REMOVED", (removed.exceptionOrNull() as? OperationFailure)?.code)
        } finally {
            dir.deleteRecursively()
        }
    }
}
