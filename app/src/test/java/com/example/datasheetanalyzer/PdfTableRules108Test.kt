package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.sandbox.PdfSandboxInspector
import com.example.datasheetanalyzer.domain.document.TextCleaner
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/** The drawn border pass is exercised through a real PDF, including clip-state restoration. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class PdfTableRules108Test {
    private fun pdf(clip: String): ByteArray {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        val output=ByteArrayOutputStream()
        PDDocument().use { doc ->
            val page=PDPage();doc.addPage(page)
            PDPageContentStream(doc,page).use { cs ->
                fun text(value: String,x: Float,y: Float) {
                    cs.beginText();cs.setFont(PDType1Font.HELVETICA,8f);cs.newLineAtOffset(x,792f-y);cs.showText(value);cs.endText()
                }
                fun line(x1: Float,y1: Float,x2: Float,y2: Float) {
                    cs.moveTo(x1,792f-y1);cs.lineTo(x2,792f-y2);cs.stroke()
                }
                text("LM383",25f,12f);text("Electrical Characteristics",25f,25f)
                text("Parameter",25f,40f);text("Test conditions",150f,40f)
                text("Min",250f,40f);text("Typ",300f,40f);text("Max",350f,40f);text("Units",450f,40f)
                text("Supply current",25f,65f);text("8",300f,60f);text("12",350f,70f);text("mA",450f,65f)
                cs.saveGraphicsState()
                when(clip) {
                    "collinear" -> {
                        cs.moveTo(0f,396f);cs.lineTo(0f,0f);cs.lineTo(612f,0f);cs.lineTo(612f,396f)
                        cs.lineTo(612f,792f);cs.lineTo(0f,792f);cs.clip()
                    }
                    "partial" -> { cs.addRect(0f,0f,480f,792f);cs.clip() }
                    "curved" -> {
                        cs.moveTo(0f,0f);cs.lineTo(612f,0f);cs.curveTo(612f,260f,610f,530f,612f,792f)
                        cs.lineTo(0f,792f);cs.closePath();cs.clip()
                    }
                }
                cs.setLineWidth(0.3f)
                for(x in listOf(25f,140f,240f,440f,490f)) line(x,35f,x,80f)
                for(y in listOf(35f,50f,80f)) line(25f,y,490f,y)
                line(140f,65f,440f,65f)
                cs.restoreGraphicsState()
            }
            doc.save(output)
        }
        return output.toByteArray()
    }
    @Test fun collinearPointsInRectangularClipStillPermitClosedMergedCells() {
        val text=TextCleaner().normalize(pdf("collinear").inputStream().use { PdfSandboxInspector().extract(it) }.pages.single().rawText)
        assertTrue(text,text.contains("Supply current | | | 8 | | mA"))
        assertTrue(text,text.contains("Supply current | | | | 12 | mA"))
    }
    @Test fun clippedAwayClosingBorderDoesNotAuthorizeFloatingUnit() {
        val text=pdf("partial").inputStream().use { PdfSandboxInspector().extract(it) }.pages.single().rawText
        assertFalse(text,text.contains("[Native table columns]"))
    }
    @Test fun unsupportedCurvedClipFallsBackWithoutSharingAmbiguousUnit() {
        val text=pdf("curved").inputStream().use { PdfSandboxInspector().extract(it) }.pages.single().rawText
        assertFalse(text,text.contains("[Native table columns]"))
    }
}
