package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.domain.security.SecurityStatus
import org.junit.Assert.*
import org.junit.Test

class PdfReading108Test {
    private val parser=EngineeringValueParser()
    private fun query(c: Criterion)=QueryNormalizer().queries(setOf(c),"").single()
    private fun document(text: String,part: String="LM383"): AnalyzedDocument {
        val ex=PdfExtraction(PdfMetadata(),1,TextCleaner().clean(listOf(PageText(1,text))))
        return ExportFixture.document().copy(extraction=ex,sections=DatasheetSectionDetector().detect(ex.pages),requestedPart=part)
    }
    private fun extract(text: String,c: Criterion)=ParameterExtractionEngine().extract(document(text),listOf(query(c))).results.single()
    private fun g(t: String,x: Float,y: Float,w: Float=20f,space: Float=6f)=PositionedGlyph(t,x,y,w,space)
    private val header=listOf(g("Electrical Characteristics",25f,20f,200f),g("Parameter",25f,40f,70f),
        g("Conditions",160f,40f,70f),g("Min",300f,40f),g("Typ",350f,40f),g("Max",400f,40f),g("Units",450f,40f))
    private fun parsedRows(text: String)=text.lines().mapIndexedNotNull { i,line ->
        if('|' in line) parser.parse(line,text.lines().take(i)) else null
    }
    private val mergedRules=listOf(25f,150f,280f,440f,490f).map { TableRule(it,30f,it,80f) }+
        listOf(30f,50f,80f).map { TableRule(25f,it,490f,it) }+TableRule(150f,65f,440f,65f)

