package com.jumlo.app.data.jobs
import com.jumlo.app.core.CapabilityPolicy
import com.jumlo.app.model.*

/** Catalog and common standard availability policy. Availability does not certify device qualification. */
object CapabilityRegistry {
    val allTools: List<UtilityTool> = listOf(
        UtilityTool("pdf_to_docx", "tools/pdf/word", PillarCategory.PDF, "PDF to Word", "\u200F\u2066PDF\u2069 إلى \u2066Word\u2069", "Turn PDFs into editable Word docs", "حوّل PDF إلى مستند Word قابل للتحرير", "description"),
        UtilityTool("pdf_to_pptx", "tools/pdf/powerpoint", PillarCategory.PDF, "PDF to PowerPoint", "\u200F\u2066PDF\u2069 إلى \u2066PowerPoint\u2069", "Turn every page into a slide", "حوّل كل صفحة إلى شريحة", "slideshow"),
        UtilityTool("pdf_02", "images_to_pdf", PillarCategory.PDF, "Images to PDF", "صور إلى \u2066PDF\u2069", "Turn photos into a clean PDF", "حوّل صورك إلى PDF نظيف", "picture_as_pdf"),
        UtilityTool("pdf_03", "merge_pdf", PillarCategory.PDF, "Merge PDF", "دمج ملفات \u2066PDF\u2069", "Combine PDFs into one, in order", "اجمع ملفات PDF في ملف واحد مرتب", "file_copy"),
        UtilityTool("pdf_05", "split_pdf", PillarCategory.PDF, "Split PDF", "تقسيم مستند \u2066PDF\u2069", "Split a PDF exactly where you want", "قسّم PDF بالضبط حيث تريد", "content_cut"),
        UtilityTool("pdf_04", "organize_pages", PillarCategory.PDF, "Organize Pages", "ترتيب صفحات \u2066PDF\u2069", "Perfect your page order in seconds", "رتّب صفحاتك بإتقان في ثوانٍ", "dashboard"),
        UtilityTool("pdf_06", "extract_pages", PillarCategory.PDF, "Extract Pages", "استخراج صفحات \u2066PDF\u2069", "Pull out only the pages you need", "استخرج الصفحات التي تحتاجها فقط", "file_open"),
        UtilityTool("pdf_07", "pdf_to_images", PillarCategory.PDF, "PDF to Images", "تحويل \u2066PDF\u2069 إلى صور", "Save any page as a sharp image", "احفظ أي صفحة كصورة واضحة", "photo_library"),
        UtilityTool("pdf_long_image", "pdf_long_image", PillarCategory.PDF, "PDF to Long Image", "\u200F\u2066PDF\u2069 إلى صورة طويلة", "Stitch pages into one tall shareable image", "ادمج الصفحات في صورة طويلة واحدة للمشاركة", "photo_library"),
        UtilityTool("pdf_01", "pdf_viewer", PillarCategory.PDF, "View PDF", "عارض \u2066PDF\u2069", "Read PDFs clearly, page by page", "اقرأ ملفات PDF بوضوح صفحة صفحة", "visibility"),
        UtilityTool("pdf_compress", "pdf_compress", PillarCategory.PDF, "Compress PDF", "ضغط \u2066PDF\u2069", "Shrink PDFs without losing readable text", "صغّر PDF دون فقدان نص قابل للقراءة", "compress"),
        UtilityTool("pdf_crop", "pdf_crop", PillarCategory.PDF, "Crop PDF Pages", "قص صفحات \u2066PDF\u2069", "Crop pages to exactly what matters", "اقصص الصفحات على ما يهم فقط", "crop"),
        UtilityTool("pdf_stamp", "pdf_stamp", PillarCategory.PDF, "Watermark & Page Numbers", "علامة مائية وترقيم", "Brand every page in one tap", "ضع علامتك على كل صفحة بلمسة واحدة", "approval"),
        UtilityTool("pdf_signature", "pdf_signature", PillarCategory.PDF, "Sign PDF", "توقيع \u2066PDF\u2069", "Sign PDFs in seconds, no printing", "وقّع PDF في ثوانٍ دون طباعة", "draw"),
        UtilityTool("pdf_protect", "pdf_protect", PillarCategory.PDF, "Password Protect PDF", "حماية \u2066PDF\u2069 بكلمة مرور", "Lock PDFs with a password you control", "اقفل PDF بكلمة مرور تتحكم بها", "lock"),
        UtilityTool("pdf_nup", "pdf_nup", PillarCategory.PDF, "Pages per Sheet (2/4-in-1)", "طباعة 2 أو 4 صفحات \u2066PDF\u2069", "Fit 2 or 4 pages per A4 sheet to save ink & paper", "اجمع 2 أو 4 صفحات في ورقة A4 واحدة لتوفير الطباعة", "grid_view"),
        UtilityTool("pdf_privacy_redact", "pdf_privacy_redact", PillarCategory.PDF, "Redact Sensitive Data", "طمس بيانات \u2066PDF\u2069 نهائيًا", "Permanently burn black boxes over private info", "احجب الأرقام والبيانات الحساسة نهائيًا قبل المشاركة", "lock"),
        UtilityTool("office_to_pdf", "office_to_pdf", PillarCategory.PDF, "Office to PDF", "\u200F\u2066Office\u2069 إلى \u2066PDF\u2069", "Turn Word, Excel and slides into a clean PDF", "حوّل Word وExcel والشرائح إلى PDF نظيف", "description"),
        UtilityTool("pdf_ocr", "tools/pdf/ocr", PillarCategory.PDF, "Extract Text", "استخراج النص", "Extract text from images and PDFs", "استخرج النص من الصور و\u2066PDF\u2069 بدقة عالية", "text_fields"),
        UtilityTool("scan_doc", "tools/scan", PillarCategory.PDF, "Smart Scan", "المسح الذكي", "Scan a stack of pages into one PDF", "امسح عدة صفحات في PDF واحد", "document_scanner"),

        UtilityTool("img_01", "convert_images", PillarCategory.IMAGES, "Convert Images", "تحويل الصور", "Convert images in batches, fast", "حوّل الصور دفعات وبسرعة", "photo_library"),
        UtilityTool("img_02", "resize_images", PillarCategory.IMAGES, "Resize Images", "تغيير أبعاد الصور", "Resize images to exactly the size you need", "غيّر حجم الصور إلى المقاس المطلوب تمامًا", "aspect_ratio"),
        UtilityTool("img_03", "crop_rotate_flip", PillarCategory.IMAGES, "Crop, Rotate & Flip", "قص وتدوير وقلب الصور", "Crop, rotate, and flip with confidence", "اقصص ودوّر واقلِب بثقة", "crop"),
        UtilityTool("img_04", "compress_images", PillarCategory.IMAGES, "Compress Images", "ضغط الصور", "Smaller images, same great look", "صور أصغر بنفس المظهر الرائع", "compress"),
        UtilityTool("img_watermark", "img_watermark", PillarCategory.IMAGES, "Watermark Images", "علامة مائية للصور", "Protect your images with your mark", "احمِ صورك بعلامتك", "approval"),
        UtilityTool("img_join", "img_join", PillarCategory.IMAGES, "Join / Collage", "دمج الصور", "Build beautiful collages in minutes", "أنشئ كولاجات جميلة في دقائق", "grid_view"),

        UtilityTool("dl_media", "media_download", PillarCategory.DOWNLOADS, "Download", "تنزيل", "", "", "download"),
        UtilityTool("dl_02", "add_single_url", PillarCategory.DOWNLOADS, "New Direct Download", "تنزيل مباشر و\u2066Google Drive\u2069", "Grab any direct file or Drive link, fast", "احصل على أي ملف مباشر أو رابط Drive بسرعة", "add_link"),
        UtilityTool("dl_03", "batch_url_intake", PillarCategory.DOWNLOADS, "Batch URL Intake", "إدخال دفعة روابط", "Paste a whole list of links — we'll handle them all", "الصق قائمة روابط كاملة — وسنتولى الباقي", "download"),

        UtilityTool("file_07", "create_zip", PillarCategory.FILES, "Create ZIP Archive", "إنشاء أرشيف \u2066ZIP\u2069", "Bundle files into one tidy ZIP", "اجمع الملفات في ZIP واحد مرتب", "folder_zip"),
        UtilityTool("file_08", "extract_zip", PillarCategory.FILES, "Extract ZIP Archive", "فك ضغط \u2066ZIP\u2069", "Unzip anything, safely", "فك ضغط أي ملف بأمان", "unarchive"),
        UtilityTool("file_encrypted_zip", "encrypted_zip", PillarCategory.FILES, "Encrypted ZIP", "\u200F\u2066ZIP\u2069 مشفر", "ZIPs locked with AES encryption", "ملفات ZIP مقفلة بتشفير AES", "lock"),
        UtilityTool("file_batch_rename", "batch_rename", PillarCategory.FILES, "Batch Rename (copies)", "إعادة تسمية دفعية", "Rename hundreds of files in one go", "أعد تسمية مئات الملفات دفعة واحدة", "drive_file_rename_outline")
    )
    val tools get() = allTools
    fun availability(id:String) = CapabilityPolicy.availability(id)
    fun visible(id:String):Boolean = if (id.startsWith("dl_") && !com.jumlo.app.BuildConfig.ENABLE_MEDIA_DOWNLOADER) false else availability(id).visible
    fun enabled(id:String):Boolean = availability(id).enabled
    fun canProcess(id:String):Boolean = CapabilityPolicy.canProcess(id)
    fun reason(id:String):String = availability(id).reason.orEmpty()
}
