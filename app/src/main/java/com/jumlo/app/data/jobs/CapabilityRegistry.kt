package com.jumlo.app.data.jobs
import com.jumlo.app.core.CapabilityPolicy
import com.jumlo.app.model.*

/** Catalog and common standard availability policy. Availability does not certify device qualification. */
object CapabilityRegistry {
    val allTools: List<UtilityTool> = listOf(
        // PDF Tools
        UtilityTool("pdf_02", "images_to_pdf", PillarCategory.PDF, "Images to PDF", "صور إلى PDF", "Compile photos into clean document", "تجميع فوري للصور في مستند PDF", "picture_as_pdf"),
        UtilityTool("pdf_03", "merge_pdf", PillarCategory.PDF, "Merge PDF", "دمج ملفات PDF", "Combine multiple PDF files & ranges", "دمج عدة ملفات PDF بدون فقد جودة", "file_copy"),
        UtilityTool("pdf_05", "split_pdf", PillarCategory.PDF, "Split PDF", "تقسيم مستند PDF", "Split by page count or custom ranges", "تقسيم حسب عدد الصفحات أو النطاقات", "content_cut"),
        UtilityTool("pdf_04", "organize_pages", PillarCategory.PDF, "Organize Pages", "ترتيب صفحات PDF", "Reorder, rotate, or delete pages", "إعادة ترتيب أو تدوير أو حذف الصفحات", "dashboard"),
        UtilityTool("pdf_06", "extract_pages", PillarCategory.PDF, "Extract Pages", "استخراج صفحات PDF", "Export selected page ranges", "تصدير نطاقات صفحات محددة", "file_open"),
        UtilityTool("pdf_07", "pdf_to_images", PillarCategory.PDF, "PDF to Images", "تحويل PDF إلى صور", "Export pages as JPG or PNG", "تصدير الصفحات بصيغة JPG أو PNG", "photo_library"),
        UtilityTool("pdf_01", "pdf_viewer", PillarCategory.PDF, "View PDF", "عارض PDF", "Inspect and read local documents", "فحص وقراءة المستندات المحلية", "visibility"),
        UtilityTool("pdf_compress", "pdf_compress", PillarCategory.PDF, "Compress PDF", "ضغط PDF", "Downscale images, keep text selectable", "تصغير الصور مع بقاء النص", "compress"),
        UtilityTool("pdf_crop", "pdf_crop", PillarCategory.PDF, "Crop PDF Pages", "قص صفحات PDF", "Set crop box per page range", "تعيين إطار القص", "crop"),
        UtilityTool("pdf_annotate", "pdf_annotate", PillarCategory.PDF, "Annotate PDF", "تعليقات PDF", "Overlay text or note annotation", "نص أو ملاحظة", "edit"),
        UtilityTool("pdf_stamp", "pdf_stamp", PillarCategory.PDF, "Watermark & Page Numbers", "علامة مائية وترقيم", "Stamp text and page numbers", "ختم نص وترقيم الصفحات", "approval"),
        UtilityTool("pdf_signature", "pdf_signature", PillarCategory.PDF, "Sign PDF", "توقيع PDF", "Place drawn or imported signature", "وضع توقيع مرسوم أو مستورد", "draw"),
        UtilityTool("pdf_image", "pdf_image", PillarCategory.PDF, "Add Image to PDF", "إضافة صورة إلى PDF", "Drag, place and resize a photo or logo", "اسحب الصورة أو الشعار وحدد مكانه وحجمه", "add_photo_alternate"),
        UtilityTool("pdf_protect", "pdf_protect", PillarCategory.PDF, "Password Protect PDF", "حماية PDF بكلمة مرور", "Add or remove standard password", "إضافة أو إزالة كلمة المرور", "lock"),
        UtilityTool("pdf_metadata", "pdf_metadata", PillarCategory.PDF, "PDF Metadata", "بيانات PDF", "Edit title, author, subject", "تحرير العنوان والمؤلف", "info"),
        UtilityTool("pdf_forms", "pdf_forms", PillarCategory.PDF, "Fill PDF Form", "تعبئة نموذج PDF", "Fill AcroForm fields, optional flatten", "تعبئة الحقول مع تسطيح اختياري", "assignment"),
        UtilityTool("pdf_compare", "pdf_compare", PillarCategory.PDF, "Compare PDFs", "مقارنة PDF", "Text comparison of two PDFs", "مقارنة نصية", "compare"),
        UtilityTool("pdf_redact", "pdf_redact", PillarCategory.PDF, "Redact PDF", "تنقيح PDF", "Truly remove content in a region", "إزالة فعلية للمحتوى", "visibility_off"),
        UtilityTool("pdf_ocr", "pdf_ocr", PillarCategory.PDF, "OCR to Searchable PDF / Text", "تعرف ضوئي OCR", "On-device OCR, searchable PDF + .txt", "نص قابل للبحث", "text_fields"),
        UtilityTool("scan_doc", "scan_doc", PillarCategory.PDF, "Scan Document", "مسح مستند", "Camera capture with perspective fix", "تصوير وتصحيح المنظور", "document_scanner"),
        UtilityTool("webpage_pdf", "webpage_pdf", PillarCategory.PDF, "Webpage to PDF", "صفحة ويب إلى PDF", "Render a public webpage to PDF", "تحويل صفحة ويب", "language"),

        // Image Tools
        UtilityTool("img_01", "convert_images", PillarCategory.IMAGES, "Convert Images", "تحويل الصور", "Batch convert JPG, PNG, WebP", "تحويل دفعات WebP, PNG, JPG", "photo_library"),
        UtilityTool("img_02", "resize_images", PillarCategory.IMAGES, "Resize Images", "تغيير أبعاد الصور", "Scale by pixels or percentage", "تحجيم بالبكسل أو النسبة المئوية", "aspect_ratio"),
        UtilityTool("img_03", "crop_rotate_flip", PillarCategory.IMAGES, "Crop, Rotate & Flip", "قص وتدوير وقلب الصور", "Standard aspect ratios & orientation", "نسب أبعاد قياسية وتعديل الاتجاه", "crop"),
        UtilityTool("img_04", "compress_images", PillarCategory.IMAGES, "Compress Images", "ضغط الصور", "Reduce file size with visual quality", "تقليل حجم الملف مع الحفاظ على الجودة", "compress"),
        UtilityTool("img_exif", "img_exif", PillarCategory.IMAGES, "EXIF Inspector", "فاحص EXIF", "View and selectively strip metadata", "عرض وإزالة البيانات الوصفية", "info"),
        UtilityTool("img_watermark", "img_watermark", PillarCategory.IMAGES, "Watermark Images", "علامة مائية للصور", "Text or logo watermark", "نص أو شعار", "approval"),
        UtilityTool("img_join", "img_join", PillarCategory.IMAGES, "Join / Collage", "دمج الصور", "Grid or strip collage", "شبكة أو شريط", "grid_view"),
        UtilityTool("img_adjust", "img_adjust", PillarCategory.IMAGES, "Adjust & Pad", "ضبط وحشو", "Brightness, contrast, saturation, padding", "سطوع وتباين وحشو", "tune"),
        UtilityTool("img_target", "img_target", PillarCategory.IMAGES, "Target Size", "حجم مستهدف", "Compress toward a byte target", "ضغط نحو حجم محدد", "compress"),
        UtilityTool("img_compare", "img_compare", PillarCategory.IMAGES, "Compare Images", "مقارنة الصور", "Dimension and difference report", "تقرير فروقات", "compare"),
        UtilityTool("img_stitch", "img_stitch", PillarCategory.IMAGES, "Stitch Screenshots", "خياطة لقطات", "Vertical screenshot stitching", "دمج عمودي للقطات", "view_agenda"),

        // Download Tools
        UtilityTool("dl_media", "media_download", PillarCategory.DOWNLOADS, "Direct Video, Audio & File Downloads", "تنزيل الفيديو والصوت والملفات المباشرة", "Direct media files, original quality, PDF, images & Google Drive", "روابط وسائط مباشرة بالجودة الأصلية وملفات PDF والصور وDrive", "download"),
        UtilityTool("dl_02", "add_single_url", PillarCategory.DOWNLOADS, "New Direct Download", "تنزيل مباشر وGoogle Drive", "Download files from direct URLs & Google Drive", "لصق رابط ملف مباشر أو Google Drive", "add_link"),
        UtilityTool("dl_03", "batch_url_intake", PillarCategory.DOWNLOADS, "Batch URL Intake", "إدخال دفعة روابط", "Parse & validate multi-line HTTP/HTTPS links", "تحليل وفحص الروابط المتعددة", "download"),
        UtilityTool("dl_settings", "settings_overview", PillarCategory.DOWNLOADS, "Download Settings", "إعدادات التنزيل", "Network preferences & storage destination", "تفضيلات الشبكة ومجلد الحفظ", "tune"),

        // File Tools
        UtilityTool("file_02", "folder_explorer", PillarCategory.FILES, "Browse Storage", "تصفح التخزين", "Inspect internal & granted SAF folders", "فحص المجلدات الداخلية والممنوحة", "folder_open"),
        UtilityTool("file_07", "create_zip", PillarCategory.FILES, "Create ZIP Archive", "إنشاء أرشيف ZIP", "Package files into compressed archive", "ضغط الملفات في أرشيف محلي", "folder_zip"),
        UtilityTool("file_08", "extract_zip", PillarCategory.FILES, "Extract ZIP Archive", "فك ضغط ZIP", "Decompress local ZIP archives", "استخراج ملفات أرشيف ZIP بأمان", "unarchive"),
        UtilityTool("file_encrypted_zip", "encrypted_zip", PillarCategory.FILES, "Encrypted ZIP", "ZIP مشفر", "AES-encrypted ZIP create/extract", "إنشاء وفك ZIP مشفر", "lock"),
        UtilityTool("file_batch_rename", "batch_rename", PillarCategory.FILES, "Batch Rename (copies)", "إعادة تسمية دفعية", "Preview pattern, publish renamed copies", "معاينة ونشر نسخ", "drive_file_rename_outline"),
        UtilityTool("file_duplicates", "duplicates", PillarCategory.FILES, "Duplicate Finder", "كاشف التكرار", "SHA-256 exact duplicate report", "تقرير تكرار بالبصمة", "copy_all"),
        UtilityTool("file_trash", "trash", PillarCategory.FILES, "Managed Trash Info", "سلة مُدارة", "Outputs are kept recoverable; see Files", "المخرجات قابلة للاسترداد", "delete"),
        UtilityTool("file_drive", "google_drive", PillarCategory.FILES, "Google Drive Import / Export", "استيراد وتصدير Google Drive", "Import or export via official Drive v3 API", "استيراد وتصدير عبر واجهة Drive الرسمية", "cloud"),
        UtilityTool("xf_05", "secure_vault", PillarCategory.VAULT, "Private Vault (unavailable)", "الخزنة المشفرة (AES-256)", "Unavailable: cryptographic backend not qualified", "تشفير الأجهزة المحلي AES-256-GCM", "enhanced_encryption", isExpansionConcept = true, conceptCode = "XF-05")
    )
    val tools get() = allTools + UtilityTool("sha256","sha256",PillarCategory.FILES,"SHA-256 verification","التحقق من بصمة SHA-256","Compute or compare actual file digests","حساب ومقارنة البصمة الرقمية للملفات","verified")
    fun availability(id:String) = CapabilityPolicy.availability(id)
    fun visible(id:String):Boolean = availability(id).visible
    fun enabled(id:String):Boolean = availability(id).enabled
    fun canProcess(id:String):Boolean = CapabilityPolicy.canProcess(id)
    fun reason(id:String):String = availability(id).reason.orEmpty()
}
