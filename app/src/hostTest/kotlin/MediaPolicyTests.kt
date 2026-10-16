package com.jumlo.app.hosttest

import com.jumlo.app.core.media.*
import java.io.File
import java.nio.file.Files

fun mediaPolicyTests(test: (String, () -> Unit) -> Unit) {
    fun fail(code: String, block: () -> Unit) {
        try { block() } catch (e: IllegalArgumentException) { check(e.message == code) { "Expected $code, got ${e.message}" }; return }
        error("Expected $code")
    }
    val v720 = MediaFormat("v720", "mp4", "avc1", "none", 1280, 720, 30.0, bytes=1000)
    val v1080 = MediaFormat("v1080", "mp4", "avc1", "none", 1920, 1080, 60.0, bytes=2000)
    val aac = MediaFormat("a128", "m4a", "none", "mp4a", audioBitrateKbps=128.0, bytes=200)
    val opus = MediaFormat("a160", "webm", "none", "opus", audioBitrateKbps=160.0, bytes=250)
    val muxed = MediaFormat("v360", "mp4", "avc1", "mp4a", 640, 360, 30.0, bytes=700)
    val asset = MediaAsset("asset-one", "FixtureExtractor", "My title", 2.0, listOf(v720,v1080,aac,opus,muxed))
    test("media selection identity detects changed dimensions behind the same ID") {
        val c = MediaCatalog.choices(asset, MediaMode.VIDEO).first()
        val changed = c.copy(formats=c.formats.map { if(it.hasVideo) it.copy(height=480) else it })
        check(MediaCatalog.fingerprint(c) != MediaCatalog.fingerprint(changed))
    }
    test("media selection identity excludes rotating signed transport state") {
        val c = MediaCatalog.choices(asset, MediaMode.VIDEO).first()
        val changed = c.copy(formats=c.formats.map { it.copy(approximateBytes=999999) })
        check(MediaCatalog.fingerprint(c) == MediaCatalog.fingerprint(changed))
    }
    test("reserved format names are treated as literal IDs rather than selector aliases") {
        val c=MediaCatalog.choices(asset.copy(formats=listOf(muxed.copy(id="best"))),MediaMode.VIDEO).single()
        check(MediaCatalog.validateSelector(c)=="best*[format_id=best]")
    }
    test("an uncertain media enqueue retries the same prepared identity") {
        val slot=MediaSubmissionSlot<String>();var calls=0
        val first=slot.prepare("same") { "request-${++calls}" }
        val retried=slot.prepare("same") { "request-${++calls}" }
        check(first==retried && calls==1)
    }
    test("changed choices and explicit new copies obtain new identities") {
        val slot=MediaSubmissionSlot<Int>();var calls=0
        check(slot.prepare("a") { ++calls }==1)
        check(slot.prepare("b") { ++calls }==2)
        slot.clear()
        check(slot.prepare("b") { ++calls }==3)
    }
    test("deep extractor JSON is rejected before invoking the recursive parser") {
        fail("MEDIA_METADATA_DEPTH") { MediaJsonGuard.check("[".repeat(65)+"0"+"]".repeat(65)) }
    }
    test("JSON guard distinguishes escaped strings from structural brackets") {
        MediaJsonGuard.check("{\"title\":\""+"[".repeat(100)+"\\\"x\"}")
        MediaJsonGuard.check("[".repeat(64)+"0"+"]".repeat(64))
    }
    test("media exposes measured qualities only, sorted by resolution") {
        check(MediaCatalog.choices(asset,MediaMode.VIDEO).map { it.expectedHeight } == listOf(1080,720,360))
    }
    test("media pairs silent video with a compatible audio track") {
        val c=MediaCatalog.choices(asset,MediaMode.VIDEO).first()
        check(c.formatIds == listOf("v1080","a128") && c.requiresMux && c.container == "mp4" && c.expectsAudio)
    }
    test("media does not download video when separate audio is available") {
        val choices=MediaCatalog.choices(asset,MediaMode.AUDIO)
        check(choices.size==2 && choices.all { it.formats.all { f -> !f.hasVideo } })
        check(choices.first().formatIds == listOf("a160"))
    }
    test("audio can be extracted from an actually muxed source") {
        val c=MediaCatalog.choices(asset.copy(formats=listOf(muxed)),MediaMode.AUDIO).single()
        check(c.formatIds==listOf("v360") && c.expectsAudio)
    }
    test("silent media does not advertise an audio-only download") {
        check(MediaCatalog.choices(asset.copy(formats=listOf(v720)),MediaMode.AUDIO).isEmpty())
    }
    test("encrypted representations are excluded rather than advertised") {
        val choices=MediaCatalog.choices(asset.copy(formats=listOf(v720.copy(drm=true),muxed)),MediaMode.VIDEO)
        check(choices.single().formatIds==listOf("v360"))
    }
    test("storyboards and unsafe native protocols are not playable options") {
        val choices=MediaCatalog.choices(asset.copy(formats=listOf(v720.copy(protocol="file"),muxed.copy(protocol="m3u8_native"))),MediaMode.VIDEO)
        check(choices.single().formatIds==listOf("v360"))
    }
    test("format-selector injection is rejected") {
        for(id in listOf("18/best","x+ba","--exec","a[height>0]","../x","a\n--exec")) {
            fail("INVALID_MEDIA_FORMAT") { MediaCatalog.choices(asset.copy(formats=listOf(muxed.copy(id=id))),MediaMode.VIDEO) }
        }
    }
    test("live and playlist resources cannot masquerade as a single finite file") {
        fail("LIVE_MEDIA_NOT_FINITE") { MediaCatalog.choices(asset.copy(isLive=true),MediaMode.VIDEO) }
        fail("PLAYLIST_REQUIRES_ITEM_SELECTION") { MediaCatalog.choices(asset.copy(isPlaylist=true),MediaMode.VIDEO) }
    }
    test("unknown size remains unknown and approximate size stays marked") {
        val one=MediaCatalog.choices(asset.copy(formats=listOf(muxed.copy(bytes=null))),MediaMode.VIDEO).single()
        check(one.estimatedBytes==null)
        val approx=MediaCatalog.choices(asset.copy(formats=listOf(muxed.copy(bytes=null,approximateBytes=1234))),MediaMode.VIDEO).single()
        check(approx.estimatedBytes==1234L && approx.sizeIsApproximate)
    }
    test("size overflow cannot become a tiny or negative media allocation") {
        val c=MediaCatalog.choices(asset.copy(formats=listOf(v720.copy(bytes=Long.MAX_VALUE),aac)),MediaMode.VIDEO).single()
        check(c.estimatedBytes==null)
    }
    test("mixed codec families use a lossless compatible merge container") {
        val c=MediaCatalog.choices(asset.copy(formats=listOf(v1080,opus)),MediaMode.VIDEO).single()
        check(c.container=="mkv")
    }
    test("media identity binds the extractor id and the submitted URL") {
        val x=MediaCatalog.identity(asset,"https://one.test/watch?token=private")
        check(x.matches(Regex("[a-f0-9]{64}")))
        check(x!=MediaCatalog.identity(asset.copy(id="other"),"https://one.test/watch?token=private"))
        check(x!=MediaCatalog.identity(asset,"https://two.test/watch?token=private"))
    }
    test("shared text resolves a single actual HTTP resource and removes anchors") {
        check(MediaLinks.normalize("Watch this: https://media.test/watch?v=12#t=4")=="https://media.test/watch?v=12")
        fail("MULTIPLE_MEDIA_LINKS") { MediaLinks.normalize("https://one.test/a https://two.test/b") }
        fail("HTTPS_REQUIRED") { MediaLinks.normalize("http://example.test/file") }
        fail("INVALID_URL") { MediaLinks.normalize("https://u:p@example.test/file") }
        check(MediaLinks.normalize("http://example.test/file",true).startsWith("http:"))
    }
    test("media probe arguments do not download, execute a shell or bypass TLS") {
        val args=MediaArguments.probe("https://example.test/watch?v=hello&x=world")
        check("--dump-single-json" in args && "--skip-download" in args && "--ignore-config" in args)
        check(args.takeLast(2)==listOf("--","https://example.test/watch?v=hello&x=world"))
        check(args.none { it in listOf("--exec","--no-check-certificates","--allow-unplayable-formats","--ignore-errors") })
    }
    test("selected video format is exact without silent best-quality fallback") {
        val c=MediaCatalog.choices(asset,MediaMode.VIDEO).first()
        val args=MediaArguments.download("https://example.test/video",c)
        check(args[args.indexOf("--format")+1]=="best*[format_id=v1080]+best*[format_id=a128]")
        check("--abort-on-unavailable-fragments" in args && "--no-continue" in args)
        check(args[args.indexOf("--output")+1]=="media.%(ext)s")
    }
    test("MP3 M4A and Opus audio choices invoke real extraction options") {
        val c=MediaCatalog.choices(asset,MediaMode.AUDIO).first()
        for(fmt in listOf(AudioOutput.MP3,AudioOutput.M4A,AudioOutput.OPUS)) {
            val args=MediaArguments.download("https://example.test/video",c,fmt,192)
            check("--extract-audio" in args && args[args.indexOf("--audio-format")+1]==fmt.name.lowercase())
            check(args[args.indexOf("--audio-quality")+1]=="192K")
        }
    }
    test("original audio keeps its available codec rather than inventing MP3 quality") {
        val c=MediaCatalog.choices(asset,MediaMode.AUDIO).first()
        val args=MediaArguments.download("https://example.test/video",c,AudioOutput.ORIGINAL)
        check(args[args.indexOf("--audio-format")+1]=="best" && "--audio-quality" !in args)
        fail("INVALID_AUDIO_BITRATE") { MediaArguments.download("https://example.test/video",c,AudioOutput.MP3,999) }
    }
    test("output publication rejects partial files and ambiguous multiple outputs") {
        val root=Files.createTempDirectory("uu-media-output-").toFile()
        try {
            File(root,"media.mp4.part").writeText("partial")
            fail("MEDIA_OUTPUT_MISSING") { MediaOutputPolicy.finishedFile(root) }
            File(root,"media.mp4").writeBytes(byteArrayOf(0,0,0,24,102,116,121,112,105,115,111,109))
            check(MediaOutputPolicy.finishedFile(root).name=="media.mp4")
            File(root,"media.webm").writeText("other")
            fail("MEDIA_OUTPUT_AMBIGUOUS") { MediaOutputPolicy.finishedFile(root) }
        } finally { root.deleteRecursively() }
    }
    test("renamed HTML cannot pass media-container inspection") {
        val root=Files.createTempDirectory("uu-media-types-").toFile()
        try {
            val f=File(root,"media.mp4");f.writeText("<html>Sign in</html>")
            fail("INVALID_MEDIA_CONTAINER") { MediaOutputPolicy.inspect(f) }
            f.writeBytes(byteArrayOf(0,0,0,24,102,116,121,112,105,115,111,109))
            check(MediaOutputPolicy.inspect(f).mimeType=="video/mp4")
        } finally { root.deleteRecursively() }
    }
    test("remote titles cannot control output directories or exceed UTF8 limits") {
        val name=MediaOutputPolicy.displayName("../bad/path: title\n"+"\u0627".repeat(300),"mp4")
        check('/' !in name && '\\' !in name && ':' !in name && name.toByteArray().size<=200 && name.endsWith(".mp4"))
        fail("INVALID_MEDIA_EXTENSION") { MediaOutputPolicy.displayName("title","../x") }
    }
    test("media progress distinguishes measured size from estimated size") {
        val p=MediaProgressParser.parse("UU_PROGRESS:v1080|1234|NA|9999|456.5|12")!!
        check(p.downloadedBytes==1234L && p.totalBytes==null && p.estimatedBytes==9999L && p.bytesPerSecond==456.5)
        check(MediaProgressParser.parse("UU_PROGRESS:v1080|-3|100|100|4|1")==null)
        check(MediaProgressParser.parse("[download] regular diagnostic")==null)
    }
}
