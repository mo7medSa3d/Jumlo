package com.jumlo.app.ui

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.jumlo.app.R
import com.jumlo.app.model.AppLanguage
import com.jumlo.app.model.ThemeMode
import com.jumlo.app.model.WorkspaceDraft
import com.jumlo.app.ui.scan.SignaturePad
import com.jumlo.app.ui.theme.JumloTheme
import com.jumlo.app.ui.workspace.ImageComparisonPreview
import com.jumlo.app.ui.workspace.PdfWatermarkPreview
import com.jumlo.app.ui.workspace.PreviewObject
import com.jumlo.app.ui.workspace.TransformablePagePreview
import com.jumlo.app.ui.workspace.CollagePlacementPreview
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Gesture assertions use the production composables and Android pointer dispatch. */
class StudioGestureInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun watermarkDragAndTwoFingerScaleRotateCommitTheVisibleObject() {
        val bitmap = Bitmap.createBitmap(612, 792, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        var x by mutableFloatStateOf(.5f)
        var y by mutableFloatStateOf(.5f)
        var font by mutableIntStateOf(42)
        var rotation by mutableIntStateOf(0)
        var commits = 0
        compose.setContent {
            JumloTheme(ThemeMode.LIGHT, AppLanguage.ENGLISH) {
                PdfWatermarkPreview(PreviewFrame(bitmap, 1, 1, 612, 792),
                    WorkspaceDraft.forTool("pdf_stamp", emptyList()).copy(text1="CONFIDENTIAL",num1=font.toString(),text4=rotation.toString()),
                    1, true, x, y, { nextX, nextY -> x=nextX;y=nextY }, { commits++ },
                    { nextFont, nextRotation -> font=nextFont;rotation=nextRotation })
            }
        }
        compose.onNodeWithTag("watermark.canvas").performTouchInput {
            swipe(center, center + Offset(40f, 28f), 240)
        }
        compose.runOnIdle { assertTrue(x>.5f);assertTrue(y>.5f);assertEquals(1,commits) }
        compose.onNodeWithTag("watermark.canvas").performTouchInput {
            val objectCenter=Offset(width*x,height*y)
            down(0,objectCenter-Offset(30f,0f))
            down(1,objectCenter+Offset(30f,0f))
            advanceEventTime(24)
            moveTo(0,objectCenter-Offset(52f,20f))
            moveTo(1,objectCenter+Offset(52f,20f))
            advanceEventTime(24)
            up(1);up(0)
        }
        compose.runOnIdle { assertTrue(font>42);assertTrue(rotation!=0);assertEquals(2,commits) }
    }

