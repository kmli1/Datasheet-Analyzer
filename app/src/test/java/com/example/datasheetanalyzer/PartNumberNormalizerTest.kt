package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartNumberNormalizerTest {
    @Test fun removesSpacesAndUppercases() {
        assertEquals("LM383", PartNumberNormalizer.normalize(" lm 383 \n"))
    }

    @Test fun keepsAmbiguousCharacters() {
        assertEquals("LM3B3-OI1S5", PartNumberNormalizer.normalize("lm3b3-OI1S5"))
    }

    @Test fun keepsPackageSuffixes() {
        assertEquals("LM358-N/P", PartNumberNormalizer.normalize(" lm358-n/p "))
    }

    @Test fun independentOfTurkishLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("IRLZ44N", PartNumberNormalizer.normalize("irlz44n"))
        } finally { Locale.setDefault(original) }
    }

    @Test fun rejectsUnsupportedInputWithoutSilentlyRemovingIt() {
        assertFalse(PartNumberNormalizer.isValid(PartNumberNormalizer.normalize("LM_383")))
        assertFalse(PartNumberNormalizer.isValid(""))
        assertFalse(PartNumberNormalizer.isValid("A".repeat(65)))
        assertTrue(PartNumberNormalizer.isValid("1N4007"))
    }
}
