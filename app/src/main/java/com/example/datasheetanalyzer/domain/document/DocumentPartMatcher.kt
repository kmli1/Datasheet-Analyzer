package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import com.example.datasheetanalyzer.domain.resolver.PartNumberMatcher
import java.util.Locale

class DocumentPartMatcher {
    private val matcher=PartNumberMatcher()
    fun match(part: String,text: String): PartMatch {
        val normalized=TextCleaner().normalize(text)
        val result=matcher.match(part,normalized)
        if(result==PartMatch.EXACT && !Regex("(?i)"+Regex.escape(part)+"[ \\t]+[A-Z](?:[0-9-]{0,8})?(?![A-Z0-9])").containsMatchIn(normalized)) return result
        // A family document naming LM383/LM383A contains the requested LM383 explicitly.
        val code=PartNumberNormalizer.normalize(part)
        if(!PartNumberNormalizer.isValid(code)) return PartMatch.NONE
        // PDF glyph positioning and OCR can insert spaces inside a printed part number.
        // Keep token boundaries and do not substitute visually similar letters/digits.
        val spaced=code.map { Regex.escape(it.toString()) }.joinToString("[ \\t]*")
        val upper=normalized.uppercase(Locale.ROOT)
        val detachedSuffix="[ \\t]+[A-Z](?:[0-9-]{0,8})?(?![A-Z0-9])"
        if(Regex("(?<![A-Z0-9])$spaced(?![A-Z0-9]|$detachedSuffix)").containsMatchIn(upper)) return PartMatch.EXACT
        val slashTokens=Regex("[A-Z0-9-]+(?:/[A-Z0-9-]+)+").findAll(normalized.uppercase(Locale.ROOT))
        if(slashTokens.any { code in it.value.split('/') }) return PartMatch.EXACT
        if(Regex("(?<![A-Z0-9])$spaced[ \\t]*[A-Z][A-Z0-9-]{0,15}(?![A-Z0-9])").containsMatchIn(upper))
            return PartMatch.FAMILY_VARIANT
        return result
    }
}
