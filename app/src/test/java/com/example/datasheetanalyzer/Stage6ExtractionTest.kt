package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.security.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class Stage6ExtractionTest {
    private val parser=EngineeringValueParser()
    private fun doc(text: String,eligible: Boolean=true,origin: TextOrigin=TextOrigin.TEXT_LAYER): AnalyzedDocument {
        val page=PageText(1,text,text,origin)
        val extraction=PdfExtraction(PdfMetadata(),1,listOf(page))
        val candidate=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet","https://ti.com/LM383.pdf"))).candidates.single()
        return AnalyzedDocument(CheckedDocument(QuarantinedPdf(File("unused.pdf"),"https://ti.com/LM383.pdf",100,"sha"),1,downloadMs=0,checkMs=0),candidate,
            extraction,DatasheetSectionDetector().detect(extraction.pages),DocumentVerification("EXACT",DocumentStatus.VERIFIED,100,PartMatch.EXACT,
            ManufacturerFinding(listOf("Texas Instruments"),ManufacturerConsistency.CONSISTENT),eligible,emptyList(),emptyList()))
    }
    private fun query(c: Criterion)=QueryNormalizer().queries(setOf(c),"").single()
    @Test fun turkishEnglishAndMultipleQueries() {
        val q=QueryNormalizer().queries(emptySet(),"besleme gerilimi kaç, input offset voltage ve slew rate")
        assertEquals(3,q.size);assertEquals(Criterion.SUPPLY_VOLTAGE,q[0].criterion)
        assertEquals(Criterion.INPUT_OFFSET_VOLTAGE,q[1].criterion);assertNull(q[2].criterion)
    }
    @Test fun maximumVoltageRetainsSpecificCriterion() {
        val q=QueryNormalizer().queries(emptySet(),"maksimum besleme gerilimi").single()
        assertEquals(Criterion.SUPPLY_VOLTAGE,q.criterion);assertEquals(QueryMode.ABSOLUTE_MAXIMUM,q.mode)
    }
    @Test fun mixedUnitRangeIsConverted() {
        val n=parser.parse("Input voltage 500 mV to 2 V")!!.numbers
        assertEquals(500.0,n.minimum!!,0.0);assertEquals(2000.0,n.maximum!!,0.0);assertEquals("mV",n.unit.symbol)
    }
    @Test fun negativeTemperatureRange() {
        val n=parser.parse("Operating temperature -40 to +85 °C")!!.numbers
        assertEquals(-40.0,n.minimum!!,0.0);assertEquals(85.0,n.maximum!!,0.0)
    }
    @Test fun minTypMaxAndConditionsWithBlankCell() {
        val n=parser.parse("Supply current | VCC = 5 V, TA = 25 °C | - | 8 | 12 | mA",
            listOf("Parameter | Test conditions | Min | Typ | Max | Units"))!!
        assertNull(n.numbers.minimum);assertEquals(8.0,n.numbers.typical!!,0.0)
        assertEquals(12.0,n.numbers.maximum!!,0.0);assertEquals("VCC = 5 V, TA = 25 °C",n.conditions)
    }
    @Test fun lostColumnsAreNotGuessed() {
        assertNull(parser.parse("Supply current 8 mA",listOf("Parameter Min Typ Max Units")))
        assertNull(parser.parse("Supply current 12 8 6 mA",listOf("Parameter Min Typ Max Units")))
    }
    @Test fun testConditionAndCompoundUnitAreNotValues() {
        assertNull(parser.parse("Supply current VCC = 5 V"))
        assertNull(parser.parse("Slew rate 3 V/µs"))
        assertNull(parser.parse("Supply voltage 3 V and 5 V"))
    }
    @Test fun requiredUnitsAndScales() {
        for(u in listOf("V","mV","µV","A","mA","µA","W","mW","Ω","kΩ","MΩ","Hz","kHz","MHz","GHz","F","µF","nF","pF","°C","s","ms","µs","ns","dB"))
            assertNotNull(u,parser.parse("Parameter 2 $u"))
        assertEquals(1e-6,parser.unit("µA")!!.scale,0.0)
        assertEquals(1e6,parser.unit("MΩ")!!.scale,0.0)
    }
    @Test fun recommendedValueWinsAbsoluteSeparate() {
        val d=doc("Absolute Maximum Ratings\nSupply voltage 28 V\nRecommended Operating Conditions\nSupply voltage 5 to 20 V")
        val r=ParameterExtractionEngine().extract(d,listOf(query(Criterion.SUPPLY_VOLTAGE))).results.single()
        assertEquals(ParameterStatus.DETECTED,r.status);assertEquals(20.0,r.matches.first().numbers!!.maximum!!,0.0)
        assertEquals(28.0,r.absoluteLimits.single().numbers!!.value!!,0.0)
    }
    @Test fun absoluteOnlyIsNeverNormalConfirmedValue() {
        val r=ParameterExtractionEngine().extract(doc("Absolute Maximum Ratings\nSupply voltage 28 V"),listOf(query(Criterion.SUPPLY_VOLTAGE))).results.single()
        assertEquals(ParameterStatus.POSSIBLE,r.status);assertTrue(r.explanation.contains("yalnızca mutlak"))
    }
    @Test fun explicitMaximumFindsAbsoluteSection() {
        val q=QueryNormalizer().queries(emptySet(),"maximum supply voltage").single()
        val r=ParameterExtractionEngine().extract(doc("Absolute Maximum Ratings\nSupply voltage 28 V\nRecommended Operating Conditions\nSupply voltage 5 to 20 V"),listOf(q)).results.single()
        assertEquals(DatasheetSection.ABSOLUTE_MAXIMUM,r.matches.first().section)
    }
    @Test fun unverifiedDocumentHasNoTechnicalValues() {
        val report=ParameterExtractionEngine().extract(doc("Recommended Operating Conditions\nSupply voltage 5 to 20 V",false),listOf(query(Criterion.SUPPLY_VOLTAGE)))
        assertFalse(report.documentEligible);assertTrue(report.results.single().matches.isEmpty())
        assertEquals(ParameterStatus.DOCUMENT_UNVERIFIED,report.results.single().status)
    }
    @Test fun ocrNumbersRemainPossible() {
        val r=ParameterExtractionEngine().extract(doc("Recommended Operating Conditions\nSupply voltage 5 to 20 V",origin=TextOrigin.OCR),listOf(query(Criterion.SUPPLY_VOLTAGE))).results.single()
        assertEquals(ParameterStatus.POSSIBLE,r.status);assertTrue(r.matches.first().confidence<=74)
    }
    @Test fun unknownCriterionUsesActualTextAndMissingValueIsExplicit() {
        val queries=QueryNormalizer().queries(emptySet(),"slew rate, offset voltage")
        val report=ParameterExtractionEngine().extract(doc("Electrical Characteristics\nSlew rate 3 V/µs"),queries)
        assertEquals(ParameterStatus.POSSIBLE,report.results[0].status);assertNull(report.results[0].matches.first().numbers)
        assertEquals(ParameterStatus.NOT_FOUND,report.results[1].status)
    }
    @Test fun combinedHeadingsAndConnectionDiagramRecognized() {
        val sections=DatasheetSectionDetector().detect(listOf(PageText(1,"General Description Features\nConnection Diagram")))
        assertEquals(setOf(DatasheetSection.DESCRIPTION,DatasheetSection.FEATURES,DatasheetSection.PINOUT),sections.map { it.section }.toSet())
        assertTrue(DatasheetSectionDetector().detect(listOf(PageText(1,"The description features 5 V supply"))).isEmpty())
    }
    @Test fun pinoutDoesNotInventOrderedPinNumbers() {
        val r=ParameterExtractionEngine().extract(doc("Connection Diagram\nTop View\nSee package drawing"),listOf(query(Criterion.PINOUT))).results.single()
        assertNull(r.matches.first().numbers);assertTrue(r.matches.first().warnings.any { it.contains("tahmin edilmez") })
    }
    @Test fun descriptiveCriteriaDoNotBecomeNumericParameters() {
        val r=ParameterExtractionEngine().extract(doc("Features 5 V support\nLow noise"),listOf(query(Criterion.FEATURES))).results.single()
        assertNull(r.matches.first().numbers)
        assertTrue(r.matches.first().rawContext.contains("5 V"))
    }

}
