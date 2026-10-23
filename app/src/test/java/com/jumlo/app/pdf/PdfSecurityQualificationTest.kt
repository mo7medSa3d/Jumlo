package com.jumlo.app.pdf

import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.core.PdfProtectionPermissions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Qualification evidence for the password capability.
 *
 * `pdf_protect` (add and remove a password) has a working handler, but it was gated
 * in the capability policy because nothing proved it behaves correctly. These tests
 * are that proof: they run on the JVM against the real PDFBox backend, not mocks.
 */
class PdfSecurityQualificationTest {
    @Test fun selectedPermissionsSurviveEncryptionAndAreVisibleToTheUserPassword() {
        val source = sample()
        val output = File(dir, "restricted.pdf")
        PdfBoxBackend.open(source, null).use { document ->
            PdfBoxBackend.protect(document, "user".toCharArray(), "owner".toCharArray(), output,
                PdfProtectionPermissions(allowPrinting = false, allowCopy = false, allowModify = true,
                    allowAnnotations = false, allowForms = false))
        }
        PdfBoxBackend.open(output, "user".toCharArray()).use { document ->
            val permission = document.currentAccessPermission
            assertFalse(permission.canPrint())
            assertFalse(permission.canPrintFaithful())
            assertFalse(permission.canExtractContent())
            assertTrue(permission.canModify())
            assertTrue(permission.canAssembleDocument())
            assertFalse(permission.canModifyAnnotations())
            assertFalse(permission.canFillInForm())
            assertTrue(permission.canExtractForAccessibility())
        }
        PdfBoxBackend.open(output, "owner".toCharArray()).use { document ->
            assertTrue(document.currentAccessPermission.isOwnerPermission)
        }
    }

    private lateinit var dir: File

    @Before fun setup() {
        dir = File(System.getProperty("java.io.tmpdir"), "pdfsec-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun teardown() {
        dir.deleteRecursively()
    }

    private fun sample(text: String = "CLASSIFIED PAYLOAD"): File =
        PdfFixtures.simpleDoc(File(dir, "source.pdf"), listOf(text))

    private fun protected(source: File, user: String, owner: String): File {
        val out = File(dir, "protected.pdf")
        PdfBoxBackend.open(source, null).use { doc ->
            PdfBoxBackend.protect(doc, user.toCharArray(), owner.toCharArray(), out)
        }
        return out
    }

    @Test fun protectedDocumentCannotBeOpenedWithoutThePassword() {
        val out = protected(sample(), "user-secret", "owner-secret")
        try {
            PdfBoxBackend.open(out, null).use { fail("an encrypted document must not open without a password") }
        } catch (_: InvalidPasswordException) {
            // expected
        }
    }

    @Test fun theUserPasswordOpensTheDocumentAndKeepsTheContent() {
        val out = protected(sample(), "user-secret", "owner-secret")
        PdfBoxBackend.open(out, "user-secret".toCharArray()).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue(PdfBoxBackend.pageText(doc, 0).contains("CLASSIFIED PAYLOAD"))
        }
    }

    @Test fun theOwnerPasswordAlsoOpensTheDocument() {
        val out = protected(sample(), "user-secret", "owner-secret")
        PdfBoxBackend.open(out, "owner-secret".toCharArray()).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test fun aWrongPasswordIsRejected() {
        val out = protected(sample(), "user-secret", "owner-secret")
        try {
            PdfBoxBackend.open(out, "wrong-password".toCharArray()).use { fail("a wrong password must be rejected") }
        } catch (_: InvalidPasswordException) {
            // expected
        }
    }

    @Test fun unlockingProducesADocumentThatNeedsNoPassword() {
        val out = protected(sample(), "user-secret", "owner-secret")
        val unlocked = File(dir, "unlocked.pdf")
        PdfBoxBackend.open(out, "user-secret".toCharArray()).use { doc ->
            PdfBoxBackend.removeProtection(doc, unlocked)
        }
        PdfBoxBackend.open(unlocked, null).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue(PdfBoxBackend.pageText(doc, 0).contains("CLASSIFIED PAYLOAD"))
        }
    }

    @Test fun unlockingIsImpossibleWithoutTheCorrectPassword() {
        val out = protected(sample(), "user-secret", "owner-secret")
        try {
            PdfBoxBackend.open(out, "not-the-password".toCharArray()).use { fail("unlock must require the correct password") }
        } catch (_: InvalidPasswordException) {
            // expected - this is the "unlock only with a correct password" rule
        }
    }

    @Test fun protectionRequiresAnOwnerPassword() {
        val source = sample()
        try {
            PdfBoxBackend.open(source, null).use { doc ->
                PdfBoxBackend.protect(doc, "user-secret".toCharArray(), "".toCharArray(), File(dir, "bad.pdf"))
            }
            fail("protection without an owner password must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun anEncryptedDocumentIsReportedAsEncrypted() {
        val out = protected(sample(), "user-secret", "owner-secret")
        PdfBoxBackend.open(out, "user-secret".toCharArray()).use { doc ->
            assertTrue(doc.isEncrypted)
            assertTrue(PdfBoxBackend.info(doc).warnings.contains("WARNING_DOCUMENT_IS_ENCRYPTED"))
        }
    }

    @Test fun anUnprotectedDocumentIsNotReportedAsEncrypted() {
        PdfBoxBackend.open(sample(), null).use { doc ->
            assertFalse(doc.isEncrypted)
            assertFalse(PdfBoxBackend.info(doc).warnings.contains("WARNING_DOCUMENT_IS_ENCRYPTED"))
        }
    }

    @Test fun everyGeneratedDocumentClosesCleanly() {
        val source = sample()
        val out = protected(source, "u", "o")
        PdfBoxBackend.open(out, "u".toCharArray()).use { it.numberOfPages }
        assertTrue(source.exists())
        assertTrue(out.exists())
    }

    @Test fun theDocumentStillHasOnePageAfterARoundTrip() {
        val source = sample()
        val out = protected(source, "u", "o")
        val unlocked = File(dir, "roundtrip.pdf")
        PdfBoxBackend.open(out, "u".toCharArray()).use { doc ->
            PdfBoxBackend.removeProtection(doc, unlocked)
        }
        PdfBoxBackend.open(unlocked, null).use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue(doc.getPage(0).mediaBox.width > 0)
        }
    }

    @Test fun protectRejectsAnEmptyOwnerPasswordBeforeTouchingTheOutput() {
        val source = sample()
        val out = File(dir, "never-written.pdf")
        try {
            PdfBoxBackend.open(source, null).use { doc ->
                PdfBoxBackend.protect(doc, "u".toCharArray(), "".toCharArray(), out)
            }
            fail("expected OWNER_PASSWORD_REQUIRED")
        } catch (_: IllegalArgumentException) {
            assertFalse("no output may be written when protection is refused", out.exists())
        }
    }

    @Test fun anUnprotectedDocumentCanBeOpenedWithAnEmptyPassword() {
        PdfBoxBackend.open(sample(), "".toCharArray()).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }
    }
}
