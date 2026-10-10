package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.export.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

internal object ExportFixture {
    val unit=EngineeringUnit("V",UnitDimension.VOLTAGE,1.0)
    fun document(eligible: Boolean=true,partial: Boolean=false): AnalyzedDocument {
        val url="https://www.datasheetarchive.com/pdf/LM383.pdf"
        val c=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet",url))).candidates.single()
        val text="LM383\nNational Semiconductor\nRecommended Operating Conditions\nSupply voltage 5 to 20 V"
        val ex=PdfExtraction(PdfMetadata(),if(partial) 100 else 1,listOf(PageText(1,text)),partial)
        val v=DocumentVerification("EXACT",DocumentStatus.VERIFIED,100,PartMatch.EXACT,
            ManufacturerFinding(listOf("National Semiconductor"),ManufacturerConsistency.CONSISTENT),eligible,
            listOf(TextEvidence(1,"LM383",TextOrigin.TEXT_LAYER)),listOf("Belge bölümlerini kaynak sayfasında incele."))
        return AnalyzedDocument(CheckedDocument(QuarantinedPdf(File("unused"),url,100,"sha"),1,downloadMs=0,checkMs=0),
            c,ex,DatasheetSectionDetector().detect(ex.pages),v,requestedPart="LM383")
    }
    fun result(status: ParameterStatus=ParameterStatus.DETECTED): ParameterResult {
        val query=QueryNormalizer().queries(emptySet(),"recommended operating voltage").single()
        val hit=ExtractedParameter(query.requested,"Besleme gerilimi",NumericValues(minimum=5.0,typical=12.0,maximum=20.0,unit=unit,structured=true),
            DatasheetSection.RECOMMENDED_OPERATING,1,100,"Supply voltage | 5 | 12 | 20 | V","Supply voltage | 5 | 12 | 20 | V",
            "TA = 25 °C, VCC = 12 V",warnings=listOf("Tipik değer garanti edilen sınır değildir."))
        val absolute=hit.copy(numbers=NumericValues(value=28.0,unit=unit),section=DatasheetSection.ABSOLUTE_MAXIMUM,
            evidenceLine="Supply voltage 28 V",warnings=listOf("Mutlak sınır normal çalışma değeri değildir."))
        return ParameterResult(query,status,listOf(hit),listOf(absolute),"Belgedeki eşleşme ve kaynak sayfası aşağıda.")
    }
    fun content(eligible: Boolean=true)=AnalysisReportContent.build("LM383",document(eligible),
        ParameterReport(listOf(result(if(eligible) ParameterStatus.DETECTED else ParameterStatus.DOCUMENT_UNVERIFIED)),eligible),"02.10.2026 15:45")
}

class ReportContentTest {
    private fun contentText(blocks: List<ReportBlock>)=blocks.joinToString("\n") { block ->
        block.text+"\n"+(block.table?.let { (listOf(it.headers)+it.rows).joinToString("\n") { row -> row.joinToString(" | ") } } ?: "")
    }
    @Test fun reportIncludesUnitsConditionsEvidenceAndSeparateAbsoluteLimits() {
        val blocks=ExportFixture.content()
        val text=contentText(blocks)
        for(expected in listOf("Min | Tipik | Maks | Değer | Birim","5 | 12 | 20 | - | V","TA = 25 °C","kaynak s.1",
            "Supply voltage | 5 | 12 | 20 | V","Mutlak maksimum sınır - normal çalışma değeri değildir","- | - | - | 28 | V"))
            assertTrue(expected,text.contains(expected))
        assertTrue(blocks.any { it.table?.headers==listOf("Kriter","Durum","Sonuç") })
    }
    @Test fun unverifiedNumbersCannotBecomeApprovedReportFields() {
        val blocks=ExportFixture.content(false)
        val text=contentText(blocks)
        assertTrue(text.contains("Belge doğrulaması tamamlanmadı"))
        assertTrue(text.contains("Supply voltage | 5 | 12 | 20 | V"))
        assertFalse(text.contains("Min:"));assertFalse(text.contains("Tipik:"));assertFalse(text.contains("Maks:"));assertFalse(text.contains("Değer:"))
        assertFalse(blocks.any { it.table?.headers?.contains("Min")==true })
    }
    @Test fun emptyTableCellsStayEmptyAndMissingCriteriaRemainExplicit() {
        val result=ExportFixture.result().let { it.copy(matches=listOf(it.matches.single().copy(
            numbers=NumericValues(typical=12.0,unit=ExportFixture.unit))),absoluteLimits=emptyList()) }
        val missing=result.copy(query=ParameterQuery("slew rate",null,listOf("slew rate")),status=ParameterStatus.NOT_FOUND,matches=emptyList())
        val blocks=AnalysisReportContent.build("LM383",ExportFixture.document(true,true),
            ParameterReport(listOf(result,missing),true),"today")
        val text=contentText(blocks)
        val numeric=blocks.single { it.table?.headers==listOf("Min","Tipik","Maks","Değer","Birim") }.table!!
        assertEquals(listOf("-","12","-","-","V"),numeric.rows.single())
        assertTrue(text.contains("Bulunamadı"));assertTrue(text.contains("kısmen okundu"))
    }
    @Test fun filenamesDoNotContainPathSeparatorsOrUnboundedInput() {
        val name=AnalysisReportContent.filename("../../LM383\n?".repeat(50),"analiz","pdf")
        assertFalse(name.contains('/'));assertFalse(name.contains('\n'));assertTrue(name.length<100);assertTrue(name.endsWith("-analiz.pdf"))
    }
}
