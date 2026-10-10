package com.example.datasheetanalyzer.domain.security

import java.io.File
import com.example.datasheetanalyzer.domain.document.PdfExtraction

data class SecurityConfig(
    val maxPdfSize: Long = 20L * 1024 * 1024,
    val maxRedirects: Int = 3,
    val prefixBytes: Int = 8192,
    val maxHtmlBytes: Int = 512 * 1024,
    val sandboxTimeoutMs: Long = 10_000,
    val maxObjects: Int = 100_000,
    val maxPages: Int = 1000,
    val maxNameBytes: Int = 1024
) {
    init {
        require(maxPdfSize in 8192..(20L * 1024 * 1024))
        require(maxRedirects in 0..3 && prefixBytes in 8..8192)
        require(maxHtmlBytes in 1024..(512 * 1024))
        require(sandboxTimeoutMs in 1000..10_000 && maxObjects in 100..100_000 && maxPages in 1..1000)
        require(maxNameBytes in 128..1024)
    }
}
enum class SecurityStatus { NOT_CHECKED, PREFLIGHT_PASSED, DOWNLOAD_VALIDATED, STRUCTURE_VALIDATED, PASSED, WEB_TEXT_VALIDATED, REJECTED }
enum class DocumentStatus { UNKNOWN, VERIFIED, LIKELY_VALID, REVIEW_REQUIRED, REJECTED }
enum class CheckPhase { VERIFYING_SOURCE, DOWNLOADING, CHECKING_DOCUMENT, EXTRACTING_TEXT, OCR, VERIFYING_DOCUMENT, READING_ARCHIVE, READING_CACHE }
data class CheckProgress(val phase: CheckPhase, val bytes: Long = 0, val total: Long? = null)
class SecurityFailure(val code: String, message: String) : Exception(message)
data class QuarantinedPdf(val file: File, val finalUrl: String, val bytes: Long, val sha256: String)
data class SandboxResult(val passed: Boolean, val code: String, val pages: Int = 0,
    val extraction: PdfExtraction? = null,val detail: String = "")
data class CheckedDocument(
    val pdf: QuarantinedPdf, val pages: Int,
    val security: SecurityStatus = SecurityStatus.PASSED,
    val identity: DocumentStatus = DocumentStatus.UNKNOWN,
    val downloadMs: Long, val checkMs: Long, val extraction: PdfExtraction? = null
)
fun interface PdfStructureValidator { suspend fun validate(file: File): SandboxResult }
