package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.workspace.*
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.export.*
import com.example.datasheetanalyzer.sandbox.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspacePdfIntegrationTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun evidence(name: String)=File(System.getProperty("datasheet.qaDir") ?: File(context.cacheDir,"workspace-qa").path,name).apply { parentFile!!.mkdirs() }
    @Test fun actualPdfTableCoordinatesIdentifyCorrectSection() {
        PDFBoxResourceLoader.init(context)
        val file=evidence("source-highlight.pdf")
        PDDocument().use { doc ->
            val font=context.assets.open(AnalysisPdfWriter.FONT_ASSET).use { PDType0Font.load(doc,it,true) }
            val page=PDPage(PDRectangle(600f,800f));doc.addPage(page)
            PDPageContentStream(doc,page).use { stream ->
                fun text(value: String,x: Float,y: Float) { stream.beginText();stream.setFont(font,12f);stream.newLineAtOffset(x,y);stream.showText(value);stream.endText() }
                text("LM383 - Synthetic test fixture only",40f,755f)
                text("Recommended Operating Conditions",40f,705f)
                text("Parameter",40f,670f);text("Min",320f,670f);text("Typ",380f,670f);text("Max",440f,670f);text("Unit",500f,670f)
                text("Supply voltage",40f,640f);text("5",320f,640f);text("12",380f,640f);text("20",440f,640f);text("V",500f,640f)
                text("Absolute Maximum Ratings",40f,560f)
                text("Supply voltage",40f,530f);text("5",320f,530f);text("12",380f,530f);text("20",440f,530f);text("V",500f,530f)
            };doc.save(file)
        }
        val boxes=file.inputStream().use { PdfSandboxInspector().evidence(it,1,"Supply voltage | 5 | 12 | 20 | V",DatasheetSection.RECOMMENDED_OPERATING) }
        assertEquals(1,boxes.size);assertTrue(boxes.single().top in .18f.. .21f);assertTrue(boxes.single().bottom in .20f.. .22f)
        file.inputStream().use { assertTrue(PdfSandboxInspector().evidence(it,1,"Supply voltage | 5 | 12 | 20 | V",null).isEmpty()) }
        evidence("source-highlight-bounds.txt").writeText(boxes.joinToString("\n") { "${it.left},${it.top},${it.right},${it.bottom}" })
    }
    @Test fun comparisonPdfKeepsConditionsAndDoesNotApproveUnverifiedValues() {
        val first=SavedAnalysis.capture("LM383",ExportFixture.document(),ParameterReport(listOf(ExportFixture.result()),true))
        val second=SavedAnalysis.capture("LM386",ExportFixture.document(false),ParameterReport(listOf(ExportFixture.result()),false))
        val blocks=ComparisonReportContent.build(listOf(first,second))
        val staged=ExportFiles.stagePdf(context,blocks);val file=evidence("comparison-report.pdf");staged.copyTo(file,true);staged.delete()
        PDDocument.load(file).use { pdf ->
            val text=PDFTextStripper().getText(pdf).replace(Regex("\\s+")," ")
            assertTrue(text.contains("Komponent karşılaştırması"));assertTrue(text.contains("25 °C"));assertTrue(text.contains("İnceleme gerekli"))
            assertTrue(text.contains("sayısal karşılaştırma yapılmadı"));assertTrue(pdf.numberOfPages in 1..10)
            assertNull(pdf.documentCatalog.openAction)
        }
    }
    @Test fun threeComponentLongComparisonWrapsAndRepeatsTableHeaders() {
        val first=SavedAnalysis.capture("LM383",ExportFixture.document(),ParameterReport(listOf(ExportFixture.result()),true))
        val long=first.report.results.single().let { r -> r.copy(matches=listOf(r.matches.single().copy(conditions="TA = 25 °C; "+"uzun test koşulu ".repeat(15)))) }
        val samples=listOf("LM383","LM386","LM358").map { part -> first.copy(part=part,report=ParameterReport((1..18).map {
            long.copy(query=long.query.copy(requested="Özel ölçüm $it",criterion=null,keywords=listOf("measurement $it")))
        },true)) }
        val staged=ExportFiles.stagePdf(context,ComparisonReportContent.build(samples));val file=evidence("comparison-long.pdf");staged.copyTo(file,true);staged.delete()
        PDDocument.load(file).use { pdf ->
            assertTrue(pdf.numberOfPages in 3..30)
            val text=PDFTextStripper().getText(pdf).replace(Regex("\\s+")," ");assertTrue(text.contains("Özel ölçüm 18"))
            assertTrue(Regex("Test koşulları").findAll(text).count()>1)
        }
    }
}
