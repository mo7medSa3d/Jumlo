package com.jumlo.app.ui.components

import androidx.annotation.StringRes
import com.jumlo.app.R

/** Stable engine codes are diagnostics; presentation always uses localized copy. */
@StringRes fun userMessageResource(code:String):Int=when {
    code=="INVALID_MEDIA_SERVICE_URL" -> R.string.msg_invalid_media_service_url
    code=="INVALID_MEDIA_SERVICE_TOKEN" -> R.string.msg_invalid_media_service_token
    code=="MEDIA_SERVICE_CREDENTIALS_UNAVAILABLE" -> R.string.msg_media_service_credentials_unavailable
    code=="MEDIA_AGE_RESTRICTED" -> R.string.msg_media_age_restricted
    code=="DESTINATION_SAVE_FAILED" -> R.string.msg_destination_save_failed
    code=="CLIPBOARD_EMPTY" -> R.string.studio_clipboard_empty
    code=="CLIPBOARD_UNAVAILABLE" -> R.string.rebuild_clipboard_unavailable
    code=="PDF_INVALID" -> R.string.msg_pdf_corrupt
    code in setOf("OFFICE_PAGE_LIMIT","OFFICE_TEXT_BUDGET_EXCEEDED") -> R.string.rebuild_office_limit
    code=="OFFICE_PAGE_ASPECT_UNSUPPORTED" -> R.string.rebuild_office_aspect
    code in setOf("OFFICE_VERIFY_FAILED","OFFICE_IMAGE_INVALID") -> R.string.rebuild_office_verify
    code=="OFFICE_EMPTY_TEXT_PAGES" -> R.string.rebuild_office_empty_pages
    code=="OFFICE_OCR_REVIEW" -> R.string.rebuild_office_ocr_review
    code=="OCR_MODEL_UNAVAILABLE" -> R.string.rebuild_ocr_model
    code=="OCR_NO_TEXT" -> R.string.rebuild_ocr_empty
    code=="OCR_TEXT_ONLY" -> R.string.rebuild_ocr_text_only
    code=="WORKER_START_FAILED" -> R.string.rebuild_worker_start
    code=="INVALID_SHARED_CONTENT" -> R.string.rebuild_invalid_share
    code=="SAVED_IN_APP_ONLY" -> R.string.rebuild_private_output
    code.startsWith("SECRET_STORAGE") -> R.string.rebuild_secret_storage
    code in setOf("IMAGE_MEMORY_BUDGET_EXCEEDED","IMAGE_DIMENSIONS_UNREPRESENTABLE") -> R.string.upgrade_image_memory
    code=="IMAGE_SIZE_BOUND_UNMET" -> R.string.upgrade_image_target_unmet
    code=="IMAGE_NOT_SMALLER" -> R.string.upgrade_image_unchanged
    code=="DOWNLOAD_RESTART_FAILED" -> R.string.upgrade_download_restart_failed
    code in setOf("INVALID_FORM_INPUT","FORM_FIELD_MISSING","FORM_FIELD_TYPE_MISMATCH") -> R.string.upgrade_form_values
    code in setOf("FORM_APPEARANCE_UNAVAILABLE","FORM_WIDGET_PAGE_MISSING","FORM_VALUE_CHANGED","FORM_FLATTEN_UNSUPPORTED") -> R.string.upgrade_form_flatten
    code in setOf("INVALID_PAGE_INSTANCE","PDF_PAGE_IMPORT_FAILED") -> R.string.upgrade_page_edit_failed
    code=="DRAFT_SAVED" -> R.string.msg_draft_saved
    code in setOf("DRAFT_SAVE_FAILED","DRAFT_RESTORE_FAILED") -> R.string.msg_draft_failed
    code in setOf("SETTINGS_READ_FAILED","SETTINGS_SAVE_FAILED") -> R.string.msg_settings_failed
    code=="OUTPUT_RENAMED" -> R.string.upgrade_renamed
    code=="OUTPUT_RENAME_FAILED" -> R.string.upgrade_rename_failed
    code=="PRINT_UNSUPPORTED_FORMAT" -> R.string.upgrade_preview_unavailable
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
    code=="MEDIA_SERVICE_NOT_CONFIGURED" -> R.string.media_error_service
    code=="MEDIA_SITE_UNSUPPORTED" -> R.string.media_error_site
    code=="MEDIA_CONVERSION_UNAVAILABLE" -> R.string.media_error_conversion
    code=="MEDIA_SESSION_REQUIRED" -> R.string.media_error_session
    code=="MEDIA_PRIVATE_CONTENT" -> R.string.media_error_private
    code=="MEDIA_GEO_BLOCKED" -> R.string.media_error_geoblocked
    code=="MEDIA_REMOVED" -> R.string.media_error_removed
    code=="MEDIA_ENCRYPTED_SOURCE" -> R.string.media_error_drm
    code=="MEDIA_RATE_LIMITED" -> R.string.media_error_rate
    code in setOf("MEDIA_NETWORK_ERROR","MEDIA_TLS_FAILED") -> R.string.media_error_network
    code in setOf("MEDIA_REANALYSIS_REQUIRED","MEDIA_SELECTION_REQUIRED","MEDIA_NO_USABLE_FORMAT","AUDIO_TRACK_UNAVAILABLE") -> R.string.media_error_format
    code in setOf("MEDIA_NATIVE_ABI_UNAVAILABLE","MEDIA_RUNTIME_INITIALIZATION_FAILED","MEDIA_BUILD_NOT_ENABLED") -> R.string.media_error_runtime
    code in setOf("PLAYLIST_REQUIRES_ITEM_SELECTION","LIVE_MEDIA_NOT_FINITE","MULTIPLE_MEDIA_LINKS") -> R.string.media_error_url
    code in setOf("MEDIA_EXTRACTION_FAILED","MEDIA_AUDIO_EXTRACTION_FAILED","MEDIA_AUDIO_MISSING","MEDIA_VIDEO_MISSING","MEDIA_EMPTY_TRACK","MEDIA_QUALITY_MISMATCH") -> R.string.media_error_generic
    code=="SECRET_EXPIRED" -> R.string.ui_sensitive_draft_note
    code=="HTTP_401" || code=="LOGIN_REQUIRED" || code=="GOOGLE_DRIVE_TOKEN_REQUIRED" -> R.string.msg_http_401
    code=="HTTP_403" || code=="PRIVATE_CONTENT" || code=="MEDIA_SOURCE_REJECTED" -> R.string.msg_http_403
    code=="HTTP_404" || code=="REMOVED" -> R.string.msg_http_404
    code=="HTTP_429" || code=="RATE_LIMITED" -> R.string.msg_http_429
    code=="TOO_MANY_REDIRECTS" -> R.string.msg_too_many_redirects
    code=="INSECURE_REDIRECT" -> R.string.msg_insecure_redirect
    code=="DOWNLOAD_NOT_A_FILE" -> R.string.msg_download_not_a_file
    code=="EMPTY_HTTP_BODY" -> R.string.msg_empty_download
    code in setOf("DOWNLOAD_RESTART_REQUIRED","FINAL_URL_CHANGED","MISSING_RESUME_VALIDATOR") -> R.string.msg_download_restart_required
    code in setOf("TRUNCATED_HTTP_BODY","OVERFLOW_HTTP_BODY","HTTP_CONTENT_LENGTH_MISMATCH") -> R.string.msg_transfer_incomplete
    code in setOf("NETWORK_TIMEOUT","MEDIA_PROCESS_TIMEOUT","MEDIA_PROCESS_STREAM_TIMEOUT") -> R.string.msg_network_timeout
    code=="NETWORK_UNAVAILABLE" -> R.string.msg_network_unavailable
    code=="WIFI_REQUIRED" -> R.string.msg_wifi_required
    code=="UNMETERED_NETWORK_REQUIRED" -> R.string.msg_unmetered_required
    code in setOf("DISK_BUDGET_EXCEEDED","INSUFFICIENT_STORAGE") -> R.string.msg_insufficient_storage
    code=="LOW_MEMORY" -> R.string.msg_low_memory
    code=="BUNDLE_INCOMPATIBLE_SELECTION" || code=="UNSUPPORTED_OUTPUT_WORKFLOW" -> R.string.files_workflow_incompatible
    code=="OUTPUT_NOT_AVAILABLE" || code=="PUBLICATION_INTEGRITY_ERROR" -> R.string.files_workflow_missing
    code=="BUNDLE_TOO_LARGE" -> R.string.files_workflow_size
    code=="BUNDLE_INVALID_SELECTION" -> R.string.files_workflow_invalid
    code=="BUNDLE_TOO_MANY_ITEMS" -> R.string.files_workflow_limit
    code=="TOO_MANY_OUTPUTS" || code=="TOO_MANY_DOWNLOAD_URLS" -> R.string.files_workflow_limit
    code=="MEDIA_BATCH_ANALYSIS_REQUIRED" -> R.string.msg_media_batch_single
    code=="BUNDLE_DUPLICATE_ITEM" -> R.string.files_workflow_duplicates
    code=="PDF_NOT_COMPRESSIBLE" -> R.string.msg_pdf_not_compressible
    code=="PDF_IMAGE_COMPRESSION_PARTIAL" -> R.string.msg_pdf_compression_partial
    code=="PDF_IMAGE_COMPRESSION_FAILED" -> R.string.msg_pdf_compression_failed
    code=="PDF_ENCRYPTED" -> R.string.msg_pdf_encrypted
code=="PDF_PASSWORD_INCORRECT" -> R.string.msg_pdf_password_incorrect
    code=="OWNER_PASSWORD_REQUIRED" -> R.string.msg_owner_password_required
    code=="ZIP_PASSWORD_REQUIRED" -> R.string.msg_zip_password_required
    code=="SHA256_MISMATCH" -> R.string.msg_sha256_mismatch
    code in setOf("NO_ACROFORM","FORM_FIELD_NOT_FOUND","FORM_FIELD_TYPE_MISMATCH") -> R.string.msg_no_acroform
    code=="ANIMATED_IMAGE_UNSUPPORTED" -> R.string.msg_animated_image_unsupported
    code in setOf("MALFORMED_IMAGE","IMAGE_DECODE_FAILED","IMAGE_ENCODE_FAILED","IMAGE_VERIFY_FAILED") -> R.string.msg_image_corrupt
    code in setOf("EMPTY_PDF","PDF_VERIFY_FAILED","INVALID_PDF_PAGE_GEOMETRY") -> R.string.msg_pdf_corrupt
    code=="ONE_PDF_REQUIRED" || code=="ONE_ZIP_REQUIRED" || code=="ONE_SOURCE_REQUIRED" -> R.string.msg_one_file
    code in setOf("NO_SOURCES","ONE_OR_TWO_PDFS_REQUIRED","TWO_SOURCES_REQUIRED","TWO_PDFS_REQUIRED","TWO_IMAGES_REQUIRED","PDF_MERGE_NEEDS_TWO_FILES","PDF_AND_SIGNATURE_IMAGE_REQUIRED","AT_LEAST_ONE_PAGE_REQUIRED") -> R.string.msg_choose_files
    code=="NO_FOLDER_SELECTED" -> R.string.msg_choose_folder
    code in setOf("ACCESS_LOST","PERSISTABLE_GRANT_REQUIRED","CONTENT_URI_REQUIRED","OUTPUT_MISSING","SOURCE_NOT_FOUND","DESTINATION_ACCESS_LOST") -> R.string.msg_access_lost
    code=="DESTINATION_EXISTS" -> R.string.files_rename_exists
    code.startsWith("PROVIDER_") || code=="FILE_DELETE_UNAVAILABLE" || code in setOf("DESTINATION_READ_ONLY","DESTINATION_CREATE_FAILED","DESTINATION_WRITE_UNAVAILABLE","DESTINATION_VERIFY_FAILED","DESTINATION_VERIFY_UNAVAILABLE") -> R.string.msg_provider_unavailable
    code in setOf("NO_COMPATIBLE_APP_OR_ACCESS_LOST","EXTERNAL_APP_UNAVAILABLE") -> R.string.msg_no_viewer
    code=="PREVIEW_UNAVAILABLE" -> R.string.msg_preview_unavailable
    code.contains("BUDGET") || code.contains("TOO_LARGE") || code.contains("SIZE_LIMIT") -> R.string.msg_size_limit
    code.contains("MIME") || code.contains("UNSUPPORTED_FORMAT") || code.contains("UNSUPPORTED_FILE") || code=="UNSAFE_ARCHIVE_PATH" || code=="ARCHIVE_HAS_NO_FILES" -> R.string.msg_file_type
    code=="HTTPS_REQUIRED" || code.contains("CLEARTEXT") || code.contains("HTTP_CONSENT") -> R.string.msg_https
    code.contains("NETWORK") || code.contains("TIMEOUT") || code.startsWith("HTTP_") || code.contains("DOWNLOAD_RESTART") -> R.string.msg_network
    code=="DUPLICATE_PAGE_ORDER" || code=="INVALID_PAGE_ORDER" -> R.string.msg_check_settings
    code=="SPLIT_BOUNDARY_REQUIRED" || code=="INVALID_SPLIT_BOUNDARY" -> R.string.msg_check_settings
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
