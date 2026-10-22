package com.jumlo.app.data.jobs
import com.jumlo.app.core.CapabilityPolicy
import com.jumlo.app.model.*

/** Catalog and common standard availability policy. Availability does not certify device qualification. */
object CapabilityRegistry {
    val allTools: List<UtilityTool> = listOf(
        // PDF Tools
        UtilityTool("pdf_02", "images_to_pdf", PillarCategory.PDF, "Image to PDF", "صور إلى PDF", "Photos to PDF", "تجميع الصور في PDF", "picture_as_pdf"),
        UtilityTool("pdf_03", "merge_pdf", PillarCategory.PDF, "Merge PDF", "دمج PDF", "Combine PDFs", "دمج ملفات PDF", "file_copy"),
        UtilityTool("pdf_05", "split_pdf", PillarCategory.PDF, "Split PDF", "تقسيم PDF", "Split by pages", "تقسيم الصفحات", "content_cut"),
        UtilityTool("pdf_compress", "pdf_compress", PillarCategory.PDF, "Compress PDF", "ضغط PDF", "Reduce file size", "تقليل حجم الملف", "compress"),
        UtilityTool("pdf_to_docx", "pdf_to_docx", PillarCategory.PDF, "PDF to Word", "PDF إلى Word", "Editable .docx", "مستند وورد قابل للتعديل", "docx"),
        UtilityTool("pdf_to_pptx", "pdf_to_pptx", PillarCategory.PDF, "PDF to PowerPoint", "PDF إلى PowerPoint", "Slides .pptx", "عرض تقديمي بوربوينت", "pptx"),
        UtilityTool("pdf_ocr", "pdf_ocr", PillarCategory.PDF, "OCR Text", "تعرف ضوئي OCR", "Extract text", "استخراج النص الضوئي", "ocr"),
        UtilityTool("pdf_04", "organize_pages", PillarCategory.PDF, "Organize Pages", "ترتيب الصفحات", "Reorder & rotate", "ترتيب وتدوير الصفحات", "dashboard"),
        UtilityTool("pdf_06", "extract_pages", PillarCategory.PDF, "Extract Pages", "استخراج صفحات", "Export page range", "تصدير صفحات محددة", "file_open"),
        UtilityTool("pdf_07", "pdf_to_images", PillarCategory.PDF, "PDF to Images", "PDF إلى صور", "Export PNG/JPG", "تصدير الصفحات كصور", "photo_library"),
        UtilityTool("pdf_01", "pdf_viewer", PillarCategory.PDF, "PDF Viewer", "عارض PDF", "Read & zoom PDFs", "قراءة وتكبير المستندات", "visibility"),
        UtilityTool("pdf_crop", "pdf_crop", PillarCategory.PDF, "Crop PDF", "قص PDF", "Trim margins", "قص هوامش الصفحات", "crop"),
        UtilityTool("pdf_annotate", "pdf_annotate", PillarCategory.PDF, "Annotate PDF", "تعليقات PDF", "Markup & notes", "تظليل وملاحظات", "edit"),
        UtilityTool("pdf_stamp", "pdf_stamp", PillarCategory.PDF, "Watermark PDF", "علامة مائية", "Stamp & number", "ختم وترقيم الصفحات", "approval"),
        UtilityTool("pdf_signature", "pdf_signature", PillarCategory.PDF, "Sign PDF", "توقيع PDF", "Add signature", "وضع توقيع", "draw"),
        UtilityTool("pdf_image", "pdf_image", PillarCategory.PDF, "Add Image to PDF", "إضافة صورة لـ PDF", "Place image", "إدراج صورة في صفحة", "add_photo_alternate"),
        UtilityTool("pdf_protect", "pdf_protect", PillarCategory.PDF, "Protect PDF", "حماية PDF", "Lock or unlock", "قفل أو فتح بكلمة مرور", "lock"),
        UtilityTool("pdf_metadata", "pdf_metadata", PillarCategory.PDF, "PDF Metadata", "بيانات PDF", "Edit properties", "تحرير خصائص المستند", "info"),
        UtilityTool("pdf_clean", "pdf_clean", PillarCategory.PDF, "Clean Metadata", "تنظيف البيانات", "Remove metadata", "إزالة بيانات التتبع", "shield_lock"),
        UtilityTool("pdf_forms", "pdf_forms", PillarCategory.PDF, "Fill PDF Form", "تعبئة نموذج PDF", "Fill AcroForms", "تعبئة الحقول التفاعلية", "assignment"),
        UtilityTool("scan_doc", "scan_doc", PillarCategory.PDF, "Smart Scan", "المسح الذكي", "Multi-page scan", "مسح متعدد الصفحات", "document_scanner"),
        UtilityTool("webpage_pdf", "webpage_pdf", PillarCategory.PDF, "Webpage to PDF", "صفحة ويب إلى PDF", "Unavailable", "غير متاح", "language"),

        // Image Tools
        UtilityTool("img_01", "convert_images", PillarCategory.IMAGES, "Convert Images", "تحويل الصور", "JPG, PNG, WebP", "تحويل الصيغ", "photo_library"),
        UtilityTool("img_02", "resize_images", PillarCategory.IMAGES, "Resize Images", "تحجيم الصور", "Scale dimensions", "تغيير الأبعاد", "aspect_ratio"),
        UtilityTool("img_03", "crop_rotate_flip", PillarCategory.IMAGES, "Crop & Rotate", "قص وتدوير", "Aspect & flip", "قص وتدوير الصور", "crop"),
        UtilityTool("img_04", "compress_images", PillarCategory.IMAGES, "Compress Images", "ضغط الصور", "Shrink image size", "تقليل حجم الصور", "compress"),
        UtilityTool("img_exif", "img_exif", PillarCategory.IMAGES, "EXIF Clean", "تنظيف EXIF", "Strip metadata", "إزالة بيانات الصور", "info"),
        UtilityTool("img_watermark", "img_watermark", PillarCategory.IMAGES, "Watermark Image", "علامة مائية للصور", "Stamp text/logo", "إضافة نص أو شعار", "approval"),
        UtilityTool("img_join", "img_join", PillarCategory.IMAGES, "Collage", "دمج الصور", "Grid or strip", "شبكة أو شريط", "grid_view"),
        UtilityTool("img_adjust", "img_adjust", PillarCategory.IMAGES, "Adjust Image", "ضبط الصور", "Contrast & tone", "تباين وإضاءة", "tune"),
        UtilityTool("img_target", "img_target", PillarCategory.IMAGES, "Target Size", "حجم مستهدف", "Fit byte target", "ضغط لحجم محدد", "compress"),
        UtilityTool("img_compare", "img_compare", PillarCategory.IMAGES, "Compare Images", "مقارنة الصور", "Removed", "أزيلت", "compare"),
        UtilityTool("img_stitch", "img_stitch", PillarCategory.IMAGES, "Stitch Images", "خياطة لقطات", "Vertical stitch", "دمج عمودي", "view_agenda"),

        // Download Tools
        UtilityTool("dl_media", "media_download", PillarCategory.DOWNLOADS, "Media Downloader", "تنزيل الوسائط", "Video, audio & files", "فيديو وصوت وملفات", "download"),
        UtilityTool("dl_02", "add_single_url", PillarCategory.DOWNLOADS, "Direct Download", "تنزيل مباشر", "Direct & Drive URLs", "روابط مباشرة وDrive", "add_link"),
        UtilityTool("dl_03", "batch_url_intake", PillarCategory.DOWNLOADS, "Batch Download", "تنزيل دفعة روابط", "Multi-URL queue", "قائمة روابط متعددة", "download"),
        UtilityTool("dl_settings", "settings_overview", PillarCategory.DOWNLOADS, "Download Settings", "إعدادات التنزيل", "Quality & folder", "الجودة ومجلد الحفظ", "tune"),

        // File Tools
        UtilityTool("file_02", "folder_explorer", PillarCategory.FILES, "Browse Storage", "تصفح التخزين", "SAF folders", "المجلدات الممنوحة", "folder_open"),
        UtilityTool("file_07", "create_zip", PillarCategory.FILES, "Create ZIP", "إنشاء ZIP", "Compress archive", "ضغط في أرشيف ZIP", "folder_zip"),
        UtilityTool("file_08", "extract_zip", PillarCategory.FILES, "Extract ZIP", "فك ضغط ZIP", "Unpack archive", "استخراج ملفات ZIP", "unarchive"),
        UtilityTool("file_encrypted_zip", "encrypted_zip", PillarCategory.FILES, "Encrypted ZIP", "ZIP مشفر", "AES-256 archive", "أرشيف محمي بكلمة مرور", "lock"),
        UtilityTool("file_batch_rename", "batch_rename", PillarCategory.FILES, "Batch Rename", "إعادة تسمية", "Pattern rename", "تسمية دفعية بنمط", "drive_file_rename_outline"),
        UtilityTool("file_duplicates", "duplicates", PillarCategory.FILES, "Duplicate Finder", "كاشف التكرار", "SHA-256 match", "فحص الملفات المكررة", "copy_all"),
        UtilityTool("file_trash", "trash", PillarCategory.FILES, "Trash", "المهملات", "Removed", "أزيلت", "delete"),
        UtilityTool("file_drive", "google_drive", PillarCategory.FILES, "Google Drive", "Google Drive", "Unavailable", "غير متاح", "cloud"),
        UtilityTool("xf_05", "secure_vault", PillarCategory.VAULT, "Private Vault", "الخزنة المشفرة", "Unavailable", "غير متاح", "enhanced_encryption", isExpansionConcept = true, conceptCode = "XF-05")
    )
    val tools get() = allTools + UtilityTool("sha256","sha256",PillarCategory.FILES,"SHA-256 Check","بصمة SHA-256","Verify digest","التحقق من البصمة الرقمية","verified")
    fun availability(id:String) = CapabilityPolicy.availability(id)
    fun visible(id:String):Boolean = availability(id).visible
    fun enabled(id:String):Boolean = availability(id).enabled
    fun canProcess(id:String):Boolean = CapabilityPolicy.canProcess(id)
    fun reason(id:String):String = availability(id).reason.orEmpty()
}
