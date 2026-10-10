package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PdfReading106Test {
    private fun g(t: String,x: Float,y: Float,w: Float=20f)=PositionedGlyph(t,x,y,w)
    private fun geometry(kind: String="standard")=listOf(g("Recommended Operating Conditions",25f,20f,200f),
        g("Parameter",25f,40f,70f),g("Test conditions",160f,40f,80f))+
        when(kind) {
            "limits" -> listOf(g("Min.",300f,40f),g("Max.",400f,40f),g("Unit",450f,40f))
            "value" -> listOf(g("Value",350f,40f),g("Unit",450f,40f))
            else -> listOf(g("Min",300f,40f),g("Typ",350f,40f),g("Max",400f,40f),g("Unit",450f,40f))
        }
    private fun parsed(text: String): ParsedValues {
        val lines=text.trim().lines();return EngineeringValueParser().parse(lines.last(),lines.dropLast(1))!!
    }
    private fun doc(text: String,part: String="LM383"): AnalyzedDocument {
        val ex=PdfExtraction(PdfMetadata(),1,TextCleaner().clean(listOf(PageText(1,text))))
        return ExportFixture.document().copy(extraction=ex,sections=DatasheetSectionDetector().detect(ex.pages),requestedPart=part)
    }
    private fun current(text: String)=ParameterExtractionEngine().extract(doc(text),
        listOf(ParameterQuery("Supply current",Criterion.SUPPLY_CURRENT,listOf("Supply current")))).results.single()
    @Test fun minMaxAndUnicodeMinusPreserveTheirColumns() {
        val n=parsed(PositionedTableReader().tables(geometry("limits")+listOf(g("Supply voltage",25f,60f,100f),
            g("–0.5",300f,60f),g("7",400f,60f),g("V",450f,60f)))).numbers
        assertEquals(-0.5,n.minimum!!,0.0);assertEquals(7.0,n.maximum!!,0.0);assertNull(n.typical)
    }
    @Test fun valueHeaderReadsRangeAndDcAlias() {
        val n=parsed(PositionedTableReader().tables(geometry("value")+listOf(g("Supply voltage",25f,60f,100f),
            g("−0.3 to 40",345f,60f,45f),g("Vdc",450f,60f)))).numbers
        assertEquals(-0.3,n.minimum!!,0.0);assertEquals(40.0,n.maximum!!,0.0);assertEquals("V",n.unit.symbol)
    }
    @Test fun symmetricSingleValueKeepsSymmetricQualifier() {
        val n=parsed(PositionedTableReader().tables(geometry("value")+listOf(g("Output current",25f,60f,100f),
            g("±20",350f,60f),g("mA",450f,60f)))).numbers
        assertEquals(20.0,n.value!!,0.0);assertTrue(n.symmetric)
    }
    @Test fun unitsOnSeparateBaselineAreReadOnlyWithExplicitHeading() {
        val gs=geometry().filterNot { it.text=="Unit" }+g("Unit",450f,34f)+
            listOf(g("Supply current",25f,60f,100f),g("8",350f,60f),g("12",400f,60f),g("mA",450f,60f))
        val n=parsed(PositionedTableReader().tables(gs)).numbers
        assertNull(n.minimum);assertEquals(8.0,n.typical!!,0.0);assertEquals(12.0,n.maximum!!,0.0)
    }
    @Test fun wrappedParameterLabelAndUnitAreJoinedLocally() {
        val gs=geometry()+listOf(g("Supply current",25f,54f,100f),g("8",350f,60f),
            g("12",400f,60f),g("mA",450f,66f))
        val text=PositionedTableReader().tables(gs)
        assertTrue(text.contains("Supply current"));assertEquals(8.0,parsed(text).numbers.typical!!,0.0)
    }
    @Test fun wrappedTestConditionIsKeptOutOfNumericCells() {
        val gs=geometry()+listOf(g("VCC = 5 V",160f,54f,70f),g("Supply current",25f,60f,100f),
            g("8",350f,60f),g("12",400f,60f),g("mA",450f,60f))
        val p=parsed(PositionedTableReader().tables(gs))
        assertEquals("VCC = 5 V",p.conditions);assertEquals(8.0,p.numbers.typical!!,0.0)
    }
    @Test fun explicitFootnoteDoesNotTurnIntoValue() {
        val gs=geometry()+listOf(g("Supply current",25f,60f,100f),g("8 (1)",350f,60f,22f),
            g("12",400f,60f),g("mA",450f,60f))
        assertEquals(8.0,parsed(PositionedTableReader().tables(gs)).numbers.typical!!,0.0)
    }
    @Test fun longTableContinuesPastOld240PointLimit() {
        val gs=geometry()+(1..18).flatMap { i -> listOf(g("Supply current",25f,40f+i*16,100f),
            g(i.toString(),350f,40f+i*16),g("mA",450f,40f+i*16)) }
        val t=PositionedTableReader().tables(gs)
        assertTrue(t.contains("| 18 |"));assertEquals(18.0,parsed(t).numbers.typical!!,0.0)
    }
    private fun variantTable(withScopes: Boolean=true)=listOf(g("Recommended Operating Conditions",25f,20f,200f))+
        (if(withScopes) listOf(g("SN54HC595",302f,30f,60f),g("SN74HC595",397f,30f,60f)) else emptyList())+
        listOf(g("Unit",490f,36f),g("Min",300f,44f),g("Nom",330f,44f),g("Max",360f,44f),
            g("Min",400f,44f),g("Nom",430f,44f),g("Max",460f,44f),
            g("Supply voltage",25f,64f,100f),g("2",300f,64f),g("5",330f,64f),g("6",360f,64f),
            g("2",400f,64f),g("5",430f,64f),g("6",460f,64f),g("V",490f,64f))
    @Test fun duplicateColumnGroupsNeedLiteralPartHeadings() {
        assertTrue(PositionedTableReader().tables(variantTable(false)).isBlank())
    }
    @Test fun nominalIsSeparateFromTypicalAndPartScopeIsPreserved() {
        val p=parsed(PositionedTableReader().tables(variantTable()))
        assertEquals("SN74HC595",p.partScope);assertNull(p.numbers.typical)
        assertEquals(5.0,p.numbers.value!!,0.0);assertTrue(p.numbers.nominal)
        assertEquals("Nominal 5",ResultPresentation.values(p.numbers)[3])
    }
    @Test fun onlySelectedVariantColumnCanSupplyNumbers() {
        val text="SN54HC595 SN74HC595\n"+PositionedTableReader().tables(variantTable())
        val r=ParameterExtractionEngine().extract(doc(text,"SN74HC595"),
            QueryNormalizer().queries(emptySet(),"recommended operating voltage")).results.single()
        assertEquals(ParameterStatus.DETECTED,r.status)
        assertEquals(1,r.matches.size);assertTrue(r.matches.single().evidenceLine.contains("SN74HC595"))
    }
    @Test fun otherGradeCaptionDoesNotApplyToGenericPart() {
        val r=current("Electrical Characteristics: LM383A\nSupply current 5 mA")
        assertEquals(ParameterStatus.NOT_FOUND,r.status)
        val same=current("Electrical Characteristics: LM383\nSupply current 5 mA")
        assertEquals(ParameterStatus.DETECTED,same.status)
    }
    @Test fun structuredRowTakesPriorityOverFlattenedDuplicate() {
        val r=current("Recommended Operating Conditions\nSupply current 12 mA\n[Native table columns]\n"+
            "Recommended Operating Conditions\nParameter | Test conditions | Min | Typ | Max | Units\nSupply current | VCC = 5 V | | 8 | 12 | mA")
        assertEquals(ParameterStatus.DETECTED,r.status);assertEquals(1,r.matches.size)
        assertNull(r.matches.single().numbers!!.minimum);assertEquals(8.0,r.matches.single().numbers!!.typical!!,0.0)
    }
    @Test fun splitKeywordAndNumericUnitLineKeepJoinedEvidence() {
        val r=current("Recommended Operating Conditions\nSupply\ncurrent\n5 mA")
        assertEquals(ParameterStatus.DETECTED,r.status);assertEquals("Supply current 5 mA",r.matches.first().evidenceLine)
    }
    @Test fun continuationCannotStealAnotherParametersWords() {
        val result=current("Recommended Operating Conditions\nSupply current\n5 mA input current")
        assertEquals(ParameterStatus.POSSIBLE,result.status)
        assertTrue(result.matches.all { it.numbers==null })
        assertFalse(EngineeringValueParser().isValueContinuation("5 mA input current"))
    }
    @Test fun malformedPipeCannotFallBackToLooseNumber() {
        assertNull(EngineeringValueParser().parse("Supply current | 8 mA | bad",listOf("Parameter | Typ | Units")))
        assertNull(EngineeringValueParser().parse("Supply current | | 12 | mA",listOf("Parameter | Min | Min | Units")))
    }
    @Test fun unsupportedCompositeUnitsAreNotReducedToTemperatureOrVoltage() {
        assertNull(EngineeringValueParser().parse("Drift 2 mV/°C"))
        assertNull(EngineeringValueParser().parse("Thermal resistance 5 °C/W"))
    }
    @Test fun nanoampAndMilliohmUnitsKeepTheirScale() {
        val p=EngineeringValueParser()
        assertEquals(1e-9,p.parse("Input bias current 10 nA")!!.numbers.unit.scale,0.0)
        assertEquals(1e-3,p.parse("Output resistance 17 mΩ")!!.numbers.unit.scale,0.0)
    }
    @Test fun explicitTableCaptionAndContinuedHeadingAreSections() {
        val hits=DatasheetSectionDetector().detect(listOf(PageText(4,"Table 3. Electrical characteristics of L7805A\n5.5 Electrical Characteristics: LM358B and LM358BA (continued)")))
        assertEquals(2,hits.size);assertTrue(hits.all { it.section==DatasheetSection.ELECTRICAL })
        assertTrue(DatasheetSectionDetector().detect(listOf(PageText(1,"Electrical Characteristics ........ 6"))).isEmpty())
    }
    @Test fun sparseTechnicalPageAfterFrontMatterCanBeOcrTarget() {
        val ex=PdfExtraction(PdfMetadata(),10,(1..10).map { PageText(it,if(it in setOf(7,10)) "" else "Native text ".repeat(30)) })
        val sections=listOf(SectionHit(DatasheetSection.ELECTRICAL,6,0,"Electrical Characteristics"))
        assertEquals(listOf(7),OcrPagePlanner().extraPages(ex,emptySet(),ExportFixture.document().verification,sections))
        assertTrue(OcrPagePlanner().extraPages(ex,setOf(7),ExportFixture.document().verification,sections).isEmpty())
        assertTrue(OcrPagePlanner(ExtractionConfig(maxOcrPages=0)).extraPages(ex,emptySet(),ExportFixture.document().verification,sections).isEmpty())
    }
    @Test fun headerFootnotesDoNotDeleteTypicalColumn() {
        val gs=geometry().map { if(it.text=="Typ") it.copy(text="Typ(2)") else it }+
            listOf(g("Supply current",25f,60f,100f),g("8",350f,60f),g("12",400f,60f),g("mA",450f,60f))
        val n=parsed(PositionedTableReader().tables(gs)).numbers
        assertNull(n.minimum);assertEquals(8.0,n.typical!!,0.0);assertEquals(12.0,n.maximum!!,0.0)
    }
    @Test fun commaSeparatedForeignPartsFenceOffEntireTable() {
        assertEquals(ParameterStatus.NOT_FOUND,current("5.5 Electrical Characteristics: LM2904, LM2904V\nSupply current 5 mA").status)
        assertEquals(ParameterStatus.DETECTED,current("5.5 Electrical Characteristics: LM383, LM383A\nSupply current 5 mA").status)
    }
    @Test fun undecodedUnitPrefixCannotBecomeAmpere() {
        val damaged="Supply current 50 \u0002A"
        assertTrue(TextCleaner().normalize(damaged).contains('\uFFFD'))
        assertNull(EngineeringValueParser().parse(damaged))
        val gs=geometry()+listOf(g("Supply current",25f,60f,100f),g("50",350f,60f),g("100",400f,60f),g("\u0002A",450f,60f))
        assertTrue(PositionedTableReader().tables(gs).isBlank())
    }
    @Test fun floatingUnitEquidistantBetweenTwoNumericRowsIsAmbiguous() {
        val gs=geometry()+listOf(g("Supply current",25f,60f,100f),g("8",350f,60f),
            g("mA",450f,65f),g("Output current",25f,70f,100f),g("12",400f,70f))
        assertTrue(PositionedTableReader().tables(gs).isBlank())
    }
    @Test fun completeNeighbouringParameterCannotBeJoinedIntoThisLabel() {
        val gs=geometry()+listOf(g("Output current",25f,54f,100f),g("Supply current",25f,60f,100f),
            g("8",350f,60f),g("mA",450f,60f))
        val text=PositionedTableReader().tables(gs)
        assertFalse(text.contains("Output current Supply current"))
    }
    @Test fun nominalCsvKeepsTypicalBlankAndLabelsValueQualifier() {
        val hit=ExportFixture.result().matches.first().copy(numbers=NumericValues(minimum=2.0,maximum=6.0,value=5.0,unit=ExportFixture.unit,nominal=true))
        val r=ExportFixture.result().copy(matches=listOf(hit),absoluteLimits=emptyList())
        val saved=com.example.datasheetanalyzer.data.workspace.SavedAnalysis.capture("LM383",ExportFixture.document(),ParameterReport(listOf(r),true))
        val csv=com.example.datasheetanalyzer.export.AnalysisCsvWriter.analyses(listOf(saved))
        assertTrue(csv.contains("\"2\";\"\";\"6\";\"5\";\"V\""))
        assertTrue(csv.contains("nominal değerdir"))
    }

    @Test fun sharedTableScopeAllowsCommonRowButRejectsGradeSpecificRow() {
        val n=NumericValues(typical=0.7,unit=EngineeringUnit("MHz",UnitDimension.FREQUENCY,1e6),structured=true)
        val h=ExportFixture.result().matches.first().copy(numbers=n,sourcePartScope="LM358 LM358A",
            evidenceLine="LM358 LM358A: GBW Gain bandwidth product | | LM358 LM358A | | 0.7 | | MHz",conditions="")
        val r=ExportFixture.result().copy(matches=listOf(h),absoluteLimits=emptyList())
        assertNotNull(VariantResultGuard.apply("LM358",r).matches.single().numbers)
        assertNull(VariantResultGuard.apply("LM358",r.copy(matches=listOf(h.copy(conditions="LM358A")))).matches.single().numbers)
        assertNull(VariantResultGuard.apply("LM358A",r.copy(matches=listOf(h.copy(conditions="LM358")))).matches.single().numbers)
        assertNull(VariantResultGuard.apply("LM2904",r).matches.single().numbers)
    }
    @Test fun centredConditionsHeaderUsesExplicitEquationLeftEdge() {
        val gs=geometry().filterNot { it.text=="Test conditions" }+g("Test conditions",240f,40f,50f)+
            listOf(g("Supply current",25f,60f,100f),g("VCC = 5 V",160f,60f,70f),g("8",350f,60f),g("mA",450f,60f))
        val p=parsed(PositionedTableReader().tables(gs))
        assertEquals("VCC = 5 V",p.conditions);assertEquals(8.0,p.numbers.typical!!,0.0)
    }

    @Test fun wrappedGradeCaptionKeepsScopeAndTocDoesNotBecomeSection() {
        assertEquals(ParameterStatus.NOT_FOUND,current("Electrical\nCharacteristics: LM2904, LM2904V\nSupply current 5 mA").status)
        assertEquals(ParameterStatus.DETECTED,current("Electrical\nCharacteristics: LM383, LM383A\nSupply current 5 mA").status)
        assertTrue(DatasheetSectionDetector().detect(listOf(PageText(1,"Electrical Characteristics: LM358B and\nLM358BA ........ 6"))).isEmpty())
    }
    @Test fun conditionBetweenTwoNumericRowsIsReviewedWithoutAssigningToBoth() {
        val gs=geometry()+listOf(g("Supply current",25f,60f,100f),g("8",350f,60f),g("mA",450f,60f),
            g("TA = 85 C",160f,66f,60f),g("Supply current",25f,72f,100f),g("12",400f,72f),g("mA",450f,72f))
        val text=PositionedTableReader().tables(gs)
        val p=parsed(text);assertTrue(p.requiresReview);assertFalse(p.conditions.contains("85"))
        val r=current(text);assertEquals(ParameterStatus.POSSIBLE,r.status)
        assertTrue(r.matches.all { it.confidence<=74 })
    }

    @Test fun controlGlyphsThatResembleWhitespaceCannotHideMissingUnitPrefix() {
        for(c in listOf(9,10,11,12,13,14,31).map(Int::toChar)) {
            val gs=geometry()+listOf(g("Supply current",25f,60f,100f),g("50",350f,60f),g("100",400f,60f),g("${c}A",450f,60f))
            assertTrue("Control glyph ${c.code}",PositionedTableReader().tables(gs).isBlank())
        }
    }

}
