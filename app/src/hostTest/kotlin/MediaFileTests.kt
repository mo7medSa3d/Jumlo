package com.jumlo.app.hosttest

import com.jumlo.app.core.media.*
import java.io.File
import java.nio.file.Files

/** Actual FFmpeg files through the production process runner and output validator.
 * Not an Android ABI/device test and not a live-platform extractor test. */
fun mediaFileTests(test:(String,()->Unit)->Unit) {
    val bootstrap="import os,sys,subprocess;os.setsid();print('UU_PID:'+str(os.getpid()),flush=True);sys.exit(subprocess.run(sys.argv[1:]).returncode)"
    val runner=MediaProcessRunner { pid,force -> ProcessBuilder("/bin/kill",if(force) "-KILL" else "-TERM","--","-$pid").start().waitFor() }
    fun run(dir:File,args:List<String>):MediaProcessResult = runner.run(MediaProcessSpec(listOf("python3","-c",bootstrap)+args,dir,timeoutMillis=30000,stdoutLimit=65536))
    fun command(dir:File,args:List<String>) {
        val result=run(dir,listOf("ffmpeg","-hide_banner","-loglevel","error","-nostdin","-y")+args)
        check(result.exitCode==0) { "FFmpeg failed: ${result.stderr.take(800)}" }
    }
    fun probe(dir:File,file:File):String {
        val result=run(dir,listOf("ffprobe","-v","error","-show_entries","stream=codec_name,codec_type,width,height","-of","csv=p=0",file.absolutePath))
        check(result.exitCode==0) { "ffprobe failed" }; return result.stdout
    }
    fun fixture(block:(File,File,File)->Unit) {
        val dir=Files.createTempDirectory("uu-real-media-").toFile()
        try {
            val video=File(dir,"video.mp4");val audio=File(dir,"audio.m4a")
            command(dir,listOf("-f","lavfi","-i","testsrc=size=320x240:rate=10","-t","1","-an","-c:v","mpeg4","-pix_fmt","yuv420p",video.absolutePath))
            command(dir,listOf("-f","lavfi","-i","sine=frequency=440:sample_rate=44100","-t","1","-vn","-c:a","aac",audio.absolutePath))
            block(dir,video,audio)
        } finally { dir.deleteRecursively() }
    }
    test("real FFmpeg merges separate video and audio without changing resolution") {
        fixture { dir,video,audio ->
            val output=File(dir,"media.mp4")
            command(dir,listOf("-i",video.absolutePath,"-i",audio.absolutePath,"-map","0:v:0","-map","1:a:0","-c","copy",output.absolutePath))
            check(MediaOutputPolicy.finishedFile(dir)==output)
            check(MediaOutputPolicy.inspect(output).mimeType=="video/mp4")
            val tracks=probe(dir,output);check(tracks.contains("video,320,240") && tracks.contains("audio"))
        }
    }
    test("real MP3 audio-only export contains audio and no video track") {
        fixture { dir,video,audio ->
            val muxed=File(dir,"source.mp4");val output=File(dir,"media.mp3")
            command(dir,listOf("-i",video.absolutePath,"-i",audio.absolutePath,"-map","0:v:0","-map","1:a:0","-c","copy",muxed.absolutePath))
            command(dir,listOf("-i",muxed.absolutePath,"-map","0:a:0","-vn","-c:a","libmp3lame","-b:a","192k",output.absolutePath))
            check(MediaOutputPolicy.inspect(output).mimeType=="audio/mpeg")
            val tracks=probe(dir,output);check(tracks.contains("mp3,audio") && !tracks.contains("video"))
        }
    }
    test("real M4A source-codec output retains AAC without a video track") {
        fixture { dir,_,audio ->
            val output=File(dir,"media.m4a")
            command(dir,listOf("-i",audio.absolutePath,"-map","0:a:0","-c:a","copy",output.absolutePath))
            check(MediaOutputPolicy.finishedFile(dir)==output)
            check(MediaOutputPolicy.inspect(output).mimeType=="audio/mp4")
            check(probe(dir,output).trim()=="aac,audio")
        }
    }
    test("real Opus encoding produces a verified Ogg container") {
        fixture { dir,_,audio ->
            val output=File(dir,"media.opus")
            command(dir,listOf("-i",audio.absolutePath,"-vn","-c:a","libopus","-b:a","128k",output.absolutePath))
            check(MediaOutputPolicy.inspect(output).mimeType=="audio/ogg")
            check(probe(dir,output).trim()=="opus,audio")
        }
    }
    test("real FFmpeg malformed-input failure cannot produce a finished media output") {
        val dir=Files.createTempDirectory("uu-rejected-media-").toFile()
        try {
            File(dir,"source.bin").writeText("not a media container")
            val result=run(dir,listOf("ffmpeg","-v","error","-nostdin","-i",File(dir,"source.bin").absolutePath,File(dir,"media.mp4").absolutePath))
            check(result.exitCode!=0)
            try { MediaOutputPolicy.finishedFile(dir);error("Invalid output accepted") }
            catch(e:IllegalArgumentException) { check(e.message in setOf("MEDIA_OUTPUT_MISSING","INVALID_MEDIA_OUTPUT")) }
        } finally { dir.deleteRecursively() }
    }
}
