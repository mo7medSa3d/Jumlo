package com.jumlo.app.ui.screenshots

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jumlo.app.MainActivity
import com.jumlo.app.R
import com.jumlo.app.data.jobs.CapabilityRegistry
import com.jumlo.app.data.jobs.stableError
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File
import java.util.UUID

/**
 * Opt-in real-Android activity/workspace UI captures, not a tool-operation verification matrix.
 * Run one screenshotVariant on dedicated clean app data, then pull the files before clearing it.
 * External account/network/scanner states remain explicitly blocked when fixtures are absent.
 */
@RunWith(AndroidJUnit4::class)
class ToolScreenshotMatrixDeviceTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    // The actual Android PdfRenderer is available here; all PDF-ready assertions remain active.
    private val hostPdfUnavailable = false
    // Bounded allowance for software TCG; no app-performance claim uses these timings.
    private val readyTimeoutMillis = 60_000L
    private data class Entry(val tool: String, val state: String, var status: String = "NOT_RUN", var detail: String = "", var file: String = "")
    private val entries = mutableListOf<Entry>()
    private val failures = mutableListOf<String>()
    private var variant = "unknown"
    private var fixtureDirectory: File? = null
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val modeEnabled get() = arguments.getString("recordScreenshots") == "true"
    private val outputRoot: File get() = File(
        requireNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)),
        "screenshots"
    )
    private fun flushCoverage() {
        if (!modeEnabled) return
        val directory = File(outputRoot, variant).apply { mkdirs() }
        File(directory, "coverage.tsv").writeText(buildString {
            append("tool\tstate\tstatus\tdetail\tfile\n")
            entries.forEach { append("${it.tool}\t${it.state}\t${it.status}\t${it.detail}\t${it.file}\n") }
        })
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        File(directory, "runtime.properties").writeText(
            "renderer=AndroidComposeCaptureToImage\napi=${Build.VERSION.SDK_INT}\n" +
                "androidRelease=${Build.VERSION.RELEASE}\nabi=${Build.SUPPORTED_ABIS.joinToString()}\n" +
                "fingerprint=${Build.FINGERPRINT}\nviewport=${metrics.widthPixels}x${metrics.heightPixels}\n" +
                "densityDpi=${metrics.densityDpi}\nevidence=ANDROID_UI_CAPTURE\n" +
                "scope=UI_CAPTURE_NOT_TOOL_OPERATION_MATRIX\npdfRenderer=ANDROID_FRAMEWORK\n")
    }
    private fun reportProgress(entry: Entry) {
        println("SCREENSHOT_PROGRESS\t$variant\t${entry.tool}\t${entry.state}\t${entry.status}\t${entry.detail}")
        System.out.flush()
        flushCoverage()
    }
    private fun recordFailure(name: String, error: Throwable) {
        val directory = File(outputRoot, variant).apply { mkdirs() }
        // This opt-in test uses only controlled fixtures. Preserve selector/stack detail for triage.
        File(directory, "$name.failure.txt").writeText(error.stackTraceToString())
    }
    private fun captureFailureDiagnostics(name: String, error: Throwable) {
        recordFailure(name, error)
        val directory = File(outputRoot, variant).apply { mkdirs() }
        val diagnosticErrors = mutableListOf<String>()
        // Run while the activity is still alive; diagnostics must never replace the first failure.
        runCatching {
            File(directory, "$name.semantics.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString())
        }.onFailure { diagnosticErrors += "SEMANTICS\n${it.stackTraceToString()}" }
        runCatching { writeScreenshot(File(directory, "$name.failure.png")) }
            .onFailure { diagnosticErrors += "IMAGE\n${it.stackTraceToString()}" }
        if (diagnosticErrors.isNotEmpty()) {
            File(directory, "$name.diagnostics-failures.txt").writeText(diagnosticErrors.joinToString("\n"))
        }
    }
    private fun writeScreenshot(target: File) {
        requireNotNull(target.parentFile).mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private val manifest = object : TestWatcher() {
        override fun starting(description: Description) {
            variant = requireNotNull(description.methodName)
            CapabilityRegistry.tools.forEach { tool ->
                listOf("empty", "loaded").forEach { state -> entries += Entry(tool.id, state) }
            }
            listOf("empty", "drawn").forEach { entries += Entry("signature_pad", it) }
            flushCoverage()
        }
        override fun failed(error: Throwable, description: Description) {
            recordFailure("driver", error)
            entries.filter { it.status == "NOT_RUN" }.forEach {
                it.status = "BLOCKED"
                it.detail = "DRIVER_FAILED_${error.javaClass.simpleName}"
            }
            flushCoverage()
        }
        override fun finished(description: Description) {
            flushCoverage()
            fixtureDirectory?.deleteRecursively()
        }
    }
    private val optIn = TestRule { base, description -> object : Statement() {
        override fun evaluate() {
            assumeTrue("Set recordScreenshots=true and screenshotVariant to this method on clean app data",
                modeEnabled && arguments.getString("screenshotVariant") == description.methodName)
            base.evaluate()
        }
    } }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(optIn).around(manifest).around(compose)

    @Test
    fun englishLight() = captureMatrix(AppLanguage.ENGLISH, ThemeMode.LIGHT)

    @Test
    fun englishDark() = captureMatrix(AppLanguage.ENGLISH, ThemeMode.DARK)

    @Test
    fun arabicLight() = captureMatrix(AppLanguage.ARABIC, ThemeMode.LIGHT)

    @Test
    fun arabicDark() = captureMatrix(AppLanguage.ARABIC, ThemeMode.DARK)

    private fun viewModel(): AppViewModel = ViewModelProvider(compose.activity)[AppViewModel::class.java]

    private fun captureMatrix(language: AppLanguage, theme: ThemeMode) {
        val launchIntent = Intent(compose.activity.intent)
        try {
            captureMatrixContents(language, theme)
        } finally {
            // Restore ActivityScenario's lifecycle identity before the Compose rule closes it.
            compose.runOnUiThread { compose.activity.intent = launchIntent }
        }
    }

    private fun captureMatrixContents(language: AppLanguage, theme: ThemeMode) {
        assumeTrue("Set recordScreenshots=true and a screenshotVariant instrumentation argument", modeEnabled)
        compose.waitUntil(readyTimeoutMillis) {
            viewModel().settings.value.isLoaded && viewModel().workspaceReady.value
        }
        if (compose.onAllNodesWithTag("onboarding.dismiss").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("onboarding.dismiss").performClick()
        }
        compose.runOnUiThread { viewModel().setLanguage(language); viewModel().setThemeMode(theme) }
        compose.waitUntil(readyTimeoutMillis) { viewModel().settings.value.let { it.language == language && it.themeMode == theme } }
        // The real locale change may recreate MainActivity; follow the current rule activity.
        compose.waitUntil(readyTimeoutMillis) {
            val configuration = compose.activity.resources.configuration
            configuration.locales[0].language == language.code &&
                configuration.layoutDirection == (if (language.isRtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR)
        }
        compose.waitForIdle()

        // Files subscribes to the actual imported/output stores; never treat an uncollected empty
        // StateFlow initial value as an empty library, and never delete pre-existing app documents.
        open("file_02")
        compose.waitUntil(readyTimeoutMillis) {
            viewModel().sourceLoad.value == com.jumlo.app.ui.DataLoadStatus.READY &&
                viewModel().outputLoad.value == com.jumlo.app.ui.DataLoadStatus.READY &&
                viewModel().trashLoad.value == com.jumlo.app.ui.DataLoadStatus.READY
        }
        check(viewModel().imported.value.isEmpty() && viewModel().outputs.value.isEmpty() && viewModel().trashed.value.isEmpty()) {
            "SCREENSHOT_CAPTURE_REQUIRES_CLEAN_APP_DATA"
        }

        // Establish actual warm-intent navigation before the per-tool capture loop.
        open("pdf_01")

        // Capture all empty routes before importing the real fixture library.
        for (tool in CapabilityRegistry.tools) capture(tool.id, "empty") {
            open(tool.id)
        }

        val fixtures = importFixtures()
        for (tool in CapabilityRegistry.tools) capture(tool.id, "loaded") {
            val keys = fixtureKeys(tool.id)
            open(tool.id)
            when (tool.id) {
                "dl_03" -> compose.runOnUiThread { viewModel().setUrls("https://example.invalid/report.pdf\nhttps://example.invalid/photo.png") }
                "file_02" -> compose.onNodeWithText(compose.activity.getString(R.string.ui_imported)).performClick()
                "dl_settings" -> Unit
                else -> {
                    val ids = keys.map { fixtures.getValue(it) }
                    compose.runOnUiThread { viewModel().chooseToolWithSources(tool.id, ids) }
                    compose.waitUntil(readyTimeoutMillis) {
                        viewModel().form.value.sourceIds == ids &&
                            ids.all { id -> viewModel().imported.value.any { it.id == id } }
                    }
                    settleLoadedPreview(tool.id, keys, ids)
                }
            }
        }
        captureSignaturePad(fixtures.getValue("pdf"))
        assertTrue("Unexpected screenshot failures: ${failures.joinToString()}", failures.isEmpty())
    }

    private fun blockedReason(tool: String, state: String): String? = when {
        hostPdfUnavailable && tool == "signature_pad" -> "HOST_PDF_RENDERER_UNAVAILABLE"
        tool == "signature_pad" -> null
        !CapabilityRegistry.visible(tool) || !CapabilityRegistry.enabled(tool) -> "CAPABILITY_UNAVAILABLE"
        tool == "scan_doc" -> "EXTERNAL_ML_KIT_SCANNER_FLOW_NOT_CAPTURED"
        state == "loaded" && tool in setOf("dl_media", "dl_02") -> "REAL_NETWORK_METADATA_FIXTURE_REQUIRED"
        state == "loaded" && tool == "file_drive" -> "GOOGLE_DRIVE_ACCOUNT_FIXTURE_REQUIRED"
        state == "loaded" && tool == "file_trash" -> "DURABLE_TRASH_OUTPUT_FIXTURE_REQUIRED"
        hostPdfUnavailable && state == "loaded" && fixtureKeys(tool).any { it.startsWith("pdf") } -> "HOST_PDF_RENDERER_UNAVAILABLE"
        else -> null
    }

    private fun capture(tool: String, state: String, arrange: () -> Unit) {
        val entry = entries.single { it.tool == tool && it.state == state }
        blockedReason(tool, state)?.let {
            entry.status = "BLOCKED"; entry.detail = it; reportProgress(entry); return
        }
        entry.status = "RUNNING"
        reportProgress(entry)
        try {
            arrange()
            compose.waitForIdle()
            assertScreenIdentity(if (tool == "signature_pad") "pdf_signature" else tool)
            val target = File(File(outputRoot, variant), "${tool}_${state}.png")
            writeScreenshot(target)
            check(target.isFile && target.length() > 0L) { "SCREENSHOT_NOT_WRITTEN" }
            entry.status = "RECORDED_ANDROID_UI"
            entry.file = target.name
            entry.detail = when (tool) {
                "dl_settings" -> "SETTINGS_ROUTE_HAS_NO_FILE_INPUT_STATE"
                "dl_03" -> if (state == "loaded") "INPUT_CONFIGURED_NO_NETWORK_REQUEST" else ""
                "pdf_ocr" -> if (state == "loaded") {
                    if (hostPdfUnavailable) "IMAGE_INPUT_NO_RECOGNITION_EXECUTION" else "PDF_INPUT_NO_RECOGNITION_EXECUTION"
                } else ""
                else -> ""
            }
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            captureFailureDiagnostics("${tool}_${state}", error)
            entry.status = "FAILED"
            entry.detail = "${error.javaClass.simpleName}_${stableError(error)}"
            failures += "$tool/$state:${entry.detail}"
        } finally {
            reportProgress(entry)
        }
    }

    private fun open(tool: String) {
        when (tool) {
            "dl_settings" -> {
                leaveWorkspaceBeforeRootTab()
                if (!exists("settings.screen")) compose.onNodeWithTag("nav.SETTINGS").performClick()
                compose.waitUntil(readyTimeoutMillis) { compose.onAllNodesWithTag("settings.screen").fetchSemanticsNodes().isNotEmpty() }
            }
            "file_02", "file_trash" -> {
                leaveWorkspaceBeforeRootTab()
                if (!exists("files.screen")) compose.onNodeWithTag("nav.FILES").performClick()
                compose.waitUntil(readyTimeoutMillis) { compose.onAllNodesWithTag("files.screen").fetchSemanticsNodes().isNotEmpty() }
                val label = if (tool == "file_trash") R.string.files_trash_title else R.string.ui_imported
                compose.onNode(hasText(compose.activity.getString(label)) and hasClickAction()).performClick()
            }
            else -> {
                compose.runOnUiThread {
                    check(viewModel().chooseTool(tool)) { "CAPABILITY_UNAVAILABLE" }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("jumlo://workspace"))
                        .setClass(compose.activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    // Deliver the real Android warm launch; the title assertion observes onNewIntent navigation.
                    compose.activity.startActivity(intent)
                }
                compose.waitUntil(readyTimeoutMillis) { compose.onAllNodesWithTag("route.workspace").fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                compose.onAllNodesWithTag("workspace.scroll").fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
                    compose.onNodeWithTag("workspace.scroll").performScrollToIndex(0)
                }
            }
        }
        assertScreenIdentity(tool)
    }

    private fun exists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun leaveWorkspaceBeforeRootTab() {
        val discard = compose.activity.getString(R.string.ui_discard_leave)
        fun workspaceNodes() = compose.onAllNodesWithTag("route.workspace", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.id }.toSet()
        // Warm deep links can leave another real workspace below the current one. Each Back
        // must make observable progress; a selected dock tab itself intentionally does not navigate.
        repeat(CapabilityRegistry.tools.size + 2) {
            compose.waitForIdle()
            val before = workspaceNodes()
            if (before.isEmpty()) return
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.scan_back))
                .assertIsDisplayed().performClick()
            compose.waitUntil(readyTimeoutMillis) {
                workspaceNodes() != before || compose.onAllNodesWithText(discard).fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithText(discard).fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText(discard).assertIsDisplayed().performClick()
                compose.waitUntil(readyTimeoutMillis) { workspaceNodes() != before }
            }
        }
        compose.waitForIdle()
        check(!exists("route.workspace")) { "WORKSPACE_BACK_STACK_NOT_EXHAUSTED" }
    }

    private fun assertScreenIdentity(tool: String) {
        val activity = compose.activity
        val language = viewModel().settings.value.language
        val (route, title) = when (tool) {
            "dl_settings" -> "settings.screen" to com.jumlo.app.model.DockTab.SETTINGS.let {
                if (language.isRtl) it.labelAr else it.labelEn
            }
            "file_02", "file_trash" -> "files.screen" to com.jumlo.app.model.DockTab.FILES.let {
                if (language.isRtl) it.labelAr else it.labelEn
            }
            else -> {
                val item = CapabilityRegistry.tools.single { it.id == tool }
                "route.workspace" to if (language.isRtl) item.titleAr else item.titleEn
            }
        }
        compose.waitUntil(readyTimeoutMillis) {
            exists(route) && compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(route).assertIsDisplayed()
        compose.onAllNodesWithText(title).onFirst().assertIsDisplayed()
        if (tool in setOf("file_02", "file_trash")) {
            val section = activity.getString(if (tool == "file_trash") R.string.files_trash_title else R.string.ui_imported)
            compose.onNode(hasText(section) and hasClickAction()).assertIsSelected().assertIsDisplayed()
        }
    }

    private fun importFixtures(): Map<String, String> {
        fixtureDirectory = File(compose.activity.cacheDir, "screenshot-fixtures-${UUID.randomUUID()}")
        val files = runBlocking(Dispatchers.IO) { ScreenshotFixtures(requireNotNull(fixtureDirectory)).create() }
        // This real route never mounts InputPreview or SourceOrderStrip while PDF fixtures arrive.
        open("dl_03")
        val ids = linkedMapOf<String, String>()
        for ((key, source) in files) {
            val previous = viewModel().form.value.sourceIds.toSet()
            compose.runOnUiThread { viewModel().intakeLocalFile(source.first, source.first.name, source.second) }
            compose.waitUntil(readyTimeoutMillis) { viewModel().form.value.sourceIds.any { it !in previous } }
            ids[key] = viewModel().form.value.sourceIds.single { it !in previous }
        }
        return ids
    }

    private fun fixtureKeys(tool: String): List<String> = when {
        tool == "pdf_ocr" && hostPdfUnavailable -> listOf("image")
        tool == "pdf_forms" -> listOf("pdf_form")
        tool == "pdf_02" -> listOf("image", "image_second")
        tool in setOf("pdf_03", "pdf_compare") -> listOf("pdf", "pdf_second")
        tool == "pdf_signature" -> listOf("pdf", "signature")
        tool == "pdf_image" -> listOf("pdf", "image_second")
        tool.startsWith("pdf_") -> listOf("pdf")
        tool in setOf("img_join", "img_stitch", "img_compare") -> listOf("image", "image_second")
        tool.startsWith("img_") -> listOf("image")
        tool == "file_08" -> listOf("zip")
        tool in setOf("dl_03", "dl_settings", "file_02") -> emptyList()
        else -> listOf("text", "duplicate")
    }

    private fun settleLoadedPreview(tool: String, keys: List<String>, ids: List<String>) {
        val visualIndex = keys.indexOfFirst { it.startsWith("pdf") || it.startsWith("image") }
        if (visualIndex < 0) {
            if (tool == "file_duplicates") {
                compose.onNodeWithTag("workspace.scroll").performScrollToKey("duplicate-result-panel")
                val group = compose.activity.getString(R.string.studio_duplicates_group, 1, 2)
                compose.waitUntil(readyTimeoutMillis) { compose.onAllNodesWithText(group).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(group).assertIsDisplayed()
            } else ids.firstOrNull()?.let { id ->
                compose.onNodeWithTag("workspace.scroll").performScrollToKey(id)
                val name = viewModel().imported.value.single { it.id == id }.displayName
                compose.onNodeWithText(name).assertIsDisplayed()
            }
            return
        }
        if (tool == "pdf_01") {
            waitForReadyTags("pdf.page.image.0")
            compose.onNodeWithTag("pdf.page.image.0", useUnmergedTree = true).assertIsDisplayed()
            return
        }
        // Exercise the same renderer against the imported bytes; a real failure stays a failure.
        val vm = viewModel()
        runBlocking(Dispatchers.IO) { vm.preview(ids[visualIndex], 1).bitmap.recycle() }
        if (tool == "pdf_forms") {
            compose.onNodeWithTag("workspace.scroll").performScrollToKey("pdf-form-fields")
            // The page bitmap can arrive before discovery of the actual fixture widgets.
            waitForReadyTags("form.page", "form.widget.name.0", "form.widget.agree.0")
            compose.onNodeWithTag("form.page", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("form.widget.name.0", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("form.widget.agree.0", useUnmergedTree = true).assertIsDisplayed()
            return
        }
        if (tool == "pdf_ocr") {
            compose.onNodeWithTag("workspace.scroll").performScrollToKey("ocr-result-panel")
            waitForReadyTags("ocr.page")
            compose.onNodeWithTag("ocr.page", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            return
        }
        val key = when (tool) {
            "img_01", "img_04", "img_target" -> "encoded-image-preview"
            "img_02", "img_adjust", "img_join", "img_stitch" -> "transformed-image-preview"
            "img_03", "pdf_crop", "pdf_annotate", "pdf_image", "pdf_redact" -> "visual-geometry-editor"
            "img_watermark" -> "image-watermark-editor"
            "pdf_02" -> "paper-preview"
            "pdf_04", "pdf_05", "pdf_06" -> "pdf-visual-editor-${ids[visualIndex]}"
            "pdf_signature" -> "signature-placement-editor"
            "pdf_stamp" -> "visual-watermark-editor"
            "pdf_metadata" -> "metadata-workspace"
            else -> "preview-${ids[visualIndex]}"
        }
        compose.onNodeWithTag("workspace.scroll").performScrollToKey(key)
        waitForReadyTags("preview.bitmap")
        compose.onAllNodesWithTag("preview.bitmap", useUnmergedTree = true).onFirst().performScrollTo().assertIsDisplayed()
        if (tool in setOf("img_01", "img_04", "img_target")) {
            compose.onNodeWithTag("encoded.preview.error").assertDoesNotExist()
            compose.onNodeWithTag("image.comparison", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    private fun waitForReadyTags(vararg tags: String) {
        compose.waitUntil(readyTimeoutMillis) {
            tags.all { tag ->
                compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun captureSignaturePad(pdfId: String) {
        fun arrange() {
            open("pdf_signature")
            compose.runOnUiThread { viewModel().chooseToolWithSources("pdf_signature", listOf(pdfId)) }
            compose.onNodeWithTag("workspace.scroll").performScrollToKey("signature-placement-editor")
            waitForReadyTags("signature.pad", "signature.object.canvas")
            compose.onNodeWithTag("signature.pad", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        }
        capture("signature_pad", "empty", ::arrange)
        capture("signature_pad", "drawn") {
            arrange()
            compose.onNodeWithTag("signature.pad", useUnmergedTree = true).performTouchInput {
                down(Offset(width * .15f, height * .65f))
                moveTo(Offset(width * .3f, height * .3f))
                moveTo(Offset(width * .5f, height * .7f))
                moveTo(Offset(width * .8f, height * .4f))
                up()
            }
        }
    }
}
