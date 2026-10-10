package com.example.datasheetanalyzer.domain.document

import java.util.Locale
import java.text.Normalizer

class DatasheetSectionDetector {
    private fun heading(value: String)=Normalizer.normalize(value,Normalizer.Form.NFKC)
        .uppercase(Locale.ROOT).replace(Regex("""\s+""")," ")
        .replace(Regex("""^[0-9]+(?:\.[0-9]+)*[.)]?\s+"""),"")
        .replace(Regex("""^TABLE\s+[0-9]+[.:]?\s+"""),"")
        .substringBefore('(').trim().trimEnd(':')
    fun detect(pages: List<PageText>): List<SectionHit> = pages.flatMap { page ->
        val lines=page.text.lines()
        lines.flatMapIndexed { index,line ->
            val nextLine=lines.getOrNull(index+1).orEmpty()
            if(line.length>180 || listOf(line,nextLine).any { Regex("""\.{3,}\s*[0-9]+$""").containsMatchIn(it) }) return@flatMapIndexed emptyList()
            val headingLine=heading(line)
            val joined=heading(line+" "+nextLine)
            val direct=DatasheetSection.entries.firstOrNull { section -> section.aliases.any { alias ->
                listOf(headingLine,joined).any { candidate -> candidate==alias ||
                    ((candidate.startsWith(alias+" ") || candidate.startsWith(alias+":")) && candidate.removePrefix(alias).trim().let { tail ->
                        tail.startsWith("OVER OPERATING ") || tail.startsWith("UNLESS OTHERWISE ") ||
                            Regex("""^(?::\s*|OF\s+)[A-Z][A-Z0-9-]{2,30}(?:\s|[,/]|$)""").containsMatchIn(tail) ||
                            tail=="CONTINUED" || tail.startsWith("AT T") ||
                            Regex("^(?:V(?:CC|DD|S)?|T(?:A|J)?|R(?:L)?|F)(?:\\s|=)").containsMatchIn(tail)
                    }) }
            } }
            if(direct!=null) listOf(SectionHit(direct,page.pageNumber,index,line)) else {
                // Side-by-side headings can occupy one extracted line (e.g. Description / Features).
                var rest=headingLine
                val found=mutableListOf<DatasheetSection>()
                val aliases=DatasheetSection.entries.flatMap { section -> section.aliases.map { it to section } }.sortedByDescending { it.first.length }
                while(rest.isNotBlank()) {
                    val match=aliases.firstOrNull { rest==it.first || rest.startsWith(it.first+" ") } ?: break
                    found+=match.second;rest=rest.removePrefix(match.first).trim()
                }
                if(rest.isBlank() && found.size>1) found.distinct().map { SectionHit(it,page.pageNumber,index,line) } else emptyList()
            }
        }
    }.distinctBy { Triple(it.section,it.pageNumber,it.lineIndex) }
}
