package com.jumlo.benchmark

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.util.Locale
import java.util.regex.Pattern

internal const val TARGET_PACKAGE="com.jumlo.app"

/** Out-of-process selectors use the app's real localized accessibility labels, not Compose tags. */
internal class BenchmarkUi(private val device:UiDevice) {
    private val targetContext=InstrumentationRegistry.getInstrumentation().context
        .createPackageContext(TARGET_PACKAGE,Context.CONTEXT_IGNORE_SECURITY)
    private val localized=listOf("en","ar").map {language ->
        targetContext.createConfigurationContext(Configuration(targetContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources
    }
    private val fixturePattern=Pattern.compile("(?s).*jumlo-benchmark-[0-9]{3}.*")
    private val fixtureId=Regex("jumlo-benchmark-[0-9]{3}")

    private fun labels(name:String):Pattern {
        val id=targetContext.resources.getIdentifier(name,"string",TARGET_PACKAGE)
        check(id!=0) {"Target string resource missing: $name"}
        return exact(localized.map {it.getString(id)})
    }
    private fun exact(values:List<String>)=Pattern.compile(values.distinct().joinToString("|","^(?:",")$") {Pattern.quote(it)})
    private fun text(name:String)=By.pkg(TARGET_PACKAGE).text(labels(name))
    private fun description(name:String)=By.pkg(TARGET_PACKAGE).desc(labels(name))

    private fun await(selector:BySelector,message:String):UiObject2 {
        val deadline=SystemClock.uptimeMillis()+10_000
        do {
            device.findObject(selector)?.let {return it}
            SystemClock.sleep(50)
        } while(SystemClock.uptimeMillis()<deadline)
        error(message)
    }

    fun dismissOnboarding() {
        device.waitForIdle()
        device.findObject(text("quick_start_skip"))?.click()
        device.waitForIdle()
    }

    fun home() {
        dismissOnboarding()
        await(description("nav_home"),"Home navigation is unavailable").click()
        await(text("modern_home_title"),"Home hero did not appear")
    }

    fun tools(requireContent:Boolean=true) {
        dismissOnboarding()
        await(description("nav_tools"),"Tools navigation is unavailable").click()
        scrollToTop()
        await(description("neon_tools_search"),"Tools search did not appear").text=""
        device.waitForIdle()
        if(requireContent) {
            val names=linkedSetOf<String>()
            scrollToTop()
            repeat(30) {
                names+=visibleTexts()
                if(names.size>=24) {
                    scrollToTop()
                    return
                }
                val before=snapshot()
                verticalList().scroll(Direction.DOWN,.7f)
                device.waitForIdle()
                check(snapshot()!=before) {"Tools grid has fewer than 24 visible distinct labels; refusing a no-op/filtered benchmark"}
            }
            error("Tools grid content precondition could not be established")
        }
    }

    fun openCropWorkspaceAndReturn() {
        tools(requireContent=false)
        await(description("neon_tools_search"),"Tools search is unavailable").text="crop"
        device.waitForIdle()
        await(By.pkg(TARGET_PACKAGE).text(exact(listOf("Crop pages","قص الصفحات"))),
            "Actual Crop pages tool tile did not appear").click()
        await(text("preset_panel_title"),"Crop workspace did not open")
        await(description("scan_back"),"Workspace Back action is unavailable").click()
        await(description("neon_tools_search"),"Back did not return to Tools").text=""
        device.waitForIdle()
    }

    /** Requires real app-published outputs generated from benchmark/generate-fixtures.py. */
    fun filesGrid() {
        dismissOnboarding()
        await(description("nav_files"),"Files navigation is unavailable").click()
        scrollToTop()
        await(text("ui_outputs"),"Files Results section is unavailable").click()
        await(description("ui_search_files"),"Files search is unavailable").text="jumlo-benchmark-"
        device.waitForIdle()
        device.findObject(text("ui_all"))?.click()
        positionAtFirstFixture()
        if(!twoColumnFixtureRow()) {
            scrollToTop()
            await(description("redesign_file_view"),"Files view toggle is unavailable").click()
            device.waitForIdle()
            positionAtFirstFixture()
        }
        check(twoColumnFixtureRow()) {
            "Files benchmark requires a portrait two-column grid at 100% font scale with real fixture thumbnails; refusing a list/no-op measurement"
        }
        val names=linkedSetOf<String>()
        repeat(40) {
            names+=visibleFixtureIds()
            if(names.size>=24) {
                scrollToTop()
                positionAtFirstFixture()
                return
            }
            val before=snapshot()
            verticalList().scroll(Direction.DOWN,.7f)
            device.waitForIdle()
            check(snapshot()!=before) {
                "Need at least 24 real processed jumlo-benchmark-NNN outputs in Files Results; found ${names.size}. See benchmark/README.md"
            }
        }
        error("Files fixture enumeration did not complete; refusing incomplete performance evidence")
    }

    fun scrollGrid() {
        // Preparation verifies enough real rows for these portrait-phone gestures.
        for(direction in listOf(Direction.DOWN,Direction.DOWN,Direction.DOWN,Direction.UP,Direction.UP,Direction.UP)) {
            val before=snapshot()
            verticalList().scroll(direction,.65f)
            device.waitForIdle()
            check(snapshot()!=before) {"Grid did not move during $direction; refusing a no-op timing result"}
        }
    }

    private fun verticalList():UiObject2 {
        val candidates=device.findObjects(By.pkg(TARGET_PACKAGE).scrollable(true))
            .filter {it.visibleBounds.width()>=device.displayWidth*.6 && it.visibleBounds.height()>=device.displayHeight*.3}
        return checkNotNull(candidates.maxByOrNull {it.visibleBounds.height()}) {
            "No full-width vertical Tools/Files grid found; horizontal filter chips are not a benchmark target"
        }.apply {setGestureMargin(device.displayWidth/8)}
    }

    private fun visibleTexts()=verticalList().findObjects(By.text(Pattern.compile("(?s).+")))
        .mapNotNull {it.text?.trim()?.takeIf(String::isNotBlank)}.toSet()

    private fun snapshot():List<String> = verticalList().findObjects(By.text(Pattern.compile("(?s).+")))
        .map {"${it.text}|${it.visibleBounds.left}|${it.visibleBounds.top}"}.sorted()

    private fun visibleFixtureIds():Set<String> = verticalList().findObjects(By.desc(fixturePattern))
        .mapNotNull {fixtureId.find(it.contentDescription.orEmpty())?.value}.toSet()

    private fun twoColumnFixtureRow():Boolean {
        val items=verticalList().findObjects(By.desc(fixturePattern)).distinctBy {it.contentDescription}
        return items.any {left ->items.any {right ->
            left.contentDescription!=right.contentDescription &&
                kotlin.math.abs(left.visibleBounds.centerY()-right.visibleBounds.centerY())<device.displayHeight/25 &&
                left.visibleBounds.right<=right.visibleBounds.left
        }}
    }

    private fun positionAtFirstFixture() {
        repeat(8) {
            if(visibleFixtureIds().size>=2) return
            val before=snapshot()
            verticalList().scroll(Direction.DOWN,.35f)
            device.waitForIdle()
            check(snapshot()!=before) {"No real fixture cards in Results; import and process the 24 benchmark images first"}
        }
        error("Real fixture cards did not appear in the Files grid")
    }

    private fun scrollToTop() {
        repeat(40) {
            val before=snapshot()
            verticalList().scroll(Direction.UP,.8f)
            device.waitForIdle()
            if(snapshot()==before) return
        }
        error("Grid did not reach its top within the bounded precondition scan")
    }
}
