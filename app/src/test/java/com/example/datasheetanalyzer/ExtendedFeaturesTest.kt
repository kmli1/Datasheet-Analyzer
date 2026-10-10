package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.workspace.SavedAnalysis
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.export.AnalysisCsvWriter
import com.example.datasheetanalyzer.figures.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VariantAndPinoutTest {
    private fun document(text: String,eligible: Boolean=true): AnalyzedDocument {
        val doc=ExportFixture.document(eligible)
        val pages=listOf(PageText(1,text))
        return doc.copy(extraction=doc.extraction.copy(pages=pages),sections=DatasheetSectionDetector().detect(pages))
    }
    @Test fun variantsAreLiteralDeduplicatedFamilyTokensWithSourceEvidence() {
        val doc=document("LM383\nLM383A LM383N\nLM383A 5 V\nLM386N unrelated\nPin Configuration")
        val variants=PartVariants.find(doc,"LM383")
        assertEquals(listOf("LM383A","LM383N"),variants.map { it.code })
        assertEquals("LM383A LM383N",variants.first().evidence);assertEquals(1,variants.first().page)
    }
    @Test fun variantsNeverComeFromMetadataOrUnverifiedOrTruncatedPages() {
        val doc=document("LM383 LM383A")
        assertTrue(PartVariants.find(doc.copy(verification=doc.verification.copy(automaticEligible=false)),"LM383").isEmpty())
        assertTrue(PartVariants.find(doc.copy(extraction=doc.extraction.copy(pages=doc.extraction.pages.map { it.copy(truncated=true) })),"LM383").isEmpty())
        assertTrue(PartVariants.find(doc.copy(extraction=doc.extraction.copy(pages=listOf(PageText(1,"LM383")),metadata=PdfMetadata("LM383A"))),"LM383").isEmpty())
    }
    @Test fun variantDiscoveryDoesNotTrustOcrSuffixesAndSupportsNumericalPrefixes() {
        val doc=document("LM383 LM383A")
        assertTrue(PartVariants.find(doc.copy(extraction=doc.extraction.copy(pages=doc.extraction.pages.map { it.copy(origin=TextOrigin.OCR) })),"LM383").isEmpty())
        assertEquals(listOf("2N2222A"),PartVariants.codes("2N2222","2N2222A"))
        assertEquals(listOf("SN74LVC1G14DBVR"),PartVariants.codes("SN74LVC1G14","SN74LVC1G14DBVR"))
    }
    @Test fun pinSourcesPreservePackageDrawingPageAndStopAtNextSection() {
        val doc=document("LM383\nPin Configuration\nDIP-8 TOP VIEW\n1 OUT\n2 GND\nElectrical Characteristics\nSupply voltage 5 V")
        val pin=PinoutSources.find(doc).single()
        assertEquals(1,pin.page);assertTrue(pin.text.contains("DIP-8 TOP VIEW"));assertTrue(pin.text.contains("1 OUT"))
        assertFalse(pin.text.contains("Supply voltage"))
    }
    @Test fun pinSourcesAreNotInventedForClippedOrUnverifiedOrMissingHeadings() {
        val doc=document("LM383\nPin Configuration\nDIP-8")
        assertTrue(PinoutSources.find(doc.copy(verification=doc.verification.copy(automaticEligible=false))).isEmpty())
        assertTrue(PinoutSources.find(doc.copy(extraction=doc.extraction.copy(pages=doc.extraction.pages.map { it.copy(truncated=true) }))).isEmpty())
        assertTrue(PinoutSources.find(document("LM383\nFeatures\n8 pins")).isEmpty())
    }
    @Test fun siblingVariantValuesRemainSourceQuotesWithoutNumericApproval() {
        val r=ExportFixture.result().copy(absoluteLimits=emptyList())
        val hit=r.matches.single().copy(evidenceLine="LM383A Supply voltage 5 to 20 V")
        val guarded=VariantResultGuard.apply("LM383N",r.copy(matches=listOf(hit)))
        assertEquals(ParameterStatus.POSSIBLE,guarded.status);assertNull(guarded.matches.single().numbers)
        assertTrue(guarded.matches.single().evidenceLine.contains("LM383A"))
    }
    @Test fun genericAndExactVariantRowsRemainUsableButMixedRowsDoNot() {
        val r=ExportFixture.result().copy(absoluteLimits=emptyList())
        assertNotNull(VariantResultGuard.apply("LM383N",r).matches.single().numbers)
        assertNotNull(VariantResultGuard.apply("LM383N",r.copy(matches=r.matches.map { it.copy(evidenceLine="LM383N Supply voltage 5 to 20 V") })).matches.single().numbers)
        assertNull(VariantResultGuard.apply("LM383N",r.copy(matches=r.matches.map { it.copy(evidenceLine="LM383N LM383A Supply voltage 5 to 20 V") })).matches.single().numbers)
    }
    @Test fun multiVariantColumnHeaderCannotOpenUnscopedNumbers() {
        val r=ExportFixture.result().copy(absoluteLimits=emptyList())
        val hit=r.matches.single().copy(rawContext="LM383N | LM383A\nSupply voltage | 5 | 20 | V")
        assertNull(VariantResultGuard.apply("LM383N",r.copy(matches=listOf(hit))).matches.single().numbers)
    }
}

