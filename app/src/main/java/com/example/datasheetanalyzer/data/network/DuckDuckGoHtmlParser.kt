package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import java.net.URI
import java.net.URLDecoder
import org.jsoup.Jsoup

class DuckDuckGoHtmlParser {
    fun parse(html: String): List<SearchCandidate> {
        val doc = Jsoup.parse(html, "https://html.duckduckgo.com/html/")
        if (doc.select("#challenge-form, .anomaly-modal, form[action*=anomaly]").isNotEmpty()) {
            throw SearchFailure(SearchFailureKind.BLOCKED, "Arama kaynağı doğrulama istedi. Daha sonra yeniden dene.")
        }
        val candidates = doc.select(".result").filter {
            !it.hasClass("result--ad") && it.select(".result__badge--ad, .result__ad").isEmpty()
        }.mapNotNull { block ->
            val link = block.selectFirst("a.result__a") ?: return@mapNotNull null
            val url = unwrap(link.absUrl("href")) ?: return@mapNotNull null
            SearchCandidate(clean(link.text(), 240), url,
                clean(block.selectFirst(".result__snippet")?.text().orEmpty(), 600))
        }.take(25)
        if (candidates.isNotEmpty()) return candidates
        if (doc.select(".no-results, .result--no-result").isNotEmpty()) return emptyList()
        // An unexpected/challenge page must not be reported as a genuine zero-result query.
        throw SearchFailure(SearchFailureKind.FORMAT_CHANGED,
            "Arama sayfası okunamadı veya biçimi değişti. Daha sonra yeniden dene.")
    }

    private fun unwrap(raw: String): String? = runCatching {
        val uri = URI(raw)
        val host = uri.host.orEmpty().lowercase()
        if (host == "duckduckgo.com" || host == "html.duckduckgo.com") {
            require(uri.path == "/l/" || uri.path == "/l")
            val value = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("uddg=") }
                ?.substringAfter('=') ?: return null
            URLDecoder.decode(value, "UTF-8")
        } else raw
    }.getOrNull()

    private fun clean(value: String, limit: Int) = value.filter {
        !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt()
    }.take(limit)
}
