package com.jumlo.app.model

import com.jumlo.app.core.AutoOutputName
import com.jumlo.app.core.DownloadLinkPolicy
import com.jumlo.app.core.DriveLink
import com.jumlo.app.core.StoragePaths
import com.jumlo.app.data.processing.ImageResizeSettings
import com.jumlo.app.data.processing.ImageTargetSettings
import com.jumlo.app.data.processing.ImageConversionSettings
import com.jumlo.app.data.processing.PdfImagesSettings
import com.jumlo.app.data.processing.PdfSplitSettings
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

    @Test
    fun annotationRequiresVisibleTextBeforeEnqueue() {
        // A highlight is a region, so it needs no body; a note or a text box does.
        WorkspaceDraft.forTool("pdf_annotate",listOf("pdf"))
            .copy(text1="  ",text2="highlight").executionSettings()
        for (kind in listOf("note","textbox","text")) {
            runCatching {
                WorkspaceDraft.forTool("pdf_annotate",listOf("pdf")).copy(text1="  ",text2=kind).executionSettings()
            }.also { assertTrue("a $kind must require text", it.isFailure) }
        }
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

    @Test fun pdfToImagesFormatsFixedWidthThreeDigitPageNameAndSubfolder() {
        assertEquals("تقرير المبيعات - 003.png", AutoOutputName.pageImageName("تقرير المبيعات.pdf", 3, "png"))
        assertEquals("Contract - 012.jpg", AutoOutputName.pageImageName("Contract.pdf", 12, "jpg"))
        assertEquals("Download/Jumlo/Images/تقرير المبيعات/", StoragePaths.relativePath("image/png", "تقرير المبيعات"))
    }

    @Test fun splitPdfRespectsCustomOutputNameInsteadOfHardcodedSplitPrefix() {
        assertEquals("عقد العمل - 001.pdf", AutoOutputName.splitPartName("عقد العمل.pdf", "fallback.pdf", 1))
        assertEquals("عقد العمل - 002.pdf", AutoOutputName.splitPartName("عقد العمل.pdf", "fallback.pdf", 2))
        val splitSettings = Json.decodeFromString<PdfSplitSettings>(
            WorkspaceDraft.forTool("pdf_05", listOf("src")).copy(outputName = "MyCustomSplit.pdf").executionSettings()
        )
        assertEquals("MyCustomSplit.pdf", splitSettings.displayName)
    }

    @Test fun googleDriveSharedLinksAndAllSocialPlatformsRouteAndClassifyCorrectly() {
        val driveUrl = "https://drive.google.com/file/d/1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345/view?usp=sharing"
        assertTrue(DownloadLinkPolicy.isGoogleDriveDownload(driveUrl))
        assertEquals(
            "https://drive.usercontent.google.com/download?id=1aBcDeFgHiJkLmNoPqRsTuVwXyZ012345&export=download&confirm=t",
            DriveLink.toDirectDownloadUrl(driveUrl)
        )
        assertEquals("YouTube", DownloadLinkPolicy.detectPlatformName("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("TikTok", DownloadLinkPolicy.detectPlatformName("https://www.tiktok.com/@user/video/123"))
        assertEquals("Instagram", DownloadLinkPolicy.detectPlatformName("https://www.instagram.com/reel/abc/"))
        assertEquals("Facebook", DownloadLinkPolicy.detectPlatformName("https://www.facebook.com/watch/?v=123"))
        assertEquals("LinkedIn", DownloadLinkPolicy.detectPlatformName("https://www.linkedin.com/posts/abc"))
        assertEquals("X (Twitter)", DownloadLinkPolicy.detectPlatformName("https://x.com/user/status/123"))
        assertEquals("SoundCloud", DownloadLinkPolicy.detectPlatformName("https://soundcloud.com/artist/track"))
        assertEquals(com.jumlo.app.core.DirectFileKind.PDF, DownloadLinkPolicy.classifyDirectFile("book.pdf", "application/pdf"))
        assertEquals(com.jumlo.app.core.DirectFileKind.VIDEO, DownloadLinkPolicy.classifyDirectFile("clip.mp4", "video/mp4"))
        assertEquals(com.jumlo.app.core.DirectFileKind.AUDIO, DownloadLinkPolicy.classifyDirectFile("song.mp3", "audio/mpeg"))
        assertFalse(DownloadLinkPolicy.isDirect("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
}
