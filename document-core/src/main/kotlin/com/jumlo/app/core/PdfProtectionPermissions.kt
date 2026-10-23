package com.jumlo.app.core

/** Reader-enforced permissions. Accessibility text extraction remains enabled. */
data class PdfProtectionPermissions(
    val allowPrinting: Boolean = true,
    val allowCopy: Boolean = true,
    val allowModify: Boolean = false,
    val allowAnnotations: Boolean = true,
    val allowForms: Boolean = true
)
