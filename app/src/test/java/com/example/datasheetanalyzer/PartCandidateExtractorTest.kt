package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.ocr.PartCandidateExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartCandidateExtractorTest {
    private val extractor = PartCandidateExtractor()

    @Test fun ranksPartAboveShortLotCodeAndExcludesLogo() {
        val codes = extractor.extract(listOf("LM383", "NATIONAL", "J94")).map { it.code }
        assertEquals("LM383", codes.first())
        assertFalse("NATIONAL" in codes)
        assertTrue("J94" in codes) // Ambiguous short codes remain unverified candidates.
    }

    @Test fun joinsSplitPrefixWithoutJoiningManufacturer() {
        val codes = extractor.extract(listOf("LM 383", "NATIONAL LM383")).map { it.code }
        assertTrue("LM383" in codes)
        assertFalse("NATIONALLM383" in codes)
    }

    @Test fun deduplicatesCaseVariantsAndKeepsSupportedParts() {
        val codes = extractor.extract(listOf("lm358 LM358", "NE555 BC547", "1N4007 IRLZ44N")).map { it.code }
        assertEquals(1, codes.count { it == "LM358" })
        assertTrue(codes.containsAll(listOf("NE555", "BC547", "1N4007", "IRLZ44N")))
    }

    @Test fun rejectsPlainTextAndPureDates() {
        assertTrue(extractor.extract(listOf("NATIONAL", "2026", "?", "123456")).isEmpty())
    }

    @Test fun keepsOcrMistakesForHumanCorrection() {
        val codes = extractor.extract(listOf("LM3B3", "LMO83")).map { it.code }
        assertTrue("LM3B3" in codes)
        assertTrue("LMO83" in codes)
        assertFalse("LM383" in codes)
    }
}
