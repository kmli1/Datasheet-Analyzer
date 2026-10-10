package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.download.DownloadManager
import com.example.datasheetanalyzer.data.network.*
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.resolver.*
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.sandbox.PdfSandboxInspector
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class PdfBinaryScan107Test {
    private val binaryRun=byteArrayOf('/'.code.toByte())+ByteArray(2048) { 0xff.toByte() }
    private fun scan(bytes: ByteArray,chunk: Int=8192) {
        val scanner=RawPdfScanner()
        bytes.asList().chunked(chunk).forEach { scanner.accept(it.toByteArray()) }
        scanner.finish()
    }
    private fun document(edit: (PDDocument)->Unit): ByteArray {
        val output=ByteArrayOutputStream()
        javaClass.getResourceAsStream("/security/valid.pdf")!!.use { input ->
            PDDocument.load(input).use { doc -> edit(doc);doc.save(output) }
        }
        return output.toByteArray()
    }
    private fun binaryPdf(active: Boolean=false)=document { doc ->
        val stream=doc.document.createCOSStream()
        stream.createOutputStream().use { it.write(binaryRun) }
        doc.documentCatalog.cosObject.setItem(COSName.getPDFName("PreviewData"),stream)
        if(active) doc.documentCatalog.cosObject.setItem(COSName.getPDFName("J#S"),COSDictionary().apply {
            setName(COSName.S,"JavaScript")
        })
    }
    private fun checkedPdf(bytes: ByteArray)=PdfSandboxInspector().inspect(bytes.inputStream())
    private fun expectFailure(code: String,block: ()->Unit) {
        try { block();fail("Expected $code") } catch(e: SecurityFailure) { assertEquals(code,e.code) }
    }
    @Test fun binaryImageRunIsNotTreatedAsAPdfName() {
        for(chunk in listOf(1,7,128,129,8192)) scan(binaryRun,chunk)
        val pdf=binaryPdf();scan(pdf,7)
        assertTrue(checkedPdf(pdf).passed)
    }
    @Test fun oversizedHeuristicRunStillFindsFollowingEscapedActiveNames() {
        for(separator in listOf(" ","/","\n","<")) for(chunk in listOf(1,7,129)) {
            expectFailure("ACTIVE_CONTENT") { scan(binaryRun+separator.toByteArray()+"/J#53 ".toByteArray(),chunk) }
        }
    }
    @Test fun longRawNameCannotBecomeAForbiddenPrefix() {
        scan(("/JavaScript"+"a".repeat(200_000)+" /Safe ").toByteArray())
    }
    @Test fun legalLongStaticNamesAreCheckedAfterParsing() {
        val pdf=document { doc ->
            doc.documentCatalog.cosObject.setItem(COSName.getPDFName("StaticResource"+"a".repeat(200)),COSName.getPDFName("b".repeat(300)))
        }
        scan(pdf,7);assertTrue(checkedPdf(pdf).passed)
    }
    @Test fun actualOversizedDictionaryKeyIsRejectedByStructureGuard() {
        val pdf=document { it.documentCatalog.cosObject.setItem(COSName.getPDFName("a".repeat(1025)),COSInteger.ONE) }
        scan(pdf);assertEquals("NAME_LIMIT",checkedPdf(pdf).code)
    }
    @Test fun actualOversizedNameValueIsRejectedByStructureGuard() {
        val pdf=document { it.documentCatalog.cosObject.setItem(COSName.getPDFName("PreviewType"),COSName.getPDFName("a".repeat(1025))) }
        scan(pdf);assertEquals("NAME_LIMIT",checkedPdf(pdf).code)
    }
    @Test fun encodedNameIsMeasuredAfterDecoding() {
        val name="x".repeat(1024)
        val pdf=document { it.documentCatalog.cosObject.setItem(COSName.getPDFName(name),COSInteger.ONE) }
        scan(pdf);assertTrue(checkedPdf(pdf).passed)
        val unicode=document { it.documentCatalog.cosObject.setItem(COSName.getPDFName("é".repeat(513)),COSInteger.ONE) }
        assertEquals("NAME_LIMIT",checkedPdf(unicode).code)
    }
    @Test fun compressedAndEscapedActiveObjectsRemainBlockedAfterLongRuns() {
        for(name in listOf("active.pdf","escaped-active.pdf","compressed-active.pdf")) {
            val pdf=javaClass.getResourceAsStream("/security/$name")!!.readBytes()
            assertEquals(name,"ACTIVE_CONTENT",checkedPdf(pdf).code)
        }
        assertEquals("ACTIVE_CONTENT",checkedPdf(binaryPdf(active=true)).code)
    }
    private fun pipeline(bytes: ByteArray,dir: java.io.File): Pair<DocumentSecurityPipeline,com.example.datasheetanalyzer.domain.model.RankedCandidate> {
        val url="https://www.ti.com/LM709AH.pdf"
        val http=SourceHttp(SafeHttpTransport {
            SafeResponse(200,mapOf("Content-Type" to "application/pdf","Content-Length" to bytes.size.toString()),ByteArrayInputStream(bytes))
        })
        val candidate=CandidateSelector().select("LM709AH",listOf(SearchCandidate("LM709AH datasheet",url))).candidates.single()
        return DocumentSecurityPipeline(PdfLinkResolver(SourceHtmlFetcher(http)),DownloadManager(http,dir),
            PdfStructureValidator { it.inputStream().use { input -> PdfSandboxInspector().inspect(input) } }) to candidate
    }
    @Test fun binaryPdfReachesFullStructureGuardInDownloadPipeline()=runBlocking {
        val dir=Files.createTempDirectory("binary107").toFile()
        try {
            val bytes=binaryPdf();val (pipeline,candidate)=pipeline(bytes,dir)
            val checked=pipeline.check("LM709AH",candidate) {}
            assertEquals(SecurityStatus.PASSED,checked.security);assertEquals(1,checked.pages)
            assertArrayEquals(bytes,checked.pdf.file.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun overlongActualNameStillDeletesQuarantinedDownload()=runBlocking {
        val dir=Files.createTempDirectory("name107").toFile()
        try {
            val bytes=document { it.documentCatalog.cosObject.setItem(COSName.getPDFName("a".repeat(1025)),COSInteger.ONE) }
            val (pipeline,candidate)=pipeline(bytes,dir)
            expectFailure("NAME_LIMIT") { runBlocking { pipeline.check("LM709AH",candidate) {} } }
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
