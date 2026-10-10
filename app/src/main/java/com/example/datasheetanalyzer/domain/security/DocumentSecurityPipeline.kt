package com.example.datasheetanalyzer.domain.security

import com.example.datasheetanalyzer.data.download.DownloadManager
import com.example.datasheetanalyzer.domain.model.RankedCandidate
import com.example.datasheetanalyzer.domain.resolver.PdfLinkResolver
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class DocumentSecurityPipeline(private val resolver: PdfLinkResolver, private val downloader: DownloadManager,
    private val validator: PdfStructureValidator) {
    private val mutex=Mutex()
    suspend fun checkLocal(pdf: QuarantinedPdf,progress: (CheckProgress)->Unit): CheckedDocument = mutex.withLock {
        var keep=false
        try {
            progress(CheckProgress(CheckPhase.CHECKING_DOCUMENT))
            val start=System.nanoTime()
            val result=validator.validate(pdf.file)
            if(!result.passed) throw SecurityFailure(result.code,"Seçilen PDF güvenlik kontrolünü geçemedi (${result.code}). ${result.detail}")
            kotlinx.coroutines.currentCoroutineContext().ensureActive();keep=true
            CheckedDocument(pdf,result.pages,downloadMs=0,checkMs=(System.nanoTime()-start)/1_000_000,extraction=result.extraction)
        } finally { if(!keep) pdf.file.delete() }
    }

    suspend fun check(part: String,candidate: RankedCandidate,progress: (CheckProgress)->Unit): CheckedDocument = mutex.withLock {
        if(!candidate.selectable) throw SecurityFailure("CANDIDATE","Bu aday seçilebilir değil.")
        withTimeout(60_000) {
            progress(CheckProgress(CheckPhase.VERIFYING_SOURCE))
            val url=resolver.resolve(part,candidate.canonicalUrl)
            val start=System.nanoTime()
            val pdf=downloader.download(url,progress)
            var keep=false
            try {
                val downloaded=System.nanoTime()
                progress(CheckProgress(CheckPhase.CHECKING_DOCUMENT,pdf.bytes,pdf.bytes))
                progress(CheckProgress(CheckPhase.EXTRACTING_TEXT,pdf.bytes,pdf.bytes))
                val result=validator.validate(pdf.file)
                if(!result.passed) throw SecurityFailure(result.code,
                    "Belge yapısı veya güvenlik kontrolü geçilemedi (${result.code})."+
                    if(result.detail.isNotBlank()) " ${result.detail}" else "")
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                keep=true
                CheckedDocument(pdf,result.pages,downloadMs=(downloaded-start)/1_000_000,
                    checkMs=(System.nanoTime()-downloaded)/1_000_000,extraction=result.extraction)
            } finally { if(!keep) pdf.file.delete() }
        }
    }
}
