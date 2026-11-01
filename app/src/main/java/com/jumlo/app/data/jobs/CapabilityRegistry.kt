package com.jumlo.app.data.jobs
import com.jumlo.app.core.CapabilityPolicy
import com.jumlo.app.model.*

/** Catalog and common standard availability policy. Availability does not certify device qualification. */
object CapabilityRegistry {
    val allTools: List<UtilityTool> = listOf(
        // PDF Tools
        UtilityTool("pdf_to_docx", "tools/pdf/word", PillarCategory.PDF, "PDF to Word", "PDF إلى Word", "Turn PDFs into editable Word docs", "حوّل PDF إلى مستند Word قابل للتحرير", "description"),
        UtilityTool("pdf_to_pptx", "tools/pdf/powerpoint", PillarCategory.PDF, "PDF to PowerPoint", "PDF إلى PowerPoint", "Turn every page into a slide", "حوّل كل صفحة إلى شريحة", "slideshow"),
        UtilityTool("pdf_02", "images_to_pdf", PillarCategory.PDF, "Images to PDF", "صور إلى PDF", "Turn photos into a clean PDF", "حوّل صورك إلى PDF نظيف", "picture_as_pdf"),
        UtilityTool("pdf_03", "merge_pdf", PillarCategory.PDF, "Merge PDF", "دمج ملفات PDF", "Combine PDFs into one, in order", "اجمع ملفات PDF في ملف واحد مرتب", "file_copy"),
        UtilityTool("pdf_05", "split_pdf", PillarCategory.PDF, "Split PDF", "تقسيم مستند PDF", "Split a PDF exactly where you want", "قسّم PDF بالضبط حيث تريد", "content_cut"),
        UtilityTool("pdf_04", "organize_pages", PillarCategory.PDF, "Organize Pages", "ترتيب صفحات PDF", "Perfect your page order in seconds", "رتّب صفحاتك بإتقان في ثوانٍ", "dashboard"),
        UtilityTool("pdf_06", "extract_pages", PillarCategory.PDF, "Extract Pages", "استخراج صفحات PDF", "Pull out only the pages you need", "استخرج الصفحات التي تحتاجها فقط", "file_open"),
        UtilityTool("pdf_07", "pdf_to_images", PillarCategory.PDF, "PDF to Images", "تحويل PDF إلى صور", "Save any page as a sharp image", "احفظ أي صفحة كصورة واضحة", "photo_library"),
        UtilityTool("pdf_01", "pdf_viewer", PillarCategory.PDF, "View PDF", "عارض PDF", "Read PDFs clearly, page by page", "اقرأ ملفات PDF بوضوح صفحة صفحة", "visibility"),
        UtilityTool("pdf_compress", "pdf_compress", PillarCategory.PDF, "Compress PDF", "ضغط PDF", "Shrink PDFs without losing readable text", "صغّر PDF دون فقدان نص قابل للقراءة", "compress"),
        UtilityTool("pdf_crop", "pdf_crop", PillarCategory.PDF, "Crop PDF Pages", "قص صفحات PDF", "Crop pages to exactly what matters", "اقصص الصفحات على ما يهم فقط", "crop"),
        UtilityTool("pdf_annotate", "pdf_annotate", PillarCategory.PDF, "Annotate PDF", "تعليقات PDF", "Mark up PDFs like a pro", "علّم على PDF باحتراف", "edit"),
        UtilityTool("pdf_stamp", "pdf_stamp", PillarCategory.PDF, "Watermark & Page Numbers", "علامة مائية وترقيم", "Brand every page in one tap", "ضع علامتك على كل صفحة بلمسة واحدة", "approval"),
        UtilityTool("pdf_signature", "pdf_signature", PillarCategory.PDF, "Sign PDF", "توقيع PDF", "Sign PDFs in seconds, no printing", "وقّع PDF في ثوانٍ دون طباعة", "draw"),
        UtilityTool("pdf_image", "pdf_image", PillarCategory.PDF, "Add Image to PDF", "إضافة صورة إلى PDF", "Drop a logo or photo onto any page", "أضف شعارًا أو صورة إلى أي صفحة", "add_photo_alternate"),
        UtilityTool("pdf_protect", "pdf_protect", PillarCategory.PDF, "Password Protect PDF", "حماية PDF بكلمة مرور", "Lock PDFs with a password you control", "اقفل PDF بكلمة مرور تتحكم بها", "lock"),
        UtilityTool("pdf_metadata", "pdf_metadata", PillarCategory.PDF, "PDF Metadata", "بيانات PDF", "Make your PDFs polished and searchable", "اجعل ملفات PDF أنيقة وقابلة للبحث", "info"),
        UtilityTool("pdf_clean", "pdf_clean", PillarCategory.PDF, "Clean Metadata", "تنظيف البيانات", "Share PDFs without the hidden trail", "شارك PDF دون أثر خفي", "shield_lock"),
        UtilityTool("pdf_forms", "pdf_forms", PillarCategory.PDF, "Fill PDF Form", "تعبئة نموذج PDF", "Fill forms fast, then flatten to share", "املأ النماذج بسرعة ثم ثبّتها للمشاركة", "assignment"),
        UtilityTool("pdf_compare", "pdf_compare", PillarCategory.PDF, "Compare PDFs", "مقارنة ملفات PDF", "See whether two PDFs match, page by page", "اعرف إن كان ملفا PDF متطابقين صفحة صفحة", "compare"),
        UtilityTool("pdf_redact", "pdf_redact", PillarCategory.PDF, "Redact PDF", "حجب محتوى PDF", "Permanently black out text, images and notes", "احجب النصوص والصور والملاحظات نهائيًا", "shield"),
        UtilityTool("office_to_pdf", "office_to_pdf", PillarCategory.PDF, "Office to PDF", "Office إلى PDF", "Turn Word, Excel and slides into a clean PDF", "حوّل Word وExcel والشرائح إلى PDF نظيف", "description"),
        UtilityTool("pdf_ocr", "tools/pdf/ocr", PillarCategory.PDF, "Extract Text", "استخراج النص", "Turn scans into copyable Latin text", "حوّل المسح إلى نص لاتيني قابل للنسخ", "text_fields"),
        UtilityTool("scan_doc", "tools/scan", PillarCategory.PDF, "Smart Scan", "المسح الذكي", "Scan a stack of pages into one PDF", "امسح عدة صفحات في PDF واحد", "document_scanner"),
        // Image Tools
        UtilityTool("img_01", "convert_images", PillarCategory.IMAGES, "Convert Images", "تحويل الصور", "Convert images in batches, fast", "حوّل الصور دفعات وبسرعة", "photo_library"),
        UtilityTool("img_02", "resize_images", PillarCategory.IMAGES, "Resize Images", "تغيير أبعاد الصور", "Resize images to exactly the size you need", "غيّر حجم الصور إلى المقاس المطلوب تمامًا", "aspect_ratio"),
        UtilityTool("img_03", "crop_rotate_flip", PillarCategory.IMAGES, "Crop, Rotate & Flip", "قص وتدوير وقلب الصور", "Crop, rotate, and flip with confidence", "اقصص ودوّر واقلِب بثقة", "crop"),
        UtilityTool("img_04", "compress_images", PillarCategory.IMAGES, "Compress Images", "ضغط الصور", "Smaller images, same great look", "صور أصغر بنفس المظهر الرائع", "compress"),
        UtilityTool("img_exif", "img_exif", PillarCategory.IMAGES, "EXIF Inspector", "فاحص EXIF", "See what's hidden in your photos — and remove it", "اكتشف ما تخفيه صورك — وأزله", "info"),
        UtilityTool("img_watermark", "img_watermark", PillarCategory.IMAGES, "Watermark Images", "علامة مائية للصور", "Protect your images with your mark", "احمِ صورك بعلامتك", "approval"),
        UtilityTool("img_join", "img_join", PillarCategory.IMAGES, "Join / Collage", "دمج الصور", "Build beautiful collages in minutes", "أنشئ كولاجات جميلة في دقائق", "grid_view"),
        UtilityTool("img_adjust", "img_adjust", PillarCategory.IMAGES, "Adjust & Pad", "ضبط وحشو", "Fine-tune light, color, and padding", "اضبط الإضاءة والألوان والحشو بدقة", "tune"),
        UtilityTool("img_target", "img_target", PillarCategory.IMAGES, "Target Size", "حجم مستهدف", "Hit an exact file size, every time", "اصل إلى حجم ملف دقيق في كل مرة", "compress"),
        UtilityTool("img_compare", "img_compare", PillarCategory.IMAGES, "Compare Images", "مقارنة الصور", "Spot the difference, side by side", "اكتشف الفروقات جنبًا إلى جنب", "compare"),
        UtilityTool("img_stitch", "img_stitch", PillarCategory.IMAGES, "Stitch Screenshots", "خياطة لقطات", "Stitch screenshots into one long shot", "ادمج لقطات الشاشة في صورة طويلة واحدة", "view_agenda"),

        UtilityTool("dl_media", "media_download", PillarCategory.DOWNLOADS, "Video, Audio & Media Downloader", "تنزيل الفيديو والصوت من جميع المنصات", "Download video and audio from the platforms you love", "نزّل الفيديو والصوت من المنصات التي تحبها", "download"),
        UtilityTool("dl_02", "add_single_url", PillarCategory.DOWNLOADS, "New Direct Download", "تنزيل مباشر وGoogle Drive", "Grab any direct file or Drive link, fast", "احصل على أي ملف مباشر أو رابط Drive بسرعة", "add_link"),
        UtilityTool("dl_03", "batch_url_intake", PillarCategory.DOWNLOADS, "Batch URL Intake", "إدخال دفعة روابط", "Paste a whole list of links — we'll handle them all", "الصق قائمة روابط كاملة — وسنتولى الباقي", "download"),
        UtilityTool("dl_settings", "settings_overview", PillarCategory.DOWNLOADS, "Download Settings", "إعدادات التنزيل", "Tune downloads and where they land", "اضبط التنزيلات ومكان حفظها", "tune"),

        // File Tools
        UtilityTool("file_02", "folder_explorer", PillarCategory.FILES, "Browse Storage", "تصفح التخزين", "See everything Jumlo manages, in one place", "شاهد كل ما يديره جملو في مكان واحد", "folder_open"),
        UtilityTool("file_07", "create_zip", PillarCategory.FILES, "Create ZIP Archive", "إنشاء أرشيف ZIP", "Bundle files into one tidy ZIP", "اجمع الملفات في ZIP واحد مرتب", "folder_zip"),
        UtilityTool("file_08", "extract_zip", PillarCategory.FILES, "Extract ZIP Archive", "فك ضغط ZIP", "Unzip anything, safely", "فك ضغط أي ملف بأمان", "unarchive"),
        UtilityTool("file_encrypted_zip", "encrypted_zip", PillarCategory.FILES, "Encrypted ZIP", "ZIP مشفر", "ZIPs locked with AES encryption", "ملفات ZIP مقفلة بتشفير AES", "lock"),
        UtilityTool("file_batch_rename", "batch_rename", PillarCategory.FILES, "Batch Rename (copies)", "إعادة تسمية دفعية", "Rename hundreds of files in one go", "أعد تسمية مئات الملفات دفعة واحدة", "drive_file_rename_outline"),
        UtilityTool("file_duplicates", "duplicates", PillarCategory.FILES, "Duplicate Finder", "كاشف التكرار", "Find exact duplicates and free up space", "اعثر على الملفات المكررة تمامًا ووفّر مساحة", "copy_all"),
        UtilityTool("file_trash", "trash", PillarCategory.FILES, "Managed Trash Info", "سلة مُدارة", "Deleted files stay recoverable", "الملفات المحذوفة تبقى قابلة للاسترداد", "delete"),
        UtilityTool("file_drive", "google_drive", PillarCategory.FILES, "Google Drive Import / Export", "استيراد وتصدير Google Drive", "Move files to and from Google Drive", "انقل الملفات من وإلى Google Drive", "cloud")
    )
    val tools get() = allTools + UtilityTool("sha256","sha256",PillarCategory.FILES,"SHA-256 verification","التحقق من بصمة SHA-256","Verify any file's fingerprint in seconds","تحقق من بصمة أي ملف في ثوانٍ","verified")
    fun availability(id:String) = CapabilityPolicy.availability(id)
    fun visible(id:String):Boolean = availability(id).visible
    fun enabled(id:String):Boolean = availability(id).enabled
    fun canProcess(id:String):Boolean = CapabilityPolicy.canProcess(id)
    fun reason(id:String):String = availability(id).reason.orEmpty()
}
