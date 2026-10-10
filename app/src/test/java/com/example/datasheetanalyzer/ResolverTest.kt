package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.TrustLevel
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.resolver.CandidateUrlPolicy
import com.example.datasheetanalyzer.domain.resolver.PartNumberMatcher
import com.example.datasheetanalyzer.domain.resolver.TrustedDomainRegistry
import org.junit.Assert.*
import org.junit.Test

class ResolverTest {
    private val registry = TrustedDomainRegistry()
    private val matcher = PartNumberMatcher()
    private val selector = CandidateSelector()
    private fun url(raw: String) = requireNotNull(CandidateUrlPolicy.parse(raw))

    @Test fun recognizesManufacturerAndSubdomains() {
        assertEquals(TrustLevel.MANUFACTURER, registry.classify(url("https://www.ti.com/a.pdf")).level)
        assertEquals(TrustLevel.MANUFACTURER, registry.classify(url("https://st.com/a.pdf")).level)
    }
    @Test fun recognizesDistributorMediaSubdomain() {
        assertEquals(TrustLevel.DISTRIBUTOR, registry.classify(url("https://mm.digikey.com/a.pdf")).level)
    }
    @Test fun rejectsLookalikeAndAppendedDomains() {
        for (host in listOf("evilti.com", "ti.com.evil.test", "mouser.com.attacker.test")) {
            assertEquals(TrustLevel.UNKNOWN, registry.classify(url("https://$host/a.pdf")).level)
        }
    }
    @Test fun blocksForumsEvenOnManufacturerDomain() {
        assertEquals(TrustLevel.BLOCKED, registry.classify(url("https://e2e.ti.com/a.pdf")).level)
        assertEquals(TrustLevel.BLOCKED, registry.classify(url("https://ti.com/blog/LM383.pdf")).level)
    }
    @Test fun blocksShorteners() {
        assertEquals(TrustLevel.BLOCKED, registry.classify(url("https://bit.ly/LM383")).level)
    }
    @Test fun rejectsUnsafeUrlForms() {
        for (raw in listOf("http://ti.com/a.pdf", "javascript:alert(1)",
            "https://ti.com@evil.test/a.pdf", "https://ti.com:8443/a.pdf", "https://127.0.0.1/a.pdf")) {
            assertNull(raw, CandidateUrlPolicy.parse(raw))
        }
    }
    @Test fun canonicalizesCasePortAndFragment() {
        assertEquals("https://ti.com/a.pdf", url("https://TI.COM:443/a.pdf#page=2").canonical)
    }
    @Test fun recognizesExactCodeWithoutSubstringConfusion() {
        assertEquals(PartMatch.EXACT, matcher.match("LM383", "LM383 datasheet"))
        for (other in listOf("TPS3831", "OPA383", "LM2833", "LM3831", "XLM383")) {
            assertEquals(other, PartMatch.NONE, matcher.match("LM383", other))
        }
    }
    @Test fun classifiesVariantsSeparately() {
        for (part in listOf("LM383A", "LM383T", "LM383-N")) {
            assertEquals(PartMatch.FAMILY_VARIANT, matcher.match("LM383", part))
        }
    }
    @Test fun matchesDecodedPdfPath() {
        assertEquals(PartMatch.EXACT, matcher.matchUrl("LM383", url("https://ti.com/lit/ds/%6cm383.pdf")))
    }
    @Test fun supportsSlashInRequestedPackageCode() {
        assertEquals(PartMatch.EXACT, matcher.matchUrl("LM358-N/P", url("https://ti.com/docs/LM358-N/P.pdf")))
    }
    @Test fun variantFilenameCannotBeOverruledByQuery() {
        assertEquals(PartMatch.FAMILY_VARIANT, matcher.matchUrl("LM383", url("https://ti.com/LM383A.pdf?q=LM383")))
    }
    @Test fun ranksManufacturerAboveUnknownFirstResult() {
        val result = selector.select("LM383", listOf(
            SearchCandidate("LM383 datasheet", "https://unknown.test/LM383.pdf"),
            SearchCandidate("LM383 datasheet", "https://ti.com/LM383.pdf")))
        assertEquals("https://ti.com/LM383.pdf", result.recommendedUrl)
        assertEquals(95, result.candidates.first().score)
        assertFalse(result.candidates.last().selectable)
    }
    @Test fun variantIsNeverAutoRecommended() {
        val result = selector.select("LM383", listOf(SearchCandidate("LM383A datasheet", "https://ti.com/LM383A.pdf")))
        assertNull(result.recommendedUrl)
        assertFalse(result.candidates.single().selectable)
    }
    @Test fun conflictingTitleAndFilenameCannotBeSelected() {
        val result = selector.select("LM383", listOf(SearchCandidate("LM383 datasheet", "https://ti.com/LM383A.pdf")))
        assertFalse(result.candidates.single().selectable)
    }
    @Test fun snippetAloneDoesNotVerifyPartIdentity() {
        val result = selector.select("LM383", listOf(SearchCandidate("Datasheet", "https://ti.com/other.pdf", "LM383")))
        assertFalse(result.candidates.single().selectable)
    }
    @Test fun duplicateFragmentsDoNotCreateDuplicateCandidates() {
        val result = selector.select("LM383", listOf(
            SearchCandidate("LM383 datasheet", "https://ti.com/LM383.pdf#page=1"),
            SearchCandidate("LM383 datasheet", "https://ti.com/LM383.pdf#page=2")))
        assertEquals(1, result.candidates.size)
    }
    @Test fun dropsInvalidUrlsAndCountsThem() {
        val result = selector.select("LM383", listOf(SearchCandidate("LM383 datasheet", "http://ti.com/LM383.pdf")))
        assertEquals(1, result.discardedCount)
        assertTrue(result.candidates.isEmpty())
    }
    @Test fun genericProductPageWithoutDocumentHintIsNotSelectable() {
        val result = selector.select("LM383", listOf(SearchCandidate("LM383", "https://ti.com/product/LM383")))
        assertFalse(result.candidates.single().selectable)
    }
}
