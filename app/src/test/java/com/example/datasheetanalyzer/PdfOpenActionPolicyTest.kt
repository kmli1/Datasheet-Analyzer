package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.security.RawPdfScanner
import com.example.datasheetanalyzer.sandbox.PdfSandboxInspector
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class PdfOpenActionPolicyTest {
    private fun document(action: (PDDocument)->COSBase): ByteArray {
        val output=ByteArrayOutputStream()
        javaClass.getResourceAsStream("/security/valid.pdf")!!.use { input ->
            PDDocument.load(input).use { doc ->
                doc.documentCatalog.cosObject.setItem(COSName.getPDFName("OpenAction"),action(doc))
                doc.save(output)
            }
        }
        return output.toByteArray()
    }
    private fun destination(doc: PDDocument)=COSArray().apply {
        add(doc.getPage(0).cosObject);add(COSName.getPDFName("XYZ"))
        add(COSNull.NULL);add(COSNull.NULL);add(COSInteger.ONE)
    }
    private fun action(type: String)=COSDictionary().apply { setName(COSName.S,type) }
    private fun inspect(bytes: ByteArray)=PdfSandboxInspector().inspect(bytes.inputStream())
    @Test fun staticOpenViewNoLongerRejectedAtDownloadOrParse() {
        val pdf=document(::destination)
        RawPdfScanner().apply { pdf.asList().chunked(7).forEach { accept(it.toByteArray()) };finish() }
        val result=inspect(pdf);assertTrue(result.code,result.passed)
    }
    @Test fun escapedOpenActionTokenIsDeferredToStructuralInspection() {
        for(split in 1..10) {
            val token="/Open#41ction [1 0 R /Fit]".toByteArray()
            RawPdfScanner().apply { accept(token.copyOfRange(0,split));accept(token.copyOfRange(split,token.size));finish() }
        }
    }
    @Test fun localGoToIsAllowed() {
        val pdf=document { doc -> action("GoTo").apply { setItem(COSName.D,destination(doc)) } }
        assertTrue(inspect(pdf).passed)
    }
    @Test fun externalUriOpenActionIsRejected() {
        val pdf=document { action("URI").apply { setString(COSName.URI,"https://example.test/") } }
        assertEquals("ACTIVE_CONTENT",inspect(pdf).code)
    }
    @Test fun localGoToCannotHideChainedExternalAction() {
        val pdf=document { doc -> action("GoTo").apply {
            setItem(COSName.D,destination(doc));setItem(COSName.NEXT,action("URI"))
        } }
        assertEquals("ACTIVE_CONTENT",inspect(pdf).code)
    }
    @Test fun scriptLaunchAndRemoteActionsRemainBlocked() {
        for(type in listOf("JavaScript","Launch","GoToR","GoToE","SubmitForm")) {
            assertEquals(type,"ACTIVE_CONTENT",inspect(document { action(type) }).code)
        }
    }
    @Test fun unrelatedPageAndMalformedDestinationAreRejected() {
        assertEquals("ACTIVE_CONTENT",inspect(document { COSArray() }).code)
        assertEquals("ACTIVE_CONTENT",inspect(document { COSArray().apply {
            add(COSDictionary().apply { setName(COSName.TYPE,"Page") });add(COSName.getPDFName("Fit"))
        } }).code)
    }
}
