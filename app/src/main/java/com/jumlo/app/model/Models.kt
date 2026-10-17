package com.jumlo.app.model


enum class PillarCategory(val labelEn: String, val labelAr: String) {
    PDF("PDF", "ملفات PDF"),
    IMAGES("Images", "أدوات الصور"),
    DOWNLOADS("Downloads", "مدير التنزيلات"),
    FILES("Files", "متصفح الملفات"),
    VAULT("Vault", "الخزنة المشفرة")
}

enum class ThemeMode(val labelEn: String, val labelAr: String, val subtitleEn: String, val subtitleAr: String) {
    SYSTEM("System Default", "افتراضي النظام", "Follows Android OS setting", "يتبع إعدادات نظام أندرويد"),
    LIGHT("Light Theme", "المظهر الفاتح", "Warm neutrals & crisp contrast", "ألوان دافئة وتباين واضح"),
    DARK("Dark Theme", "المظهر الداكن", "Near-black #0B0B0D & deep containers", "أسود عميق #0B0B0D وحاويات مريحة للعين")
}

enum class AppLanguage(val code: String, val displayName: String, val nativeSubtitle: String, val isRtl: Boolean) {
    ENGLISH("en-US", "English (US)", "System default layout", false),
    ARABIC("ar", "العربية (Arabic)", "العربية - واجهة كاملة من اليمين لليسان", true)
}

enum class DownloadQuality { BALANCED, BEST, DATA_SAVER }
enum class FileViewMode { LIST, GRID, GALLERY }
enum class HapticIntensity { OFF, LIGHT, MEDIUM, STRONG }

enum class DockTab(val route: String, val labelEn: String, val labelAr: String) {
    HOME("home", "Home", "الرئيسية"),
    TOOLS("tools_catalog", "Tools", "الأدوات"),
    DOWNLOADS("downloads", "Downloads", "التنزيلات"),
    FILES("files_overview", "Files", "الملفات"),
    SETTINGS("settings", "Settings", "الإعدادات")
}

enum class CollisionPolicy(val labelEn: String, val labelAr: String) {
    KEEP_BOTH("Keep both (+1)", "الاحتفاظ بالاثنين (+1)"),
    SKIP("Skip", "تخطي"),
    REPLACE("Replace file", "استبدال الملف")
}

enum class ImageFormat(val ext: String, val mime: String, val labelEn: String, val descEn: String, val descAr: String) {
    JPG("jpg", "image/jpeg", "JPG", "Compressed", "ضغط متوافق"),
    PNG("png", "image/png", "PNG", "Lossless", "بدون فقدان"),
    WEBP("webp", "image/webp", "WebP", "Ultra-light", "فائق الكفاءة")
}

enum class MatteColorOption(val labelEn: String, val labelAr: String, val hex: String, val argb: Int) {
    WHITE("White (Default)", "أبيض قياسي", "#FFFFFF", 0xFFFFFFFF.toInt()),
    BLACK("Black", "أسود داكن", "#1B1B1F", 0xFF1B1B1F.toInt()),
    CUSTOM("Custom Tint", "لون مخصص", "#FAFAF9", 0xFFFAFAF9.toInt())
}

enum class CompressionPreset(val labelEn: String, val labelAr: String, val quality: Int, val descEn: String) {
    BALANCED("Balanced", "متوازن", 80, "~80% Quality · Recommended for photos"),
    HIGH_QUALITY("High Quality", "جودة قصوى", 90, "90% Quality · Minimal compression"),
    MAX_REDUCTION("Max Reduction", "حجم أصغر", 60, "60% Quality · Compact web sharing"),
    CUSTOM("Custom", "مخصص", 80, "Slider (1-100) · Fine-grained control")
}

enum class ResizeMode(val labelEn: String, val labelAr: String) {
    LONGEST_EDGE("Longest Edge", "الحافة الأطول"),
    WIDTH_HEIGHT("Width / Height", "العرض / الارتفاع"),
    BOUNDING_BOX("Bounding Box", "مربع الإحاطة"),
    PERCENTAGE("Percentage", "النسبة المئوية")
}

