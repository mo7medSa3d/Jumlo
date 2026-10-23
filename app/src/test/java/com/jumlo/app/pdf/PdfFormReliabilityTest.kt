package com.jumlo.app.pdf

import com.jumlo.app.data.pdf.FormEntry
import com.jumlo.app.data.pdf.PdfBoxBackend
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import org.junit.Assert.*
import org.junit.Test

class PdfFormReliabilityTest {
    @Test fun widgetBoundsUseTheActualPageRotatedCropAndIncludeMissingPagePointer() {
        PDDocument().use { doc ->
            val acro=form(doc)
            val page=doc.getPage(0)
            page.mediaBox=PDRectangle(600f,800f);page.cropBox=PDRectangle(100f,200f,300f,400f)
            page.rotation=90;page.userUnit=2f
            val field=PDTextField(acro).apply {partialName="visible"}
            val widget=PDAnnotationWidget().apply {rectangle=PDRectangle(160f,240f,60f,80f)}
            field.widgets=listOf(widget);acro.fields=listOf(field);page.annotations=listOf(widget)
            val info=PdfBoxBackend.formFields(doc).single().widgets.single()
            assertEquals(1,info.page);assertEquals(0,info.widgetIndex)
            assertEquals(.1,info.left,.00001);assertEquals(.2,info.top,.00001)
            assertEquals(.3,info.right,.00001);assertEquals(.4,info.bottom,.00001)
            assertNull("Discovery remains read-only",acro.defaultResources)
            assertNull(widget.page)
        }
    }
    private fun form(doc: PDDocument): PDAcroForm = PDAcroForm(doc).also {
        doc.documentCatalog.acroForm = it
        it.needAppearances = true
        doc.addPage(PDPage())
    }

    @Test fun fieldDiscoveryIncludesFullyQualifiedNestedTerminalFields() {
        PDDocument().use { doc ->
            val acro = form(doc)
            val parent = PDNonTerminalField(acro).apply { partialName = "person" }
            val child = PDTextField(acro).apply { partialName = "name"; value = "Ada" }
            parent.children = listOf(child)
            child.cosObject.setItem(COSName.PARENT, parent.cosObject)
            acro.fields = listOf(parent)
            assertEquals(listOf("person.name|text|Ada"), PdfBoxBackend.listFormFields(doc))
            assertEquals("person.name",PdfBoxBackend.formFields(doc).single().name)
        }
    }

    private fun radio(doc: PDDocument, acro: PDAcroForm): PDRadioButton {
        val radio = PDRadioButton(acro).apply { partialName = "answer" }
        radio.widgets = listOf("First", "Second").map { name ->
            PDAnnotationWidget().apply {
                rectangle = PDRectangle(20f, 20f)
                setPage(doc.getPage(0))
                val states = COSDictionary()
                for (state in listOf("Off", name)) {
                    val appearance = PDAppearanceStream(doc).apply { bBox = PDRectangle(20f, 20f) }
                    appearance.cosObject.createOutputStream().use { it.write("0 0 20 20 re S".toByteArray()) }
                    states.setItem(COSName.getPDFName(state), appearance)
                }
                appearance = PDAppearanceDictionary().also { it.cosObject.setItem(COSName.N, states) }
                setAppearanceState("Off")
            }
        }
        acro.fields = listOf(radio)
        doc.getPage(0).annotations = radio.widgets
        return radio
    }

    @Test fun radioSelectionWorksWhenOptionsAreDefinedOnlyByWidgetAppearanceNames() {
        PDDocument().use { doc ->
            val radio = radio(doc, form(doc))
            assertTrue(radio.exportValues.isEmpty())
            PdfBoxBackend.fillForm(doc, mapOf("answer" to FormEntry.Radio(1)))
            assertEquals("Second", radio.value)
            assertEquals("Off", radio.widgets[0].appearanceState.name)
            assertEquals("Second", radio.widgets[1].appearanceState.name)
            val field=PdfBoxBackend.formFields(doc).single()
            assertEquals(listOf("First","Second"),field.choices)
            assertEquals(1,field.selectedIndex)
        }
    }

    @Test fun readOnlyFieldsCannotBeOverwrittenByFormSubmission() {
        PDDocument().use { doc ->
            val acro = form(doc)
            val text = PDTextField(acro).apply { partialName = "reference"; value = "KEEP"; isReadOnly = true }
            acro.fields = listOf(text)
            val failure=runCatching { PdfBoxBackend.fillForm(doc, mapOf("reference" to FormEntry.Text("changed"))) }.exceptionOrNull()
            assertEquals("FORM_FIELD_READ_ONLY",failure?.message)
            assertEquals("KEEP", text.value)
            assertTrue(PdfBoxBackend.formFields(doc).single().readOnly)
        }
    }
}