class OperatingConditionsTest {
    private val field=ConditionField.SUPPLY
    private fun report(n: NumericValues=NumericValues(5.0,12.0,20.0,unit=ExportFixture.unit),eligible: Boolean=true,
        status: ParameterStatus=ParameterStatus.DETECTED,section: DatasheetSection=DatasheetSection.RECOMMENDED_OPERATING): ParameterReport {
        val r=ExportFixture.result(status)
        return ParameterReport(listOf(r.copy(matches=r.matches.map { it.copy(numbers=n,section=section) },absoluteLimits=emptyList())),eligible)
    }
    @Test fun recommendedBoundsAreInclusiveAndOutOfRangeIsExplicit() {
        val r=report()
        for(v in listOf(5.0,12.0,20.0)) assertEquals(ConditionVerdict.WITHIN,OperatingConditions.check(r,field,v).verdict)
        assertEquals(ConditionVerdict.BELOW,OperatingConditions.check(r,field,4.9).verdict)
        assertEquals(ConditionVerdict.ABOVE,OperatingConditions.check(r,field,20.1).verdict)
    }
    @Test fun typicalAndUnqualifiedValuesNeverBecomeOperatingBounds() {
        for(n in listOf(NumericValues(typical=12.0,unit=ExportFixture.unit),NumericValues(value=12.0,unit=ExportFixture.unit)))
            assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(report(n),field,12.0).verdict)
    }
    @Test fun missingOneBoundIsNeverPresentedAsFullPass() {
        assertEquals(ConditionVerdict.INCOMPLETE,OperatingConditions.check(report(NumericValues(maximum=20.0,unit=ExportFixture.unit)),field,12.0).verdict)
        assertEquals(ConditionVerdict.ABOVE,OperatingConditions.check(report(NumericValues(maximum=20.0,unit=ExportFixture.unit)),field,21.0).verdict)
    }
    @Test fun unitScalesAreConvertedAndDifferentDimensionsAreRejected() {
        val mv=EngineeringUnit("mV",UnitDimension.VOLTAGE,.001)
        assertEquals(ConditionVerdict.WITHIN,OperatingConditions.check(report(NumericValues(minimum=5000.0,maximum=20000.0,unit=mv)),field,12.0).verdict)
        assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(report(NumericValues(minimum=5.0,maximum=20.0,unit=EngineeringUnit("mA",UnitDimension.CURRENT,.001))),field,12.0).verdict)
    }
    @Test fun wrongCriterionDoesNotSupplyACompatibleVoltage() {
        val r=report().let { it.copy(results=it.results.map { p -> p.copy(query=p.query.copy(criterion=Criterion.INPUT_VOLTAGE)) }) }
        assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(r,field,12.0).verdict)
    }
    @Test fun unverifiedPossibleOcrAndLowConfidenceDoNotPass() {
        val sources=listOf(report(eligible=false),report(status=ParameterStatus.POSSIBLE),
            report().let { it.copy(results=it.results.map { r -> r.copy(matches=r.matches.map { h -> h.copy(origin=TextOrigin.OCR) }) }) },
            report().let { it.copy(results=it.results.map { r -> r.copy(matches=r.matches.map { h -> h.copy(confidence=74) }) }) })
        for(r in sources) assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(r,field,12.0).verdict)
    }
    @Test fun electricalAndAbsoluteSectionsDoNotBecomeRecommendedRanges() {
        for(s in listOf(DatasheetSection.ELECTRICAL,DatasheetSection.ABSOLUTE_MAXIMUM))
            assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(report(section=s),field,12.0).verdict)
    }
    @Test fun absoluteExceedanceTakesPriorityWithoutMakingLimitsAnOperatingRange() {
        val base=report();val r=base.results.single()
        val abs=r.matches.single().copy(numbers=NumericValues(maximum=28.0,unit=ExportFixture.unit),section=DatasheetSection.ABSOLUTE_MAXIMUM)
        val full=base.copy(results=listOf(r.copy(absoluteLimits=listOf(abs))))
        assertEquals(ConditionVerdict.ABSOLUTE_EXCEEDED,OperatingConditions.check(full,field,30.0).verdict)
        assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(ParameterReport(listOf(r.copy(matches=listOf(abs))),true),field,12.0).verdict)
    }
    @Test fun conflictingTestConditionsDoNotProduceACombinedRange() {
        val base=report();val r=base.results.single()
        val different=r.matches.single().copy(conditions="TA = 85 °C")
        assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(base.copy(results=listOf(r.copy(matches=r.matches+different))),field,12.0).verdict)
    }
    @Test fun junctionTemperatureDoesNotApproveAmbientTemperature() {
        val base=report(NumericValues(minimum=-40.0,maximum=125.0,unit=ConditionField.AMBIENT.unit))
        val r=base.results.single();val junction=r.matches.single().copy(evidenceLine="Operating junction temperature -40 to 125 °C")
        val report=base.copy(results=listOf(r.copy(query=r.query.copy(criterion=Criterion.OPERATING_TEMPERATURE),matches=listOf(junction))))
        assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(report,ConditionField.AMBIENT,25.0).verdict)
    }
    @Test fun invalidNumbersAndInvertedOrSymmetricRangesAreRejected() {
        assertNull(OperatingConditions.parse("NaN"));assertNull(OperatingConditions.parse("Infinity"));assertEquals(2.5,OperatingConditions.parse("2,5")!!,0.0)
        assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(report(),field,Double.NaN).verdict)
        for(n in listOf(NumericValues(minimum=20.0,maximum=5.0,unit=ExportFixture.unit),NumericValues(minimum=-20.0,maximum=20.0,unit=ExportFixture.unit,symmetric=true)))
            assertEquals(ConditionVerdict.UNKNOWN,OperatingConditions.check(report(n),field,12.0).verdict)
    }
    @Test fun checksExtractTheirOwnCriteriaFromVerifiedSourceRatherThanSelectedUiCriteria() {
        val r=OperatingConditions.report(ExportFixture.document())
        assertEquals(8,r.results.size)
        assertEquals(ConditionVerdict.WITHIN,OperatingConditions.check(r,field,12.0).verdict)
    }
}