enum class FittingStrategy(val labelEn: String, val descEn: String) {
    FIT_WITHIN("Fit within", "Preserves whole picture"),
    FILL_CROP("Fill & crop", "No borders, loses edges"),
    PAD_MATTE("Pad with matte", "Solid or blur letterbox"),
    STRETCH("Stretch", "Distorts proportions")
}

enum class AspectRatioPreset(val labelEn: String, val ratioX: Float?, val ratioY: Float?) {
    FREE("Free", null, null),
    ORIGINAL("Original (3:4)", 3f, 4f),
    SQUARE("1:1 Square", 1f, 1f),
    FOUR_THREE("4:3", 4f, 3f),
    SIXTEEN_NINE("16:9", 16f, 9f),
    CUSTOM("Custom", 4f, 5f)
}

enum class PdfPaperSize(val labelEn: String, val labelAr: String, val widthMm: Int, val heightMm: Int, val widthPt: Int, val heightPt: Int) {
    A4("A4", "A4 افتراضي", 210, 297, 595, 842),
    A5("A5", "A5 كتيب", 148, 210, 420, 595),
    LETTER("Letter", "Letter رسالة", 216, 279, 612, 792),
    CUSTOM("Custom", "مخصص", 210, 297, 595, 842)
}

enum class PdfOrientation(val labelEn: String, val labelAr: String) {
    AUTO("Auto", "تلقائي ذكي"),
    PORTRAIT("Portrait", "عمودي"),
    LANDSCAPING("Landscape", "أفقي")
}

enum class PdfSplitMode(val labelEn: String, val labelAr: String) {
    EVERY_N_PAGES("Every N pages", "كل N صفحات"),
    EACH_PAGE("Each page (24)", "كل صفحة مستقلة"),
    CUSTOM("Custom", "نطاق مخصص")
}

enum class ZipCompressionLevel(val labelEn: String, val subEn: String, val deflateLevel: Int) {
    FASTEST("Fastest", "Store/Low", 1),
    DEFAULT("Default", "Balanced", 6),
    MAXIMUM("Maximum", "Best effort", 9)
}

data class UtilityTool(
    val id: String,
    val route: String,
    val pillar: PillarCategory,
    val titleEn: String,
    val titleAr: String,
    val subtitleEn: String,
    val subtitleAr: String,
    val iconName: String,
    val isExpansionConcept: Boolean = false,
    val conceptCode: String? = null
)

data class ManagedFileItem(
    val id: String,
    val name: String,
    val path: String,
    val folder: String, // "My files / Results", "My files / Downloads", "My files / Drafts"
    val extension: String, // "pdf", "webp", "jpg", "png", "zip", "heic", "iso", "tar.gz"
    val mimeType: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val pageCount: Int? = null,
    val dimensions: String? = null,
    val itemCount: Int? = null,
    val modifiedLabelEn: String,
    val modifiedLabelAr: String,
    val isDraft: Boolean = false,
    val isArchive: Boolean = false,
    val isSelected: Boolean = false,
    val spaceSavedPercent: Int? = null,
    val uncompressedBytes: Long? = null
)

data class PdfPageItem(
    val pageNumber: Int, // 1-indexed
    val aspectLabel: String = "A4",
    val rotationDegrees: Int = 0,
    val isSelected: Boolean = false,
    val originalPageNumber: Int = pageNumber,
    val sizeEstimateKb: Int = 0,
    val snippetTitle: String = ""
)

data class ImageQueueItem(
    val id: String,
    val name: String,
    val format: String, // PNG, HEIC, JPG, WEBP
    val widthPx: Int,
    val heightPx: Int,
    val originalSizeBytes: Long,
    val formattedOriginalSize: String,
    val estimatedCompressedBytes: Long,
    val formattedEstimatedSize: String,
    val reductionPercent: Int,
    val hasAlpha: Boolean = false,
    val customOverrideQuality: Int? = null,
    val status: BatchItemStatus = BatchItemStatus.READY,
    val errorReason: String? = null
)

enum class BatchItemStatus {
    READY, SUCCEEDED, FAILED
}

enum class DownloadStatus(val labelEn: String, val labelAr: String) {
    ACTIVE("Active", "نشط"),
    PAUSED_WIFI("Waiting for Wi-Fi", "بانتظار Wi-Fi"),
    PAUSED_USER("Paused", "متوقف مؤقتاً"),
    COMPLETED("Completed", "مكتمل"),
    FAILED("Needs attention", "يتطلب الانتباه")
}

