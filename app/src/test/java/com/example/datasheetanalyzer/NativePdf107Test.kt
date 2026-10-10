package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.download.DownloadManager
import com.example.datasheetanalyzer.data.network.*
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.*
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.sandbox.PdfSandboxInspector
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/** Opt-in real archive response, kept outside the distributable source package. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class NativePdf107Test {
    @Test fun lm709ahArchiveResponsePassesDownloadStructureAndBodyIdentity()=runBlocking<Unit> {
        val realDir=System.getProperty("datasheet.realDir");assumeNotNull(realDir)
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
        val source=File(realDir!!,"lm709-archive.pdf");assertTrue(source.isFile)
        val bytes=source.readBytes()
        // Matches the failing historical scanner: '/' followed by a long binary run.
        assertTrue(bytes.asSequence().windowed(130).any { row -> row[0]=='/'.code.toByte() &&
            row.drop(1).all { val c=it.toInt() and 255;c>32 && c.toChar() !in "/()<>[]{}%" } })
        val dir=File(RuntimeEnvironment.getApplication().cacheDir,"native107").apply { mkdirs() }
        val url="https://www.datasheetarchive.com/pdf/download/distributors/Datasheets-22/DSA-424033.pdf"
        val http=SourceHttp(SafeHttpTransport {
            SafeResponse(200,mapOf("Content-Type" to "application/pdf","Content-Length" to bytes.size.toString()),ByteArrayInputStream(bytes))
        })
        val candidate=CandidateSelector().select("LM709AH",listOf(SearchCandidate("LM709AH Datasheet — National Semiconductor",url))).candidates.single()
        val pipeline=DocumentSecurityPipeline(PdfLinkResolver(SourceHtmlFetcher(http)),DownloadManager(http,dir),PdfStructureValidator {
            val extraction=it.inputStream().use { input -> PdfSandboxInspector().extract(input) }
            SandboxResult(true,"PASSED",extraction.totalPages,extraction)
        })
        try {
            val checked=pipeline.check("LM709AH",candidate) {}
            assertEquals(SecurityStatus.PASSED,checked.security)
            val extraction=checked.extraction!!.let { it.copy(pages=TextCleaner().clean(it.pages)) }
            assertTrue(extraction.totalPages in 8..10);assertFalse(extraction.truncated)
            assertEquals(TextOrigin.TEXT_LAYER,extraction.pages.first().origin)
            assertTrue(extraction.pages.first().rawText.contains("LM709AH"))
            val sections=DatasheetSectionDetector().detect(extraction.pages)
            assertTrue(sections.any { it.section==DatasheetSection.ELECTRICAL })
            val verification=DocumentVerifier().verify("LM709AH",candidate,extraction,sections)
            assertTrue(verification.reasons.joinToString(),verification.automaticEligible)
            System.getProperty("datasheet.qaDir")?.let { root ->
                File(root,"lm709ah-107.txt").apply {
                    parentFile!!.mkdirs();writeText("${verification.code}: ${verification.reasons}\n"+
                        extraction.pages.joinToString("\n") { "PAGE ${it.pageNumber}\n${it.text}" })
                }
            }
        } finally { dir.deleteRecursively() }
    }
}
