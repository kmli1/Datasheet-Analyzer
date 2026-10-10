package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.security.SecurityStatus

data class PinoutSource(val title: String,val page: Int,val text: String)
object PinoutSources {
    fun find(document: AnalyzedDocument): List<PinoutSource> {
        if(!document.verification.automaticEligible || document.checked.security!=SecurityStatus.PASSED) return emptyList()
        return document.sections.filter { it.section==DatasheetSection.PINOUT }.mapNotNull { heading ->
            val page=document.extraction.pages.firstOrNull { it.pageNumber==heading.pageNumber && !it.truncated } ?: return@mapNotNull null
            val lines=page.text.lines()
            if(heading.lineIndex !in lines.indices) return@mapNotNull null
            val end=document.sections.filter { it.pageNumber==heading.pageNumber && it.lineIndex>heading.lineIndex }
                .minOfOrNull { it.lineIndex } ?: lines.size
            PinoutSource(heading.heading,heading.pageNumber,
                lines.subList(heading.lineIndex,minOf(end,heading.lineIndex+50,lines.size)).joinToString("\n").take(5000))
        }.distinctBy { it.page }.take(3)
    }
}