data class ChunkStreamProgress(
    val chunkIndex: Int, // 1..4
    val progressPercent: Int,
    val startByte: Long,
    val endByte: Long,
    val currentByte: Long
)

data class DownloadTaskItem(
    val id: String,
    val fileName: String,
    val extension: String,
    val domain: String,
    val fullUrl: String,
    val mimeType: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedMbps: Float,
    val avgSpeedMbps: Float = 0f,
    val etaLabel: String,
    val status: DownloadStatus,
    val supportsRange: Boolean = false,
    val tlsInfo: String = "Unknown",
    val eTag: String = "",
    val destinationPath: String = "My files / Downloads",
    val wifiOnlyGuard: Boolean = true,
    val chunks: List<ChunkStreamProgress> = emptyList(),
    val startedTimeLabel: String = ""
)

data class ParsedUrlCandidate(
    val lineNumber: Int,
    val rawUrl: String,
    val fileName: String,
    val estimatedSizeLabel: String,
    val status: UrlValidationStatus,
    val statusReason: String,
    val keepDuplicate: Boolean = false
)

enum class UrlValidationStatus {
    VALID, DUPLICATE, UNSUPPORTED_PROTOCOL, MALFORMED
}

data class VaultEncryptedItem(
    val id: String,
    val fileName: String,
    val category: String, // "IDENTITY" or "DOCUMENT"
    val badgeText: String, // "ARMORED", "GCM-TAGGED", "ENCRYPTED"
    val subtitle: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val ivBase64: String = "",
    val cipherPath: String = ""
)

data class ActiveEngineOperation(
    val id: String,
    val titleEn: String,
    val titleAr: String,
    val sourceFileName: String,
    val sourceMetaEn: String,
    val sourceMetaAr: String,
    val engineVersion: String = "",
    val progressPercent: Int = 0,
    val currentStageIndex: Int = 0, // 1..4
    val totalStages: Int = 0,
    val currentPart: Int = 0,
    val totalParts: Int = 0,
    val writtenBytesLabel: String = "",
    val projectedFootprintLabel: String = "",
    val etaLabelEn: String = "",
    val etaLabelAr: String = "",
    val destinationPath: String = "",
    val isRunning: Boolean = false,
    val isCancelled: Boolean = false
)

data class StorageBreakdown(
    val totalAllocatedBytes: Long = 0,
    val managedResultsBytes: Long = 0,
    val cachedInboundBytes: Long = 0,
    val workingDraftsBytes: Long = 0,
    val engineCacheBytes: Long = 0,
    val myFilesCount: Int = 0,
    val downloadsCount: Int = 0,
    val pdfCount: Int = 0,
    val imageCount: Int = 0,
    val archiveCount: Int = 0
)

data class AppSettingsState(
    val hasCompletedOnboarding: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val respectReducedMotion: Boolean = false,
    val hapticFeedbackOnCommit: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.LIGHT,
    val soundsEnabled: Boolean = true,
    val reorderHandleHaptics: Boolean = true,
    val defaultDestinationFolder: String = "My files / Results",
    val downloadDestinationFolder: String = "Download/Jevlo",
    val maxConcurrentDownloads: Int = 2,
    val downloadQuality: DownloadQuality = DownloadQuality.BALANCED,
    val audioOnlyDefault: Boolean = false,
    val fileViewMode: FileViewMode = FileViewMode.GRID,
    val accentColor: String = "VIOLET",
    val fontScale: String = "NORMAL",
    val wifiOnlyDefault: Boolean = false,
    val stripExifByDefault: Boolean = true,
    val notificationsGranted: Boolean = false,
    val vaultAutoLockSeconds: Int = 120,
    val mediaTermsAccepted: Boolean = false,
    val quickActionIds: List<String> = listOf("pdf_02", "pdf_03", "img_02", "dl_media"),
    val recentToolIds: List<String> = emptyList(),
    val mostUsedToolIds: List<String> = emptyList(),
    /** The initial ViewModel placeholder is never presented as a saved preference. */
    val isLoaded: Boolean = false
)