class ApproximateGraphTest {
    private val plot=FigureBounds(.1f,.2f,.9f,.8f)
    private fun calibration(x: CalibratedAxis=CalibratedAxis(0.0,100.0,"Hz"),y: CalibratedAxis=CalibratedAxis(-10.0,10.0,"V"))=GraphCalibration(plot,x,y)
    @Test fun linearCoordinatesRespectSourceYAxisDirectionAndPlotOffsets() {
        val c=calibration();val center=c.read(.5f,.5f,1000,1000)!!
        assertEquals(50.0,center.x,1e-5);assertEquals(0.0,center.y,1e-5)
        assertEquals(-10.0,c.read(.1f,.8f,1000,1000)!!.y,0.0)
        assertEquals(10.0,c.read(.9f,.2f,1000,1000)!!.y,0.0)
    }
    @Test fun logarithmicAxesInterpolateDecadesInsteadOfAverages() {
        val c=calibration(CalibratedAxis(1.0,10000.0,"Hz",AxisScale.LOGARITHMIC),CalibratedAxis(1.0,100.0,"V",AxisScale.LOGARITHMIC))
        val p=c.read(.5f,.5f,1000,1000)!!
        assertEquals(100.0,p.x,1e-3);assertEquals(10.0,p.y,1e-4)
    }
    @Test fun pointsOutsidePlotOrNonFiniteCoordinatesAreNotRead() {
        val c=calibration()
        assertNull(c.read(0f,.5f,1000,1000));assertNull(c.read(.5f,1f,1000,1000));assertNull(c.read(Float.NaN,.5f,1000,1000))
        assertNull(c.read(.5f,.5f,0,1000))
    }
    @Test fun twoPixelToleranceIsBoundedAndTracksImageResolution() {
        val c=calibration();val low=c.read(.5f,.5f,100,100)!!;val high=c.read(.5f,.5f,1000,1000)!!
        assertTrue(low.xHigh-low.xLow>high.xHigh-high.xLow);assertTrue(low.yLow<low.y && low.yHigh>low.y)
        assertEquals(0.0,c.read(.1f,.8f,100,100)!!.xLow,0.0)
    }
    @Test fun invalidAxisInputsDoNotCreateCalibration() {
        for(pair in listOf(1.0 to 1.0,2.0 to 1.0,Double.NaN to 10.0,0.0 to Double.POSITIVE_INFINITY)) {
            try { CalibratedAxis(pair.first,pair.second,"V");fail("Invalid axis accepted") } catch(_: IllegalArgumentException) {}
        }
        try { CalibratedAxis(0.0,10.0,"V",AxisScale.LOGARITHMIC);fail("Log zero accepted") } catch(_: IllegalArgumentException) {}
    }
    @Test fun veryLargeFiniteAxesAvoidSubtractionOverflow() {
        val c=calibration(CalibratedAxis(-1e300,1e300,"V"),CalibratedAxis(1e-250,1e250,"A",AxisScale.LOGARITHMIC))
        val p=c.read(.5f,.5f,2000,1000)!!
        assertTrue(p.x.isFinite() && p.y.isFinite() && p.xLow.isFinite())
    }
}

