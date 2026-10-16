package com.universalutilities.app.data.jobs
import com.universalutilities.app.BuildConfig
import com.universalutilities.app.core.Capabilities
import com.universalutilities.app.model.*

/** Release gates stay shut until the Android/device qualification matrix is recorded. */
object CapabilityRegistry {
    val allTools: List<UtilityTool> = listOf(
        // 7 Core PDF Tools + 1 Expansion Concept
        UtilityTool("pdf_02", "images_to_pdf", PillarCategory.PDF, "Images to PDF", "صور إلى PDF", "Compile photos into clean document", "تجميع فوري للصور في مستند PDF", "picture_as_pdf"),
        UtilityTool("pdf_03", "merge_pdf", PillarCategory.PDF, "Merge PDF", "دمج ملفات PDF", "Combine multiple PDF files & ranges", "دمج عدة ملفات PDF بدون فقد جودة", "file_copy"),
        UtilityTool("pdf_05", "split_pdf", PillarCategory.PDF, "Split PDF", "تقسيم مستند PDF", "Split by page count or custom ranges", "تقسيم حسب عدد الصفحات أو النطاقات", "content_cut"),
        UtilityTool("pdf_04", "organize_pages", PillarCategory.PDF, "Organize Pages", "ترتيب صفحات PDF", "Reorder, rotate, or delete pages", "إعادة ترتيب أو تدوير أو حذف الصفحات", "dashboard"),
        UtilityTool("pdf_06", "extract_pages", PillarCategory.PDF, "Extract Pages", "استخراج صفحات PDF", "Export selected page ranges", "تصدير نطاقات صفحات محددة", "file_open"),
        UtilityTool("pdf_07", "pdf_to_images", PillarCategory.PDF, "PDF to Images", "تحويل PDF إلى صور", "Export pages as JPG or PNG", "تصدير الصفحات بصيغة JPG أو PNG", "photo_library"),
        UtilityTool("pdf_01", "pdf_viewer", PillarCategory.PDF, "View PDF", "عارض PDF", "Inspect and read local documents", "فحص وقراءة المستندات المحلية", "visibility"),
        UtilityTool("xp_01_04", "pdf_expansion", PillarCategory.PDF, "PDF Compression, Watermark & Numbering", "ضغط PDF والعلامة المائية والترقيم", "Vector preservation, 150 DPI resample & dynamic stamps", "ضغط المتجهات والختم الديناميكي", "compress", isExpansionConcept = true, conceptCode = "XP-01 & XP-04"),

        // 4 Core Image Tools + 1 Expansion Concept
        UtilityTool("img_01", "convert_images", PillarCategory.IMAGES, "Convert Images", "تحويل الصور", "Batch convert JPG, PNG, WebP", "تحويل دفعات WebP, PNG, JPG", "photo_library"),
        UtilityTool("img_02", "resize_images", PillarCategory.IMAGES, "Resize Images", "تغيير أبعاد الصور", "Scale by pixels or percentage", "تحجيم بالبكسل أو النسبة المئوية", "aspect_ratio"),
        UtilityTool("img_03", "crop_rotate_flip", PillarCategory.IMAGES, "Crop, Rotate & Flip", "قص وتدوير وقلب الصور", "Standard aspect ratios & orientation", "نسب أبعاد قياسية وتعديل الاتجاه", "crop"),
        UtilityTool("img_04", "compress_images", PillarCategory.IMAGES, "Compress Images", "ضغط الصور", "Reduce file size with visual quality", "تقليل حجم الملف مع الحفاظ على الجودة", "compress"),
        UtilityTool("xi_02_03", "image_expansion", PillarCategory.IMAGES, "Image Collage & Watermark", "دمج الصور والعلامة المائية", "2×2 Grid, 3-Column Strip & RGBA watermark burn-in", "شبكة 2×2 ودمج العلامة المائية", "grid_view", isExpansionConcept = true, conceptCode = "XI-02 & XI-03"),

        UtilityTool("dl_media", "media_download", PillarCategory.DOWNLOADS, "Video & Audio Downloader", "\u062a\u0646\u0632\u064a\u0644 \u0627\u0644\u0641\u064a\u062f\u064a\u0648 \u0648\u0627\u0644\u0635\u0648\u062a", "Analyze real qualities, merge tracks, or save audio only", "\u0627\u062e\u062a\u064a\u0627\u0631 \u0627\u0644\u062c\u0648\u062f\u0629 \u0623\u0648 \u062a\u0646\u0632\u064a\u0644 \u0627\u0644\u0635\u0648\u062a \u0641\u0642\u0637", "download"),
        // Direct file downloads remain separate from media extraction.
        UtilityTool("dl_02", "add_single_url", PillarCategory.DOWNLOADS, "New Direct Download", "تنزيل جديد", "Download files from direct URLs", "لصق الرابط المباشر للتنزيل", "add_link"),
        UtilityTool("dl_03", "batch_url_intake", PillarCategory.DOWNLOADS, "Batch URL Intake", "إدخال دفعة روابط", "Parse & validate multi-line HTTP/HTTPS links", "تحليل وفحص الروابط المتعددة", "download"),
        UtilityTool("dl_settings", "settings_overview", PillarCategory.DOWNLOADS, "Download Settings", "إعدادات التنزيل", "Network preferences & storage destination", "تفضيلات الشبكة ومجلد الحفظ", "tune"),

        // 3 Core File Tools + 1 Expansion Vault Concept
        UtilityTool("file_02", "folder_explorer", PillarCategory.FILES, "Browse Storage", "تصفح التخزين", "Inspect internal & granted SAF folders", "فحص المجلدات الداخلية والممنوحة", "folder_open"),
        UtilityTool("file_07", "create_zip", PillarCategory.FILES, "Create ZIP Archive", "إنشاء أرشيف ZIP", "Package files into compressed archive", "ضغط الملفات في أرشيف محلي", "folder_zip"),
        UtilityTool("file_08", "extract_zip", PillarCategory.FILES, "Extract ZIP Archive", "فك ضغط ZIP", "Decompress local ZIP archives", "استخراج ملفات أرشيف ZIP بأمان", "unarchive"),
        UtilityTool("xf_05", "secure_vault", PillarCategory.VAULT, "Private Vault (unavailable)", "الخزنة المشفرة (AES-256)", "Unavailable: cryptographic backend not qualified", "تشفير الأجهزة المحلي AES-256-GCM", "enhanced_encryption", isExpansionConcept = true, conceptCode = "XF-05")
    )
    val tools get() = allTools + UtilityTool("sha256","sha256",PillarCategory.FILES,"SHA-256 verification","SHA-256","Compute or compare actual file digests","SHA-256","verified")
    fun enabled(id:String):Boolean = BuildConfig.DEBUG && Capabilities.isEnabled(id) && (id!="dl_media" || BuildConfig.EXTENDED_MEDIA)
    fun reason(id:String):String = when {
        id=="dl_media" && !BuildConfig.EXTENDED_MEDIA -> "MEDIA_BUILD_NOT_ENABLED"
        id=="xf_05" -> "VAULT_BACKEND_UNQUALIFIED"
        id.startsWith("pdf_") || id.startsWith("xp_") -> if(!Capabilities.isEnabled(id)) "STRUCTURAL_PDF_BACKEND_UNQUALIFIED" else "ANDROID_RELEASE_QUALIFICATION_PENDING"
        !Capabilities.isEnabled(id) -> "CAPABILITY_NOT_IMPLEMENTED"
        else -> "ANDROID_RELEASE_QUALIFICATION_PENDING"
    }
}
