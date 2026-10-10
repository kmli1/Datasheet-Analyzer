package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer

/** Match the parameter label, without treating a test condition as another result. */
object CriterionLabelMatcher {
    fun label(text: String): String {
        val first=text.substringBefore('|')
        val prefix=first.substringBefore(':')
        val parts=prefix.split(Regex("[ ,/]+"))
        val scoped=':' in first && parts.isNotEmpty() && parts.all { it.any(Char::isDigit) && PartNumberNormalizer.isValid(it) }
        return if(scoped) first.substringAfter(':') else first
    }
    fun matches(text: String,query: ParameterQuery,allowSymbols: Boolean=true): Boolean {
        val label=label(text).substringBefore('=')
        val normalized=SearchText.normalize(label)
        val excluded=when(query.criterion) {
            Criterion.INPUT_OFFSET_VOLTAGE -> Regex("""\b(?:drift|coefficient|rejection|noise|current)\b|\bvs (?:power|supply)\b""")
            Criterion.GAIN -> Regex("""\b(?:bandwidth|gbw|frequency)\b""")
            Criterion.SUPPLY_VOLTAGE -> Regex("""\b(?:rejection|psrr)\b""")
            Criterion.INPUT_VOLTAGE -> Regex("""\b(?:offset|noise|rejection)\b""")
            else -> null
        }
        if(excluded?.containsMatchIn(normalized)==true) return false
        return query.keywords.any { alias ->
            val key=SearchText.normalize(alias)
            SearchText.phrase(label,alias) && (alias.length>3 || allowSymbols &&
                (normalized==key || normalized.startsWith("$key ")))
        }
    }
}
