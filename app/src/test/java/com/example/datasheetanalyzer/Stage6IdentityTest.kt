package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.PartMatch
import org.junit.Assert.*
import org.junit.Test

class Stage6IdentityTest {
    private val matcher=DocumentPartMatcher()
    @Test fun spacesFromPdfGlyphsMatchExact() {
        for(text in listOf("LM 383", "L M 3 8 3", "LM\t383", "LM383/LM383A")) assertEquals(text,PartMatch.EXACT,matcher.match("LM383",text))
    }
    @Test fun boundariesAndDigitsCannotBeRelaxed() {
        for(text in listOf("LM 3831","XLM 383","LM 38B","LM 2833","LM 3\n83")) assertEquals(text,PartMatch.NONE,matcher.match("LM383",text))
        assertEquals(PartMatch.FAMILY_VARIANT,matcher.match("LM383","LM 383A"))
        assertEquals(PartMatch.FAMILY_VARIANT,matcher.match("LM383","LM 383 A"))
        assertEquals(PartMatch.FAMILY_VARIANT,matcher.match("LM383","LM383 A"))
    }
    @Test fun watermarkDoesNotSuppressIdentityOcr() {
        val text="AllDataSheet archive source advertisement ".repeat(15)
        val pdf=PdfExtraction(PdfMetadata(),4,(1..4).map { PageText(it,text) })
        assertTrue(OcrPagePlanner().firstPages(pdf).isEmpty())
        assertEquals(listOf(1,2,3),OcrPagePlanner().identityRecoveryPages(pdf,"LM383",emptySet()))
        assertEquals(listOf(2,3),OcrPagePlanner().identityRecoveryPages(pdf,"LM383",setOf(1)))
    }
    @Test fun bodyIdentitySkipsRecoveryButMetadataDoesNot() {
        assertTrue(OcrPagePlanner().identityRecoveryPages(PdfExtraction(PdfMetadata(),1,listOf(PageText(1,"LM 383"))),"LM383",emptySet()).isEmpty())
        assertEquals(listOf(1),OcrPagePlanner().identityRecoveryPages(PdfExtraction(PdfMetadata("LM383"),1,listOf(PageText(1,"Archive watermark"))),"LM383",emptySet()))
    }
    @Test fun recoveryRemainsWithinGlobalOcrLimit() {
        assertEquals(listOf(3),OcrPagePlanner().identityRecoveryPages(PdfExtraction(PdfMetadata(),20,emptyList()),"LM383",setOf(1,2,4,5)))
    }
}
