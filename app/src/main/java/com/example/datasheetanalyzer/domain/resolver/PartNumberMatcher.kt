package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import java.util.Locale
import java.net.URI
import java.net.URLDecoder

class PartNumberMatcher {
    private val tokens = Regex("[A-Z0-9]+(?:[-/][A-Z0-9]+)*")
    private val suffix = Regex("(?:[A-Z][A-Z0-9-]{0,15}|[-/][A-Z0-9/-]{1,16})")

    fun match(partNumber: String, text: String): PartMatch {
        val part = PartNumberNormalizer.normalize(partNumber)
        if (!PartNumberNormalizer.isValid(part)) return PartMatch.NONE
        val found = tokens.findAll(text.uppercase(Locale.ROOT)).map { it.value }.toList()
        if (part in found) return PartMatch.EXACT
        return if (found.any { it.startsWith(part) && suffix.matches(it.removePrefix(part)) }) {
            PartMatch.FAMILY_VARIANT
        } else PartMatch.NONE
    }

    fun matchUrl(partNumber: String, url: CandidateUrl): PartMatch {
        val segments = URI(url.canonical).rawPath.orEmpty().split('/').filter { it.isNotBlank() }
            .map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
        val part = PartNumberNormalizer.normalize(partNumber)
        val slashFields = if ('/' in part) segments.takeLast(5)
            .windowed(part.count { it == '/' } + 1, 1).map { it.joinToString("/") } else emptyList()
        val fields = segments.takeLast(3) + slashFields
        val matches = fields.map { match(partNumber, it) }
        return when {
            PartMatch.EXACT in matches -> PartMatch.EXACT
            PartMatch.FAMILY_VARIANT in matches -> PartMatch.FAMILY_VARIANT
            else -> {
                val queryMatches = url.query.orEmpty().split('&', '=').map { match(partNumber, it) }
                when {
                    PartMatch.EXACT in queryMatches -> PartMatch.EXACT
                    PartMatch.FAMILY_VARIANT in queryMatches -> PartMatch.FAMILY_VARIANT
                    else -> PartMatch.NONE
                }
            }
        }
    }
}
