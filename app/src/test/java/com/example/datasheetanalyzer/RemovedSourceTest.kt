package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.cache.DocumentCache
import com.example.datasheetanalyzer.data.network.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.*
import com.example.datasheetanalyzer.domain.security.*
import java.io.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemovedSourceTest {
    @Test fun removedSitesAndMirrorsAreBlocked() {
        for(host in listOf("www.alldatasheet.com","pdf1.alldatasheet.com","alldatasheet.net","alldatasheet.jp","alldatasheetcn.com"))
            assertEquals(host,TrustLevel.BLOCKED,TrustedDomainRegistry().classify(CandidateUrlPolicy.parse("https://$host/LM383.pdf")!!).level)
    }
    @Test fun removedSiteNeverAppearsInResults() {
        val result=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet","https://www.alldatasheet.com/LM383.pdf"),
            SearchCandidate("LM383 Datasheet","https://www.ti.com/lit/ds/symlink/lm383.pdf")))
        assertEquals(1,result.candidates.size);assertEquals(1,result.discardedCount)
        assertEquals("www.ti.com",result.candidates.single().host)
    }
    @Test fun realArchiveRemainsRegistered() {
        assertEquals(TrustLevel.ARCHIVE,TrustedDomainRegistry().classify(CandidateUrlPolicy.parse("https://www.datasheetarchive.com/pdf/LM383.pdf")!!).level)
    }
    @Test fun manufacturersAreRecommendedBeforeIndependentArchive() {
        val result=CandidateSelector().select("LM386",listOf(SearchCandidate("LM386 Datasheet","https://www.datasheetarchive.com/pdf/LM386.pdf"),
            SearchCandidate("LM386 Datasheet","https://www.ti.com/lit/ds/symlink/lm386.pdf")))
        assertEquals("www.ti.com",result.candidates.first().host)
    }
    @Test fun excludedSourceMakesZeroHttpRequests()=runBlocking {
        var calls=0
        val http=SourceHttp(SafeHttpTransport { calls++;SafeResponse(200,emptyMap(),byteArrayOf().inputStream()) })
        try { http.request(SafeRequest("https://www.alldatasheet.com/LM383.pdf"));fail() }
        catch(e: SecurityFailure) { assertEquals("SOURCE",e.code) }
        assertEquals(0,calls)
    }
    @Test fun redirectToRemovedSourceIsRejectedBeforeSecondRequest()=runBlocking {
        val calls=mutableListOf<String>()
        val http=SourceHttp(SafeHttpTransport { calls+=it.url
            SafeResponse(302,mapOf("Location" to "https://pdf1.alldatasheet.com/LM383.pdf"),byteArrayOf().inputStream()) })
        try { http.request(SafeRequest("https://www.ti.com/LM383.pdf"));fail() }
        catch(e: SecurityFailure) { assertEquals("SOURCE",e.code) }
        assertEquals(listOf("https://www.ti.com/LM383.pdf"),calls)
    }
    @Test fun removedBaseCannotResolvePdfLinks() {
        try { PdfLinkResolver.extract("LM383","https://www.alldatasheet.com/LM383.pdf","<a href='https://www.ti.com/LM383.pdf'>LM383</a>");fail() }
        catch(e: SecurityFailure) { assertEquals("SOURCE",e.code) }
    }
    @Test fun permittedViewerCannotIntroduceRemovedDownloadHost() {
        val src="/viewer?file="+java.net.URLEncoder.encode("https://www.alldatasheet.com/LM383.pdf","UTF-8")
        assertTrue(PdfLinkResolver.extract("LM383","https://www.datasheetarchive.com/datasheet/LM383/National-Semiconductor","<iframe src='$src'></iframe>").isEmpty())
    }
    private fun legacyCache(source: String,url: String,queryPart: String="LM383",bytesOnly: Boolean=false)=runBlocking {
        val root=Files.createTempDirectory("removed-source").toFile()
        try {
            val cacheDir=File(root,"cache").apply { mkdirs() }
            val original=File(root,"user-original.pdf").apply { writeBytes(RemovedSourceTest::class.java.getResourceAsStream("/security/valid.pdf")!!.use { it.readBytes() }) }
            val sha=DocumentCache.digest(original);original.copyTo(File(cacheDir,"$sha.payload"))
            val entry=File(cacheDir,"legacy.entry")
            DataOutputStream(entry.outputStream()).use { out ->
                out.writeInt(DocumentCache.POLICY);out.writeLong(System.currentTimeMillis());out.writeUTF("LM383")
                out.writeUTF(source);out.writeUTF(url);out.writeUTF(sha);out.writeLong(original.length())
            }
            val cache=DocumentCache(cacheDir,File(root,"quarantine"))
            if(bytesOnly) assertEquals(0,cache.bytes()) else assertNull(cache.load(queryPart))
            assertFalse(entry.exists());assertFalse(File(cacheDir,"$sha.payload").exists());assertTrue(original.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun oldRemovedPdfIsNotLoaded()=legacyCache("PDF","https://www.alldatasheet.com/LM383.pdf")
    @Test fun oldHtmlPreviewIsNotLoaded()=legacyCache("ARCHIVE_HTML","https://www.alldatasheet.com/html-pdf/1/NSC/LM383.html")
    @Test fun removedCacheIsPurgedWhenAnotherPartIsSearched()=legacyCache("PDF","https://www.alldatasheet.com/LM383.pdf","LM386")
    @Test fun historyCacheSizeAlsoPurgesRemovedEntries()=legacyCache("PDF","https://www.alldatasheet.com/LM383.pdf",bytesOnly=true)
}
