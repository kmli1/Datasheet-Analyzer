package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.workspace.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.export.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Nominal106IntegrationTest {
    private fun report(): ParameterReport {
        val base=ExportFixture.result()
        return ParameterReport(listOf(base.copy(matches=listOf(base.matches.first().copy(
            numbers=NumericValues(minimum=2.0,maximum=6.0,value=5.0,unit=ExportFixture.unit,structured=true,nominal=true),
            evidenceLine="LM383: Supply voltage | 2 | 5 | 6 | V",conditions="",sourcePartScope="LM383",warnings=listOf("Nominal değer tipik değildir."))),absoluteLimits=emptyList())),true)
    }
    @Test fun nominalRecordSurvivesRestartWithoutFillingTypicalCell() {
        val folder=Files.createTempDirectory("nominal-record-").toFile()
        try {
            val saved=SavedAnalysis.capture("LM383",ExportFixture.document(),report());WorkspaceStore(folder).save(saved)
            val restored=WorkspaceStore(folder).state.value
            assertNull(restored.loadError);assertEquals(saved,restored.analyses.single())
            val n=restored.analyses.single().report.results.single().matches.single().numbers!!
            assertTrue(n.nominal);assertNull(n.typical);assertEquals(5.0,n.value!!,0.0)
            val file=File(folder,"workspace.json")
            file.writeText(file.readText().replace(",\"nominal\":true",""))
            val older=WorkspaceStore(folder).state.value
            assertNull(older.loadError);assertFalse(older.analyses.single().report.results.single().matches.single().numbers!!.nominal)
        } finally { folder.deleteRecursively() }
    }
    @Test fun exportedPdfLabelsNominalAndKeepsMissingTypical() {
        val blocks=AnalysisReportContent.build("LM383",ExportFixture.document(),report(),"04.10.2026")
        assertEquals(listOf("2","-","6","Nominal 5","V"),blocks.single { it.table?.headers==listOf("Min","Tipik","Maks","Değer","Birim") }.table!!.rows.single())
        val context=RuntimeEnvironment.getApplication()
        val staged=ExportFiles.stagePdf(context,blocks)
        val qa=System.getProperty("datasheet.qaDir") ?: context.cacheDir.path
        val file=File(qa,"analysis-nominal.pdf").apply { parentFile!!.mkdirs() }
        staged.copyTo(file,true);staged.delete()
        PDDocument.load(file).use { pdf ->
            val text=PDFTextStripper().getText(pdf).replace(Regex("\\s+")," ")
            assertTrue(text.contains("Nominal 5"));assertTrue(text.contains("Nominal değer tipik değildir"))
            assertEquals("Datasheet Analyzer 1.0.8",pdf.documentInformation.creator)
        }
    }
}