class CsvOutputTest {
    private fun sample(report: ParameterReport=ParameterReport(listOf(ExportFixture.result()),true))=SavedAnalysis.capture("LM383",ExportFixture.document(),report)
    @Test fun descriptiveSectionTableExportsExplicitRowsAndPreservesBlankTypicalCell() {
        val query=QueryNormalizer().queries(setOf(Criterion.RECOMMENDED_OPERATING_CONDITIONS),"").single()
        val hit=ExportFixture.result().matches.single().copy(numbers=null,
            evidenceLine="Recommended Operating Conditions",rawContext="Parameter | Min | Typ | Max | Unit\nSupply voltage | 5 | | 20 | V")
        val csv=AnalysisCsvWriter.analyses(listOf(sample(ParameterReport(listOf(ParameterResult(query,ParameterStatus.DETECTED,listOf(hit))),true))))
        assertTrue(csv.contains("\"5\";\"\";\"20\";\"\";\"V\""))
    }
    @Test fun descriptiveSourceTableCannotExportAnotherVariantsNumericRow() {
        val query=QueryNormalizer().queries(setOf(Criterion.RECOMMENDED_OPERATING_CONDITIONS),"").single()
        val hit=ExportFixture.result().matches.single().copy(numbers=null,
            evidenceLine="Recommended Operating Conditions",rawContext="Parameter | Min | Typ | Max | Unit\nLM383A Supply voltage | 5 | | 20 | V")
        val saved=sample(ParameterReport(listOf(ParameterResult(query,ParameterStatus.DETECTED,listOf(hit))),true)).copy(part="LM383N")
        assertFalse(AnalysisCsvWriter.analyses(listOf(saved)).contains("\"5\";\"\";\"20\""))
    }
    @Test fun originalMinTypicalMaxAndMissingCellsStayInCorrectCsvColumns() {
        val r=ExportFixture.result().let { it.copy(matches=it.matches.map { h -> h.copy(numbers=h.numbers!!.copy(minimum=null,maximum=null)) },absoluteLimits=emptyList()) }
        val csv=AnalysisCsvWriter.analyses(listOf(sample(ParameterReport(listOf(r),true))))
        assertTrue(csv.startsWith("\uFEFF"));assertTrue(csv.contains("\"\";\"12\";\"\";\"\";\"V\""))
        assertTrue(csv.contains("TA = 25 °C"));assertTrue(csv.endsWith("\r\n"))
    }
    @Test fun unverifiedAndPossibleRecordsKeepEvidenceButDoNotExportApprovedNumbers() {
        for(report in listOf(ParameterReport(listOf(ExportFixture.result()),false),
            ParameterReport(listOf(ExportFixture.result(ParameterStatus.DOCUMENT_UNVERIFIED)),true),
            ParameterReport(listOf(ExportFixture.result(ParameterStatus.POSSIBLE).copy(absoluteLimits=emptyList())),true))) {
            val csv=AnalysisCsvWriter.analyses(listOf(sample(report)))
            assertTrue(csv.contains("Supply voltage | 5 | 12 | 20 | V"))
            assertFalse(csv.contains("\"5\";\"12\";\"20\""))
        }
    }
    @Test fun formulaInjectionAndEmbeddedQuotesDelimitersAndNewlinesAreEscaped() {
        for(text in listOf("=HYPERLINK(\"bad\")","+cmd","-formula"," @SUM(A1)","\tformula"))
            assertTrue(AnalysisCsvWriter.cell(text).startsWith("\"'"))
        assertEquals("\"a;\"\"b\"\"\nç\"",AnalysisCsvWriter.cell("a;\"b\"\nç"))
        assertEquals("\"LM383\"",AnalysisCsvWriter.cell("LM383"))
    }
    @Test fun negativeNumericalLimitsStayNumbersAndMissingResultsKeepSourceIdentity() {
        val result=ExportFixture.result().let { it.copy(matches=it.matches.map { h -> h.copy(numbers=NumericValues(minimum=-40.0,maximum=85.0,unit=ConditionField.AMBIENT.unit)) },absoluteLimits=emptyList()) }
        val missing=result.copy(status=ParameterStatus.NOT_FOUND,matches=emptyList())
        val csv=AnalysisCsvWriter.analyses(listOf(sample(ParameterReport(listOf(result,missing),true))))
        assertTrue(csv.contains("\"-40\";\"\";\"85\""));assertTrue(csv.contains("Bulunamadı"))
        assertEquals(3,csv.count { it=='\n' })
        System.getProperty("datasheet.qaDir")?.let { File(it,"analysis-sample.csv").apply { parentFile!!.mkdirs();writeText(csv) } }
    }
    @Test fun approximateCsvIncludesCalibrationAndDoesNotReplaceVerifiedValues() {
        val c=GraphCalibration(FigureBounds(.1f,.1f,.9f,.9f),CalibratedAxis(1.0,100.0,"Hz",AxisScale.LOGARITHMIC),CalibratedAxis(0.0,5.0,"V"))
        val points=listOf(c.read(.5f,.5f,1000,1000)!!)
        val csv=AnalysisCsvWriter.graph("LM383","Gain vs Frequency",2,c,points)
        assertTrue(csv.contains("Elle kalibre edilmiş yaklaşık okuma"));assertTrue(csv.contains("LOGARITHMIC"));assertTrue(csv.contains("Kaynak sayfa"))
        System.getProperty("datasheet.qaDir")?.let { File(it,"graph-approximate.csv").apply { parentFile!!.mkdirs();writeText(csv) } }
    }
}
