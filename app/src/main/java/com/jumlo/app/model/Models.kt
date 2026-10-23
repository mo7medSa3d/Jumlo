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
    DARK("Dark Theme", "المظهر الداكن", "Pure black OLED background", "خلفية سوداء خالصة لشاشات OLED")
}

enum class AppLanguage(val code: String, val displayName: String, val nativeSubtitle: String, val isRtl: Boolean) {
    ENGLISH("en-US", "English (US)", "System default layout", false),
    ARABIC("ar", "العربية (Arabic)", "العربية - واجهة كاملة من اليمين لليسار", true)
}

enum class FileViewMode { LIST, GRID;
    companion object {
        /** Migrate the removed gallery view without losing other preferences. */
        fun fromStored(value: String?): FileViewMode = entries.firstOrNull { it.name == value } ?: GRID
    }
}
enum class HapticIntensity { OFF, LIGHT, MEDIUM, STRONG }

enum class DockTab(val route: String, val labelEn: String, val labelAr: String) {
    HOME("home", "Home", "الرئيسية"),
    TOOLS("tools", "Tools", "الأدوات"),
    DOWNLOADS("downloads", "Downloads", "التنزيلات"),
    PRINT("print", "Print", "طباعة"),
    FILES("files", "Files", "الملفات"),
    SETTINGS("settings", "Settings", "الإعدادات")
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
    val folder: String,
    val extension: String,
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

data class AppSettingsState(
    val hasCompletedOnboarding: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val respectReducedMotion: Boolean = false,
    val hapticFeedbackOnCommit: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.LIGHT,
    val soundsEnabled: Boolean = true,
    val downloadDestinationFolder: String = "Download/Jumlo",
    val maxConcurrentDownloads: Int = 2,
    val defaultVideoQuality: String = "1080",
    val defaultAudioQuality: Int = 192,
    val fileViewMode: FileViewMode = FileViewMode.GRID,
    val accentColor: String = "BLUE",
    val fontScale: String = "NORMAL",
    val wifiOnlyDefault: Boolean = false,
    val notificationsGranted: Boolean = false,
    val quickActionIds: List<String> = listOf("pdf_02", "pdf_03", "img_02", "dl_media"),
    val recentToolIds: List<String> = emptyList(),
    val mostUsedToolIds: List<String> = emptyList(),
    /** Complete count-based order for the flat catalogue; no usage is synthesized. */
    val rankedToolIds: List<String> = emptyList(),
    /** Local only; UUIDs of outputs pinned by the user, not cloud identities. */
    val favoriteOutputIds: Set<String> = emptySet(),
    /** The initial ViewModel placeholder is never presented as a saved preference. */
    val isLoaded: Boolean = false,
    /** User-facing document names; verified output payloads remain immutable. */
    val outputNames: Map<String,String> = emptyMap(),
    /** The extraction service the downloader may use; empty means direct links only. */
    val mediaServiceUrl: String = "",
    /** The shared secret of the downloader service, when it asks for one. */
    val mediaServiceToken: String = ""
)
