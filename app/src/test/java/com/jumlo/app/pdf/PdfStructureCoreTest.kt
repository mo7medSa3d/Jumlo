package com.jumlo.app.pdf

import com.jumlo.app.data.pdf.FormEntry
import com.jumlo.app.data.pdf.OrganizeOp
import com.jumlo.app.data.pdf.PageGroup
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.jumlo.app.data.pdf.PdfInput
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Pure-JVM acceptance tests for the structural PDF backend on real PDF fixtures. */
class PdfStructureCoreTest {
    private val dir: File = createTmp()

    private fun createTmp(): File = java.nio.file.Files.createTempDirectory("pdfcore-test").toFile().also { it.deleteOnExit() }

    private fun docFile(marker: String, pages: Int): File = PdfFixtures.simpleDoc(File(dir, "$marker$pages.pdf"), List(pages) { i -> "$marker page ${i + 1}" })

    @Test fun merge_concatenates_pages_in_input_order() {
        val out = File(dir, "merged.pdf")
        PdfBoxBackend.merge(listOf(PdfInput(docFile("alpha", 2)), PdfInput(docFile("beta", 3)), PdfInput(docFile("gamma", 1))), out)
        PdfBoxBackend.open(out, null).use { merged ->
            val info = PdfBoxBackend.info(merged)
            assertEquals(6, info.pageCount)
            val texts = PdfBoxBackend.pageTexts(merged)
            assertEquals("alpha page 1", texts[0])
            assertEquals("beta page 1", texts[2])
            assertEquals("gamma page 1", texts[5])
        }
    }

    @Test fun split_produces_one_file_per_group() {
        val source = docFile("alpha", 4)
        val first = File(dir, "p1.pdf"); val second = File(dir, "p2.pdf")
        val files = PdfBoxBackend.split(source, listOf(PageGroup(0, 2), PageGroup(2, 1)), listOf(first, second))
        assertEquals(2, files.size)
        assertEquals("alpha page 1", PdfBoxBackend.open(files[0], null).use { PdfBoxBackend.pageTexts(it)[0] })
        assertEquals(1, PdfBoxBackend.open(files[1], null).use { it.numberOfPages })
    }

    @Test fun extract_preserves_selected_order() {
        val out = File(dir, "extracted.pdf")
        PdfBoxBackend.organize(docFile("alpha", 5), listOf(OrganizeOp(3), OrganizeOp(1)), out)
        PdfBoxBackend.open(out, null).use {
            val texts = PdfBoxBackend.pageTexts(it)
            assertEquals(2, it.numberOfPages)
            assertEquals("alpha page 4", texts[0])
            assertEquals("alpha page 2", texts[1])
        }
    }

    @Test fun organize_applies_rotation_and_delete() {
        val out = File(dir, "organized.pdf")
        PdfBoxBackend.organize(docFile("alpha", 3), listOf(OrganizeOp(0, 90), OrganizeOp(2, 180)), out)
        PdfBoxBackend.open(out, null).use {
            assertEquals(2, it.numberOfPages)
            assertEquals(90, it.getPage(0).rotation)
            assertEquals(180, it.getPage(1).rotation)
        }
    }

    @Test fun repeatedPageInstancesKeepNaturalRotationAndIndependentRelativeRotations() {
        val out=File(dir,"duplicate-rotations.pdf")
        val rotated=File(dir,"duplicate-rotated.pdf")
        PdfBoxBackend.open(docFile("duplicate",1),null).use { doc ->
            doc.getPage(0).rotation=90
            doc.save(rotated)
        }
        PdfBoxBackend.organize(rotated,listOf(OrganizeOp(0,90),OrganizeOp(0,180),OrganizeOp(0,0)),out)
        PdfBoxBackend.open(out,null).use { result ->
            assertEquals(listOf(180,270,90),result.pages.map { it.rotation })
            val texts=PdfBoxBackend.pageTexts(result)
            assertEquals(texts.toString(),3,texts.count { it.filterNot(Char::isWhitespace).contains("duplicate") })
        }
    }

