package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.data.network.SourceHtmlFetcher
import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.security.PdfPolicy
import com.example.datasheetanalyzer.domain.security.SecurityFailure
import java.net.URI
import java.net.URLDecoder
import org.jsoup.Jsoup

/** Resolve explicit document links only. Never execute scripts or fetch page assets. */
class PdfLinkResolver(private val fetcher: SourceHtmlFetcher) {
    suspend fun resolve(part: String, candidate: String): String {
        var url=PdfPolicy.source(candidate).canonical
        if(URI(url).path.lowercase().endsWith(".pdf")) return url
        val seen=mutableSetOf<String>()
        repeat(3) {
            if(fetcher.isPdfEndpoint(url)) return url
            if(!seen.add(url)) throw SecurityFailure("LINK_LOOP","PDF bağlantısı döngüye girdi.")
            val links=extract(part,url,fetcher.fetch(url))
            links.firstOrNull { URI(it).path.lowercase().endsWith(".pdf") }?.let { return it }
            url=links.firstOrNull { it !in seen } ?: throw SecurityFailure("PDF_LINK",
                "Bu sayfada açık bir PDF bağlantısı bulunamadı. Başka bir kaynağı dene.")
        }
        throw SecurityFailure("PDF_LINK","PDF bağlantısı çözüm sınırı aşıldı.")
    }
    companion object {
        fun extract(part: String,base: String,html: String): List<String> {
            val origin=PdfPolicy.source(base)
            val doc=Jsoup.parse(html,base)
            val matcher=PartNumberMatcher()
            val values=doc.select("a[href], iframe[src], embed[src], object[data]").mapNotNull { e ->
                val raw=when(e.tagName()) { "a"->e.absUrl("href");"object"->e.absUrl("data");else->e.absUrl("src") }
                val viewer=CandidateUrlPolicy.parse(raw)
                val decoded=if(viewer!=null && viewer.host==origin.host &&
                    (viewer.path.endsWith("/viewer.html") || viewer.path.endsWith("/viewer"))) {
                    URI(raw).rawQuery?.split('&')?.firstOrNull { it.startsWith("file=") }?.substringAfter('=')?.let {
                        runCatching { URI(base).resolve(URLDecoder.decode(it,"UTF-8")).toString() }.getOrNull()
                    }
                } else null
                val target=runCatching { PdfPolicy.source(decoded ?: raw) }.getOrNull() ?: return@mapNotNull null
                if(matcher.matchUrl(part,target)!=PartMatch.EXACT && matcher.match(part,e.text())!=PartMatch.EXACT &&
                    e.tagName()=="a") return@mapNotNull null
                if(!target.path.lowercase().endsWith(".pdf")) return@mapNotNull null
                target.canonical
            }
            return values.distinct()
        }
    }
}
