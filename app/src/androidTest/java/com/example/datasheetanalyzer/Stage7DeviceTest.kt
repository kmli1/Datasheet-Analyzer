package com.example.datasheetanalyzer

import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.datasheetanalyzer.data.cache.DocumentCache
import com.example.datasheetanalyzer.data.repository.ResolverDependencies
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.ocr.DocumentOcr
import com.example.datasheetanalyzer.sandbox.IsolatedPdfClient
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

object Stage7Fixture {
    fun pdf(file: File) {
        val pdf=PdfDocument()
        try {
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(600,800,1).create());val paint=Paint().apply { color=Color.BLACK;textSize=20f;typeface=Typeface.DEFAULT }
            val lines=listOf("LM383","National Semiconductor","General Description","Synthetic device-test fixture only","Features","Electrical Characteristics","Supply Voltage 5 to 20 V","Operating Temperature 0 to 70 C","Applications","Do not use fixture values for engineering")
            lines.forEachIndexed { i,line -> page.canvas.drawText(line,30f,55f+i*42f,paint) }
            pdf.finishPage(page);file.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
    }
}
@RunWith(AndroidJUnit4::class)
class Stage7DeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun nativePdfExtractionAndPreviewStayIsolated()=runBlocking {
        val file=File(context.cacheDir,"stage7-native-test.pdf");Stage7Fixture.pdf(file);var preview: File?=null
        try {
            val client=IsolatedPdfClient(context);val result=client.validate(file)
            assertTrue(result.code,result.passed);assertTrue(result.extraction!!.pages.single().rawText.contains("LM383"))
            preview=client.renderPage(file,1);val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(preview!!.absolutePath,bounds)
            assertTrue(bounds.outWidth.toLong()*bounds.outHeight<=2_000_000)
        } finally { preview?.delete();file.delete() }
    }
    @Test fun bundledOcrReadsTwoImagesAndCleansInputs()=runBlocking {
        val ocr=DocumentOcr()
        try { repeat(2) { index ->
            val file=File(context.cacheDir,"stage7-ocr-$index.png");val bitmap=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
            val paint=Paint().apply { color=Color.BLACK;textSize=90f;typeface=Typeface.DEFAULT_BOLD;isAntiAlias=true };canvas.drawText("LM383",80f,180f,paint);canvas.drawText("Supply Voltage",80f,330f,paint);canvas.drawText("5 to 20 V",80f,480f,paint)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            assertTrue(ocr.recognize(file).contains("LM383"));assertFalse(file.exists())
        } } finally { ocr.close() }
    }
    @Test fun localPipelineCacheRoundtripKeepsOriginal()=runBlocking {
        val file=File(context.cacheDir,"stage7-pipeline-original.pdf");Stage7Fixture.pdf(file)
        val sha=DocumentCache.digest(file);val input=QuarantinedPdf(file,"local://document/$sha",file.length(),sha)
        val pipeline=ResolverDependencies.pipeline(context);var result: com.example.datasheetanalyzer.domain.document.AnalyzedDocument?=null
        val local=ResolverDependencies.local(context)
        try {
            local.cache.clear();val candidate=DocumentCache.localCandidate("LM383",input.finalUrl)
            result=pipeline.analyze("LM383",candidate,emptyList(),{},localPdf=input)
            assertTrue(result!!.verification.automaticEligible);val saved=local.cache.load("LM383")!!
            assertTrue(saved.fromCache);assertTrue(saved.verification.automaticEligible);saved.checked.pdf.file.delete();assertTrue(file.exists())
        } finally { pipeline.close();result?.checked?.pdf?.file?.delete();file.delete();local.cache.clear() }
    }
    @Test fun invalidImageIsRejectedBeforeOcr()=runBlocking {
        val file=File(context.cacheDir,"not-an-image.bin").apply { writeText("not a valid image") }
        try { try { IsolatedPdfClient(context).renderImage(file);fail("Image decode must fail") } catch(_: SecurityFailure) { } } finally { file.delete() }
    }
    @Test fun sourceEvidenceCoordinatesAreReturnedByIsolatedService()=runBlocking {
        val file=File(context.cacheDir,"source-evidence-test.pdf");Stage7Fixture.pdf(file)
        try {
            val rectangles=IsolatedPdfClient(context).findEvidence(file,1,"Supply Voltage 5 to 20 V",com.example.datasheetanalyzer.domain.document.DatasheetSection.ELECTRICAL)
            assertTrue(rectangles.isNotEmpty());assertTrue(rectangles.all { it.left>=0 && it.right<=1 && it.top>=0 && it.bottom<=1 })
        } finally { file.delete() }
    }
}
