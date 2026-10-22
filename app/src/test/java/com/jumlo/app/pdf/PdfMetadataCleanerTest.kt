package com.jumlo.app.pdf

import com.jumlo.app.data.pdf.PdfBoxBackend
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDMetadata
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Calendar

/**
 * The cleaner must actually remove the authoring trail, and it must report exactly
 * what it removed. A cleaner that only clears the visible fields would leave the
 * producing application, the timestamps and the XMP packet behind.
 */
class PdfMetadataCleanerTest {
    private lateinit var dir: File

    @Before fun setup() {
        dir = File(System.getProperty("java.io.tmpdir"), "pdfclean-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun teardown() {
        dir.deleteRecursively()
    }

    private fun trackedDoc(): PDDocument {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val info = doc.documentInformation
        info.title = "Q4 revenue by customer"
        info.subject = "Confidential"
        info.keywords = "revenue, confidential"
        info.author = "Dana Okafor"
        info.creator = "Acme Field Sales 3.2"
        info.producer = "Acme PDF Engine 3.2.1"
        info.setCustomMetadataValue("TrackingId", "GPS-51.5074,-0.1278")
        info.creationDate = Calendar.getInstance()
        info.modificationDate = Calendar.getInstance()
        // The XMP packet carries the same trail and is what most readers display.
        val xmp = PDMetadata(doc)
        xmp.createOutputStream().use { out ->
            out.write(
                ("<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>" +
                    "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF " +
                    "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"></rdf:RDF>" +
                    "</x:xmpmeta><?xpacket end=\"w\"?>").toByteArray()
            )
        }
        doc.documentCatalog.setMetadata(xmp)
        return doc
    }

    @Test fun theAuthoringTrailIsRemoved() {
        val doc = trackedDoc()
        PdfBoxBackend.scrubMetadata(doc, keepTitle = true)
        val info = doc.documentInformation
        assertEquals("", info.author ?: "")
        assertEquals("", info.creator ?: "")
        assertEquals("", info.producer ?: "")
        assertTrue("the creation date must be gone", info.creationDate == null)
        assertTrue("the modification date must be gone", info.modificationDate == null)
        assertEquals("", info.getCustomMetadataValue("TrackingId") ?: "")
        doc.close()
    }

    @Test fun theTitleAndKeywordsSurviveByDefault() {
        val doc = trackedDoc()
        PdfBoxBackend.scrubMetadata(doc, keepTitle = true)
        val info = doc.documentInformation
        assertEquals("Q4 revenue by customer", info.title ?: "")
        assertEquals("Confidential", info.subject ?: "")
        assertEquals("revenue, confidential", info.keywords ?: "")
        doc.close()
    }

    @Test fun theTitleCanBeRemovedToo() {
        val doc = trackedDoc()
        PdfBoxBackend.scrubMetadata(doc, keepTitle = false)
        val info = doc.documentInformation
        assertEquals("", info.title ?: "")
        assertEquals("", info.subject ?: "")
        assertEquals("", info.keywords ?: "")
        doc.close()
    }

    @Test fun theXmpPacketIsRemoved() {
        val doc = trackedDoc()
        assertTrue("the fixture must start with an XMP packet", doc.documentCatalog.metadata != null)
        PdfBoxBackend.scrubMetadata(doc, keepTitle = true)
        assertTrue("the XMP packet must be gone", doc.documentCatalog.metadata == null)
        doc.close()
    }

    @Test fun everyRemovedKeyIsReported() {
        val doc = trackedDoc()
        val removed = PdfBoxBackend.scrubMetadata(doc, keepTitle = true)
        assertTrue("the author must be reported: $removed", removed.contains("Author"))
        assertTrue("the creator must be reported: $removed", removed.contains("Creator"))
        assertTrue("the producer must be reported: $removed", removed.contains("Producer"))
        assertTrue("the creation date must be reported: $removed", removed.contains("CreationDate"))
        assertTrue("the modification date must be reported: $removed", removed.contains("ModDate"))
        assertTrue("the XMP packet must be reported: $removed", removed.contains("xmp"))
        assertTrue("a custom tracking key must be reported: $removed", removed.any { it.contains("TrackingId") })
        assertFalse("a kept title must not be reported as removed: $removed", removed.contains("content:title"))
        doc.close()
    }

    @Test fun aDocumentWithoutATrailReportsNothingRemoved() {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val removed = PdfBoxBackend.scrubMetadata(doc, keepTitle = true)
        assertTrue("nothing was there to remove: $removed", removed.isEmpty())
        doc.close()
    }

    @Test fun cleaningSurvivesASaveAndReopen() {
        val source = File(dir, "tracked.pdf")
        trackedDoc().use { it.save(source) }
        val cleaned = File(dir, "cleaned.pdf")
        PdfBoxBackend.open(source, null).use { doc ->
            PdfBoxBackend.scrubMetadata(doc, keepTitle = true)
            doc.save(cleaned)
        }
        PdfBoxBackend.open(cleaned, null).use { doc ->
            val info = doc.documentInformation
            assertEquals("", info.author ?: "")
            assertEquals("", info.producer ?: "")
            assertEquals("Q4 revenue by customer", info.title ?: "")
            assertTrue(doc.documentCatalog.metadata == null)
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test fun thePageContentIsUntouched() {
        val source = PdfFixtures.simpleDoc(File(dir, "content.pdf"), listOf("body text"))
        val cleaned = File(dir, "content-clean.pdf")
        PdfBoxBackend.open(source, null).use { doc ->
            PdfBoxBackend.scrubMetadata(doc, keepTitle = false)
            doc.save(cleaned)
        }
        PdfBoxBackend.open(cleaned, null).use { doc ->
            assertTrue("the page must still have its text", PdfBoxBackend.containsText(doc, "body text"))
        }
    }
}
