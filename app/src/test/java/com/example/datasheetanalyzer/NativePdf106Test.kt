package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.sandbox.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Opt-in, offline real PDFs. No copyrighted manufacturer files or network calls in the source package. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class NativePdf106Test {
    private fun source(name: String): File {
        val directory=System.getProperty("datasheet.realDir");assumeNotNull(directory)
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        return File(directory!!,name).also { assertTrue(it.isFile) }
    }
    private fun qa(name: String,text: String) {
        val root=System.getProperty("datasheet.qaDir") ?: return
        File(root,name).apply { parentFile!!.mkdirs();writeText(text) }
    }
    private fun document(name: String,part: String,url: String): AnalyzedDocument {
        val file=source(name)
        val ex=file.inputStream().use { PdfSandboxInspector().extract(it) }.let { it.copy(pages=TextCleaner().clean(it.pages)) }
        qa(name+".txt",ex.pages.joinToString("\n") { "PAGE ${it.pageNumber}\n${it.text}" })
        val candidate=CandidateSelector().select(part,listOf(SearchCandidate("$part datasheet",url))).candidates.single()
        val sections=DatasheetSectionDetector().detect(ex.pages)
        val v=DocumentVerifier().verify(part,candidate,ex,sections)
        qa(name+".verification.txt","${v.code} score=${v.score} eligible=${v.automaticEligible}\n${v.reasons.joinToString("\n")}")
        return AnalyzedDocument(CheckedDocument(QuarantinedPdf(file,url,file.length(),"qa"),ex.totalPages,security=SecurityStatus.PASSED,
            identity=v.status,downloadMs=0,checkMs=0,extraction=ex),candidate,ex,sections,v,requestedPart=part)
    }
    private fun baseline(name: String,page: Int): String {
        val glyphs=mutableListOf<PositionedGlyph>()
        PDDocument.load(source(name)).use { doc ->
            val stripper=object: PDFTextStripper() {
                override fun processTextPosition(p: TextPosition) {
                    super.processTextPosition(p)
                    if(p.dir==0f && glyphs.size<50_000) glyphs+=PositionedGlyph(p.unicode.orEmpty(),p.xDirAdj,p.yDirAdj,p.widthDirAdj)
                }
            }
            stripper.startPage=page;stripper.endPage=page;stripper.getText(doc)
        }
        return Baseline105TableReader().tables(glyphs).also { qa(name+".baseline105-page$page.txt",it) }
    }
    @Test fun ti595ReadsGroupedRecommendedNominalAndSeparateAbsoluteLimits() {
        val doc=document("sn74hc595.pdf","SN74HC595","https://www.ti.com/lit/ds/symlink/sn74hc595.pdf")
        assertTrue(doc.verification.reasons.joinToString(),doc.verification.automaticEligible)
        val r=ParameterExtractionEngine().extract(doc,QueryNormalizer().queries(emptySet(),"recommended operating voltage")).results.single()
        qa("sn74hc595-results.txt",r.toString())
        assertEquals(ParameterStatus.DETECTED,r.status)
        val n=r.matches.first().numbers!!
        assertEquals(2.0,n.minimum!!,0.0);assertEquals(6.0,n.maximum!!,0.0)
        assertNull(n.typical);assertEquals(5.0,n.value!!,0.0);assertTrue(n.nominal)
        assertTrue(r.matches.all { it.page==4 && it.evidenceLine.contains("SN74HC595") })
        assertTrue(r.absoluteLimits.any { it.numbers?.minimum== -0.5 && it.numbers.maximum==7.0 })
        assertFalse(baseline("sn74hc595.pdf",4).contains("Supply voltage |"))
        val temp=ParameterExtractionEngine().extract(doc,listOf(ParameterQuery("Operating free-air temperature",Criterion.OPERATING_TEMPERATURE,
            listOf("Operating free-air temperature")))).results.single()
        assertTrue(temp.matches.any { it.numbers?.minimum== -40.0 && it.numbers.maximum==85.0 })
        assertTrue(temp.matches.none { it.numbers?.minimum== -55.0 })
    }
    @Test fun onsemi317ReadsValueRangeAndReferenceVoltageWithTestNumbers() {
        val doc=document("lm317-onsemi.pdf","LM317","https://www.onsemi.com/download/data-sheet/pdf/lm317-d.pdf")
        assertTrue(doc.verification.reasons.joinToString(),doc.verification.automaticEligible)
        val queries=listOf(ParameterQuery("Input-Output Voltage Differential",null,listOf("Input-Output Voltage Differential"),QueryMode.ABSOLUTE_MAXIMUM),
            ParameterQuery("Reference Voltage",Criterion.OUTPUT_VOLTAGE,listOf("Reference Voltage")))
        val r=ParameterExtractionEngine().extract(doc,queries)
        qa("lm317-results.txt",r.toString())
        val absolute=r.results[0].matches.first().numbers!!
        assertEquals(-0.3,absolute.minimum!!,0.0);assertEquals(40.0,absolute.maximum!!,0.0);assertEquals("V",absolute.unit.symbol)
        val ref=r.results[1].matches.first().numbers!!
        assertEquals(1.2,ref.minimum!!,0.0);assertEquals(1.25,ref.typical!!,0.0);assertEquals(1.3,ref.maximum!!,0.0)
        assertTrue(r.results[1].matches.first().conditions.contains("40 V"))
        val unknownMicro=ParameterExtractionEngine().extract(doc,listOf(ParameterQuery("Adjustment Pin Current",null,listOf("Adjustment Pin Current")))).results.single()
        assertTrue(unknownMicro.matches.none { it.numbers!=null })
        assertFalse(baseline("lm317-onsemi.pdf",2).contains("Input-Output Voltage Differential |"))
    }
    @Test fun st78ReadsLongTableEndButDoesNotApproveAnUnverifiedGrade() {
        val doc=document("l78.pdf","L7805A","https://www.st.com/resource/en/datasheet/l78.pdf")
        assertFalse(doc.verification.automaticEligible)
        val page=doc.extraction.pages.single { it.pageNumber==6 }
        val source=page.text.substringAfter("[Native table columns]","")
        assertTrue(source.contains("Short circuit current"))
        val lines=source.lines()
        val idx=lines.indexOfFirst { it.contains("Short circuit current") && '|' in it }
        assertTrue(idx>0)
        val n=EngineeringValueParser().parse(lines[idx],lines.take(idx))!!.numbers
        assertNull(n.minimum);assertEquals(0.2,n.typical!!,0.0);assertNull(n.maximum)
        assertFalse(baseline("l78.pdf",6).contains("Short circuit current |"))
        val results=ParameterExtractionEngine().extract(doc,listOf(ParameterQuery("Output voltage",Criterion.OUTPUT_VOLTAGE,listOf("Output voltage"))))
        assertTrue(results.results.all { it.status==ParameterStatus.DOCUMENT_UNVERIFIED && it.matches.all { m -> m.numbers==null } })
    }
    @Test fun ti358KeepsGradeTablesSeparateAndReadsExplicitCommonTypCells() {
        val doc=document("lm358.pdf","LM358","https://www.ti.com/lit/ds/symlink/lm358.pdf")
        assertTrue(doc.verification.reasons.joinToString(),doc.verification.automaticEligible)
        val r=ParameterExtractionEngine().extract(doc,listOf(ParameterQuery("Gain bandwidth product",Criterion.BANDWIDTH,listOf("Gain bandwidth product"))))
        qa("lm358-results.txt",r.toString())
        assertEquals(ParameterStatus.DETECTED,r.results.single().status)
        assertEquals(0.7,r.results.single().matches.first().numbers!!.typical!!,0.0)
        assertTrue(r.results.single().matches.all { it.numbers==null || it.page==10 })
        assertTrue(doc.extraction.pages.single { it.pageNumber==6 }.text.contains("LM358B LM358BA:"))
        assertTrue(doc.extraction.pages.single { it.pageNumber==7 }.text.contains("LM358B LM358BA:"))
        val continued=doc.extraction.pages.single { it.pageNumber==7 }.text
        val continuedLines=continued.lines()
        val currentRow=continuedLines.indexOfFirst { it.contains("Quiescent current per amplifier") && '|' in it }
        val continuedValue=EngineeringValueParser().parse(continuedLines[currentRow],continuedLines.take(currentRow))!!
        assertFalse(continuedValue.requiresReview)
        assertTrue(continuedValue.conditions.contains("40°C") && continuedValue.conditions.contains("85°C"))
        baseline("lm358.pdf",6)
    }
}
