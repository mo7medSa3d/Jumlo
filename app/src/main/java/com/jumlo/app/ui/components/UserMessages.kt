package com.jumlo.app.ui.components

import androidx.annotation.StringRes
import com.jumlo.app.R

/** Stable engine codes are diagnostics; presentation always uses localized copy. */
@StringRes fun userMessageResource(code:String):Int=when {
    code=="DRAFT_SAVED" -> R.string.msg_draft_saved
    code in setOf("DRAFT_SAVE_FAILED","DRAFT_RESTORE_FAILED") -> R.string.msg_draft_failed
    code in setOf("SETTINGS_READ_FAILED","SETTINGS_SAVE_FAILED") -> R.string.msg_settings_failed
    code=="MOVED_TO_TRASH" -> R.string.msg_trashed
    code=="RESTORED_FROM_TRASH" -> R.string.msg_restored
    code=="TRASH_PURGED" -> R.string.msg_purged
    code=="CLEANUP_DONE" -> R.string.msg_cleanup_done
    code=="CLEANUP_FAILED" -> R.string.msg_cleanup_failed
    code=="EXPORT_VERIFIED" -> R.string.msg_export_verified
    code=="ERROR_LOG_EXPORTED" -> R.string.msg_log_exported
    code=="FILE_COPIED_VERIFIED" -> R.string.msg_copy_verified
    code=="FILE_MOVED_VERIFIED" -> R.string.msg_move_verified
    code=="COLLISION_SKIPPED" -> R.string.msg_collision_skipped
    code.startsWith("EXPORT_") -> R.string.msg_export_failed
    code=="UNSUPPORTED_SOCIAL_PLATFORM" -> R.string.msg_unsupported_social_platform
    code=="HTTP_401" || code=="LOGIN_REQUIRED" || code=="GOOGLE_DRIVE_TOKEN_REQUIRED" -> R.string.msg_http_401
    code=="HTTP_403" || code=="PRIVATE_CONTENT" -> R.string.msg_http_403
    code=="HTTP_404" || code=="REMOVED" -> R.string.msg_http_404
    code=="HTTP_429" || code=="RATE_LIMITED" -> R.string.msg_http_429
    code=="TOO_MANY_REDIRECTS" -> R.string.msg_too_many_redirects
    code=="INSECURE_REDIRECT" -> R.string.msg_insecure_redirect
    code=="DOWNLOAD_NOT_A_FILE" -> R.string.msg_download_not_a_file
    code=="NETWORK_TIMEOUT" -> R.string.msg_network_timeout
    code=="NETWORK_UNAVAILABLE" -> R.string.msg_network_unavailable
    code=="WIFI_REQUIRED" -> R.string.msg_wifi_required
    code=="UNMETERED_NETWORK_REQUIRED" -> R.string.msg_unmetered_required
    code in setOf("DISK_BUDGET_EXCEEDED","INSUFFICIENT_STORAGE") -> R.string.msg_insufficient_storage
    code=="LOW_MEMORY" -> R.string.msg_low_memory
    code=="PDF_ENCRYPTED" -> R.string.msg_pdf_encrypted
    code=="OWNER_PASSWORD_REQUIRED" -> R.string.msg_owner_password_required
    code=="ZIP_PASSWORD_REQUIRED" -> R.string.msg_zip_password_required
    code=="SHA256_MISMATCH" -> R.string.msg_sha256_mismatch
    code in setOf("NO_ACROFORM","FORM_FIELD_NOT_FOUND","FORM_FIELD_TYPE_MISMATCH") -> R.string.msg_no_acroform
    code=="REDACTION_VERIFICATION_FAILED" -> R.string.msg_redaction_failed
    code=="ONE_PDF_REQUIRED" || code=="ONE_ZIP_REQUIRED" || code=="ONE_SOURCE_REQUIRED" -> R.string.msg_one_file
    code in setOf("NO_SOURCES","TWO_SOURCES_REQUIRED","TWO_PDFS_REQUIRED","TWO_IMAGES_REQUIRED","PDF_MERGE_NEEDS_TWO_FILES","PDF_AND_SIGNATURE_IMAGE_REQUIRED") -> R.string.msg_choose_files
    code=="NO_FOLDER_SELECTED" -> R.string.msg_choose_folder
    code in setOf("ACCESS_LOST","PERSISTABLE_GRANT_REQUIRED","CONTENT_URI_REQUIRED","OUTPUT_MISSING","SOURCE_NOT_FOUND") -> R.string.msg_access_lost
    code.startsWith("PROVIDER_") || code=="FILE_DELETE_UNAVAILABLE" -> R.string.msg_provider_unavailable
    code in setOf("NO_COMPATIBLE_APP_OR_ACCESS_LOST","EXTERNAL_APP_UNAVAILABLE") -> R.string.msg_no_viewer
    code=="PREVIEW_UNAVAILABLE" -> R.string.msg_preview_unavailable
    code.contains("BUDGET") || code.contains("TOO_LARGE") || code.contains("SIZE_LIMIT") -> R.string.msg_size_limit
    code.contains("MIME") || code.contains("UNSUPPORTED_FORMAT") || code.contains("UNSUPPORTED_FILE") || code=="UNSAFE_ARCHIVE_PATH" || code=="ARCHIVE_HAS_NO_FILES" -> R.string.msg_file_type
    code=="HTTPS_REQUIRED" || code.contains("CLEARTEXT") || code.contains("HTTP_CONSENT") -> R.string.msg_https
    code.contains("NETWORK") || code.contains("TIMEOUT") || code.startsWith("HTTP_") || code.contains("DOWNLOAD_RESTART") -> R.string.msg_network
    code=="INVALID_URL" || code=="INVALID_DOWNLOAD_LIST" -> R.string.msg_url
    code=="WATERMARK_CONTENT_REQUIRED" -> R.string.ui_watermark_text_required
    code=="WATERMARK_LOGO_REQUIRED" -> R.string.ui_watermark_logo_required
    code=="INVALID_ANNOTATION" -> R.string.ui_annotation_text_required
    code=="STAMP_CONTENT_REQUIRED" -> R.string.ui_stamp_content_required
    code=="INVALID_RENAME_PATTERN" -> R.string.ui_rename_pattern_invalid
    code.startsWith("INVALID_") || code=="CHOOSE_ONE_RESIZE_MODE" || code=="PAGE_OUT_OF_RANGE" || code=="TARGET_SIZE_UNREACHABLE" -> R.string.msg_check_settings
    code.contains("CAPABILITY") || code.contains("UNQUALIFIED") || code.contains("QUALIFICATION") || code.contains("UNSUPPORTED_DRAFT") -> R.string.msg_unavailable
    else -> R.string.msg_failed
}
