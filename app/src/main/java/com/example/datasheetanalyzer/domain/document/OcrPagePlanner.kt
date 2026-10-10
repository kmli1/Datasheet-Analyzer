package com.example.datasheetanalyzer.domain.document

class OcrPagePlanner(private val config: ExtractionConfig=ExtractionConfig()) {
    fun identityRecoveryPages(extraction: PdfExtraction,part: String,attempted: Set<Int>): List<Int> {
        val matcher=DocumentPartMatcher()
        if(extraction.pages.filter { it.pageNumber<=3 }.any {
            matcher.match(part,it.rawText)==com.example.datasheetanalyzer.domain.model.PartMatch.EXACT
        }) return emptyList()
        // A watermark can exceed the sparse-text threshold while the actual datasheet is an image.
        return (1..minOf(3,extraction.totalPages)).filter { it !in attempted }
            .take((config.maxOcrPages-attempted.size).coerceAtLeast(0))
    }
    private fun sparse(page: PageText?) = page==null || page.text.count { it.isLetterOrDigit() }<config.sparsePageChars
    fun firstPages(extraction: PdfExtraction): List<Int> = (1..minOf(3,extraction.totalPages)).filter { n ->
        val page=extraction.pages.firstOrNull { it.pageNumber==n }
        sparse(page) || page?.let(OcrTextRecovery::damaged)==true
    }.take(config.maxOcrPages)
    fun extraPages(extraction: PdfExtraction,attempted: Set<Int>,verification: DocumentVerification,sections: List<SectionHit>): List<Int> {
        val remaining=(config.maxOcrPages-attempted.size).coerceAtLeast(0)
        if(remaining==0) return emptyList()
        val technical=setOf(DatasheetSection.RECOMMENDED_OPERATING,DatasheetSection.ABSOLUTE_MAXIMUM,DatasheetSection.ELECTRICAL)
        val targeted=extraction.pages.filter { page ->
            page.pageNumber !in attempted && !page.truncated && (sparse(page) || OcrTextRecovery.damaged(page)) &&
                sections.filter { it.pageNumber<=page.pageNumber }.maxWithOrNull(
                    compareBy<SectionHit> { it.pageNumber }.thenBy { it.lineIndex })?.let { nearest ->
                    nearest.section in technical && page.pageNumber-nearest.pageNumber<=1
                }==true
        }.map { it.pageNumber }
        val fallback=if(verification.automaticEligible && sections.isNotEmpty()) emptyList() else
            (4..minOf(5,extraction.totalPages)).filter { n -> n !in attempted &&
                sparse(extraction.pages.firstOrNull { it.pageNumber==n }) }
        return (targeted+fallback).distinct().take(remaining)
    }
}
