package com.jumlo.app.model

import com.jumlo.app.data.processing.ImageResizeSettings
import com.jumlo.app.data.processing.ImageTargetSettings
import com.jumlo.app.data.processing.ImageConversionSettings
import com.jumlo.app.data.processing.PdfImagesSettings
import com.jumlo.app.data.processing.ImageExifSettings
import com.jumlo.app.data.processing.ImageWatermarkSettings
import com.jumlo.app.data.processing.PdfAnnotateSettings
import com.jumlo.app.data.processing.PdfStampSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkspaceSettingsTest {
    @Test(expected=IllegalArgumentException::class)
    fun watermarkRequiresVisibleTextBeforeEnqueue() {
        WorkspaceDraft.forTool("img_watermark",listOf("photo")).copy(text1=" \n ").executionSettings()
    }

    @Test(expected=IllegalArgumentException::class)
    fun logoWatermarkRequiresASecondSelectedImage() {
        WorkspaceDraft.forTool("img_watermark",listOf("photo")).copy(flag1=true).executionSettings()
    }

    @Test fun logoWatermarkDoesNotRequireHiddenText() {
        val settings=Json.decodeFromString<ImageWatermarkSettings>(WorkspaceDraft.forTool("img_watermark",listOf("photo","logo")).copy(flag1=true).executionSettings())
        assertTrue(settings.useLogo)
        assertEquals("",settings.text)
    }

    @Test fun watermarkPreservesEnteredTextAndOpacity() {
        val settings=Json.decodeFromString<ImageWatermarkSettings>(WorkspaceDraft.forTool("img_watermark",listOf("photo")).copy(text1="Owner",num1="65").executionSettings())
        assertEquals("Owner",settings.text)
        assertEquals(65,settings.opacityPercent)
    }

    @Test(expected=IllegalArgumentException::class)
    fun annotationRequiresVisibleTextBeforeEnqueue() {
        WorkspaceDraft.forTool("pdf_annotate",listOf("pdf")).copy(text1="  ").executionSettings()
    }

    @Test fun annotationPreservesUserTextAndPage() {
        val settings=Json.decodeFromString<PdfAnnotateSettings>(WorkspaceDraft.forTool("pdf_annotate",listOf("pdf")).copy(text1="ملاحظة",num1="٢",flag1=true).executionSettings())
        assertEquals("ملاحظة",settings.text)
        assertEquals(2,settings.page)
        assertTrue(settings.asAnnotation)
    }

    @Test(expected=IllegalArgumentException::class)
    fun stampRequiresTextOrPageNumbers() {
        WorkspaceDraft.forTool("pdf_stamp",listOf("pdf")).executionSettings()
    }

    @Test fun pageNumberStampDoesNotRequireText() {
        val settings=Json.decodeFromString<PdfStampSettings>(WorkspaceDraft.forTool("pdf_stamp",listOf("pdf")).copy(flag1=true).executionSettings())
        assertTrue(settings.pageNumbers)
        assertEquals("",settings.text)
    }

    @Test(expected=IllegalArgumentException::class)
    fun renamePatternIsRejectedBeforeEnqueueWhenItExceedsHandlerLimit() {
        WorkspaceDraft.forTool("file_batch_rename",listOf("source")).copy(text1="x".repeat(201)).executionSettings()
    }

    @Test fun retainedExifUsesVisibleJpegQualityRegardlessOfStaleFormat() {
        val settings=Json.decodeFromString<ImageExifSettings>(WorkspaceDraft.forTool("img_exif",emptyList()).copy(format="PNG",quality="63",flag1=false).executionSettings())
        assertEquals("JPEG",settings.output.format)
        assertEquals(63,settings.output.quality)
    }

    @Test fun losslessPngDoesNotRequireHiddenQuality() {
        val settings=Json.decodeFromString<ImageConversionSettings>(WorkspaceDraft.forTool("img_01",emptyList()).copy(format="PNG",quality="").executionSettings())
        assertEquals("PNG",settings.format)
        assertEquals(85,settings.quality)
        val pages=Json.decodeFromString<PdfImagesSettings>(WorkspaceDraft.forTool("pdf_07",emptyList()).copy(format="PNG",quality="invalid").executionSettings())
        assertEquals("PNG",pages.format)
        assertEquals(85,pages.quality)
    }

    @Test(expected=IllegalArgumentException::class)
    fun jpegStillRejectsInvalidVisibleQuality() {
        WorkspaceDraft.forTool("img_01",emptyList()).copy(format="JPEG",quality="").executionSettings()
    }

    @Test fun targetSizeIgnoresStaleHiddenFormatAndQuality() {
        val draft=WorkspaceDraft.forTool("img_target",emptyList()).copy(format="stale-format",quality="not-a-number",prefix="target_",num1="250000")
        val settings=Json.decodeFromString<ImageTargetSettings>(draft.executionSettings())
        assertEquals("JPEG",settings.output.format)
        assertEquals(85,settings.output.quality)
        assertEquals("target_",settings.output.namePrefix)
        assertEquals(250000L,settings.targetBytes)
    }

    @Test fun directDownloadsRequireExplicitCleartextConsent() {
        assertFalse(WorkspaceDraft.forTool("dl_02",emptyList()).cleartextConsent)
    }
    @Test fun structuralPdfDraftsStartWithPdfOutputNames() {
        for (id in listOf("pdf_03","pdf_04","pdf_05","pdf_06","pdf_crop","pdf_compress","pdf_stamp","pdf_annotate","pdf_signature","pdf_metadata","pdf_forms"))
            assertTrue("Wrong default for $id",WorkspaceDraft.forTool(id,emptyList()).outputName.endsWith(".pdf"))
    }

    @Test(expected=IllegalArgumentException::class)
    fun percentageBeyondEngineLimitIsRejectedBeforeEnqueue() {
        WorkspaceDraft(toolId="img_02",percentage="500").executionSettings()
    }
    @Test fun percentageModeDoesNotRequireUnusedPixelFields() {
        val json=WorkspaceDraft(toolId="img_02",percentage="50",width="",height="").executionSettings()
        assertEquals(50,Json.decodeFromString<ImageResizeSettings>(json).percentage)
    }
    @Test(expected=IllegalArgumentException::class)
    fun conflictingResizeModesAreNotSilentlyIgnored() {
        WorkspaceDraft(toolId="img_02",percentage="50",longestEdge="1000").executionSettings()
    }
}
