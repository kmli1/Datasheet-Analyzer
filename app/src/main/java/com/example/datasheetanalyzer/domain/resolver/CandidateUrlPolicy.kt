package com.example.datasheetanalyzer.domain.resolver

import java.net.URI
import java.util.Locale

data class CandidateUrl(val canonical: String, val host: String, val path: String, val query: String?)

object CandidateUrlPolicy {
    fun parse(raw: String): CandidateUrl? = runCatching {
        require(raw.length <= 2048)
        val uri = URI(raw.trim()).normalize()
        require(uri.scheme.equals("https", ignoreCase = true))
        require(uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443))
        val host = requireNotNull(uri.host).lowercase(Locale.ROOT).trimEnd('.')
        require(host.contains('.') && host.matches(Regex("[a-z0-9.-]+")))
        require(!host.matches(Regex("[0-9.]+")))
        require(host.split('.').all { it.isNotBlank() && !it.startsWith('-') && !it.endsWith('-') })
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val canonical = "https://$host$path" + (uri.rawQuery?.let { "?$it" } ?: "")
        CandidateUrl(canonical, host, uri.path.orEmpty(), uri.query)
    }.getOrNull()
}
