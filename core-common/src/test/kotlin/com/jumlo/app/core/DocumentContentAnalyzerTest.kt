package com.jumlo.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentContentAnalyzerTest {
    @Test fun recognizesEnglishAndArabicInvoicesAndContractsWithRecommendations() {
        val invoice = DocumentContentAnalyzer.analyze(
            "TAX INVOICE #8841\nSubtotal: $420.00\nVAT 15%: $63.00\nTotal Due: $483.00\nDate: 2026-11-20",
            sourceDisplayName = "Scan-20261120.pdf",
            isRtl = false
        )
        assertNotNull(invoice)
        assertEquals(DocumentCategory.INVOICE, invoice!!.category)
        assertTrue(invoice.suggestedBaseName.contains("Invoice"))
        assertTrue(invoice.suggestedBaseName.contains("2026-11-20"))
        assertTrue("pdf_compress" in invoice.recommendedTools)

        val arabicContract = DocumentContentAnalyzer.analyze(
            "عقد اتفاقية تقديم خدمات\nالطرف الأول: شركة النور\nالطرف الثاني: مكتب الاستشارات\nتخضع هذه الاتفاقية لبنود التعاقد والتوقيع.",
            isRtl = true
        )
        assertNotNull(arabicContract)
        assertEquals(DocumentCategory.CONTRACT, arabicContract!!.category)
        assertTrue("pdf_signature" in arabicContract.recommendedTools)
        assertTrue("pdf_protect" in arabicContract.recommendedTools)

        val passport = DocumentContentAnalyzer.analyze(
            "PASSPORT\nP<EGYDOE<<JOHN<<<<<<<<<<<<<<<<<<<<<\nNationality EGY\nDate of expiry 2031-05-01"
        )
        assertNotNull(passport)
        assertEquals(DocumentCategory.PASSPORT, passport!!.category)
        assertTrue("pdf_protect" in passport.recommendedTools)
    }

    @Test fun ignoresEmptyOrUnclassifiedShortSnippets() {
        assertNull(DocumentContentAnalyzer.analyze("hello"))
        assertNull(DocumentContentAnalyzer.analyze("Random note without structured document markers 12345"))
    }
}