    @Test fun signatureObjectDragPinchAndRotateWorkInArabicRtl() {
        val page=Bitmap.createBitmap(612,792,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.WHITE)}
        val signature=Bitmap.createBitmap(300,120,Bitmap.Config.ARGB_8888).apply {
            android.graphics.Canvas(this).drawLine(15f,85f,280f,25f,android.graphics.Paint().apply {color=android.graphics.Color.BLACK;strokeWidth=8f})
        }
        var obj by mutableStateOf(PreviewObject("signature",signature,.2f,.35f,.7f,.55f))
        var selected by mutableStateOf<String?>("signature")
        var commits=0
        compose.setContent {JumloTheme(ThemeMode.DARK,AppLanguage.ARABIC) {
            TransformablePagePreview(PreviewFrame(page,1,1,612,792),listOf(obj),selected,{selected=it},{obj=it},{commits++},tag="signature.gesture")
        }}
        compose.onNodeWithTag("signature.gesture").performTouchInput {
            val origin=Offset(width*.45f,height*.45f)
            swipe(origin,origin+Offset(24f,18f),240)
        }
        compose.runOnIdle {assertTrue(obj.center.x>.45f);assertTrue(obj.center.y>.45f)}
        val before=obj.width
        compose.onNodeWithTag("signature.gesture").performTouchInput {
            val center=Offset(width*obj.center.x,height*obj.center.y)
            down(0,center-Offset(35f,0f));down(1,center+Offset(35f,0f))
            advanceEventTime(24)
            moveTo(0,center-Offset(50f,20f));moveTo(1,center+Offset(50f,20f))
            advanceEventTime(24);up(1);up(0)
        }
        compose.runOnIdle {assertTrue(obj.width>before);assertTrue(obj.rotation!=0f);assertEquals(2,commits)}
    }

    @Test fun tiledWatermarkKeepsItsCenterWhilePinchAndRotationStillWork() {
        val page=Bitmap.createBitmap(612,792,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.WHITE)}
        val logo=Bitmap.createBitmap(300,120,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        var obj by mutableStateOf(PreviewObject("tile",logo,.3f,.4f,.7f,.6f))
        compose.setContent {JumloTheme(ThemeMode.LIGHT,AppLanguage.ENGLISH) {
            TransformablePagePreview(PreviewFrame(page,1,1,612,792),listOf(obj),"tile",{},{obj=it},
                tag="tile.gesture",allowTranslation=false)
        }}
        compose.onNodeWithTag("tile.gesture").performTouchInput {swipe(center,center+Offset(40f,20f),240)}
        compose.runOnIdle {assertEquals(.5f,obj.center.x,.001f);assertEquals(.5f,obj.center.y,.001f)}
        val before=obj.width
        compose.onNodeWithTag("tile.gesture").performTouchInput {
            down(0,center-Offset(30f,0f));down(1,center+Offset(30f,0f))
            advanceEventTime(24)
            moveTo(0,center-Offset(45f,20f));moveTo(1,center+Offset(65f,20f))
            advanceEventTime(24);up(1);up(0)
        }
        compose.runOnIdle {
            assertEquals(.5f,obj.center.x,.001f);assertEquals(.5f,obj.center.y,.001f)
            assertTrue(obj.width>before);assertTrue(obj.rotation!=0f)
        }
    }

    @Test fun collageDragMovesTheActualSlotIndex() {
        val image=Bitmap.createBitmap(600,300,Bitmap.Config.ARGB_8888).apply {
            val canvas=android.graphics.Canvas(this)
            canvas.drawColor(android.graphics.Color.BLUE)
            canvas.drawRect(300f,0f,600f,300f,android.graphics.Paint().apply {color=android.graphics.Color.GREEN})
        }
        var moved:Pair<Int,Int>?=null
        compose.setContent {JumloTheme(ThemeMode.LIGHT,AppLanguage.ENGLISH) {
            CollagePlacementPreview(image,listOf(Rect(0f,0f,.5f,1f),Rect(.5f,0f,1f,1f))) {from,to->moved=from to to}
        }}
        compose.onNodeWithTag("collage.canvas").performTouchInput {
            down(Offset(width*.25f,height*.5f));advanceEventTime(650)
            moveTo(Offset(width*.4f,height*.5f));advanceEventTime(24)
            moveTo(Offset(width*.75f,height*.5f));up()
        }
        compose.runOnIdle {assertEquals(0 to 1,moved)}
    }

    @Test fun signatureUndoRedoSurviveSavedStateRestoration() {
        val restore=StateRestorationTester(compose)
        restore.setContent {
            JumloTheme(ThemeMode.DARK, AppLanguage.ARABIC) {
                Column(Modifier.verticalScroll(rememberScrollState())) { SignaturePad {} }
            }
        }
        compose.onNodeWithTag("signature.undo").assertIsNotEnabled()
        compose.onNodeWithTag("signature.pad").performTouchInput { swipe(Offset(width*.15f,height*.65f),Offset(width*.8f,height*.35f),240) }
        compose.onNodeWithTag("signature.undo").assertIsEnabled().performClick()
        compose.onNodeWithTag("signature.redo").assertIsEnabled()
        compose.onNodeWithTag("signature.save").assertIsNotEnabled()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("signature.redo").assertIsEnabled().performClick()
        compose.onNodeWithTag("signature.save").assertIsEnabled()
        compose.onNodeWithTag("signature.undo").assertIsEnabled()
    }

    @Test fun comparisonDividerDragUpdatesItsAccessibleSlider() {
        val original=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.BLUE)}
        val edited=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.GREEN)}
        var mode by mutableStateOf(ThemeMode.LIGHT)
        var language by mutableStateOf(AppLanguage.ENGLISH)
        var visible by mutableStateOf(true)
        val targetContext=InstrumentationRegistry.getInstrumentation().targetContext
        fun progress()=compose.onNodeWithTag("image.comparison.slider").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        fun assertPixel(fraction:Float,expected:Int,description:String) {
            val image=compose.onNodeWithTag("image.comparison").captureToImage()
            val actual=image.toPixelMap()[(image.width*fraction).toInt(),(image.height*.75f).toInt()].toArgb()
            assertEquals("$language/$mode: $description",expected,actual)
        }
        try {
            compose.setContent {
                if(visible) key(mode,language) {
                    val context=LocalContext.current
                    val configuration=remember(language,context) {
                        Configuration(context.resources.configuration).apply {setLocale(Locale.forLanguageTag(language.code))}
                    }
                    val localized=remember(configuration,context) {context.createConfigurationContext(configuration)}
                    CompositionLocalProvider(LocalContext provides localized,LocalConfiguration provides configuration) {
                        JumloTheme(mode,language) {
                            Column(Modifier.width(320.dp)) {ImageComparisonPreview(original,edited)}
                        }
                    }
                }
            }
            for(nextLanguage in listOf(AppLanguage.ENGLISH,AppLanguage.ARABIC)) {
                for(nextMode in listOf(ThemeMode.LIGHT,ThemeMode.DARK)) {
                    compose.runOnIdle {language=nextLanguage;mode=nextMode}
                    compose.waitForIdle()
                    val rtl=nextLanguage==AppLanguage.ARABIC
                    val localized=targetContext.createConfigurationContext(Configuration(targetContext.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(nextLanguage.code))
                    })
                    val before=compose.onNodeWithText(localized.getString(R.string.studio_before),useUnmergedTree=true)
                        .fetchSemanticsNode().boundsInRoot
                    val after=compose.onNodeWithText(localized.getString(R.string.studio_after),useUnmergedTree=true)
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue("$language/$mode: Before follows the original side",if(rtl) before.center.x>after.center.x else before.center.x<after.center.x)
                    assertEquals(.5f,progress(),.001f)
                    assertPixel(if(rtl) .75f else .25f,android.graphics.Color.BLUE,"initial original layer below Before badge")
                    assertPixel(if(rtl) .25f else .75f,android.graphics.Color.GREEN,"initial edited layer below After badge")

                    compose.onNodeWithTag("image.comparison").performTouchInput {
                        swipe(center,Offset(width*(if(rtl) .1f else .9f),height*.5f),240)
                    }
                    assertTrue("$language/$mode: divider drag expands the original fraction",progress()>.8f)
                    assertPixel(.5f,android.graphics.Color.BLUE,"divider drag expands original pixels")

                    compose.onNodeWithTag("image.comparison.slider").performSemanticsAction(SemanticsActions.SetProgress) {it(.25f)}
                    assertEquals(.25f,progress(),.001f)
                    assertPixel(if(rtl) .85f else .15f,android.graphics.Color.BLUE,"accessible progress preserves original side")
                    assertPixel(.5f,android.graphics.Color.GREEN,"accessible progress shrinks original coverage")

                    compose.onNodeWithTag("image.comparison.slider").performTouchInput {
                        click(Offset(width*(if(rtl) .2f else .8f),center.y))
                    }
                    assertTrue("$language/$mode: physical slider track agrees with logical progress",progress()>.65f)
                    assertPixel(.5f,android.graphics.Color.BLUE,"slider interaction expands the same original pixels")
                }
            }
        } finally {
            compose.runOnIdle {visible=false}
            compose.waitForIdle()
            original.recycle();edited.recycle()
        }
    }
}
