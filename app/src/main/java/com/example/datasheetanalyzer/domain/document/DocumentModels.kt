package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.RankedCandidate
import com.example.datasheetanalyzer.domain.security.CheckedDocument
import com.example.datasheetanalyzer.domain.security.DocumentStatus

data class ExtractionConfig(
    val maxTextPages: Int=80, val maxPageChars: Int=20_000, val maxTotalChars: Int=300_000,
    val maxOcrPages: Int=5, val sparsePageChars: Int=60, val maxRasterPixels: Int=2_000_000,
    val automaticThreshold: Int=80, val maxCandidateAttempts: Int=3
) {
    init {
        require(maxTextPages in 1..80 && maxPageChars in 100..20_000 && maxTotalChars in 1000..300_000)
        require(maxOcrPages in 0..5 && maxRasterPixels in 100_000..2_000_000)
        require(automaticThreshold in 60..100 && maxCandidateAttempts in 1..3)
    }
}
enum class TextOrigin { TEXT_LAYER, OCR, WEB_TEXT }
enum class DocumentSource { PDF, LOCAL_PDF, ARCHIVE_HTML }
data class PageText(val pageNumber: Int,val rawText: String,val text: String=rawText,
                    val origin: TextOrigin=TextOrigin.TEXT_LAYER,val truncated: Boolean=false,
                    val nativeRawText: String?=null)
data class PdfMetadata(val title: String="",val author: String="")
data class PdfExtraction(val metadata: PdfMetadata,val totalPages: Int,val pages: List<PageText>,val truncated: Boolean=false)
enum class DatasheetSection(val label: String,val aliases: List<String>) {
    DESCRIPTION("Açıklama",listOf("GENERAL DESCRIPTION","DESCRIPTION")),
    FEATURES("Özellikler",listOf("KEY FEATURES","FEATURES")),
    ABSOLUTE_MAXIMUM("Mutlak maksimum sınırlar",listOf("ABSOLUTE MAXIMUM RATINGS","MAXIMUM RATINGS")),
    RECOMMENDED_OPERATING("Önerilen çalışma koşulları",listOf("RECOMMENDED OPERATING CONDITIONS","RECOMMENDED OPERATION CONDITIONS","RECOMMENDED OPERATING RATINGS","OPERATING RATINGS","OPERATING CONDITIONS")),
    ELECTRICAL("Elektriksel özellikler",listOf("ELECTRICAL CHARACTERISTICS","ELECTRICAL SPECIFICATIONS")),
    PINOUT("Pin bağlantıları",listOf("CONNECTION DIAGRAM","CONNECTION DIAGRAMS","PIN CONFIGURATION","PIN CONFIGURATIONS","PIN DESCRIPTION","PIN DESCRIPTIONS","PIN FUNCTIONS","PINOUT")),
    APPLICATIONS("Uygulamalar",listOf("APPLICATION INFORMATION","APPLICATIONS")),
    TYPICAL_CHARACTERISTICS("Tipik karakteristikler",listOf("TYPICAL CHARACTERISTICS","TYPICAL PERFORMANCE CHARACTERISTICS")),
    PACKAGE("Paket bilgisi",listOf("PACKAGE INFORMATION","PACKAGE OUTLINE","PACKAGE DIMENSIONS","PACKAGING INFORMATION"))
}
data class SectionHit(val section: DatasheetSection,val pageNumber: Int,val lineIndex: Int,val heading: String)
data class TextEvidence(val pageNumber: Int?,val text: String,val origin: TextOrigin?=null)
enum class ManufacturerConsistency { CONSISTENT, UNKNOWN, CONFLICT }
data class ManufacturerFinding(val detected: List<String>,val consistency: ManufacturerConsistency)
data class DocumentVerification(
    val code: String,val status: DocumentStatus,val score: Int,val match: PartMatch,
    val manufacturer: ManufacturerFinding,val automaticEligible: Boolean,
    val evidence: List<TextEvidence>,val reasons: List<String>
)
data class AnalyzedDocument(val checked: CheckedDocument,val candidate: RankedCandidate,
    val extraction: PdfExtraction,val sections: List<SectionHit>,val verification: DocumentVerification,
    val attempts: List<String> = emptyList(),val sourceKind: DocumentSource=DocumentSource.PDF,
    val sourcePages: Map<Int,String> = emptyMap(),val fromCache: Boolean=false,val requestedPart: String="")
