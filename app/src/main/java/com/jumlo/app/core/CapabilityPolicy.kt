package com.jumlo.app.core

/** A route is not automatically a job handler. Unknown IDs fail closed. */
enum class CapabilityKind { PROCESSING, NAVIGATION, UNAVAILABLE }
enum class CapabilityEvidence { SOURCE_IMPLEMENTED, HOST_CORE_TESTED, DEVICE_PENDING, SECURITY_PENDING, NOT_IMPLEMENTED }
data class CapabilityAvailability(
    val visible: Boolean,
    val enabled: Boolean,
    val reason: String?,
    val kind: CapabilityKind,
    val evidence: CapabilityEvidence
)

/**
 * Auditable standard-distribution policy, independent of DEBUG and build flavor.
 * Availability is not release qualification: Android/device evidence is still required
 * before distribution. Security/privacy-sensitive candidates remain blocked in every build.
 */
object CapabilityPolicy {
    private val navigation = setOf("pdf_01", "file_02", "dl_settings", "file_trash")
    private val hostCoreTested = setOf("file_07", "file_08", "sha256", "dl_02", "dl_03")
    private val processing = hostCoreTested + setOf(
        "file_duplicates", "file_batch_rename",
        "pdf_02", "pdf_07", "pdf_03", "pdf_04", "pdf_05", "pdf_06",
        "pdf_compress", "pdf_crop", "pdf_annotate", "pdf_stamp", "pdf_signature", "pdf_metadata", "pdf_forms", "pdf_compare",
        "img_01", "img_02", "img_03", "img_04", "img_exif", "img_watermark", "img_join", "img_adjust", "img_target", "img_compare", "img_stitch"
    )
    private val blocked = mapOf(
        "pdf_ocr" to "SEARCHABLE_OCR_INTEGRITY_QUALIFICATION_PENDING",
        "webpage_pdf" to "WEBPAGE_RENDER_SECURITY_QUALIFICATION_PENDING",
        "pdf_protect" to "PDF_PROTECTION_QUALIFICATION_PENDING",
        "pdf_redact" to "PDF_REDACTION_QUALIFICATION_PENDING",
        "file_encrypted_zip" to "ENCRYPTED_ZIP_QUALIFICATION_PENDING",
        "file_drive" to "DRIVE_PRIVACY_QUALIFICATION_PENDING",
        "dl_media" to "EXTENDED_MEDIA_QUALIFICATION_PENDING",
        "xf_05" to "VAULT_BACKEND_UNQUALIFIED"
    )
    fun availability(id: String): CapabilityAvailability = when {
        id in blocked -> CapabilityAvailability(false, false, blocked.getValue(id), CapabilityKind.UNAVAILABLE, CapabilityEvidence.SECURITY_PENDING)
        id in navigation -> CapabilityAvailability(true, true, null, CapabilityKind.NAVIGATION, CapabilityEvidence.DEVICE_PENDING)
        id in processing -> CapabilityAvailability(true, true, null, CapabilityKind.PROCESSING, if (id in hostCoreTested) CapabilityEvidence.HOST_CORE_TESTED else CapabilityEvidence.SOURCE_IMPLEMENTED)
        else -> CapabilityAvailability(false, false, "CAPABILITY_NOT_IMPLEMENTED", CapabilityKind.UNAVAILABLE, CapabilityEvidence.NOT_IMPLEMENTED)
    }
    fun canProcess(id: String): Boolean = availability(id).let { it.enabled && it.kind == CapabilityKind.PROCESSING }
}
