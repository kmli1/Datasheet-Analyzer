package com.example.datasheetanalyzer.domain.document

import java.text.Normalizer
import java.util.Locale

class TextCleaner {
    fun normalize(raw: String): String = Normalizer.normalize(raw,Normalizer.Form.NFKC)
        .replace(" ̊C","°C").replace("˚C","°C").replace("ºC","°C")
        .replace("\r\n","\n").replace('\r','\n').replace("\u00ad","")
        .replace(Regex("""[\u2010-\u2015\u2212]"""),"-")
        .replace(Regex("""[\p{Cf}]"""),"")
        // Undecoded font symbols are uncertainty, not harmless whitespace (e.g. missing µ before A).
        .replace(Regex("""[\p{Cc}&&[^\n\t]]"""),"\uFFFD")
        .replace(Regex("""([a-z]{2,})-\n([a-z]{2,})"""),"$1$2")
        .lines().joinToString("\n") { it.replace(Regex("""[\t \u00a0]+""")," ").trim() }
        .replace(Regex("""\n{3,}"""),"\n\n").trim()

    fun clean(pages: List<PageText>): List<PageText> {
        val normalized=pages.associate { it.pageNumber to normalize(it.rawText).lines() }
        val repeated=normalized.values.flatMap { lines -> (lines.take(2)+lines.takeLast(2)).distinct() }
            .groupingBy { it }.eachCount().filter { (line,count) ->
                count>=3 && line.length>=4 && line.length<=140 &&
                    DatasheetSection.entries.none { section -> section.aliases.any { it==line.uppercase(Locale.ROOT) } }
            }.keys
        return pages.map { page ->
            val lines=normalized.getValue(page.pageNumber)
            val kept=lines.filterIndexed { i,line ->
                val edge=i<2 || i>=lines.size-2
                !(edge && (line in repeated || Regex("""(?i)^(?:page\s+)?[0-9]{1,4}(?:\s*(?:of|/)\s*[0-9]{1,4})?$""").matches(line)))
            }
            val merged=mutableListOf<String>()
            for(line in kept) {
                val prior=merged.lastOrNull().orEmpty()
                val prose=prior.length>65 && prior.lastOrNull()?.isLetter()==true &&
                    line.firstOrNull()?.isLowerCase()==true &&
                    !prior.any { it.isDigit() } && !line.any { it.isDigit() }
                if(prose) merged[merged.lastIndex]=prior+" "+line else merged+=line
            }
            page.copy(text=merged.joinToString("\n").trim())
        }
    }
}
