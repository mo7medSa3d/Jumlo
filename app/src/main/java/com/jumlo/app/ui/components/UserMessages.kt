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
    code in setOf("NO_SOURCES","ONE_SOURCE_REQUIRED","TWO_SOURCES_REQUIRED","PDF_MERGE_NEEDS_TWO_FILES") -> R.string.msg_choose_files
    code=="NO_FOLDER_SELECTED" -> R.string.msg_choose_folder
    code in setOf("ACCESS_LOST","PERSISTABLE_GRANT_REQUIRED","CONTENT_URI_REQUIRED","OUTPUT_MISSING") -> R.string.msg_access_lost
    code.startsWith("PROVIDER_") || code=="FILE_DELETE_UNAVAILABLE" -> R.string.msg_provider_unavailable
    code=="NO_COMPATIBLE_APP_OR_ACCESS_LOST" -> R.string.msg_no_viewer
    code=="PREVIEW_UNAVAILABLE" -> R.string.msg_preview_unavailable
    code.contains("BUDGET") || code.contains("TOO_LARGE") || code.contains("SIZE_LIMIT") -> R.string.msg_size_limit
    code.contains("MIME") || code.contains("UNSUPPORTED_FORMAT") || code.contains("UNSUPPORTED_FILE") -> R.string.msg_file_type
    code=="HTTPS_REQUIRED" || code.contains("CLEARTEXT") || code.contains("HTTP_CONSENT") -> R.string.msg_https
    code.contains("NETWORK") || code.contains("TIMEOUT") || code.startsWith("HTTP_") || code.contains("DOWNLOAD_RESTART") -> R.string.msg_network
    code=="INVALID_URL" || code=="INVALID_DOWNLOAD_LIST" -> R.string.msg_url
    code=="WATERMARK_CONTENT_REQUIRED" -> R.string.ui_watermark_text_required
    code=="WATERMARK_LOGO_REQUIRED" -> R.string.ui_watermark_logo_required
    code=="INVALID_ANNOTATION" -> R.string.ui_annotation_text_required
    code=="STAMP_CONTENT_REQUIRED" -> R.string.ui_stamp_content_required
    code=="INVALID_RENAME_PATTERN" -> R.string.ui_rename_pattern_invalid
    code.startsWith("INVALID_") || code=="CHOOSE_ONE_RESIZE_MODE" || code=="PAGE_OUT_OF_RANGE" -> R.string.msg_check_settings
    code.contains("CAPABILITY") || code.contains("UNQUALIFIED") || code.contains("QUALIFICATION") || code.contains("UNSUPPORTED_DRAFT") -> R.string.msg_unavailable
    code=="LOGIN_REQUIRED" -> R.string.msg_login
    else -> R.string.msg_failed
}
