package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.extraction.SearchText

/** Text/OCR fallback has no crop coordinates and is always shown as a complete source page. */
class FigureTextMatcher {
    fun find(pages: List<PageText>,title: String): List<FigureMatch> {
        val wanted=FigureQuery.normalize(title)
        if(wanted.length<3) return emptyList()
        return pages.filterNot { it.truncated }.mapNotNull { page ->
            val lines=page.text.lines()
            val line=lines.withIndex().firstOrNull { (index,text) ->
                text.length<=200 && !Regex("\\d\\s*(?:m?V|m?A|m?W|°C|k?Hz)\\b").containsMatchIn(text) &&
                    SearchText.phrase(FigureQuery.normalize(text+" "+lines.getOrNull(index+1).orEmpty().take(180)),wanted)
            } ?: return@mapNotNull null
            FigureMatch((line.value+" "+lines.getOrNull(line.index+1).orEmpty()).trim().take(180),page.pageNumber)
        }.take(2)
    }
}
