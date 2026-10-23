package com.jumlo.app.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Deterministic input files, imported through the production SourceStore by the driver. */
internal class ScreenshotFixtures(private val directory: File) {
    fun create(): Map<String, Pair<File, String>> {
        check(directory.isDirectory || directory.mkdirs())
        val files = linkedMapOf<String, Pair<File, String>>()
        fun add(key: String, name: String, mime: String, write: (File) -> Unit) {
            val file = File(directory, name)
            write(file)
            check(file.isFile && file.length() > 0)
            files[key] = file to mime
        }
        add("pdf", "Project-notes.pdf", "application/pdf") {
            ScreenshotPdfFixtures.imageDoc(it, textPage = true)
        }
        add("pdf_second", "Meeting-notes.pdf", "application/pdf") {
            ScreenshotPdfFixtures.simpleDoc(it, listOf("Project notes - page one", "Project notes - page two", "Project notes - page three"))
        }
        add("pdf_form", "Registration-form.pdf", "application/pdf") { ScreenshotPdfFixtures.formDoc(it) }
        add("image", "Landscape.png", "image/png") { image(it, false) }
        add("image_second", "Portrait.png", "image/png") { image(it, true) }
        add("signature", "Signature.png", "image/png") { signature(it) }
        add("text", "Notes.txt", "text/plain") { it.writeText("Jumlo screenshot fixture\nProject notes\n") }
        add("duplicate", "Notes-copy.txt", "text/plain") { it.writeText("Jumlo screenshot fixture\nProject notes\n") }
        add("zip", "Project-archive.zip", "application/zip") { file ->
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("notes.txt").apply { time = 0L })
                zip.write("Project archive fixture\n".toByteArray())
                zip.closeEntry()
            }
        }
        return files
    }

    private fun image(target: File, portrait: Boolean) {
        val bitmap = Bitmap.createBitmap(if (portrait) 640 else 960, if (portrait) 960 else 640, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(Color.rgb(245, 239, 225))
            paint.color = Color.rgb(35, 87, 102)
            canvas.drawRect(0f, bitmap.height * .55f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
            paint.color = Color.rgb(224, 145, 79)
            canvas.drawCircle(bitmap.width * .72f, bitmap.height * .29f, bitmap.width * .15f, paint)
            paint.color = Color.rgb(61, 115, 118)
            val hill = Path().apply {
                moveTo(0f, bitmap.height * .64f)
                lineTo(bitmap.width * .32f, bitmap.height * .29f)
                lineTo(bitmap.width * .7f, bitmap.height * .64f)
                close()
            }
            canvas.drawPath(hill, paint)
            paint.color = Color.WHITE
            paint.textSize = 42f
            canvas.drawText(if (portrait) "Portrait fixture" else "Landscape fixture", 40f, bitmap.height - 55f, paint)
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun signature(target: File) {
        val bitmap = Bitmap.createBitmap(640, 240, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.TRANSPARENT)
            val path = Path().apply {
                moveTo(45f, 175f); cubicTo(90f, 25f, 170f, 210f, 190f, 80f)
                cubicTo(230f, 20f, 225f, 210f, 300f, 125f)
                cubicTo(340f, 70f, 375f, 180f, 425f, 100f)
                lineTo(585f, 140f)
            }
            Canvas(bitmap).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(24, 44, 84)
                style = Paint.Style.STROKE
                strokeWidth = 7f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            })
            check(Color.alpha(bitmap.getPixel(0, 0)) == 0)
            target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }
}
