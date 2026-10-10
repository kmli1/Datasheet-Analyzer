package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import com.example.datasheetanalyzer.domain.security.SecurityStatus
import java.util.Locale

data class PartVariant(val code: String,val page: Int,val evidence: String)

/** Offers literal source codes only. Package and temperature grades are never inferred from suffixes. */
object PartVariants {
    private val token=Regex("(?<![A-Z0-9])[A-Z0-9]+(?:[-/][A-Z0-9]+)*(?![A-Z0-9])")
    fun family(part: String): String {
        val normalized=PartNumberNormalizer.normalize(part).substringBefore('-').substringBefore('/')
        val last=normalized.indexOfLast(Char::isDigit)
        return if(last>=2) normalized.take(last+1) else normalized
    }
    fun codes(part: String,text: String): List<String> {
        val base=family(part)
        if(base.length<3 || !base.any(Char::isLetter)) return emptyList()
        return token.findAll(text.uppercase(Locale.ROOT)).map { it.value }.filter {
            it.startsWith(base) && (it==base || Regex("(?:[A-Z][A-Z0-9]{0,15}|[-/][A-Z0-9/-]{1,16})").matches(it.removePrefix(base))) &&
                PartNumberNormalizer.isValid(it)
        }.distinct().toList()
    }
    fun find(document: AnalyzedDocument,part: String): List<PartVariant> {
        if(!document.verification.automaticEligible || document.checked.security!=SecurityStatus.PASSED) return emptyList()
        val result=mutableListOf<PartVariant>()
        for(page in document.extraction.pages.filter { !it.truncated && it.origin==TextOrigin.TEXT_LAYER }) {
            for(line in page.rawText.lines()) for(code in codes(part,line)) {
                if(code!=PartNumberNormalizer.normalize(part) && result.none { it.code==code })
                    result+=PartVariant(code,page.pageNumber,line.trim().take(350))
                if(result.size>=20) return result
            }
        }
        return result
    }
}
