package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.export.*
import com.example.datasheetanalyzer.sandbox.PdfSandboxInspector
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Offline real-document regressions; manufacturer PDFs stay outside the distributable project. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class NativePdf108Test {
    private val five=setOf(Criterion.INPUT_OFFSET_VOLTAGE,Criterion.INPUT_VOLTAGE,Criterion.OUTPUT_VOLTAGE,Criterion.GAIN,Criterion.SUPPLY_VOLTAGE)
    private fun document(name: String,part: String,url: String): AnalyzedDocument {
        val directory=System.getProperty("datasheet.realDir");assumeNotNull(directory)
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        val file=File(directory!!,name);assertTrue(file.isFile)
        val ex=file.inputStream().use { PdfSandboxInspector().extract(it) }.let { it.copy(pages=TextCleaner().clean(it.pages)) }
        val c=CandidateSelector().select(part,listOf(SearchCandidate("$part datasheet",url))).candidates.single()
        val sections=DatasheetSectionDetector().detect(ex.pages)
        val v=DocumentVerifier().verify(part,c,ex,sections);assertTrue(v.reasons.joinToString(),v.automaticEligible)
        return AnalyzedDocument(CheckedDocument(QuarantinedPdf(file,url,file.length(),"qa"),ex.totalPages,
            security=SecurityStatus.PASSED,identity=v.status,downloadMs=0,checkMs=0,extraction=ex),c,ex,sections,v,requestedPart=part)
    }
    private fun report(d: AnalyzedDocument)=ParameterExtractionEngine().extract(d,QueryNormalizer().queries(five,""))
    private fun qa(d: AnalyzedDocument,r: ParameterReport) {
        val directory=System.getProperty("datasheet.qaDir") ?: return
        val root=File(directory).apply { mkdirs() }
        File(root,"reading108-${d.requestedPart}.txt").writeText(r.results.joinToString("\n\n")+
            "\nPAGES\n"+d.extraction.pages.joinToString("\n") { "PAGE ${it.pageNumber}\n${it.text}" })
        val blocks=AnalysisReportContent.build(d.requestedPart,d,r,"05.10.2026")
        File(root,"reading108-${d.requestedPart}.pdf").outputStream().use { output ->
            RuntimeEnvironment.getApplication().assets.open(AnalysisPdfWriter.FONT_ASSET).use { font -> AnalysisPdfWriter.write(blocks,output,font) }
        }
    }
    @Test fun lm358ReadsTheCorrectOffsetGradeMergedUnitsAndLinearGain() {
        val d=document("lm358.pdf","LM358","https://www.ti.com/lit/ds/symlink/lm358.pdf");val r=report(d);qa(d,r)
        assertEquals(5,r.results.size);assertTrue(r.results.all { it.status!=ParameterStatus.NOT_FOUND })
        val offset=r.results.single { it.query.criterion==Criterion.INPUT_OFFSET_VOLTAGE }
        assertEquals(ParameterStatus.DETECTED,offset.status)
        val n=offset.matches.first().numbers!!;assertNull(n.minimum)
        assertEquals(3.0,n.typical!!,0.0);assertEquals(7.0,n.maximum!!,0.0);assertEquals("mV",n.unit.symbol)
        assertTrue(offset.matches.none { it.numbers?.maximum==3.0 || it.numbers?.maximum==5.0 })
        assertTrue(offset.matches.filter { it.numbers!=null }.all { it.page==10 })
        val supply=r.results.single { it.query.criterion==Criterion.SUPPLY_VOLTAGE }
        assertEquals(ParameterStatus.DETECTED,supply.status)
        assertEquals(3.0,supply.matches.first().numbers!!.minimum!!,0.0);assertEquals(30.0,supply.matches.first().numbers!!.maximum!!,0.0)
        assertEquals(DatasheetSection.RECOMMENDED_OPERATING,supply.matches.first().section)
        val gain=r.results.single { it.query.criterion==Criterion.GAIN }.matches.first().numbers!!
        assertEquals(UnitDimension.GAIN_RATIO,gain.unit.dimension);assertEquals("V/mV",gain.unit.symbol)
        assertEquals(25.0,gain.minimum!!,0.0);assertEquals(100.0,gain.typical!!,0.0);assertNull(gain.maximum)
        assertEquals(ParameterStatus.DETECTED,r.results.single { it.query.criterion==Criterion.OUTPUT_VOLTAGE }.status)
        val input=r.results.single { it.query.criterion==Criterion.INPUT_VOLTAGE }
        assertEquals(ParameterStatus.POSSIBLE,input.status);assertTrue(input.matches.all { it.numbers==null })
    }
    @Test fun lm709ahUsesDocumentedPackageScopeAndDoesNotTurnUnknownMicroIntoMilli() {
        val d=document("lm709-archive.pdf","LM709AH","https://www.datasheetarchive.com/datasheet/LM709AH/National-Semiconductor?term=LM709AH")
        val r=report(d);qa(d,r)
        val proof=VerifiedPackageScope.find(d)!!;assertEquals("LM709A",proof.grade);assertEquals("H",proof.packageCode);assertEquals(7,proof.page)
        val offset=r.results.single { it.query.criterion==Criterion.INPUT_OFFSET_VOLTAGE }
        assertEquals(ParameterStatus.POSSIBLE,offset.status)
        val n=offset.matches.first().numbers!!;assertNull(n.minimum)
        assertEquals(0.6,n.typical!!,0.0);assertEquals(2.0,n.maximum!!,0.0)
        assertTrue(offset.matches.filter { it.numbers!=null }.all { it.sourcePartScope=="LM709A" && it.confidence<=74 })
        assertTrue(offset.matches.none { it.numbers?.maximum==10.0 || it.numbers?.value==10.0 })
        val page=d.extraction.pages.single { it.pageNumber==2 }
        assertTrue(page.rawText.contains("�A"));assertTrue(OcrPagePlanner().firstPages(d.extraction).contains(2))
        val bias=ParameterExtractionEngine().extract(d,listOf(ParameterQuery("Input bias current",null,listOf("Input bias current")))).results.single()
        assertTrue(bias.matches.none { it.numbers?.unit?.symbol=="mA" || it.numbers?.unit?.symbol=="A" })
        assertTrue(r.results.filter { it.query.criterion!=Criterion.INPUT_OFFSET_VOLTAGE }.all { it.status!=ParameterStatus.DETECTED })
    }
}
