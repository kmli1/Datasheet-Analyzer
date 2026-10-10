package com.example.datasheetanalyzer

import android.graphics.*
import android.net.Uri
import com.example.datasheetanalyzer.export.*
import com.example.datasheetanalyzer.domain.document.FigureBounds
import com.example.datasheetanalyzer.figures.GraphRasterCropper
import com.example.datasheetanalyzer.sandbox.PdfFigureExtractor
import com.example.datasheetanalyzer.data.workspace.SavedAnalysis
import com.example.datasheetanalyzer.domain.extraction.ParameterReport
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
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
class ExportIntegrationTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun evidence(name: String)=File(System.getProperty("datasheet.qaDir") ?:
        File(context.cacheDir,"export-qa").absolutePath,name).apply { parentFile!!.mkdirs() }
    @Test fun csvSavesThroughDocumentResolverWithBomAndTemporaryCopiesAreRemoved()=runBlocking {
        val text=AnalysisCsvWriter.analyses(listOf(SavedAnalysis.capture("LM383",ExportFixture.document(),
            ParameterReport(listOf(ExportFixture.result()),true))))
        val staged=ExportFiles.stageCsv(context,text);val output=evidence("analysis-export.csv")
        ExportFiles.save(context,staged.path,Uri.fromFile(output))
        assertFalse(staged.exists());assertEquals(text,output.readText())
        assertArrayEquals(byteArrayOf(0xEF.toByte(),0xBB.toByte(),0xBF.toByte()),output.readBytes().take(3).toByteArray())
        val cancelled=ExportFiles.stageCsv(context,text);ExportFiles.discard(context,cancelled.path)
        assertFalse(cancelled.exists());assertTrue(output.exists())
    }
    @Test fun unicodeResultPdfIsSelectableAndSavesThroughDocumentResolver()=runBlocking {
        val staged=ExportFiles.stagePdf(context,ExportFixture.content())
        val result=evidence("analysis-report.pdf")
        ExportFiles.save(context,staged.path,Uri.fromFile(result))
        assertFalse(staged.exists());assertTrue(result.length()>1000)
        PDDocument.load(result).use { pdf ->
            assertTrue(pdf.numberOfPages in 1..3)
            val text=PDFTextStripper().getText(pdf)
            for(expected in listOf("Önerilen çalışma koşulları","Min","Tipik","Maks","Birim","25 °C","kaynak s.1","Sonuç özeti"))
                assertTrue(expected,text.contains(expected))
            assertNull(pdf.documentCatalog.openAction)
        }
    }
    @Test fun longEvidenceAndLongUrlsWrapAcrossPagesWithoutLosingText() {
        val blocks=ExportFixture.content()+listOf(ReportBlock("UZUN BAĞLAM",ReportStyle.HEADING))+
            (1..25).map { ReportBlock("Satır $it: "+"Önerilen çalışma gerilimi ±12 V, akım 5 µA, direnç 10 Ω. ".repeat(8)) }+
            ReportBlock("https://example.com/"+"long-source-path".repeat(80))+
            ReportBlock("SON KAYNAK SATIRI")
        val staged=ExportFiles.stagePdf(context,blocks)
        val result=evidence("analysis-long.pdf");staged.copyTo(result,true);staged.delete()
        PDDocument.load(result).use { pdf ->
            assertTrue(pdf.numberOfPages>3);assertTrue(pdf.numberOfPages<100)
            val text=PDFTextStripper().getText(pdf)
            assertTrue(text.contains("SON KAYNAK SATIRI"));assertTrue(text.contains("5 µA"));assertTrue(text.contains("10 Ω"))
            assertEquals(595f,pdf.getPage(0).mediaBox.width,0f)
        }
    }
    @Test fun reviewRequiredPdfNeverAddsVerifiedNumericFields() {
        val staged=ExportFiles.stagePdf(context,ExportFixture.content(false))
        val result=evidence("analysis-review.pdf");staged.copyTo(result,true);staged.delete()
        PDDocument.load(result).use { pdf ->
            val text=PDFTextStripper().getText(pdf)
            assertTrue(text.contains("sayısal sonuçlar otomatik onaylanmadı"))
            assertFalse(text.contains("Min:"));assertFalse(text.contains("Maks:"))
            assertTrue(text.contains("Supply voltage | 5 | 12 | 20 | V"))
        }
    }
    @Test fun fullPageAndGraphPngContainExactSourcePixels()=runBlocking {
        val bitmap=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            canvas.drawRect(50f,25f,150f,75f,Paint().apply { color=Color.RED })
            val page=ExportFiles.stagePng(context,bitmap,null)
            val graph=ExportFiles.stagePng(context,bitmap,NormalizedCrop(.25f,.25f,.75f,.75f))
            BitmapFactory.decodeFile(page.path).let { full ->
                assertEquals(200,full.width);assertEquals(100,full.height)
                assertEquals(Color.WHITE,full.getPixel(0,0));assertEquals(Color.RED,full.getPixel(75,40));full.recycle()
            }
            val result=evidence("graph-crop.png")
            ExportFiles.save(context,graph.path,Uri.fromFile(result))
            BitmapFactory.decodeFile(result.path).let { cropped ->
                assertEquals(100,cropped.width);assertEquals(50,cropped.height)
                assertEquals(Color.RED,cropped.getPixel(0,0));assertEquals(Color.RED,cropped.getPixel(99,49));cropped.recycle()
            }
            assertFalse(bitmap.isRecycled);assertFalse(graph.exists())
            ExportFiles.discard(context,page.path);assertFalse(page.exists())
        } finally { bitmap.recycle() }
    }
    @Test fun cleanupAndSaveRejectFilesOutsideOwnedStagingDirectory()=runBlocking {
        val original=File(context.filesDir,"original.pdf").apply { writeText("original content") }
        val target=evidence("invalid-output.bin")
        try {
            ExportFiles.discard(context,original.path);assertTrue(original.exists())
            assertTrue(runCatching { ExportFiles.save(context,original.path,Uri.fromFile(target)) }.isFailure)
            assertTrue(original.exists());assertFalse(target.exists())
        } finally { original.delete() }
    }
    @Test fun graphCropAndPdfEmbedOriginalPixelsAndCaptionCoordinates() {
        val bitmap=Bitmap.createBitmap(500,700,Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            val pen=Paint().apply { color=Color.BLACK;strokeWidth=2f }
            for(x in listOf(50f,280f)) {
                canvas.drawLine(x,180f,x,380f,pen);canvas.drawLine(x,380f,x+180f,380f,pen)
                canvas.drawLine(x,360f,x+160f,200f,pen)
            }
            val crop=GraphRasterCropper.find(bitmap,FigureBounds(.10f,.215f,.46f,.235f))
            assertNotNull(crop);assertTrue(crop!!.right<.52f);assertTrue(crop.bottom>.54f)
            assertNull(GraphRasterCropper.find(bitmap,null))
            val stagedPng=ExportFiles.stagePng(context,bitmap,crop)
            val blocks=ExportFixture.content()+ReportBlock("Output Power vs Supply Voltage",ReportStyle.HEADING)+
                ReportBlock("Kaynak grafiği",ReportStyle.IMAGE,image=ReportImage(stagedPng.readBytes()))
            val staged=ExportFiles.stagePdf(context,blocks)
            val result=evidence("analysis-graph.pdf");staged.copyTo(result,true);staged.delete();stagedPng.delete()
            PDDocument.load(result).use { pdf ->
                val captions=PdfFigureExtractor().extract(pdf,listOf("Output Power versus Supply Voltage"))
                assertTrue(captions.isNotEmpty());assertNotNull(captions.first().match.titleBounds)
                assertTrue((0 until pdf.numberOfPages).any { page -> pdf.getPage(page).resources.xObjectNames.any {
                    pdf.getPage(page).resources.isImageXObject(it)
                } })
            }
        } finally { bitmap.recycle() }
    }
}
