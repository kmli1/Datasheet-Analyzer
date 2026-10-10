package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.network.*
import com.example.datasheetanalyzer.domain.document.DocumentCandidatePlanner
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NetworkFallbackTest {
    private fun ranked(url: String)=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet",url))).candidates.single()
    @Test fun routeSeparatorsAreNotPartSuffixes() {
        assertEquals(PartMatch.EXACT,PartNumberMatcher().matchUrl("LM383",CandidateUrlPolicy.parse("https://www.datasheetarchive.com/datasheet/LM383/National-Semiconductor")!!))
    }
    @Test fun encodedSuffixIsStillAVariant() {
        assertEquals(PartMatch.FAMILY_VARIANT,PartNumberMatcher().matchUrl("LM383",CandidateUrlPolicy.parse("https://www.ti.com/lit/LM383%2FNOPB.pdf")!!))
    }
    @Test fun slashInRequestedPartIsPreserved() {
        assertEquals(PartMatch.EXACT,PartNumberMatcher().matchUrl("LM358-N/P",CandidateUrlPolicy.parse("https://www.ti.com/docs/LM358-N/P.pdf")!!))
    }
    @Test fun blockedSearchesStillReturnIndependentManufacturer()=runBlocking {
        val blocked=SearchProvider { throw SearchFailure(SearchFailureKind.BLOCKED,"blocked") }
        val expected=SearchCandidate("LM383 Datasheet","https://www.ti.com/lit/ds/symlink/lm383.pdf")
        val provider=PreferredSearchProvider(blocked,blocked,alternatives=listOf(SearchProvider { listOf(expected) }))
        assertEquals(listOf(expected),provider.searchDatasheets("LM383"))
    }
    @Test fun allFailuresRemainVisible()=runBlocking {
        val blocked=SearchProvider { throw SearchFailure(SearchFailureKind.BLOCKED,"blocked") }
        try { PreferredSearchProvider(blocked,blocked).searchDatasheets("LM383");fail() }
        catch(e: SearchFailure) { assertEquals(SearchFailureKind.BLOCKED,e.kind) }
    }
    @Test fun parentCancellationIsNotSwallowed()=runBlocking {
        val slow=SearchProvider { delay(Long.MAX_VALUE);emptyList() }
        val task=async { PreferredSearchProvider(slow,slow).searchDatasheets("LM383") }
        yield();task.cancel()
        try { task.await();fail() } catch(_: CancellationException) { }
    }
    @Test fun manufacturerHintsNeedARealPdfResponse()=runBlocking {
        val provider=ManufacturerPdfSearchProvider(SourceHttp(SafeHttpTransport { r ->
            if(r.url.contains("ti.com")) SafeResponse(206,mapOf("Content-Type" to "application/pdf","Content-Range" to "bytes 0-1023/5000"),"%PDF-1.4 test".byteInputStream())
            else SafeResponse(404,emptyMap(),byteArrayOf().inputStream())
        }))
        val result=provider.searchDatasheets("LM383")
        assertEquals(1,result.size);assertEquals("https://www.ti.com/lit/ds/symlink/lm383.pdf",result.single().url)
    }
    @Test fun htmlMasqueradingAsPdfIsIgnored()=runBlocking {
        val provider=ManufacturerPdfSearchProvider(SourceHttp(SafeHttpTransport {
            SafeResponse(200,mapOf("Content-Type" to "application/pdf"),"<html>verify</html>".byteInputStream())
        }))
        assertTrue(provider.searchDatasheets("LM383").isEmpty())
    }
    @Test fun manufacturerHintsNeverStripRequestedSuffix()=runBlocking {
        val urls=java.util.Collections.synchronizedList(mutableListOf<String>())
        val provider=ManufacturerPdfSearchProvider(SourceHttp(SafeHttpTransport {
            urls+=it.url;SafeResponse(404,emptyMap(),byteArrayOf().inputStream())
        }))
        provider.searchDatasheets("LM383A")
        assertTrue(urls.isNotEmpty());assertTrue(urls.all { "lm383a" in it })
    }
    @Test fun archiveMetadataBuildsDirectDocumentRoute() {
        val html="<table><tr data-mfrpartnumber='LM383' data-mfr='National Semiconductor' data-sheettype='Original' data-description='Audio amplifier'></tr></table>"
        val result=DatasheetArchiveSearchProvider.parse(html).single()
        assertEquals("https://www.datasheetarchive.com/datasheet/LM383/National-Semiconductor?term=LM383",result.url)
    }
    @Test fun shortformAndUnknownArchiveRowsAreExcluded() {
        val html="<table><tr data-mfrpartnumber='LM383' data-mfr='National Semiconductor' data-sheettype='Shortform'></tr><tr data-mfrpartnumber='LM383' data-mfr='Unknown' data-sheettype='Original'></tr></table>"
        assertTrue(DatasheetArchiveSearchProvider.parse(html).isEmpty())
    }
    @Test fun archiveChallengeRemainsBlocked() {
        try { DatasheetArchiveSearchProvider.parse("<form id='challenge-form'></form>");fail() }
        catch(e: SearchFailure) { assertEquals(SearchFailureKind.BLOCKED,e.kind) }
    }
    @Test fun independentSourcesFitWithinThreeAttempts() {
        val first=ranked("https://www.datasheetarchive.com/pdf/LM383.pdf")
        val duplicate=ranked("https://www.datasheetarchive.com/pdf/LM383.pdf")
        val another=ranked("https://www.datasheetarchive.com/datasheet/LM383/National-Semiconductor")
        val ti=ranked("https://www.ti.com/lit/ds/symlink/lm383.pdf")
        val archive=ranked("https://www.st.com/resource/en/datasheet/lm383.pdf")
        assertEquals(listOf(first,ti,archive),DocumentCandidatePlanner.plan(first,listOf(first,duplicate,another,ti,archive),3))
    }
    @Test fun viewerFileIsDecodedAndSourceChecked() {
        val base="https://www.datasheetarchive.com/datasheet/LM383/National-Semiconductor"
        val target="https://www.datasheetarchive.com/pdf/download/distributors/Datasheets-12/DSA-228987.pdf?h=test"
        val src="/viewer?file="+java.net.URLEncoder.encode(target,"UTF-8")
        assertEquals(listOf(target),PdfLinkResolver.extract("LM383",base,"<iframe src='$src'></iframe>"))
    }
    @Test fun viewerCannotRedirectToUnregisteredHost() {
        val src="/viewer?file="+java.net.URLEncoder.encode("https://evil.example/LM383.pdf","UTF-8")
        assertTrue(PdfLinkResolver.extract("LM383","https://www.datasheetarchive.com/datasheet/LM383/National-Semiconductor","<iframe src='$src'></iframe>").isEmpty())
    }
}
