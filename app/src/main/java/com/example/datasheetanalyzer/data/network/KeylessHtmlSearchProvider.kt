package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import com.example.datasheetanalyzer.domain.resolver.SearchProvider
import java.net.URLEncoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KeylessHtmlSearchProvider(
    private val fetcher: HtmlFetcher,
    private val parser: DuckDuckGoHtmlParser = DuckDuckGoHtmlParser(),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) : SearchProvider {
    private val mutex = Mutex()
    private var lastRequest: Long? = null

    override suspend fun searchDatasheets(partNumber: String): List<SearchCandidate> = mutex.withLock {
        val part = PartNumberNormalizer.normalize(partNumber)
        require(PartNumberNormalizer.isValid(part))
        val now = clockMs()
        val last = lastRequest
        if (last != null && now - last < 3000) {
            throw SearchFailure(SearchFailureKind.RATE_LIMITED, "Yeni arama için birkaç saniye bekle.")
        }
        lastRequest = now
        val query = URLEncoder.encode("\"$part\" datasheet pdf (site:datasheetarchive.com OR site:ti.com OR site:st.com OR site:analog.com OR site:onsemi.com OR site:nxp.com OR site:infineon.com OR site:microchip.com OR site:renesas.com OR site:vishay.com OR site:rohm.com OR site:toshiba-semicon-storage.com OR site:digikey.com OR site:mouser.com)", "UTF-8")
        parser.parse(fetcher.fetch("https://html.duckduckgo.com/html/?q=$query"))
    }
}
