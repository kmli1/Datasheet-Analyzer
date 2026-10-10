package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.security.*
import org.junit.Assert.*
import org.junit.Test

class DocumentEvidenceRegressionTest {
    private val url="https://www.datasheetarchive.com/pdf/LM383.pdf"
    @Test fun unreadableIdentityNeedsReview() {
        val c=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet",url))).candidates.single()
        val p=PdfExtraction(PdfMetadata(),1,listOf(PageText(1,"")))
        val v=DocumentVerifier().verify("LM383",c,p,emptyList())
        assertEquals("IDENTITY_UNREADABLE",v.code);assertEquals(DocumentStatus.REVIEW_REQUIRED,v.status);assertFalse(v.automaticEligible)
    }
    @Test fun foreignPartValueIsNotShownAsRequestedPartValue() {
        val c=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet",url))).candidates.single()
        val p=PdfExtraction(PdfMetadata(),1,listOf(PageText(1,"LM383\nNational Semiconductor\nGeneral Description\nFeatures\nElectrical Characteristics\nLM383A Supply Voltage 22 V\nSupply Voltage 5 to 20 V",origin=TextOrigin.TEXT_LAYER)))
        val cleaned=p.copy(pages=TextCleaner().clean(p.pages));val sections=DatasheetSectionDetector().detect(cleaned.pages)
        val v=DocumentVerifier().verify("LM383",c,cleaned,sections);assertTrue(v.automaticEligible)
        val doc=AnalyzedDocument(CheckedDocument(QuarantinedPdf(java.io.File("unused"),url,8,""),1,security=SecurityStatus.PASSED,identity=v.status,downloadMs=0,checkMs=0,extraction=cleaned),c,cleaned,sections,v,sourceKind=DocumentSource.PDF,requestedPart="LM383")
        val result=ParameterExtractionEngine().extract(doc,listOf(ParameterQuery("Supply Voltage",Criterion.SUPPLY_VOLTAGE,listOf("Supply Voltage")))).results.single()
        assertTrue(result.matches.none { it.evidenceLine.contains("LM383A") });assertTrue(result.matches.isNotEmpty())
    }
}
