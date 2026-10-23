package com.jumlo.app.media

import com.jumlo.app.core.media.*
import com.jumlo.app.data.media.MediaRuntime
import com.jumlo.app.data.media.RoutingMediaRuntime
import com.jumlo.app.data.jobs.OperationFailure
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaRoutingTest {
    private val asset = MediaAsset("id","fixture","Fixture",1.0,listOf(MediaFormat("18","mp4","h264","aac")))
    private val choice = MediaCatalog.choices(asset,MediaMode.VIDEO).single()
    private fun engine(error: String? = null) = object : MediaRuntime {
        override fun inspect(url:String,allowHttp:Boolean,check:()->Unit):MediaAsset {
            if(error!=null) throw OperationFailure(error)
            return asset
        }
        override fun download(url:String,choice:MediaChoice,audio:AudioOutput,bitrate:Int,
            allowHttp:Boolean,directory:File,check:()->Unit,progress:(MediaProgress)->Unit) {
            File(directory,"selected-engine.txt").writeText("chosen")
        }
    }
    @Test fun extractorFailuresAreNotReplacedWithMisleadingDirectLinkErrors() {
        for(code in listOf("MEDIA_NETWORK_ERROR","MEDIA_EXTRACTION_FAILED","MEDIA_SESSION_REQUIRED","MEDIA_RATE_LIMITED")) {
            val runtime=RoutingMediaRuntime(listOf(engine(code),engine("MEDIA_SITE_UNSUPPORTED")))
            val failure=runCatching { runtime.inspect("https://example.org/watch",false) }.exceptionOrNull()
            assertEquals(code,(failure as OperationFailure).code)
        }
    }
    @Test fun downloadRequiresASuccessfulAnalysisInsteadOfGuessingAnEngine() {
        val runtime=RoutingMediaRuntime(listOf(engine()))
        val dir=Files.createTempDirectory("media-route-").toFile()
        try {
            val failure=runCatching { runtime.download("https://example.org/watch",choice,AudioOutput.ORIGINAL,192,false,dir,{},{}) }.exceptionOrNull()
            assertTrue(failure is OperationFailure)
            assertEquals("MEDIA_REANALYSIS_REQUIRED",(failure as OperationFailure).code)
            assertFalse(File(dir,"selected-engine.txt").exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun anUnsupportedEngineCanHandTheAnalyzedChoiceToTheNextInstalledEngine() {
        val runtime=RoutingMediaRuntime(listOf(engine("MEDIA_SITE_UNSUPPORTED"),engine()))
        val dir=Files.createTempDirectory("media-route-").toFile()
        try {
            assertEquals(asset,runtime.inspect("https://example.org/watch",false))
            runtime.download("https://example.org/watch",choice,AudioOutput.ORIGINAL,192,false,dir,{},{})
            assertEquals("chosen",File(dir,"selected-engine.txt").readText())
        } finally { dir.deleteRecursively() }
    }
    @Test fun missingNativeLibrariesBecomeAnActionableFailureInsteadOfEscapingAnalysis() {
        val unavailable=object:MediaRuntime by engine() {
            override fun inspect(url:String,allowHttp:Boolean,check:()->Unit):MediaAsset {
                throw UnsatisfiedLinkError("fixture missing native ABI")
            }
        }
        val failure=runCatching { RoutingMediaRuntime(listOf(unavailable)).inspect("https://example.org/watch",false) }.exceptionOrNull()
        assertTrue("Native load failure escaped the runtime boundary: $failure",failure is OperationFailure)
        assertEquals("MEDIA_RUNTIME_INITIALIZATION_FAILED",failure?.message)
    }
    @Test fun analysisCancellationNeverStartsAnAlternateEngine() {
        val cancelled=object:MediaRuntime by engine() {
            override fun inspect(url:String,allowHttp:Boolean,check:()->Unit):MediaAsset {
                throw java.util.concurrent.CancellationException("fixture cancellation")
            }
        }
        val failure=runCatching { RoutingMediaRuntime(listOf(cancelled,engine())).inspect("https://example.org/watch",false) }.exceptionOrNull()
        assertTrue(failure is java.util.concurrent.CancellationException)
    }
}
