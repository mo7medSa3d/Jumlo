package com.jumlo.app.pdf

import com.jumlo.app.core.PdfAnnotationKind
import com.jumlo.app.core.PdfDisplayCoordinates
import com.jumlo.app.data.pdf.PdfBoxBackend
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The annotation kinds must produce real PDF annotations, in the region the user
 * dragged, and they must survive a save and reopen.
 */
class PdfAnnotationKindTest {
    private lateinit var dir: File

    @Before fun setup() {
        dir = File(System.getProperty("java.io.tmpdir"), "pdfannot-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun teardown() {
        dir.deleteRecursively()
    }

    @Test fun theQuadIsTheDraggedRegionOnAnUprightPage() {
        val frame = PdfDisplayCoordinates.frame(0.0, 0.0, 595.0, 842.0, 0)
        val corners = PdfDisplayCoordinates.normalizedCorners(frame, 0.1, 0.1, 0.5, 0.2)
        // x,y pairs: upper-left, upper-right, lower-left, lower-right
        assertEquals(59.5f, corners[0], 0.01f)
        assertEquals(757.8f, corners[1], 0.01f)
        assertEquals(297.5f, corners[2], 0.01f)
        assertEquals(757.8f, corners[3], 0.01f)
        assertEquals(59.5f, corners[4], 0.01f)
        assertEquals(673.6f, corners[5], 0.01f)
        assertEquals(297.5f, corners[6], 0.01f)
        assertEquals(673.6f, corners[7], 0.01f)
    }

    @Test fun theQuadFollowsThePageRotation() {
        val frame = PdfDisplayCoordinates.frame(0.0, 0.0, 595.0, 842.0, 90)
        val corners = PdfDisplayCoordinates.normalizedCorners(frame, 0.0, 0.0, 1.0, 1.0)
        // A rotated page maps the display rectangle onto the page's own axes.
        assertTrue("the corners must still be finite: ${corners.toList()}", corners.all { it.isFinite() })
        val xs = listOf(corners[0], corners[2], corners[4], corners[6])
        val ys = listOf(corners[1], corners[3], corners[5], corners[7])
        assertTrue("the quad must have width", xs.max() - xs.min() > 1f)
        assertTrue("the quad must have height", ys.max() - ys.min() > 1f)
    }

    @Test fun everyKindProducesItsOwnAnnotationType() {
        val expected = mapOf(
            PdfAnnotationKind.HIGHLIGHT to "Highlight",
            PdfAnnotationKind.UNDERLINE to "Underline",
            PdfAnnotationKind.STRIKETHROUGH to "StrikeOut",
            PdfAnnotationKind.NOTE to "Text",
            PdfAnnotationKind.SQUARE to "Square",
            PdfAnnotationKind.CIRCLE to "Circle"
        )
        expected.forEach { (kind, subtype) ->
            val out = File(dir, "${kind.id}.pdf")
            PdfFixtures.simpleDoc(File(dir, "src-${kind.id}.pdf"), listOf("page one")).let { source ->
                PdfBoxBackend.open(source, null).use { doc ->
                    PdfBoxBackend.addAnnotation(doc, 0, kind, 0.1, 0.1, 0.6, 0.2, "note text")
                    doc.save(out)
                }
            }
            PdfBoxBackend.open(out, null).use { doc ->
                val annotations = doc.getPage(0).annotations
                assertEquals("${kind.id} must add one annotation", 1, annotations.size)
                assertEquals("${kind.id} subtype", subtype, annotations[0].subtype)
            }
        }
    }

    @Test fun aTextBoxIsAFreeTextAnnotation() {
        val out = File(dir, "textbox.pdf")
        PdfFixtures.simpleDoc(File(dir, "src-textbox.pdf"), listOf("page one")).let { source ->
            PdfBoxBackend.open(source, null).use { doc ->
                PdfBoxBackend.addAnnotation(doc, 0, PdfAnnotationKind.TEXTBOX, 0.2, 0.2, 0.6, 0.35, "typed")
                doc.save(out)
            }
        }
        PdfBoxBackend.open(out, null).use { doc ->
            val annotation = doc.getPage(0).annotations.single()
            assertEquals("FreeText", annotation.subtype)
            assertEquals("typed", annotation.contents)
        }
    }

    @Test fun theAnnotationSitsWhereTheUserDraggedIt() {
        val out = File(dir, "placed.pdf")
        PdfFixtures.simpleDoc(File(dir, "src-placed.pdf"), listOf("page one")).let { source ->
            PdfBoxBackend.open(source, null).use { doc ->
                PdfBoxBackend.addAnnotation(doc, 0, PdfAnnotationKind.HIGHLIGHT, 0.2, 0.3, 0.7, 0.36)
                doc.save(out)
            }
        }
        PdfBoxBackend.open(out, null).use { doc ->
            val rectangle = doc.getPage(0).annotations.single().rectangle
            // A4 is 595 x 842 points.
            assertEquals(0.2 * 595.0, rectangle.lowerLeftX.toDouble(), 0.5)
            assertEquals((1.0 - 0.36) * 842.0, rectangle.lowerLeftY.toDouble(), 0.5)
            assertEquals(0.5 * 595.0, rectangle.width.toDouble(), 0.5)
            assertEquals(0.06 * 842.0, rectangle.height.toDouble(), 0.5)
        }
    }

    @Test fun markupAnnotationsCarryTheQuad() {
        val out = File(dir, "quad.pdf")
        PdfFixtures.simpleDoc(File(dir, "src-quad.pdf"), listOf("page one")).let { source ->
            PdfBoxBackend.open(source, null).use { doc ->
                PdfBoxBackend.addAnnotation(doc, 0, PdfAnnotationKind.HIGHLIGHT, 0.1, 0.1, 0.5, 0.14)
                doc.save(out)
            }
        }
        PdfBoxBackend.open(out, null).use { doc ->
            val annotation = doc.getPage(0).annotations.single()
            val quad = annotation.cosObject.getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.QUADPOINTS)
                    as? com.tom_roush.pdfbox.cos.COSArray
            assertNotNull("a markup annotation must carry its quad", quad)
            assertEquals(8, quad!!.size())
            assertEquals(0.1 * 595.0, (quad.get(0) as com.tom_roush.pdfbox.cos.COSFloat).floatValue().toDouble(), 0.5)
        }
    }