    @Test fun vosAndVioSymbolsMatchInsideScopedParameterCells() {
        for(symbol in listOf("VOS","V_OS","VIO","V_IO","VIOS")) {
            val r=extract("Electrical Characteristics\nParameter | Test conditions | Part | Min | Typ | Max | Units\nLM383: $symbol | | LM383 | | 0.6 | 2 | mV",Criterion.INPUT_OFFSET_VOLTAGE)
            assertEquals(symbol,ParameterStatus.DETECTED,r.status);assertEquals(0.6,r.matches.first().numbers!!.typical!!,0.0)
        }
    }
    @Test fun turkishOffsetNamesResolveToSameCriterion() {
        for(name in listOf("giriş ofset voltajı","giriş offset gerilimi","v_io"))
            assertEquals(name,Criterion.INPUT_OFFSET_VOLTAGE,QueryNormalizer().queries(emptySet(),name).single().criterion)
    }
    @Test fun coordinateTableWithOnlyAnOffsetSymbolKeepsBlankMinimum() {
        for(symbol in listOf("VOS","V_IO")) {
            val text=PositionedTableReader().tables(header+listOf(g(symbol,25f,60f),g("0.6",350f,60f),g("2",400f,60f),g("mV",450f,60f)))
            val r=extract(text,Criterion.INPUT_OFFSET_VOLTAGE)
            assertEquals(symbol,ParameterStatus.DETECTED,r.status)
            assertNull(r.matches.first().numbers!!.minimum);assertEquals(0.6,r.matches.first().numbers!!.typical!!,0.0)
        }
    }
    @Test fun extraBareTableNumbersAreNeverLooseScalars() {
        assertNull(parser.parse("Input offset voltage 3 7 mV"))
        assertNotNull(parser.parse("Input offset voltage 7 mV"))
    }
    @Test fun voltageOffsetDoesNotMatchDriftOrRejectionRows() {
        for(label in listOf("Input offset voltage drift","Temperature coefficient of input offset voltage","PSRR Input offset voltage vs power supply"))
            assertFalse(label,CriterionLabelMatcher.matches(label,query(Criterion.INPUT_OFFSET_VOLTAGE)))
    }
    @Test fun gainDoesNotMatchGainBandwidth() {
        assertFalse(CriterionLabelMatcher.matches("GBW Gain bandwidth product",query(Criterion.GAIN)))
        assertTrue(CriterionLabelMatcher.matches("AOL Open-loop voltage gain",query(Criterion.GAIN)))
    }
    @Test fun conditionCellCannotSupplyAnotherParameterLabel() {
        assertFalse(CriterionLabelMatcher.matches("Input offset voltage | Output voltage = 1.4 V | 3 | mV",query(Criterion.OUTPUT_VOLTAGE)))
    }
    @Test fun sectionCaptionIsNotStrippedAsPartPrefix() {
        assertTrue(CriterionLabelMatcher.matches("Electrical Characteristics: LM383",query(Criterion.ELECTRICAL_CHARACTERISTICS)))
    }
    @Test fun fiveCriteriaAreExtractedIndependentlyAndBlanksStayBlank() {
        val text="Electrical Characteristics\nParameter | Test conditions | Part | Min | Typ | Max | Units\n"+
            "LM383: Supply voltage | | LM383 | 5 | | 20 | V\n"+
            "LM383: Common-mode voltage range | | LM383 | -8 | | 8 | V\n"+
            "LM383: Voltage output swing | RL = 10 kΩ | LM383 | | 12 | | V\n"+
            "LM383: VOS | | LM383 | | 0.6 | 2 | mV\n"+
            "LM383: Large signal voltage gain | | LM383 | 25 | 70 | | V/mV"
        val criteria=setOf(Criterion.SUPPLY_VOLTAGE,Criterion.INPUT_VOLTAGE,Criterion.OUTPUT_VOLTAGE,Criterion.INPUT_OFFSET_VOLTAGE,Criterion.GAIN)
        val r=ParameterExtractionEngine().extract(document(text),QueryNormalizer().queries(criteria,""))
        assertEquals(5,r.results.size);assertTrue(r.results.joinToString(),r.results.all { it.status==ParameterStatus.DETECTED })
        assertNull(r.results.single { it.query.criterion==Criterion.INPUT_OFFSET_VOLTAGE }.matches.first().numbers!!.minimum)
        assertNull(r.results.single { it.query.criterion==Criterion.GAIN }.matches.first().numbers!!.maximum)
    }
    @Test fun foundLabelWithUnresolvedValuesIsAnExplicitSourceQuote() {
        val r=extract("Electrical Characteristics\nParameter Min Typ Max Units\nInput offset voltage 3 7 mV",Criterion.INPUT_OFFSET_VOLTAGE)
        assertEquals(ParameterStatus.POSSIBLE,r.status);assertNull(r.matches.first().numbers)
        assertTrue(r.explanation.contains("adı kaynakta bulundu"));assertTrue(r.matches.first().evidenceLine.contains("3 7 mV"))
    }
    @Test fun absentLabelStillReturnsNotFound() {
        assertEquals(ParameterStatus.NOT_FOUND,extract("Electrical Characteristics\nSupply voltage 5 V",Criterion.INPUT_OFFSET_VOLTAGE).status)
    }
    @Test fun repeatedColumnSetsNeverBecomeLastColumnScalarEvenAfterTwelveLines() {
        val previous=listOf("Parameter Min Typ Max Min Typ Max Min Typ Max Units")+List(25) { "Other parameter" }
        assertNull(parser.parse("Input offset voltage 3.0 6.0 10 mV",previous))
    }
    @Test fun newSectionEndsUnresolvedMultiPartHeader() {
        val r=extract("Electrical Characteristics\nParameter Min Typ Max Min Typ Max Units\nOther row\nRecommended Operating Conditions\nSupply voltage 5 to 20 V",Criterion.SUPPLY_VOLTAGE)
        assertEquals(ParameterStatus.DETECTED,r.status);assertEquals(20.0,r.matches.first().numbers!!.maximum!!,0.0)
    }
    @Test fun linearGainKeepsItsOwnDimensionAndScale() {
        val ratio=parser.parse("Voltage gain 25 V/mV",dimension=UnitDimension.GAIN)!!.numbers
        assertEquals(UnitDimension.GAIN_RATIO,ratio.unit.dimension);assertEquals(1000.0,ratio.unit.scale,0.0)
        assertEquals(UnitDimension.GAIN,parser.parse("Voltage gain 80 dB",dimension=UnitDimension.GAIN)!!.numbers.unit.dimension)
        assertNull(parser.parse("Voltage 25 V/mV",dimension=UnitDimension.VOLTAGE))
    }
    @Test fun opaqueConditionDoesNotDiscardOtherwiseExplicitNumericCells() {
        val p=parser.parse("VOS | TA � 25 �C | | 0.6 | 2 | mV",listOf("Parameter | Test conditions | Min | Typ | Max | Units"))!!
        assertTrue(p.requiresReview);assertNull(p.numbers.minimum);assertEquals(2.0,p.numbers.maximum!!,0.0)
        val label=parser.parse("VOS, TA � 25 �C | | 0.6 | 2 | mV",listOf("Parameter | Min | Typ | Max | Units"))!!
        assertTrue(label.requiresReview);assertNull(label.numbers.minimum);assertEquals(0.6,label.numbers.typical!!,0.0)
    }
    @Test fun opaqueUnitOrResultNeverGetsNumericFields() {
        val h=listOf("Parameter | Test conditions | Min | Typ | Max | Units")
        assertNull(parser.parse("VOS | TA = 25°C | | 0.6 | 2 | �V",h))
        assertNull(parser.parse("VOS | TA = 25°C | | �0.6 | 2 | mV",h))
    }
    @Test fun explicitMergedUnitAndParameterCellsCanServeTwoRows() {
        val text=PositionedTableReader().tables(header+listOf(g("8",350f,60f),g("Supply current",25f,62f,100f),g("mA",450f,62f),g("12",400f,70f)),mergedRules)
        val rows=parsedRows(text);assertEquals(text,2,rows.size)
        assertEquals(8.0,rows[0].numbers.typical!!,0.0);assertNull(rows[0].numbers.maximum)
        assertEquals(12.0,rows[1].numbers.maximum!!,0.0);assertNull(rows[1].numbers.typical)
    }
    @Test fun dividerInsideUnitCellPreventsSharingToAnotherRow() {
        val rules=mergedRules+TableRule(440f,65f,490f,65f)
        val text=PositionedTableReader().tables(header+listOf(g("8",350f,60f),g("Supply current",25f,62f,100f),g("mA",450f,62f),g("12",400f,70f)),rules)
        assertEquals(1,parsedRows(text).size)
    }
    @Test fun missingBorderCannotProveMergedCell() {
        assertNull(TableRuleGrid(mergedRules.filterNot { it.x1==490f && it.x2==490f }).cell(460f,60f))
    }
    @Test fun contiguousDrawnSegmentsProveASingleClosedMergedCell() {
        val rules=listOf(TableRule(440f,50f,440f,65f),TableRule(440f,65f,440f,80f),TableRule(490f,50f,490f,80f),
            TableRule(440f,50f,460f,50f),TableRule(460f,50f,490f,50f),TableRule(440f,80f,490f,80f))
        assertEquals(TableCell(440f,50f,490f,80f),TableRuleGrid(rules).cell(460f,60f))
        assertNull(TableRuleGrid(rules.filterNot { it.x1==460f }).cell(460f,60f))
    }
    @Test fun onlyPureLiteralPartListsArePartCells() {
        assertEquals("LM358 LM358A",TablePartScope.fromPartCell("LM358, LM358A"))
        assertEquals("",TablePartScope.fromPartCell("LM358 VS = 5 V"))
    }
    @Test fun blankSpaceBaselinesDoNotHideTwoLevelHeaders() {
        val gs=header.filterNot { it.text=="Units" }+g("Units",450f,34f)+g(" ",10f,37f)+
            listOf(g("Supply current",25f,60f,100f),g("8",350f,60f),g("mA",450f,60f))
        assertEquals(8.0,parsedRows(PositionedTableReader().tables(gs)).single().numbers.typical!!,0.0)
    }
    @Test fun legacyNarrowWordSpacesStillProduceReadableSectionAndLabel() {
        val glyphs=header.filterNot { it.y==20f }+listOf(g("Electrical",25f,20f,40f,2f),g("Characteristics",67f,20f,80f,2f))+
            listOf(g("Input",25f,60f,20f,2f),g("offset",47f,60f,25f,2f),g("voltage",74f,60f,35f,2f),g("3",350f,60f),g("mV",450f,60f))
        assertTrue(PositionedTableReader().tables(glyphs).contains("Input offset voltage"))
    }
    @Test fun correctGradeNumbersRankAheadOfARejectedGrade() {
        val text="Electrical Characteristics: LM383, LM383A\nParameter | Test conditions | Part | Min | Typ | Max | Units\n"+
            "LM383 LM383A: Input offset voltage | LM383A | LM383 LM383A | | 2 | 3 | mV\n"+
            "LM383 LM383A: Input offset voltage | LM383 | LM383 LM383A | | 3 | 7 | mV"
        val r=extract(text,Criterion.INPUT_OFFSET_VOLTAGE)
        assertEquals(ParameterStatus.DETECTED,r.status);assertEquals(7.0,r.matches.first().numbers!!.maximum!!,0.0)
        assertTrue(r.matches.none { it.numbers?.maximum==3.0 })
    }
    private fun packageDocument(order: String="LM709AH, LM709H or LM709CH",caption: String="Metal Can Package (H)",origin: TextOrigin=TextOrigin.TEXT_LAYER,truncated: Boolean=false): AnalyzedDocument {
        val scopes="Electrical Characteristics\nParameter | Test conditions | Part | Min | Typ | Max | Units\n"+
            listOf("LM709A","LM709","LM709C").joinToString("\n") { "$it: Input offset voltage | | $it | | 0.6 | 2 | mV" }
        val ex=PdfExtraction(PdfMetadata(),2,listOf(PageText(1,scopes),PageText(2,"$caption\nOrder Number $order\nNS Package Number H08C",origin=origin,truncated=truncated)))
        return document(scopes,"LM709AH").copy(extraction=ex,sections=DatasheetSectionDetector().detect(ex.pages),checked=ExportFixture.document().checked.copy(security=SecurityStatus.PASSED))
    }
    @Test fun literalPackageOrderListAndThreeTableGradesPermitDocumentedBinding() {
        val d=packageDocument();val proof=VerifiedPackageScope.find(d)!!
        assertEquals("LM709A",proof.grade);assertEquals(2,proof.page)
        val r=ParameterExtractionEngine().extract(d,listOf(query(Criterion.INPUT_OFFSET_VOLTAGE))).results.single()
        assertEquals(ParameterStatus.DETECTED,r.status);assertEquals("LM709A",r.matches.first().sourcePartScope)
        assertTrue(r.matches.first().warnings.first().contains("H paketi, kaynak s.2"))
    }
    @Test fun suffixIsNotGuessedWithoutLiteralPackageCaption() {
        assertNull(VerifiedPackageScope.find(packageDocument(caption="Metal can package")))
        assertNull(VerifiedPackageScope.find(packageDocument(order="LM709AH")))
        assertFalse(TablePartScope.allows("LM709AH","LM709A"))
    }
    @Test fun ocrTruncatedOrUnverifiedPackageMappingCannotAuthorizeGrade() {
        assertNull(VerifiedPackageScope.find(packageDocument(origin=TextOrigin.OCR)))
        assertNull(VerifiedPackageScope.find(packageDocument(truncated=true)))
        val d=packageDocument();assertNull(VerifiedPackageScope.find(d.copy(verification=d.verification.copy(automaticEligible=false))))
        assertNull(VerifiedPackageScope.find(d.copy(checked=d.checked.copy(security=SecurityStatus.REJECTED))))
    }
    @Test fun mixedPackageOrderListCannotAuthorizeSuffixRemoval() {
        assertNull(VerifiedPackageScope.find(packageDocument(order="LM709AH, LM709H or LM709CN")))
        assertNull(VerifiedPackageScope.find(packageDocument().copy(requestedPart="LM709AZ")))
    }
    @Test fun ocrReplacementCanRetainAlreadyReadNativeScopeProof() {
        val d=packageDocument();val page=d.extraction.pages.first()
        val retained=page.copy(origin=TextOrigin.OCR,rawText="OCR text",text="OCR text",nativeRawText=page.rawText)
        assertEquals("LM709A",VerifiedPackageScope.find(d.copy(extraction=d.extraction.copy(pages=listOf(retained,d.extraction.pages[1]))))!!.grade)
    }
    @Test fun packageProofStillRejectsAnotherElectricalGradeInRow() {
        val hit=ExportFixture.result().matches.first().copy(sourcePartScope="LM709C",conditions="",evidenceLine="LM709C: Input offset voltage | | LM709C | | 2 | 10 | mV")
        assertNull(VariantResultGuard.apply("LM709AH",ExportFixture.result().copy(matches=listOf(hit)),setOf("LM709A")).matches.first().numbers)
    }
    @Test fun damagedNativeTextIsAnOcrTargetEvenWhenItIsNotSparse() {
        val page=PageText(2,"Electrical Characteristics\nInput offset voltage TA � 25�C RS � 10k� "+"text ".repeat(60))
        val ex=PdfExtraction(PdfMetadata(),2,listOf(PageText(1,"LM383 ".repeat(20)),page))
        assertEquals(listOf(2),OcrPagePlanner().firstPages(ex))
        assertTrue(OcrPagePlanner(ExtractionConfig(maxOcrPages=0)).firstPages(ex).isEmpty())
    }
    @Test fun shorterClearerOcrCanReplaceDamagedNativeTextAndRetainNativeEvidence() {
        val old=PageText(2,"Electrical Characteristics\n"+"text � ".repeat(30)+"\n[Native table columns]\nInput offset voltage | | 3 | 7 | mV")
        val raw="Electrical Characteristics\nInput offset voltage TA = 25°C RS ≤ 10 kΩ "+"word ".repeat(18)
        val replacement=OcrTextRecovery.choose(old,raw,"LM383",ExtractionConfig())!!
        assertEquals(TextOrigin.OCR,replacement.origin);assertEquals(old.rawText,replacement.nativeRawText)
        assertTrue(replacement.rawText.contains("[Native table columns]"))
    }
    @Test fun uselessOrDamagedOcrDoesNotReplaceLegibleNativePage() {
        val old=PageText(2,"Electrical Characteristics\nInput offset voltage 3 7 mV "+"text ".repeat(30))
        assertNull(OcrTextRecovery.choose(old,"� �", "LM383",ExtractionConfig()))
        assertNull(OcrTextRecovery.choose(old,"Input offset voltage 3 7 mV", "LM383",ExtractionConfig()))
    }
}