    @Test fun assemblePreservesInheritedPageResources() {
        val out=File(dir,"inherited.pdf")
        val inherited=File(dir,"inherited-source.pdf")
        PdfBoxBackend.open(docFile("inherited marker",1),null).use { doc ->
            val page=doc.getPage(0)
            val parent=page.cosObject.getDictionaryObject(com.tom_roush.pdfbox.cos.COSName.PARENT) as com.tom_roush.pdfbox.cos.COSDictionary
            parent.setItem(com.tom_roush.pdfbox.cos.COSName.RESOURCES,page.resources)
            page.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.RESOURCES)
            doc.save(inherited)
        }
        PdfBoxBackend.organize(inherited,listOf(OrganizeOp(0),OrganizeOp(0,90)),out)
        PdfBoxBackend.open(out,null).use { result ->
            val texts=PdfBoxBackend.pageTexts(result)
            assertTrue(texts.toString(),texts.all { it.filterNot(Char::isWhitespace).contains("inheritedmarker") })
        }
    }

    @Test fun split_preserves_form_bookmarks_and_document_information() {
        val source = PdfFixtures.catalogDoc(File(dir, "catalog.pdf"), 3)
        val part = File(dir, "catalog-part.pdf")
        PdfBoxBackend.split(source, listOf(PageGroup(0, 2)), listOf(part))
        PdfBoxBackend.open(part, null).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals("Catalog Title", doc.documentInformation.title)
            assertEquals("Jumlo", doc.documentInformation.author)
            val acro = doc.documentCatalog.acroForm
            assertTrue("AcroForm survived the split", acro != null)
            assertEquals(listOf("name"), acro!!.fields.map { it.partialName })
            val outline = doc.documentCatalog.documentOutline
            assertTrue("Bookmark survived the split", outline != null)
            assertEquals("Chapter 1", outline!!.firstChild?.title)
        }
    }

    @Test fun extract_keeps_the_form_widget_on_the_retained_page() {
        val source = PdfFixtures.catalogDoc(File(dir, "catalog-extract.pdf"), 2)
        val out = File(dir, "catalog-extracted.pdf")
        PdfBoxBackend.extract(source, listOf(0), out)
        PdfBoxBackend.open(out, null).use { doc ->
            assertEquals(1, doc.numberOfPages)
            val acro = doc.documentCatalog.acroForm
            assertEquals(listOf("name"), acro!!.fields.map { it.partialName })
            assertEquals(1, doc.getPage(0).annotations.size)
        }
    }

    @Test fun organize_with_a_duplicated_page_keeps_a_valid_document() {
        val source = PdfFixtures.catalogDoc(File(dir, "catalog-duplicate.pdf"), 2)
        val out = File(dir, "catalog-duplicated.pdf")
        PdfBoxBackend.organize(source, listOf(OrganizeOp(0), OrganizeOp(0, 90)), out)
        PdfBoxBackend.open(out, null).use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertEquals(listOf(0, 90), doc.pages.map { it.rotation })
            assertEquals(2, PdfBoxBackend.pageTexts(doc).size)
        }
    }

    @Test fun rotate_in_place_persists_degrees() {
        val target = File(dir, "rotated.pdf")
        PdfBoxBackend.open(docFile("alpha", 2), null).use { doc ->
            PdfBoxBackend.rotate(doc, listOf(1), 90)
            doc.save(target)
        }
        PdfBoxBackend.open(target, null).use { assertEquals(90, it.getPage(1).rotation) }
    }

    @Test fun insert_pages_from_another_document() {
        val out = File(dir, "inserted.pdf")
        PdfBoxBackend.open(docFile("alpha", 2), null).use { doc ->
            PdfBoxBackend.insert(docFile("alpha", 2), PdfInput(docFile("beta", 2)), 1, out)
        }
        PdfBoxBackend.open(out, null).use {
            val texts = PdfBoxBackend.pageTexts(it)
            assertEquals(4, it.numberOfPages)
            assertEquals("alpha page 1", texts[0])
            assertEquals("beta page 1", texts[1])
            assertEquals("beta page 2", texts[2])
            assertEquals("alpha page 2", texts[3])
        }
    }

    @Test fun crop_box_updates_page_geometry() {
        val target = File(dir, "cropped.pdf")
        PdfBoxBackend.open(docFile("alpha", 1), null).use { doc ->
            PdfBoxBackend.setCrop(doc, 0, 0.1, 0.2, 0.9, 0.8)
            doc.save(target)
        }
        PdfBoxBackend.open(target, null).use { doc ->
            val info = PdfBoxBackend.info(doc)
            val media = info.pageBoxes[0].media
            val crop = info.pageBoxes[0].crop
            assertTrue(crop[0] > media[0])
            assertTrue(crop[2] < media[2])
        }
    }

    @Test fun stamp_text_and_annotation_persist() {
        val target = File(dir, "stamped.pdf")
        PdfBoxBackend.open(docFile("alpha", 1), null).use { doc ->
            PdfBoxBackend.stampText(doc, listOf(0), "CONFIDENTIAL", 0.1, 0.5, 24f, 0.5f)
            PdfBoxBackend.addTextAnnotation(doc, 0, "Review note", 0.2, 0.3)
            PdfBoxBackend.stampInvisibleText(doc, 0, "ocr-searchable-token", 0.05, 0.05, 10f)
            doc.save(target)
        }
        PdfBoxBackend.open(target, null).use { doc ->
            val text = PdfBoxBackend.pageTexts(doc).single()
            assertTrue(text.contains("CONFIDENTIAL"))
            assertTrue(text.contains("ocr-searchable-token"))
            assertEquals(1, doc.getPage(0).annotations.size)
            assertEquals("Review note", doc.getPage(0).annotations.single().contents)
        }
    }

    @Test fun metadata_round_trip() {
        val target = File(dir, "meta.pdf")
        PdfBoxBackend.open(docFile("alpha", 1), null).use { doc ->
            PdfBoxBackend.setMetadata(doc, mapOf("title" to "T1", "author" to "A1"))
            doc.save(target)
        }
        PdfBoxBackend.open(target, null).use {
            val meta = PdfBoxBackend.metadata(it)
            assertEquals("T1", meta["title"])
            assertEquals("A1", meta["author"])
        }
    }

    @Test fun protect_then_remove_standard_password() {
        val protectedFile = File(dir, "prot.pdf")
        val removedFile = File(dir, "removed.pdf")
        PdfBoxBackend.open(docFile("alpha", 1), null).use { doc ->
            PdfBoxBackend.protect(doc, "userpw".toCharArray(), "ownerpw".toCharArray(), protectedFile)
        }
        var rejected = false
        try { PdfBoxBackend.open(protectedFile, null) } catch (e: InvalidPasswordException) { rejected = true }
        assertTrue("unprotected open must fail", rejected)
        PdfBoxBackend.open(protectedFile, "userpw".toCharArray()).use { doc ->
            PdfBoxBackend.removeProtection(doc, removedFile)
        }
        PdfBoxBackend.open(removedFile, null).use { assertEquals(1, it.numberOfPages) }
    }

    @Test fun wrong_password_fails_explicitly() {
        val protectedFile = File(dir, "prot2.pdf")
        PdfBoxBackend.open(docFile("alpha", 1), null).use { doc ->
            PdfBoxBackend.protect(doc, "u".toCharArray(), "o".toCharArray(), protectedFile)
        }
        var rejected = false
        try { PdfBoxBackend.open(protectedFile, "wrong".toCharArray()) } catch (e: InvalidPasswordException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun fill_form_and_flatten() {
        val flat = File(dir, "form-flat.pdf")
        PdfBoxBackend.open(PdfFixtures.formDoc(File(dir, "form.pdf")), null).use { doc ->
            PdfBoxBackend.fillForm(doc, mapOf("name" to FormEntry.Text("Mo"), "agree" to FormEntry.Check(true)))
            assertEquals("Mo", PdfBoxBackend.listFormFields(doc).first { it.startsWith("name|") }.substringAfter('|').substringAfter('|'))
            PdfBoxBackend.flattenForm(doc, flat)
        }
        PdfBoxBackend.open(flat, null).use { flattened ->
            assertTrue("fields must be gone after flatten", PdfBoxBackend.listFormFields(flattened).isEmpty())
            val text = PdfBoxBackend.pageTexts(flattened).joinToString(" ")
            assertTrue("flattened value must be visible", text.contains("Mo"))
        }
    }

    @Test fun parserPreservesPrimitiveLookingTextAndUsesActualCheckboxType() {
        for(raw in listOf("name=42\nagree=true", "{\"name\":\"42\",\"agree\":true}", "{\"name\":42,\"agree\":\"true\"}")) {
            PdfBoxBackend.open(PdfFixtures.formDoc(File(dir,"input-${raw.hashCode()}.pdf")),null).use { doc ->
                PdfBoxBackend.fillForm(doc,com.jumlo.app.data.pdf.FormInputParser.parse(raw))
                assertEquals("42",doc.documentCatalog.acroForm.getField("name").valueAsString)
                assertTrue((doc.documentCatalog.acroForm.getField("agree") as com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox).isChecked)
            }
        }
        for(raw in listOf("name=true", "{\"name\":\"true\"}", "{\"name\":true}")) {
            PdfBoxBackend.open(PdfFixtures.formDoc(File(dir,"bool-${raw.hashCode()}.pdf")),null).use { doc ->
                PdfBoxBackend.fillForm(doc,com.jumlo.app.data.pdf.FormInputParser.parse(raw))
                assertEquals("true",doc.documentCatalog.acroForm.getField("name").valueAsString)
            }
        }
    }

    @Test fun flattenRegeneratesExistingTextAndFindsWidgetWithoutPagePointer() {
        val flat=File(dir,"missing-page-flat.pdf")
        PdfBoxBackend.open(PdfFixtures.formDoc(File(dir,"existing.pdf")),null).use { doc ->
            PdfBoxBackend.fillForm(doc,mapOf("name" to FormEntry.Text("Existing 42")))
            val widget=doc.documentCatalog.acroForm.getField("name").widgets.single()
            widget.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.P)
            widget.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.AP)
            PdfBoxBackend.flattenForm(doc,flat)
        }
        PdfBoxBackend.open(flat,null).use { assertTrue(PdfBoxBackend.pageTexts(it).single().contains("Existing 42")) }
    }

    @Test fun unresolvedWidgetFailsBeforeRemovingFieldsOrAnnotations() {
        val flat=File(dir,"must-not-flatten.pdf")
        PdfBoxBackend.open(PdfFixtures.formDoc(File(dir,"unresolved.pdf")),null).use { doc ->
            val acro=doc.documentCatalog.acroForm
            val widget=acro.getField("name").widgets.single()
            widget.cosObject.removeItem(com.tom_roush.pdfbox.cos.COSName.P)
            doc.getPage(0).annotations=doc.getPage(0).annotations.filter { it.cosObject !== widget.cosObject }
            val before=doc.getPage(0).annotations.size
            try { PdfBoxBackend.flattenForm(doc,flat); throw AssertionError("missing page must fail") }
            catch(expected:IllegalArgumentException) { assertEquals("FORM_WIDGET_PAGE_MISSING",expected.message) }
            assertTrue(doc.documentCatalog.acroForm.cosObject === acro.cosObject)
            assertEquals(2,acro.fields.size)
            assertEquals(before,doc.getPage(0).annotations.size)
            assertFalse(flat.exists())
        }
    }

    @Test fun compress_keeps_text_and_shrinks_images() {
        val source = PdfFixtures.imageDoc(File(dir, "big.pdf"), textPage = true)
        val baseline = source.length()
        val out = File(dir, "small.pdf")
        PdfBoxBackend.open(source, null).use { doc ->
            PdfBoxBackend.compress(doc, maxDpi = 72, jpegQuality = 40)
            doc.save(out)
        }
        val texts = PdfBoxBackend.open(out, null).use { PdfBoxBackend.pageTexts(it) }
        assertTrue("text must survive compression", texts[1].contains("marker"))
        assertTrue("output must be smaller than raw-image baseline", out.length() < baseline)
    }


    @Test fun page_info_exposes_boxes_and_text_flag() {
        PdfBoxBackend.open(docFile("alpha", 2), null).use {
            val info = PdfBoxBackend.info(it)
            assertEquals(2, info.pageCount)
            assertEquals(2, info.pageBoxes.size)
            assertEquals(4, info.pageBoxes[0].media.size)
            assertEquals(0, info.pageBoxes[0].rotationDegrees)
            assertTrue(info.hasDigitalText)
        }
    }
}
