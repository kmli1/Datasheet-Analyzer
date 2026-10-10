package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.security.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Regression cases for user-entered recommended voltage and partial/uncertain documents. */
class RecommendedVoltageTest {
    private val url="https://www.datasheetarchive.com/pdf/LM383.pdf"
    private fun document(body: String,partial: Boolean=false,clipped: Boolean=false,
                         title: String="",security: SecurityStatus=SecurityStatus.PASSED): AnalyzedDocument {
        val candidate=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet",url))).candidates.single()
        val extraction=PdfExtraction(PdfMetadata(title),if(partial) 100 else 1,
            listOf(PageText(1,body,origin=TextOrigin.TEXT_LAYER,truncated=clipped)),partial)
        val sections=DatasheetSectionDetector().detect(extraction.pages)
        val verification=DocumentVerifier().verify("LM383",candidate,extraction,sections)
        return AnalyzedDocument(CheckedDocument(QuarantinedPdf(File("unused.pdf"),url,100,"sha"),1,
            security=security,downloadMs=0,checkMs=0),candidate,extraction,sections,verification,requestedPart="LM383")
    }
    private fun extract(doc: AnalyzedDocument,query: String="recommendation operation voltage")=
        ParameterExtractionEngine().extract(doc,QueryNormalizer().queries(emptySet(),query))
    private val identity="LM383\nNational Semiconductor\nGeneral Description\nFeatures\n"

