package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.model.RankedCandidate
import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.ocr.DocumentOcr
import com.example.datasheetanalyzer.sandbox.IsolatedPdfClient
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DocumentAnalysisPipeline(private val security: DocumentSecurityPipeline,
    private val raster: IsolatedPdfClient,private val ocr: DocumentOcr,
    private val config: ExtractionConfig=ExtractionConfig(),
    private val cache: com.example.datasheetanalyzer.data.cache.DocumentCache?=null) {
    private val mutex=Mutex()
    suspend fun analyze(part: String,selected: RankedCandidate,candidates: List<RankedCandidate>,
        progress: (CheckProgress)->Unit,localPdf: QuarantinedPdf?=null,forceRefresh: Boolean=false): AnalyzedDocument = mutex.withLock {
        var review: AnalyzedDocument?=null
        var output: AnalyzedDocument?=null
        var delivered=false
        try { withTimeout(180_000) {
            val cleaner=TextCleaner();val detector=DatasheetSectionDetector();val verifier=DocumentVerifier(config)
            val planner=OcrPagePlanner(config);val attempts=mutableListOf<String>()
            val choices=DocumentCandidatePlanner.plan(selected,candidates,if(localPdf!=null) 1 else config.maxCandidateAttempts)
            for(candidate in choices) {
                var checked: CheckedDocument?=null;var keep=false
                try {
                    // Assignment stays within the producing context, including cancellation delivery races.
                    if(localPdf==null) {
                        progress(CheckProgress(CheckPhase.READING_CACHE))
                        (if(forceRefresh) null else cache?.load(part,candidate.canonicalUrl))?.let { return@withTimeout it.also { cached -> output=cached } }

                    }
                    checked=if(localPdf!=null) security.checkLocal(localPdf,progress) else security.check(part,candidate,progress)
                    val document=checked
                    progress(CheckProgress(CheckPhase.EXTRACTING_TEXT))
                    var extraction=document.extraction ?: throw SecurityFailure("NO_TEXT_RESULT","Metin çıkarımı tamamlanamadı.")
                    extraction=extraction.copy(pages=cleaner.clean(extraction.pages))
                    val attempted=mutableSetOf<Int>();val warnings=mutableListOf<String>()
                    var ocrAvailable=true
                    suspend fun runOcr(pageNumbers: List<Int>) {
                        for(number in pageNumbers) {
                            if(!ocrAvailable) break
                            currentCoroutineContext().ensureActive();attempted+=number
                            progress(CheckProgress(CheckPhase.OCR))
                            var image: java.io.File?=null
                            try {
                                image=raster.renderPage(document.pdf.file,number)
                                val raw=ocr.recognize(image)
                                val old=extraction.pages.firstOrNull { it.pageNumber==number }
                                OcrTextRecovery.choose(old,raw,part,config)?.copy(pageNumber=number)?.let { replacement ->
                                    val pages=(extraction.pages.filter { it.pageNumber!=number }+replacement).sortedBy { it.pageNumber }
                                    extraction=extraction.copy(pages=cleaner.clean(pages),truncated=extraction.truncated || replacement.truncated)
                                }
                            } catch(e: TimeoutCancellationException) {
                                currentCoroutineContext().ensureActive();ocrAvailable=false
                                warnings+="Sayfa $number OCR işlemi zaman aşımına uğradı; kalan sayfalar atlandı ve diğer kaynaklar değerlendiriliyor."
                            } catch(e: CancellationException) { throw e }
                              catch(_: Exception) { warnings+="Sayfa $number OCR metni okunamadı." }
                            finally { image?.delete() }
                        }
                    }
                    runOcr(planner.firstPages(extraction))
                    runOcr(planner.identityRecoveryPages(extraction,part,attempted))
                    var sections=detector.detect(extraction.pages)
                    var verification=verifier.verify(part,candidate,extraction,sections)
                    runOcr(planner.extraPages(extraction,attempted,verification,sections))
                    progress(CheckProgress(CheckPhase.VERIFYING_DOCUMENT))
                    sections=detector.detect(extraction.pages)
                    verification=verifier.verify(part,candidate,extraction,sections)
                    verification=verification.copy(reasons=verification.reasons+warnings)
                    if(verification.match==PartMatch.NONE && warnings.isNotEmpty()) verification=verification.copy(
                        code="IDENTITY_UNREADABLE",status=DocumentStatus.REVIEW_REQUIRED,automaticEligible=false)
                    if(verification.code in setOf("WRONG_DOCUMENT","WRONG_MANUFACTURER","IDENTITY_UNREADABLE")) {
                        attempts+="${candidate.host}: ${verification.code}; ilk 3 sayfa kimliği doğrulanamadı"+
                            if(warnings.isEmpty()) "." else "; ${warnings.joinToString(" ")}"
                        if(review==null || verification.score>review!!.verification.score) {
                            review?.checked?.pdf?.file?.delete()
                            review=AnalyzedDocument(document.copy(identity=verification.status,extraction=extraction),
                                candidate,extraction,sections,verification,attempts.toList(),sourceKind=if(localPdf!=null) DocumentSource.LOCAL_PDF else DocumentSource.PDF,requestedPart=part)
                            keep=true
                        }
                        continue
                    }
                    currentCoroutineContext().ensureActive()
                    keep=true
                    output=AnalyzedDocument(document.copy(identity=verification.status,extraction=extraction),
                        candidate,extraction,sections,verification,attempts.toList(),sourceKind=if(localPdf!=null) DocumentSource.LOCAL_PDF else DocumentSource.PDF,requestedPart=part)
                    cacheSafely(part,output!!)
                    return@withTimeout output!!
                } catch(e: SecurityFailure) {
                    // A rejected source is never allowed through; try the next registered candidate instead.
                    if(e.code in setOf("STORAGE","CANDIDATE")) throw e
                    attempts+="${candidate.host}: ${e.message}"
                } catch(e: TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    attempts+="${candidate.host}: belge işlemi zaman aşımına uğradı."
                } catch(e: com.example.datasheetanalyzer.domain.model.SearchFailure) {
                    attempts+="${candidate.host}: ${e.message}; başka kaynak deneniyor."
                } catch(e: java.io.IOException) {
                    attempts+="${candidate.host}: bağlantı tamamlanamadı; başka kaynak deneniyor."
                } finally { if(!keep) checked?.pdf?.file?.delete() }
            }
            // Keep one security-checked preview so a text/OCR failure can be inspected, never auto-approved.
            review?.let {
                output=it.copy(attempts=attempts.toList())
                return@withTimeout output!!
            }
            throw SecurityFailure("NO_VALID_DOCUMENT","Denenen ${choices.size} kaynakta uygun belge bulunamadı.\n"+
                attempts.joinToString("\n")+"\nBaşka bir kaynak seçebilir veya aramayı yenileyebilirsin.")
        }.also { delivered=true } } finally {
            if(!delivered) output?.checked?.pdf?.file?.delete()
            if(!delivered || review?.checked?.pdf?.file!=output?.checked?.pdf?.file) review?.checked?.pdf?.file?.delete()
        }
    }
    fun close() { ocr.close() }
    suspend fun figures(document: AnalyzedDocument,queries: List<com.example.datasheetanalyzer.domain.extraction.ParameterQuery>): List<FigureResult> {
        val titles=queries.filter { it.mode==com.example.datasheetanalyzer.domain.extraction.QueryMode.GRAPH }
            .take(3).map { FigureQuery.clean(it.requested) }.filter { it.length>=3 }
        if(titles.isEmpty()) return emptyList()
        if(!document.verification.automaticEligible || document.checked.security!=SecurityStatus.PASSED)
            return titles.map { FigureResult(it,explanation="Belge kimliği doğrulanamadı; grafik eşleştirmesi için kaynak belgeyi kontrol et.") }
        var native: List<LocatedFigure> = emptyList();var failed=false
        try { native=raster.findFigures(document.checked.pdf.file,titles) }
        catch(e: TimeoutCancellationException) { currentCoroutineContext().ensureActive();failed=true }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { failed=true }
        return titles.mapIndexed { index,title ->
            val hits=native.filter { it.requestIndex==index }.map { it.match }.filter { it.page<=document.extraction.totalPages }
            val fallback=if(hits.isEmpty()) FigureTextMatcher().find(document.extraction.pages,title) else emptyList()
            val matches=(hits+fallback).distinctBy { it.page to it.title }.take(2)
            FigureResult(title,matches,when {
                matches.isNotEmpty() -> "Başlık kaynaktaki metinle eşleşti. Görselin başlığını ve koşullarını kaynak sayfasında kontrol et."
                failed -> "Grafik araması tamamlanamadı. Başka başlıkla yeniden dene veya kaynak sayfasını aç."
                else -> "Bu başlıkla bir grafik bulunamadı. Datasheet'teki başlığı kullan; taranmış/metinsiz grafikler kaynak sayfasından incelenebilir."
            })
        }
    }
    private suspend fun cacheSafely(part: String,document: AnalyzedDocument) {
        try { cache?.save(part,document) } catch(c: CancellationException) { throw c } catch(_: Exception) { /* Cache failure never changes document evidence. */ }
    }

}
