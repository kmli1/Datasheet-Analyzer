package com.example.datasheetanalyzer.data.download

import com.example.datasheetanalyzer.data.network.SafeRequest
import com.example.datasheetanalyzer.data.network.SafeResponse
import com.example.datasheetanalyzer.data.network.SourceHttp
import com.example.datasheetanalyzer.domain.security.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class DownloadManager(private val http: SourceHttp, private val directory: File,
    private val config: SecurityConfig = SecurityConfig()) {
    suspend fun download(url: String, progress: (CheckProgress)->Unit = {}): QuarantinedPdf {
        progress(CheckProgress(CheckPhase.VERIFYING_SOURCE))
        PdfPolicy.source(url)
        val head=http.request(SafeRequest(url,"HEAD"))
        head.response.use { r ->
            if(r.status==200) { metadata(r);PdfPolicy.length(r.header("Content-Length"),config.maxPdfSize) }
            else if(r.status !in setOf(405,501)) throw SecurityFailure("HEAD","Kaynak ön kontrolü tamamlanamadı (HTTP ${r.status}).")
        }
        val prefix=http.request(SafeRequest(head.url,headers=mapOf("Range" to "bytes=0-${config.prefixBytes-1}","Accept" to "application/pdf")))
        prefix.response.use { r ->
            if(r.status !in setOf(200,206)) throw SecurityFailure("RANGE","PDF ön kontrolü tamamlanamadı.")
            metadata(r)
            if(r.status==206) checkRange(r)
            else PdfPolicy.length(r.header("Content-Length"),config.maxPdfSize)
            val bytes=readPrefix(r,config.prefixBytes)
            PdfPolicy.magic(bytes)
            RawPdfScanner().apply { accept(bytes);finish() }
        }
        if(!directory.exists() && !directory.mkdirs()) throw SecurityFailure("STORAGE","Karantina klasörü oluşturulamadı.")
        val file=File(directory,UUID.randomUUID().toString()+".part")
        var keep=false
        try {
            val full=http.request(SafeRequest(prefix.url,headers=mapOf("Accept" to "application/pdf")))
            val pdf=full.response.use { r ->
                if(r.status!=200) throw SecurityFailure("HTTP","PDF indirilemedi (HTTP ${r.status}).")
                metadata(r)
                val expected=PdfPolicy.length(r.header("Content-Length"),config.maxPdfSize)
                val digest=MessageDigest.getInstance("SHA-256");val scan=RawPdfScanner()
                val header=ByteArrayOutputStream();var bytes=0L;var lastProgress=0L
                file.outputStream().use { output ->
                    val b=ByteArray(8192)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n=r.body.read(b);if(n<0) break
                        bytes+=n
                        if(bytes>config.maxPdfSize || (expected!=null && bytes>expected))
                            throw SecurityFailure("SIZE","PDF boyutu izin verilen sınırı aştı.")
                        if(header.size()<8) {
                            header.write(b,0,minOf(n,8-header.size()))
                            if(header.size()==8) PdfPolicy.magic(header.toByteArray())
                        }
                        scan.accept(b,n);digest.update(b,0,n);output.write(b,0,n)
                        if(bytes-lastProgress>=65536) { progress(CheckProgress(CheckPhase.DOWNLOADING,bytes,expected));lastProgress=bytes }
                    }
                }
                PdfPolicy.magic(header.toByteArray());scan.finish()
                if(expected!=null && bytes!=expected) throw SecurityFailure("TRUNCATED","PDF indirmesi eksik kaldı.")
                progress(CheckProgress(CheckPhase.DOWNLOADING,bytes,expected))
                QuarantinedPdf(file,full.url,bytes,digest.digest().joinToString("") { "%02x".format(it) })
            }
            keep=true
            return pdf
        } finally { if(!keep) file.delete() }
    }
    private fun metadata(r: SafeResponse) {
        PdfPolicy.pdfMime(r.header("Content-Type"))
        if(r.header("Content-Encoding")?.lowercase()?.let { it!="identity" }==true)
            throw SecurityFailure("ENCODING","Sıkıştırılmış HTTP aktarımı kabul edilmedi.")
    }
    private fun checkRange(r: SafeResponse) {
        val m=Regex("bytes 0-([0-9]+)/([0-9]+)").matchEntire(r.header("Content-Range").orEmpty())
            ?: throw SecurityFailure("RANGE","PDF aralık yanıtı geçersiz.")
        val end=m.groupValues[1].toLongOrNull() ?: throw SecurityFailure("RANGE","Aralık sınırı geçersiz.")
        val total=PdfPolicy.length(m.groupValues[2],config.maxPdfSize)!!
        if(end>=total || end>=config.prefixBytes) throw SecurityFailure("RANGE","PDF aralığı sınırları geçersiz.")
    }
    private suspend fun readPrefix(r: SafeResponse,limit: Int): ByteArray {
        val output=ByteArrayOutputStream();val b=ByteArray(1024)
        while(output.size()<limit) {
            currentCoroutineContext().ensureActive()
            val n=r.body.read(b,0,minOf(b.size,limit-output.size()));if(n<0) break
            output.write(b,0,n)
        }
        return output.toByteArray()
    }
}
