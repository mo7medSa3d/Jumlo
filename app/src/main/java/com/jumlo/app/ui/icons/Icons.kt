package com.jumlo.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/** Only symbols actually used by Jumlo; no material-icons-extended artifact. */
object Icons {
    object Rounded
    object AutoMirrored { object Rounded }
}

private val cache = ConcurrentHashMap<String, ImageVector>()
internal fun curatedIcon(name: String, mirrored: Boolean = false): ImageVector =
    cache.getOrPut("$name/$mirrored") {
        ImageVector.Builder(name = "Jumlo.$name", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f, autoMirror = mirrored).apply {
            requireNotNull(paths[name]).forEach { (alpha, data) ->
                addPath(pathData = PathParser().parsePathString(data).toNodes(),
                    fill = SolidColor(Color.Black), fillAlpha = alpha,
                    pathFillType = PathFillType.EvenOdd)
            }
        }.build()
    }

private val paths: Map<String, List<Pair<Float, String>>> = mapOf(
    "add" to listOf(1.0f to "M10.5 3h3v7.5H21v3h-7.5V21h-3v-7.5H3v-3h7.5z"),
    "remove" to listOf(1.0f to "M3 10.5h18v3H3z"),
    "close" to listOf(1.0f to "M5 3l7 7 7-7 2 2-7 7 7 7-2 2-7-7-7 7-2-2 7-7-7-7z"),
    "check" to listOf(1.0f to "M2.5 12.5l2.2-2.2 4.6 4.6L19.4 4.8l2.2 2.2L9.3 19.3z"),
    "check_circle" to listOf(0.22f to "M12 1a11 11 0 1 0 0 22 11 11 0 0 0 0-22z", 1.0f to "M5 12l2-2 3.2 3.2L17 6.5l2 2-8.8 8.8z"),
    "done_all" to listOf(1.0f to "M1 12l2-2 4 4L17 4l2 2L7 18zM11 16l2 2L23 8l-2-2z"),
    "home" to listOf(1.0f to "M2 10l10-8 10 8-2 2-1-.8V21h-5v-7h-4v7H5v-9.8L4 12z"),
    "grid" to listOf(1.0f to "M3 2h6a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zM15 2h6a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1h-6a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1zM3 14h6a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1z", 0.5f to "M15 14h6a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1h-6a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1z"),
    "dashboard" to listOf(1.0f to "M2 2h9v13H2zM13 2h9v7h-9z", 0.5f to "M2 17h9v5H2zM13 11h9v11h-9z"),
    "folder" to listOf(0.35f to "M2 3h7l3 3h9v4H2z", 1.0f to "M2 8h20l-2 12H3L1 10z"),
    "folder_shared" to listOf(0.28f to "M2 3h7l3 3h10v15H2z", 1.0f to "M12 8a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM7 19v-1c0-4 10-4 10 0v1z"),
    "archive" to listOf(0.3f to "M2 5h20v16H2z", 1.0f to "M2 2h20v5H2zM10 7h4v3h-4zM8 10h4v3H8zM10 13h4v3h-4zM9 17h6v4H9z"),
    "unarchive" to listOf(0.3f to "M2 7h20v14H2z", 1.0f to "M1 3h22v4H1zM10 19v-7H6l6-6 6 6h-4v7z"),
    "document" to listOf(0.3f to "M4 2h10l6 6v14H4z", 1.0f to "M14 2v6h6zM7 11h10v2H7zM7 15h10v2H7zM7 19h7v2H7z"),
    "pdf" to listOf(0.3f to "M4 2h10l6 6v14H4z", 1.0f to "M14 2v6h6zM6 12h4v5H8v3H6zM8 14v1h.6v-1zM11 12h5v8h-5zM13 14v4h1v-4zM17 12h4v2h-2v1h2v2h-2v3h-2z"),
    "files" to listOf(0.35f to "M2 5h14v17H2z", 1.0f to "M7 1h10l5 5v12H7zM17 1v6h5z"),
    "download" to listOf(1.0f to "M10 2h4v9h5l-7 7-7-7h5zM2 18h3v3h14v-3h3v6H2z"),
    "upload" to listOf(1.0f to "M10 18h4V9h5l-7-7-7 7h5zM2 18h3v3h14v-3h3v6H2z"),
    "arrow_back" to listOf(1.0f to "M12 2l2 2-6.5 6.5H22v3H7.5L14 20l-2 2L2 12z"),
    "arrow_forward" to listOf(1.0f to "M12 2l-2 2 6.5 6.5H2v3h14.5L10 20l2 2 10-10z"),
    "arrow_up" to listOf(1.0f to "M12 2L2 12l2 2 6.5-6.5V22h3V7.5L20 14l2-2z"),
    "arrow_down" to listOf(1.0f to "M10.5 2h3v14.5L20 10l2 2-10 10L2 12l2-2 6.5 6.5z"),
    "chevron_left" to listOf(1.0f to "M15 3l3 3-6 6 6 6-3 3-9-9z"),
    "chevron_right" to listOf(1.0f to "M9 3L6 6l6 6-6 6 3 3 9-9z"),
    "sort" to listOf(1.0f to "M2 4h20v3H2zM2 10h14v3H2zM2 16h8v3H2z"),
    "view_list" to listOf(1.0f to "M2 3h4v4H2zM9 3h13v4H9zM2 10h4v4H2zM9 10h13v4H9zM2 17h4v4H2zM9 17h13v4H9z"),
    "settings" to listOf(1.0f to "M9 1h6l1 4 3-1 3 5-3 3 3 3-3 5-3-1-1 4H9l-1-4-3 1-3-5 3-3-3-3 3-5 3 1zM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z"),
    "scan" to listOf(1.0f to "M2 2h6v3H5v3H2zM16 2h6v6h-3V5h-3zM19 16h3v6h-6v-3h3zM2 16h3v3h3v3H2zM7 7h10v3H7zM7 12h10v2H7zM7 16h7v2H7z"),
    "compress" to listOf(0.25f to "M3 9h18v6H3z", 1.0f to "M10 1h4v3h4l-6 6-6-6h4zM10 23v-3H6l6-6 6 6h-4v3z"),
    "merge" to listOf(1.0f to "M10.5 9H7l5-7 5 7h-3.5v5c0 2 4 4 7 4v3c-4 0-7-1-8.5-4C10.5 20 7 21 3 21v-3c3 0 7.5-2 7.5-4z"),
    "layers" to listOf(1.0f to "M12 2L1 8l11 6 11-6z", 0.5f to "M3 11l9 5 9-5 2 2-11 6L1 13zM3 16l9 5 9-5 2 2-11 6L1 18z"),
    "copy" to listOf(0.35f to "M1 6h4v15h14v3H1z", 1.0f to "M7 1h10l5 5v13H7zM17 1v6h5z"),
    "split" to listOf(1.0f to "M7 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM7 5a2 2 0 1 1 0 4 2 2 0 0 1 0-4zM7 12a5 5 0 1 0 0 10 5 5 0 0 0 0-10zM7 15a2 2 0 1 1 0 4 2 2 0 0 1 0-4zM10 7l12 12-2 2L9 10zM13 10l7-8 2 2-7 8z"),
    "crop" to listOf(1.0f to "M5 1h3v15h15v3H8a3 3 0 0 1-3-3V8H1V5h4zM10 5h6a3 3 0 0 1 3 3v15h-3V8h-6z"),
    "rotate" to listOf(1.0f to "M12 3a9 9 0 1 0 9 9h-3a6 6 0 1 1-6-6v4l7-5-7-5z"),
    "undo" to listOf(1.0f to "M9 2L1 9l8 7v-5h5a5 5 0 0 1 4 8l2 2a8 8 0 0 0-6-13H9z"),
    "redo" to listOf(1.0f to "M15 2l8 7-8 7v-5h-5a5 5 0 0 0-4 8l-2 2a8 8 0 0 1 6-13h5z"),
    "eye" to listOf(1.0f to "M12 4C5 4 1 12 1 12s4 8 11 8 11-8 11-8-4-8-11-8zM12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM12 10a2 2 0 1 1 0 4 2 2 0 0 1 0-4z"),
    "lock" to listOf(1.0f to "M5 10V8a7 7 0 0 1 14 0v2h2v13H3V10zM8 10h8V8a4 4 0 0 0-8 0zM11 14v5h2v-5z"),
    "shield" to listOf(0.35f to "M12 1l10 4v7c0 6-10 11-10 11S2 18 2 12V5z", 1.0f to "M6 12l2-2 3 3 6-6 2 2-8 8z"),
    "info" to listOf(0.25f to "M12 1a11 11 0 1 0 0 22 11 11 0 0 0 0-22z", 1.0f to "M10.5 5h3v3h-3zM10.5 10h3v9h-3z"),
    "help" to listOf(0.25f to "M12 1a11 11 0 1 0 0 22 11 11 0 0 0 0-22z", 1.0f to "M7 8a5 5 0 0 1 10 0c0 3-4 4-4 6h-3c0-4 4-4 4-6a2 2 0 0 0-4 0zM10 17h3v3h-3z"),
    "edit" to listOf(1.0f to "M3 15L16 2a2 2 0 0 1 3 0l3 3a2 2 0 0 1 0 3L9 21l-7 1zM15 7l2 2 2-2-2-2z"),
    "watermark" to listOf(0.3f to "M3 2h18v20H3z", 1.0f to "M12 4l-5 8a6 6 0 1 0 10 0z"),
    "checklist" to listOf(0.3f to "M7 2h15v20H7z", 1.0f to "M0 6l2-2 2 2 3-3 2 2-5 5zM11 5h8v3h-8zM0 16l2-2 2 2 3-3 2 2-5 5zM11 15h8v3h-8z"),
    "ocr" to listOf(1.0f to "M1 1h6v3H4v3H1zM17 1h6v6h-3V4h-3zM1 17h3v3h3v3H1zM20 17h3v6h-6v-3h3zM6 6h12v3h-4.5v9h-3V9H6z"),
    "trash" to listOf(0.3f to "M4 7h16l-1 15H5z", 1.0f to "M8 1h8v3h6v3H2V4h6zM8 10h2v9H8zM14 10h2v9h-2z"),
    "cloud" to listOf(1.0f to "M7 7a6 6 0 0 1 11-1 6 6 0 0 1 1 12H6A6 6 0 0 1 7 6z"),
    "printer" to listOf(0.3f to "M4 1h16v7H4zM5 16h14v7H5z", 1.0f to "M3 7h18a2 2 0 0 1 2 2v9h-4v-4H5v4H1V9a2 2 0 0 1 2-2zM17 9v2h3V9z"),
    "user" to listOf(1.0f to "M12 2a5 5 0 1 0 0 10 5 5 0 0 0 0-10z", 0.55f to "M2 23v-3c0-9 20-9 20 0v3z"),
    "star" to listOf(1.0f to "M12 1l3.5 7 7.5 1-5.5 5.5 1.5 8-7-4-7 4 1.5-8L1 9l7.5-1z"),
    "star_border" to listOf(0.75f to "M12 1l3.5 7 7.5 1-5.5 5.5 1.5 8-7-4-7 4 1.5-8L1 9l7.5-1z"),
    "heart" to listOf(1.0f to "M12 5C7-2-2 4 2 11l10 11 10-11C26 4 17-2 12 5z"),
    "heart_border" to listOf(0.75f to "M12 5C7-2-2 4 2 11l10 11 10-11C26 4 17-2 12 5z"),
    "search" to listOf(1.0f to "M10 1a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM10 4a6 6 0 1 1 0 12 6 6 0 0 1 0-12zM16 14l8 8-2 2-8-8z"),
    "image" to listOf(0.3f to "M2 2h20v20H2z", 1.0f to "M5 17l4-5 4 3 4-6 5 10H2zM7 5a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"),
    "photo_library" to listOf(0.35f to "M1 5h3v16h16v3H1z", 1.0f to "M6 1h18v18H6zM9 15h12l-4-7-3 4-2-2zM10 4a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"),
    "broken_image" to listOf(0.3f to "M2 2h20v20H2z", 1.0f to "M2 14l6-5 5 5 4-3 5 5v6H2zM5 2l5 5-3 4 4 5-3 6h3l3-7-4-4 3-4-5-5z"),
    "link" to listOf(1.0f to "M9 3h-3a6 6 0 0 0 0 12h3v-3H6a3 3 0 0 1 0-6h3zM15 9h3a6 6 0 0 1 0 12h-3v-3h3a3 3 0 0 0 0-6h-3zM7 10h10v4H7z"),
    "audio" to listOf(1.0f to "M10 2h12v4h-8v12a5 5 0 1 1-4-4z"),
    "audio_file" to listOf(0.3f to "M4 1h10l6 6v16H4z", 1.0f to "M14 1v6h6zM11 9h6v3h-3v6a3 3 0 1 1-3-3z"),
    "video" to listOf(1.0f to "M1 4h22v17H1zM4 4l3 4h3L7 4zM12 4l3 4h3l-3-4zM10 10v8l6-4z"),
    "videocam" to listOf(1.0f to "M2 5h14v14H2zM16 10l7-5v14l-7-5z"),
    "play" to listOf(1.0f to "M6 2v20l17-10z"),
    "pause" to listOf(1.0f to "M4 2h6v20H4zM14 2h6v20h-6z"),
    "sparkles" to listOf(1.0f to "M9 2l3 7 7 3-7 3-3 7-3-7-6-3 6-3zM19 0l1.5 4L24 5.5 20.5 7 19 11l-1.5-4-4-1.5 4-1.5z"),
    "bolt" to listOf(1.0f to "M13 0L3 14h7l-1 10L22 8h-8z"),
    "globe" to listOf(0.35f to "M12 1a11 11 0 1 0 0 22 11 11 0 0 0 0-22z", 1.0f to "M12 1C5 5 5 19 12 23 19 19 19 5 12 1zM12 4c3 3 3 13 0 16-3-3-3-13 0-16zM1 8h22v3H1zM1 14h22v3H1z"),
    "compare" to listOf(1.0f to "M10 1h3v22h-3zM2 4h6v3H5v10h3v3H2zM15 4h7v16h-7v-3h4V7h-4zM5 13l3-3v7H5zM15 10l4 7h-4z"),
    "swap" to listOf(1.0f to "M15 1l8 7-8 7v-5H2V6h13zM9 11l-8 6 8 6v-4h13v-4H9z"),
    "flip" to listOf(1.0f to "M11 1h2v3h-2zM11 6h2v3h-2zM11 11h2v3h-2zM11 16h2v3h-2zM11 21h2v2h-2zM2 4l7 4v12l-7 3z", 0.4f to "M22 4l-7 4v12l7 3z"),
    "format_size" to listOf(1.0f to "M9 3h14v3h-5v15h-4V6H9zM1 10h10v3H8v8H4v-8H1z"),
    "palette" to listOf(1.0f to "M12 1C-2 1-2 23 11 23c6 0 0-6 4-6h4C27 17 24 1 12 1zM6 8a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM10 4a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM16 5a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM19 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4z"),
    "dark" to listOf(1.0f to "M15 1A11 11 0 1 0 23 17C14 20 8 9 15 1z"),
    "notification" to listOf(1.0f to "M10 1h4v3c5 1 5 7 5 11l3 3v2H2v-2l3-3c0-4 0-10 5-11zM9 22h6a3 3 0 0 1-6 0z"),
    "storage" to listOf(1.0f to "M2 2h20v8H2zM5 5v2h3V5zM2 14h20v8H2zM5 17v2h3v-2z"),
    "share" to listOf(1.0f to "M18 1a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM6 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM18 15a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM7 10l10-6 1.5 2.5-10 6zM8.5 12.5l10 6L17 21 7 15z"),
    "clock" to listOf(0.3f to "M12 1a11 11 0 1 0 0 22 11 11 0 0 0 0-22z", 1.0f to "M10.5 5h3v7l5 3-1.5 2.5-6.5-4z"),
    "history" to listOf(1.0f to "M12 3a9 9 0 1 1-9 9h3a6 6 0 1 0 6-6H7v4L0 5l7-5v3zM11 8h2v5l4 2-1 2-5-3z"),
    "bookmarks" to listOf(0.35f to "M7 1h16v19l-4-3V4H7z", 1.0f to "M2 6h14v18l-7-4-7 4z"),
    "book" to listOf(1.0f to "M1 3c4-2 7-2 10 0v20c-3-2-6-2-10 0zM13 3c3-2 6-2 10 0v20c-4-2-7-2-10 0z"),
    "clipboard" to listOf(0.3f to "M3 4h18v19H3z", 1.0f to "M8 1h8v6H8zM6 11h12v3H6zM6 17h9v3H6z"),
    "clean" to listOf(1.0f to "M16 0l4 2-6 12-4-2zM8 11l9 5-6 8H1l2-8z", 0.35f to "M17 18l6 3v3h-9z"),
    "build" to listOf(1.0f to "M15 1l-3 6 5 3 4-5c3 6-1 11-7 10L6 23l-5-5 9-9C8 4 10 1 15 1z"),
    "fingerprint" to listOf(1.0f to "M2 12C2-2 22-2 22 12h-3c0-10-14-10-14 0v4H2zM7 12c0-7 10-7 10 0v5c0 2 2 3 4 3v3c-4 0-7-2-7-6v-5c0-3-4-3-4 0v5H7zM11 12h2v5c0 5-3 7-7 7v-3c3 0 5-1 5-4zM3 18h3v6H3z"),
    "warning" to listOf(1.0f to "M12 1l12 22H0zM10.5 8h3v7h-3zM10.5 18h3v3h-3z"),
    "radio_empty" to listOf(0.25f to "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z"),
    "more_vert" to listOf(1.0f to "M12 1a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM12 18a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"),
    "more_horiz" to listOf(1.0f to "M3.5 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5zM20.5 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z"),
    "zoom" to listOf(1.0f to "M1 1h9v3H6l5 5-2 2-5-5v4H1zM14 1h9v9h-3V6l-5 5-2-2 5-5h-4zM1 14h3v4l5-5 2 2-5 5h4v3H1zM20 14h3v9h-9v-3h4l-5-5 2-2 5 5z"),
    "vibration" to listOf(1.0f to "M8 1h8v22H8zM3 6h3v12H3zM18 6h3v12h-3zM0 9h2v6H0zM22 9h2v6h-2z"),
)
