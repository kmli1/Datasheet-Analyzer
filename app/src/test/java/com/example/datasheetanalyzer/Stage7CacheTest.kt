package com.example.datasheetanalyzer
import com.example.datasheetanalyzer.data.cache.DocumentCache
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.security.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class Stage7CacheTest {
    private fun fixture(block: suspend (File,DocumentCache,AnalyzedDocument,()->Unit)->Unit)=runBlocking {
        val root=Files.createTempDirectory("cache-test").toFile();var time=1700000000000L
        val cache=DocumentCache(File(root,"cache"),File(root,"quarantine")) { time }
        val url="https://www.datasheetarchive.com/pdf/lm383.pdf"
        val candidate=CandidateSelector().select("LM383",listOf(SearchCandidate("LM383 Datasheet",url))).candidates.single()
        val text="LM383\nNational Semiconductor\nGeneral Description\nFeatures\nElectrical Characteristics\nSupply Voltage 5 to 20 V"
        val file=File(root,"original.pdf").apply { writeBytes(Stage7CacheTest::class.java.getResourceAsStream("/security/valid.pdf")!!.use { it.readBytes() }) }
        val extraction=PdfExtraction(PdfMetadata(),1,TextCleaner().clean(listOf(PageText(1,text,origin=TextOrigin.TEXT_LAYER))))
        val sections=DatasheetSectionDetector().detect(extraction.pages);val verification=DocumentVerifier().verify("LM383",candidate,extraction,sections)
        assertTrue(verification.automaticEligible)
        val doc=AnalyzedDocument(CheckedDocument(QuarantinedPdf(file,url,file.length(),DocumentCache.digest(file)),1,security=SecurityStatus.PASSED,identity=verification.status,downloadMs=0,checkMs=0,extraction=extraction),candidate,extraction,sections,verification,sourceKind=DocumentSource.PDF,requestedPart="LM383")
        try { block(root,cache,doc) { time+=8L*24*60*60*1000 } } finally { root.deleteRecursively() }
    }
    @Test fun roundtripRechecksIdentityAndKeepsOriginal()=fixture { _,cache,doc,_ -> cache.save("LM383",doc);val result=cache.load("LM383")!!;assertTrue(result.fromCache);assertTrue(result.verification.automaticEligible);assertEquals(doc.extraction,result.extraction);result.checked.pdf.file.delete();assertTrue(doc.checked.pdf.file.exists()) }
    @Test fun otherPartCannotLoad()=fixture { _,cache,doc,_ -> cache.save("LM383",doc);assertNull(cache.load("LM386")) }
    @Test fun expiredCacheCannotLoad()=fixture { _,cache,doc,advance -> cache.save("LM383",doc);advance();assertNull(cache.load("LM383")) }
    @Test fun modifiedPayloadCannotLoad()=fixture { root,cache,doc,_ -> cache.save("LM383",doc);File(root,"cache").listFiles()!!.first { it.extension=="payload" }.appendText("tampered");assertNull(cache.load("LM383")) }
    @Test fun corruptedManifestCannotLoad()=fixture { root,cache,doc,_ -> cache.save("LM383",doc);File(root,"cache").listFiles()!!.first { it.extension=="entry" }.writeBytes(byteArrayOf(1,2,3));assertNull(cache.load("LM383")) }
    @Test fun unverifiedDocumentIsNeverSaved()=fixture { _,cache,doc,_ -> cache.save("LM383",doc.copy(verification=doc.verification.copy(automaticEligible=false)));assertNull(cache.load("LM383"));assertEquals(0,cache.bytes()) }
    @Test fun clearKeepsUserOriginal()=fixture { _,cache,doc,_ -> cache.save("LM383",doc);cache.clear();assertEquals(0,cache.bytes());assertTrue(doc.checked.pdf.file.exists()) }
}
