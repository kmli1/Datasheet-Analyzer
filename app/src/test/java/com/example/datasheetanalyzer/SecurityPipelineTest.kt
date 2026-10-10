package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.download.DownloadManager
import com.example.datasheetanalyzer.data.network.*
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.resolver.*
import com.example.datasheetanalyzer.domain.security.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.net.InetAddress
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SecurityPipelineTest {
    private val bytes=javaClass.getResourceAsStream("/security/valid.pdf")!!.readBytes()
    private val url="https://ti.com/LM383.pdf"
    private fun response(status:Int=200,data:ByteArray=bytes,headers:Map<String,String> = mapOf("Content-Type" to "application/pdf","Content-Length" to data.size.toString())) =
        SafeResponse(status,headers,ByteArrayInputStream(data))
    private fun failure(expected:String,block:()->Unit) {
        try { block();fail("Expected rejection") } catch(e:SecurityFailure) { assertEquals(expected,e.code) }
    }
    @Test fun acceptsPdfMagicRejectsFakeExtension() {
        PdfPolicy.magic(bytes)
        failure("MAGIC") { PdfPolicy.magic("notpdf.pdf".toByteArray()) }
    }
    @Test fun mimeIsNotInferredFromExtension() {
        PdfPolicy.pdfMime("application/pdf; charset=binary")
        failure("MIME") { PdfPolicy.pdfMime("text/html") }
    }
    @Test fun unknownAndHttpSourcesRejectedBeforeRequest() {
        failure("SOURCE") { PdfPolicy.source("https://unknown.test/LM383.pdf") }
        failure("URL") { PdfPolicy.source("http://ti.com/LM383.pdf") }
    }
    @Test fun rejectsKnownAndActualOversize() {
        failure("SIZE") { PdfPolicy.length("20971521",20L*1024*1024) }
        runBlocking {
            val dir=Files.createTempDirectory("pdf-test").toFile()
            val oversized=bytes+ByteArray(9000)
            val http=SourceHttp(SafeHttpTransport { r -> response(data=if(r.headers.containsKey("Range")) bytes else oversized,
                headers=mapOf("Content-Type" to "application/pdf")) })
            failure("SIZE") { runBlocking { DownloadManager(http,dir,SecurityConfig(maxPdfSize=8192)).download(url) } }
            assertTrue(dir.listFiles()!!.isEmpty());dir.delete()
        }
    }
    @Test fun rawScanHandlesEveryChunkBoundaryAndEscapes() {
        for(token in listOf("/JavaScript ","/J#53 ","/EmbeddedFile ")) {
            for(split in 1 until token.length) failure("ACTIVE_CONTENT") {
                val scan=RawPdfScanner();scan.accept(token.take(split).toByteArray());scan.accept(token.drop(split).toByteArray());scan.finish()
            }
        }
    }
    @Test fun rawScanDoesNotMatchLongerNames() {
        RawPdfScanner().apply { accept("/JSomething /AAOther /JavaScriptDisabled".toByteArray());finish() }
    }
    @Test fun publicDnsExcludesPrivateLocalAndReserved() {
        for(ip in listOf("127.0.0.1","10.0.0.1","169.254.169.254","192.168.0.1","100.64.1.1","198.19.0.1","203.0.113.8","::1","fc00::1","2001:db8::1"))
            assertFalse(ip,PdfPolicy.publicAddress(InetAddress.getByName(ip)))
        assertTrue(PdfPolicy.publicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(PdfPolicy.publicAddress(InetAddress.getByName("2606:4700::1111")))
    }
    @Test fun redirectToUnknownDomainIsRejectedBeforeFollowing() = runBlocking<Unit> {
        var calls=0
        val http=SourceHttp(SafeHttpTransport { calls++;response(302,headers=mapOf("Location" to "https://evil.test/x.pdf")) })
        failure("SOURCE") { runBlocking { http.request(SafeRequest(url)) } }
        assertEquals(1,calls)
    }
    @Test fun redirectsAreBounded() = runBlocking<Unit> {
        var calls=0
        val http=SourceHttp(SafeHttpTransport { calls++;response(302,headers=mapOf("Location" to "/next.pdf")) })
        failure("REDIRECT") { runBlocking { http.request(SafeRequest(url)) } }
        assertEquals(4,calls)
    }
    @Test fun head405FallsBackToRangeAndStreamingHash() = runBlocking<Unit> {
        val requests=mutableListOf<SafeRequest>();val dir=Files.createTempDirectory("pdf-test").toFile()
        val http=SourceHttp(SafeHttpTransport { r -> requests+=r;if(r.method=="HEAD") response(405) else response() })
        val pdf=DownloadManager(http,dir).download(url)
        assertEquals(listOf("HEAD","GET","GET"),requests.map { it.method })
        assertEquals("bytes=0-8191",requests[1].headers["Range"])
        assertEquals(bytes.size.toLong(),pdf.bytes)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },pdf.sha256)
        assertArrayEquals(bytes,pdf.file.readBytes());pdf.file.delete();dir.delete()
    }
    @Test fun suspiciousDownloadDeletesPartialFile() = runBlocking<Unit> {
        val dir=Files.createTempDirectory("pdf-test").toFile()
        val data=bytes+"/Launch ".toByteArray()
        val http=SourceHttp(SafeHttpTransport { r -> if(r.method=="HEAD" || r.headers.containsKey("Range")) response() else response(data=data) })
        failure("ACTIVE_CONTENT") { runBlocking { DownloadManager(http,dir).download(url) } }
        assertTrue(dir.listFiles()!!.isEmpty());dir.delete()
    }
    @Test fun truncatedBodyDeletesPartialFile() = runBlocking<Unit> {
        val dir=Files.createTempDirectory("pdf-test").toFile()
        val http=SourceHttp(SafeHttpTransport { r -> response(headers=mapOf("Content-Type" to "application/pdf",
            "Content-Length" to (if(r.method=="GET" && !r.headers.containsKey("Range")) bytes.size+100 else bytes.size).toString())) })
        failure("TRUNCATED") { runBlocking { DownloadManager(http,dir).download(url) } }
        assertTrue(dir.listFiles()!!.isEmpty());dir.delete()
    }
    @Test fun rangeStartingAtNonzeroOffsetRejected() = runBlocking<Unit> {
        val dir=Files.createTempDirectory("pdf-test").toFile()
        val http=SourceHttp(SafeHttpTransport { r -> if(r.headers.containsKey("Range")) response(206,headers=mapOf(
            "Content-Type" to "application/pdf","Content-Range" to "bytes 8-100/200")) else response() })
        failure("RANGE") { runBlocking { DownloadManager(http,dir).download(url) } };dir.delete()
    }
    @Test fun compressedHttpTransferRejected() = runBlocking<Unit> {
        val dir=Files.createTempDirectory("pdf-test").toFile()
        val http=SourceHttp(SafeHttpTransport { response(headers=mapOf("Content-Type" to "application/pdf","Content-Encoding" to "gzip")) })
        failure("ENCODING") { runBlocking { DownloadManager(http,dir).download(url) } };dir.delete()
    }
    @Test fun failedStructureValidationDeletesDownload() = runBlocking<Unit> {
        val dir=Files.createTempDirectory("pdf-test").toFile();val http=SourceHttp(SafeHttpTransport { response() })
        val candidate=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 datasheet",url))).candidates.single()
        val pipeline=DocumentSecurityPipeline(PdfLinkResolver(SourceHtmlFetcher(http)),DownloadManager(http,dir),
            PdfStructureValidator { SandboxResult(false,"INVALID_STRUCTURE") })
        failure("INVALID_STRUCTURE") { runBlocking { pipeline.check("LM383",candidate) {} } }
        assertTrue(dir.listFiles()!!.isEmpty());dir.delete()
    }
    @Test fun cancellationDeletesPartialFile() = runBlocking<Unit> {
        val dir=Files.createTempDirectory("pdf-test").toFile()
        var calls=0
        val http=SourceHttp(SafeHttpTransport { calls++;if(calls==3) throw CancellationException("fixture") else response() })
        try { DownloadManager(http,dir).download(url);fail("cancel") } catch(_: CancellationException) {}
        assertTrue(dir.listFiles()!!.isEmpty());dir.delete()
    }
}
