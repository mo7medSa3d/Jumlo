package com.jumlo.app.printing

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PrintViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun setup() {Dispatchers.setMain(dispatcher)}
    @After fun teardown() {Dispatchers.resetMain()}
    private val source=PrintSource("/private/inbound/a.bin","Report.pdf","application/pdf")
    @Test fun recompositionDoesNotResetSettingsAndNewSourceResetsOnlyRange() = runTest(dispatcher) {
        val store=FakeStore();val vm=PrintViewModel(Application(),store,FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.edit(vm.state.value.options.copy(range="2",scalePercent=140));advanceUntilIdle()
        vm.attach(source);advanceUntilIdle()
        assertEquals("2",vm.state.value.options.range)
        assertEquals(140,vm.state.value.options.scalePercent)
        vm.attach(source.copy(path="/private/inbound/b.bin"));advanceUntilIdle()
        assertEquals("",vm.state.value.options.range)
        assertEquals(140,vm.state.value.options.scalePercent)
        assertEquals(3,vm.state.value.pages.size)
    }
    @Test fun invalidRangePreventsPrintingAndCanRecover()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.edit(vm.state.value.options.copy(range="9"));advanceUntilIdle()
        assertTrue(vm.state.value.rangeError)
        assertNull(vm.state.value.layout)
        vm.edit(vm.state.value.options.copy(range="1,3"));advanceUntilIdle()
        assertFalse(vm.state.value.rangeError)
        assertEquals(listOf(PrintPage(1),PrintPage(3)),vm.state.value.pages)
    }
    @Test fun missingSourceFailureIsVisibleAndRetryLoadsAgain()=runTest(dispatcher) {
        val engine=FakeEngine().apply {fail=true}
        val vm=PrintViewModel(Application(),FakeStore(),engine,SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        assertEquals("PRINT_SOURCE_MISSING",vm.state.value.error)
        assertFalse(vm.state.value.inspecting)
        engine.fail=false;vm.retry();advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals(3,vm.state.value.pages.size)
    }
    @Test fun processRecreationRestoresUnsavedRangeAndPresetRetainsPhysicalSettings()=runTest(dispatcher) {
        val options=PrintOptions(range="2",paper=PrintPaper.LEGAL,grayscale=true)
        val json=kotlinx.serialization.json.Json {encodeDefaults=true}
        val handle=SavedStateHandle(mapOf("print_options" to json.encodeToString(PrintOptions.serializer(),options),"print_source" to json.encodeToString(PrintSource.serializer(),source),"print_input" to PrintSourceIdentity.key(source)))
        val store=FakeStore()
        val vm=PrintViewModel(Application(),store,FakeEngine(),handle,dispatcher)
        vm.attach(source);advanceUntilIdle()
        assertEquals(listOf(PrintPage(2)),vm.state.value.pages)
        vm.savePreset("Office");advanceUntilIdle()
        assertEquals("",store.savedPreset?.options?.range)
        assertEquals(PrintPaper.LEGAL,store.savedPreset?.options?.paper)
    }
    @Test fun selectingImagePresetBeforeInspectionUsesTheNewDpi()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        val image=source.copy(mimeType="image/jpeg")
        vm.attach(image)
        vm.edit(vm.state.value.options.copy(imageDpi=72,scale=PrintScale.ACTUAL))
        advanceUntilIdle()
        assertEquals(72,vm.state.value.options.imageDpi)
        assertEquals(600.0,vm.state.value.info!!.pages.first().crop.width,.001)
    }
    @Test fun largeEditedSelectionUsesDurableSessionAndCompactSavedStateIdentity()=runTest(dispatcher) {
        val store=FakeStore();val handle=SavedStateHandle()
        val vm=PrintViewModel(Application(),store,FakeEngine(),handle,dispatcher)
        val large=source.copy(pages=List(3000) {PrintPage(1)})
        vm.attach(large);advanceUntilIdle()
        assertNull(handle.get<String>("print_source"))
        assertEquals(true,handle.get<Boolean>("print_source_large"))
        assertEquals(64,handle.get<String>("print_input")!!.length)
        assertEquals(large,store.data.value.sessionSource)
    }
    @Test fun openingHomeCancelsPendingInspectionWithoutPublishingStaleResult()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source)
        vm.open(null)
        advanceUntilIdle()
        assertNull(vm.state.value.source)
        assertNull(vm.state.value.info)
        assertTrue(vm.state.value.pages.isEmpty())
    }
    @Test fun invalidNumericDraftSurvivesRecreationAndBlocksPresetSave()=runTest(dispatcher) {
        val store=FakeStore();val handle=SavedStateHandle()
        val vm=PrintViewModel(Application(),store,FakeEngine(),handle,dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.setNumericDraft("marginLeft","١٢٫٥")
        assertFalse(vm.state.value.inputErrors.contains("marginLeft"))
        vm.setNumericDraft("marginLeft","bad-value")
        assertTrue(vm.state.value.inputErrors.contains("marginLeft"))
        vm.savePreset("Office");advanceUntilIdle()
        assertNull(store.savedPreset)
        val next=PrintViewModel(Application(),store,FakeEngine(),handle,dispatcher)
        assertEquals("bad-value",next.state.value.numericDrafts["marginLeft"])
        assertTrue(next.state.value.inputErrors.contains("marginLeft"))
        next.resetInvalidNumericDrafts()
        assertTrue(next.state.value.inputErrors.isEmpty())
    }
    @Test fun continuousPrintSliderCreatesExactlyOneUndoStep()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        val before=vm.state.value.options.scalePercent
        vm.beginContinuousEdit("scale")
        vm.edit(vm.state.value.options.copy(scalePercent=137))
        vm.edit(vm.state.value.options.copy(scalePercent=153))
        vm.endContinuousEdit("scale",true)
        assertTrue(vm.state.value.canUndo)
        vm.undo()
        assertEquals(before,vm.state.value.options.scalePercent)
        assertFalse(vm.state.value.canUndo)
        vm.redo()
        assertEquals(153,vm.state.value.options.scalePercent)
    }
    @Test fun canceledPrintSliderRestoresOriginalValue()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        val before=vm.state.value.options.scalePercent
        vm.beginContinuousEdit("scale")
        vm.edit(vm.state.value.options.copy(scalePercent=180))
        vm.endContinuousEdit("scale",false)
        assertEquals(before,vm.state.value.options.scalePercent)
        assertFalse(vm.state.value.canUndo)
    }
    @Test fun retryRestartsSavedDataObserverAfterReadError()=runTest(dispatcher) {
        val backing=FakeStore()
        var fails=true
        val store=object:PrintStore by backing {
            override val data=flow {
                if(fails) throw java.io.IOException("temporary settings outage")
                emit(PrintSavedData())
            }
        }
        val vm=PrintViewModel(Application(),store,FakeEngine(),SavedStateHandle(),dispatcher)
        advanceUntilIdle()
        assertEquals("PRINT_STORAGE",vm.state.value.error)
        fails=false
        vm.retry()
        advanceUntilIdle()
        assertEquals(null,vm.state.value.error)
        assertTrue(vm.state.value.loaded)
    }
    @Test fun visualPageSelectionDrivesTheRangeString()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.setPageSelection(listOf(0,2));advanceUntilIdle()
        assertEquals("1,3",vm.state.value.options.range)
        assertEquals(listOf(PrintPage(1),PrintPage(3)),vm.state.value.pages)
        assertEquals(3,vm.state.value.orderedPages.size)
    }
    @Test fun selectingEveryPageAbbreviatesTheRangeButKeepsTheDocument()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.setPageSelection(listOf(0,1,2));advanceUntilIdle()
        assertEquals("",vm.state.value.options.range)
        assertEquals(3,vm.state.value.pages.size)
    }
    @Test fun reorderingKeepsTheSelectedPagesSelected()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.setPageSelection(listOf(0,2));advanceUntilIdle()
        vm.applyPageOrder(listOf(2,0,1));advanceUntilIdle()
        // The third page now leads, and the two chosen pages are still the ones that print.
        assertEquals(3,vm.state.value.orderedPages.first().sourcePage)
        assertEquals("1-2",vm.state.value.options.range)
        assertEquals(listOf(3,1),vm.state.value.pages.map { it.sourcePage })
    }
    @Test fun draggedPageOrderRecoversFromStaleOrDuplicateSavedValues()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        // The existing renderer ignores invalid permutations, showing pages 1,2,3.
        vm.edit(vm.state.value.options.copy(pageOrder=listOf(1,1,3)));advanceUntilIdle()
        assertEquals(listOf(1,2,3),vm.state.value.orderedPages.map { it.sourcePage })
        vm.applyPageOrder(listOf(2,0,1));advanceUntilIdle()
        assertEquals(listOf(3,1,2),vm.state.value.orderedPages.map { it.sourcePage })
        assertEquals(listOf(3,1,2),vm.state.value.options.pageOrder)
    }

    @Test fun moveButtonRecoversFromStaleSavedPageOrder()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.edit(vm.state.value.options.copy(pageOrder=listOf(2,2,3)));advanceUntilIdle()
        assertEquals(listOf(1,2,3),vm.state.value.orderedPages.map { it.sourcePage })
        vm.movePage(0,2);advanceUntilIdle()
        assertEquals(listOf(2,3,1),vm.state.value.orderedPages.map { it.sourcePage })
    }

    @Test fun rotationTargetsTheVisiblePageWhenSavedOrderIsInvalid()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.edit(vm.state.value.options.copy(pageOrder=listOf(3,3,1)));advanceUntilIdle()
        assertEquals(listOf(1,2,3),vm.state.value.orderedPages.map { it.sourcePage })
        vm.rotatePage(1);advanceUntilIdle()
        assertEquals(mapOf(2 to 90),vm.state.value.options.pageRotations)
        assertEquals(90,vm.state.value.orderedPages[1].rotationDegrees)
    }

    @Test fun rotatingAPageStoresTheRotationAgainstTheSourceOrder()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.rotatePage(1);advanceUntilIdle()
        assertEquals(mapOf(2 to 90),vm.state.value.options.pageRotations)
        assertEquals(90,vm.state.value.orderedPages[1].rotationDegrees)
        vm.rotatePage(1);advanceUntilIdle()
        assertEquals(180,vm.state.value.orderedPages[1].rotationDegrees)
    }
    @Test fun pageManagementIsUndoable()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.applyPageOrder(listOf(2,0,1));advanceUntilIdle()
        assertEquals(3,vm.state.value.orderedPages.first().sourcePage)
        vm.undo();advanceUntilIdle()
        assertEquals(1,vm.state.value.orderedPages.first().sourcePage)
        vm.redo();advanceUntilIdle()
        assertEquals(3,vm.state.value.orderedPages.first().sourcePage)
    }
    @Test fun openingAnotherDocumentDropsThePageOrderAndRotations()=runTest(dispatcher) {
        val vm=PrintViewModel(Application(),FakeStore(),FakeEngine(),SavedStateHandle(),dispatcher)
        vm.attach(source);advanceUntilIdle()
        vm.applyPageOrder(listOf(2,0,1));advanceUntilIdle()
        vm.rotatePage(0);advanceUntilIdle()
        vm.attach(source.copy(path="/private/inbound/b.bin"));advanceUntilIdle()
        assertTrue(vm.state.value.options.pageOrder.isEmpty())
        assertTrue(vm.state.value.options.pageRotations.isEmpty())
    }
    private class FakeStore:PrintStore {
        override val data=MutableStateFlow(PrintSavedData())
        var savedPreset:PrintPreset?=null
        override fun resolve(source:PrintSource)=File(source.path)
        override suspend fun snapshot(source:PrintSource)=PrintSnapshot(source,"test",false)
        override suspend fun releaseSnapshot(snapshot:PrintSnapshot) {}
        override suspend fun session(source:PrintSource?) {data.value=data.value.copy(sessionSource=source)}
        override suspend fun settings(options:PrintOptions) {data.value=data.value.copy(lastOptions=options.reusable())}
        override suspend fun recent(source:PrintSource) {}
        override suspend fun favorite(source:PrintSource) {}
        override suspend fun preset(name:String,options:PrintOptions) {savedPreset=PrintPreset.create(name,options)}
        override suspend fun deletePreset(name:String) {}
        override suspend fun record(entry:PrintHistoryEntry) {}
        override suspend fun reconcile(states:Map<String,PrintJobState>) {}
    }
    private class FakeEngine:PrintEngine {
        var fail=false
        override fun inspect(source:PrintSource,options:PrintOptions,check:()->Unit):PrintDocumentInfo {
            if(fail) throw IllegalArgumentException("PRINT_SOURCE_MISSING")
            check();val width=if(source.mimeType.startsWith("image/")) 600.0*72/options.imageDpi else 600.0
            return PrintDocumentInfo(List(3) {PrintPageInfo(PrintCrop(0.0,0.0,width,800.0),0)})
        }
        override fun initialLayout(options:PrintOptions,info:PrintDocumentInfo,pages:List<PrintPage>)=PrintLayout(options.paperSize(info.pages.first().size()),options.margins,options.grayscale)
        override fun compose(source:PrintSource,options:PrintOptions,pages:List<PrintPage>,layout:PrintLayout,destination:File,check:()->Unit,progress:(Int,Int)->Unit) {throw UnsupportedOperationException()}
        override fun preview(file:File,index:Int,maxEdge:Int):Bitmap {throw UnsupportedOperationException()}
    }
}