    @Test fun thePageContentIsNotDamagedByAnAnnotation() {
        val out = File(dir, "intact.pdf")
        PdfFixtures.simpleDoc(File(dir, "src-intact.pdf"), listOf("keep this text")).let { source ->
            PdfBoxBackend.open(source, null).use { doc ->
                PdfBoxBackend.addAnnotation(doc, 0, PdfAnnotationKind.HIGHLIGHT, 0.1, 0.1, 0.5, 0.2)
                doc.save(out)
            }
        }
        PdfBoxBackend.open(out, null).use { doc ->
            assertTrue("the page must keep its text", PdfBoxBackend.containsText(doc, "keep this text"))
            assertEquals(1, doc.numberOfPages)
        }
    }

    @Test fun anImpossibleRegionIsRejected() {
        PdfFixtures.simpleDoc(File(dir, "src-bad.pdf"), listOf("x")).let { source ->
            PdfBoxBackend.open(source, null).use { doc ->
                runCatching { PdfBoxBackend.addAnnotation(doc, 0, PdfAnnotationKind.HIGHLIGHT, 0.8, 0.1, 0.2, 0.2) }
                    .also { assertTrue("an inverted region must be rejected", it.isFailure) }
                runCatching { PdfBoxBackend.addAnnotation(doc, 5, PdfAnnotationKind.HIGHLIGHT, 0.1, 0.1, 0.2, 0.2) }
                    .also { assertTrue("an out-of-range page must be rejected", it.isFailure) }
            }
        }
    }

    @Test fun aFreehandStrokeBecomesAnInkAnnotation() {
        val out = File(dir, "ink.pdf")
        PdfFixtures.simpleDoc(File(dir, "src-ink.pdf"), listOf("page one")).let { source ->
            PdfBoxBackend.open(source, null).use { doc ->
                PdfBoxBackend.addInkAnnotation(doc, 0, listOf(
                    com.jumlo.app.core.PdfInkPoint(0.1, 0.1),
                    com.jumlo.app.core.PdfInkPoint(0.4, 0.3),
                    com.jumlo.app.core.PdfInkPoint(0.8, 0.2)
                ))
                doc.save(out)
            }
        }
        PdfBoxBackend.open(out, null).use { doc ->
            val annotation = doc.getPage(0).annotations.single()
            assertEquals("Ink", annotation.subtype)
            val inkList = annotation.cosObject.getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.INKLIST)
                    as? com.tom_roush.pdfbox.cos.COSArray
            assertNotNull("an ink annotation must carry its stroke", inkList)
            assertEquals("one stroke", 1, inkList!!.size())
            val stroke = inkList.get(0) as com.tom_roush.pdfbox.cos.COSArray
            assertEquals("three points are six numbers", 6, stroke.size())
            assertTrue("the page content must survive", PdfBoxBackend.containsText(doc, "page one"))
        }
    }

    @Test fun aStrokeNeedsAtLeastTwoPoints() {
        PdfFixtures.simpleDoc(File(dir, "src-ink-bad.pdf"), listOf("x")).let { source ->
            PdfBoxBackend.open(source, null).use { doc ->
                runCatching {
                    PdfBoxBackend.addInkAnnotation(doc, 0, listOf(com.jumlo.app.core.PdfInkPoint(0.1, 0.1)))
                }.also { assertTrue("a single point is not a stroke", it.isFailure) }
            }
        }
    }

    @Test fun theInkKindIsMarkedAsFreehand() {
        assertTrue(PdfAnnotationKind.INK.isFreehand)
        assertFalse(PdfAnnotationKind.HIGHLIGHT.isFreehand)
        assertEquals(PdfAnnotationKind.INK, PdfAnnotationKind.from("ink"))
    }

    @Test fun theKindParserIsForgivingAboutLegacyValues() {
        assertEquals(PdfAnnotationKind.HIGHLIGHT, PdfAnnotationKind.from("highlight"))
        assertEquals(PdfAnnotationKind.UNDERLINE, PdfAnnotationKind.from(" UNDERLINE "))
        assertEquals(PdfAnnotationKind.TEXT, PdfAnnotationKind.from("something-else"))
        assertEquals(PdfAnnotationKind.TEXT, PdfAnnotationKind.from(null))
    }
}
