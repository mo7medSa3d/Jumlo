package com.jumlo.app.core

/** Semantic document categories inferred cleanly from OCR or embedded PDF text (English + Arabic). */
enum class DocumentCategory(
    val labelEn: String,
    val labelAr: String,
    val recommendedTools: List<String>
) {
    INVOICE("Invoice", "فاتورة", listOf("pdf_compress", "pdf_protect", "pdf_to_docx")),
    RECEIPT("Receipt", "إيصال", listOf("pdf_compress", "pdf_02", "file_07")),
    ID_CARD("ID Card", "بطاقة هوية", listOf("scan_id_card", "pdf_protect", "pdf_privacy_redact")),
    PASSPORT("Passport", "جواز سفر", listOf("pdf_protect", "pdf_privacy_redact", "pdf_compress")),
    CONTRACT("Contract", "عقد واتفاقية", listOf("pdf_signature", "pdf_protect", "pdf_to_docx")),
    CERTIFICATE("Certificate", "شهادة", listOf("pdf_compress", "pdf_stamp", "pdf_protect")),
    STATEMENT("Bank Statement", "كشف حساب", listOf("pdf_protect", "pdf_privacy_redact", "pdf_compress")),
    RESUME("CV / Resume", "سيرة ذاتية", listOf("pdf_compress", "pdf_to_docx", "pdf_protect")),
    FORM("Form", "نموذج", listOf("pdf_signature", "pdf_to_docx", "pdf_compress")),
    REPORT("Report", "تقرير", listOf("pdf_compress", "pdf_to_docx", "pdf_to_pptx"));
}

data class DocumentAnalysis(
    val category: DocumentCategory,
    val confidence: Float,
    val suggestedBaseName: String,
    val recommendedTools: List<String>
)

/**
 * Zero-network bilingual (English + Arabic) classifier that identifies document types,
 * derives structured output filenames, and surfaces accurate follow-up workflow suggestions.
 */
object DocumentContentAnalyzer {
    private val DATE_REGEX = Regex("""\b(20\d{2}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]20\d{2})\b""")
    private val MRZ_REGEX = Regex("""P<[A-Z0-9<]{5,}""")

    private data class Rule(
        val category: DocumentCategory,
        val strongTerms: List<String>,
        val supportingTerms: List<String>
    )

    private val rules = listOf(
        Rule(
            DocumentCategory.PASSPORT,
            listOf("passport", "جواز سفر", "جواز السفر", "passeport"),
            listOf("nationality", "issuing", "date of expiry", "تاريخ الانتهاء", "الجنسية", "جهة الإصدار")
        ),
        Rule(
            DocumentCategory.ID_CARD,
            listOf("national id", "identity card", "البطاقة الشخصية", "بطاقة الرقم القومي", "هوية وطنية", "رخصة قيادة", "driver license"),
            listOf("date of birth", "تاريخ الميلاد", "الرقم القومي", "id no", "gender", "الجنس")
        ),
        Rule(
            DocumentCategory.INVOICE,
            listOf("invoice", "tax invoice", "فاتورة", "فاتورة ضريبية", "vat number"),
            listOf("subtotal", "total due", "amount due", "vat", "bill to", "الضريبة", "الإجمالي", "المبلغ المستحق")
        ),
        Rule(
            DocumentCategory.RECEIPT,
            listOf("receipt", "payment receipt", "إيصال", "سند قبض", "إيصال استلام"),
            listOf("paid", "cash", "card", "change", "thank you", "تم الدفع", "نقدا", "المبلغ")
        ),
        Rule(
            DocumentCategory.CONTRACT,
            listOf("agreement", "contract", "عقد", "اتفاقية", "طرف أول", "طرف ثاني"),
            listOf("terms and conditions", "whereas", "hereby", "witnesseth", "signature", "بنود", "التزامات", "توقيع")
        ),
        Rule(
            DocumentCategory.STATEMENT,
            listOf("bank statement", "account statement", "كشف حساب"),
            listOf("iban", "balance", "credit", "debit", "opening balance", "رصيد", "دائن", "مدين", "رقم الحساب")
        ),
        Rule(
            DocumentCategory.CERTIFICATE,
            listOf("certificate", "diploma", "شهادة", "إفادة"),
            listOf("hereby certified", "awarded", "completion", "graduation", "تشهد", "تقدير", "تخرج")
        ),
        Rule(
            DocumentCategory.RESUME,
            listOf("curriculum vitae", "resume", "سيرة ذاتية", "السيرة الذاتية"),
            listOf("experience", "education", "skills", "summary", "الخبرات", "المؤهل", "المهارات")
        ),
        Rule(
            DocumentCategory.FORM,
            listOf("application form", "registration form", "نموذج", "استمارة"),
            listOf("applicant", "full name", "signature", "date", "مقدم الطلب", "الاسم الكامل", "التوقيع")
        ),
        Rule(
            DocumentCategory.REPORT,
            listOf("annual report", "summary report", "تقرير", "ملخص تنفيذي"),
            listOf("executive summary", "findings", "conclusion", "analysis", "النتائج", "التوصيات", "تحليل")
        )
    )

    fun analyze(text: String, sourceDisplayName: String? = null, isRtl: Boolean = false): DocumentAnalysis? {
        val cleaned = text.trim()
        if (cleaned.length < 12) return null
        val lower = cleaned.lowercase()

        var bestCategory: DocumentCategory? = null
        var bestScore = 0
        for (rule in rules) {
            var score = 0
            for (term in rule.strongTerms) {
                if (lower.contains(term)) score += 4
            }
            for (term in rule.supportingTerms) {
                if (lower.contains(term)) score += 2
            }
            if (rule.category == DocumentCategory.PASSPORT && MRZ_REGEX.containsMatchIn(cleaned)) {
                score += 6
            }
            if (score > bestScore) {
                bestScore = score
                bestCategory = rule.category
            }
        }

        if (bestCategory == null || bestScore < 4) return null
        val confidence = (bestScore / 10f).coerceIn(0.45f, 0.99f)
        val label = if (isRtl) bestCategory.labelAr else bestCategory.labelEn
        val dateSuffix = DATE_REGEX.find(cleaned)?.value?.replace('/', '-')
        val sourceClean = sourceDisplayName?.let { AutoOutputName.cleanBaseName(it, "") }
            ?.takeIf { it.isNotBlank() && !it.equals("Document", true) && !it.startsWith("Scan", true) }
        val base = listOfNotNull(label, sourceClean, dateSuffix).joinToString(" - ")
        return DocumentAnalysis(
            category = bestCategory,
            confidence = confidence,
            suggestedBaseName = AutoOutputName.cleanBaseName(base, label),
            recommendedTools = bestCategory.recommendedTools
        )
    }
}