    @Test fun userSpellingAndTurkishQueriesMapToRecommendedVoltage() {
        for(input in listOf("recommendation operation voltage","recommended operation voltage",
            "recommended operating voltage","recommended operating supply voltage","önerilen çalışma gerilimi")) {
            val q=QueryNormalizer().queries(emptySet(),input).single()
            assertEquals(input,Criterion.SUPPLY_VOLTAGE,q.criterion)
            assertEquals(input,QueryMode.RECOMMENDED_OPERATING,q.mode)
        }
    }
    @Test fun recommendedVoltageWinsOverAbsoluteAndElectricalValues() {
        val doc=document(identity+"Absolute Maximum Ratings\nSupply voltage 28 V\n"+
            "Electrical Characteristics\nSupply voltage 12 V\n"+
            "7.3 Recommended Operating\nConditions (TA = 25 °C)\nSupply voltage 5 to 20 V")
        assertTrue(doc.verification.automaticEligible)
        val result=extract(doc).results.single()
        assertEquals(ParameterStatus.DETECTED,result.status)
        assertEquals(DatasheetSection.RECOMMENDED_OPERATING,result.matches.first().section)
        assertEquals(5.0,result.matches.first().numbers!!.minimum!!,0.0)
        assertEquals(20.0,result.matches.first().numbers!!.maximum!!,0.0)
        assertEquals(28.0,result.absoluteLimits.single().numbers!!.value!!,0.0)
    }
    @Test fun recognizedQualifiedHeadingsStillExcludeProseAndContents() {
        val detector=DatasheetSectionDetector()
        for(heading in listOf("6.3 Recommended Operating Conditions:",
            "Recommended Operating Conditions (continued)",
            "Recommended Operating Conditions over operating free-air temperature range",
            "Recommended Operating Ratings","Recommended Operation Conditions")) {
            assertEquals(heading,DatasheetSection.RECOMMENDED_OPERATING,
                detector.detect(listOf(PageText(1,heading))).single().section)
        }
        assertTrue(detector.detect(listOf(PageText(1,"Recommended Operating Conditions ... 7\n"+
            "Recommended Operating Conditions are explained on page 7"))).isEmpty())
    }
    @Test fun longPdfWithCleanEvidenceDoesNotDisableKnownValues() {
        val doc=document(identity+"Recommended Operating Conditions\nSupply voltage 5 to 20 V",partial=true)
        assertTrue(doc.verification.automaticEligible)
        val report=extract(doc);assertTrue(report.documentEligible)
        assertEquals(ParameterStatus.DETECTED,report.results.single().status)
        assertTrue(report.results.single().matches.first().warnings.any { it.contains("kısmen") })
    }
    @Test fun truncatedIdentityCannotApproveNumbers() {
        val doc=document(identity+"Recommended Operating Conditions\nSupply voltage 5 to 20 V",partial=true,clipped=true,title="LM383")
        val report=extract(doc)
        assertFalse(report.documentEligible);assertTrue(report.results.single().matches.isEmpty())
    }
    @Test fun rawSourceQuoteIsAvailableWhenStructureNeedsReview() {
        val doc=document("LM383\nNational Semiconductor\nSupply voltage 5 to 20 V")
        assertFalse(doc.verification.automaticEligible)
        val report=extract(doc)
        assertFalse(report.documentEligible)
        assertEquals(ParameterStatus.DOCUMENT_UNVERIFIED,report.results.single().status)
        val hit=report.results.single().matches.single()
        assertNull(hit.numbers);assertTrue(hit.rawContext.contains("5 to 20 V"));assertTrue(hit.confidence<=59)
    }
    @Test fun wrongPartAndFamilyVariantDoNotProduceReviewExcerpts() {
        for(part in listOf("LM386","LM383A")) {
            val doc=document("$part\nNational Semiconductor\nRecommended Operating Conditions\nSupply voltage 5 to 20 V")
            assertFalse(doc.verification.automaticEligible)
            assertTrue(extract(doc).results.single().matches.isEmpty())
        }
    }
    @Test fun metadataOnlyOrFailedSecurityDoesNotUnlockSourceQuote() {
        val metadata=document("Supply voltage 5 to 20 V",title="LM383")
        assertTrue(extract(metadata).results.single().matches.isEmpty())
        val failed=document("LM383\nSupply voltage 5 to 20 V",security=SecurityStatus.REJECTED)
        assertTrue(extract(failed).results.single().matches.isEmpty())
    }
    @Test fun recommendedQueryDoesNotPresentOtherSectionAsRecommended() {
        val result=extract(document(identity+"Electrical Characteristics\nSupply voltage 12 V")).results.single()
        assertEquals(ParameterStatus.POSSIBLE,result.status)
        assertTrue(result.explanation.contains("Önerilen çalışma koşulları bölümünde eşleşme bulunamadı"))
    }
    @Test fun explicitMaximumQueryRetainsAbsoluteLimit() {
        val result=extract(document(identity+"Absolute Maximum Ratings\nSupply voltage 28 V\n"+
            "Recommended Operating Conditions\nSupply voltage 5 to 20 V"),"maximum supply voltage").results.single()
        assertEquals(DatasheetSection.ABSOLUTE_MAXIMUM,result.matches.first().section)
        assertEquals(28.0,result.matches.first().numbers!!.value!!,0.0)
    }
    @Test fun clippedValuePageStaysPossibleEvenWithCleanDocumentIdentity() {
        val doc=document(identity+"Recommended Operating Conditions")
        val ex=doc.extraction.copy(totalPages=2,pages=doc.extraction.pages+
            PageText(2,"Supply voltage 5 to 20 V",truncated=true),truncated=true)
        val sections=DatasheetSectionDetector().detect(ex.pages)
        val updated=doc.copy(extraction=ex,sections=sections,
            verification=DocumentVerifier().verify("LM383",doc.candidate,ex,sections))
        assertTrue(updated.verification.automaticEligible)
        val result=extract(updated).results.single()
        assertEquals(ParameterStatus.POSSIBLE,result.status)
        assertTrue(result.matches.first().confidence<=74)
    }
    @Test fun clippedHeadingsDoNotIncreaseDocumentApprovalScore() {
        val doc=document("LM383\nRecommended Operating Conditions\nSupply voltage 5 to 20 V")
        val ex=doc.extraction.copy(totalPages=2,pages=doc.extraction.pages+
            PageText(2,"General Description\nFeatures\nElectrical Characteristics",truncated=true),truncated=true)
        val sections=DatasheetSectionDetector().detect(ex.pages)
        val v=DocumentVerifier().verify("LM383",doc.candidate,ex,sections)
        assertEquals(75,v.score);assertFalse(v.automaticEligible)
    }
    @Test fun conflictingManufacturerDoesNotUnlockReviewExcerpts() {
        val doc=document("LM383\nNational Semiconductor\nSupply voltage 5 to 20 V")
        val source=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet","https://www.st.com/LM383.pdf"))).candidates.single()
        val v=DocumentVerifier().verify("LM383",source,doc.extraction,doc.sections)
        assertEquals(ManufacturerConsistency.CONFLICT,v.manufacturer.consistency)
        assertTrue(extract(doc.copy(candidate=source,verification=v)).results.single().matches.isEmpty())
    }
}
