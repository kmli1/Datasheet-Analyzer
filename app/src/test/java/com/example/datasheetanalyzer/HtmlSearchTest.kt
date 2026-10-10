package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.network.DuckDuckGoHtmlParser
import com.example.datasheetanalyzer.data.network.HtmlFetcher
import com.example.datasheetanalyzer.data.network.KeylessHtmlSearchProvider
import com.example.datasheetanalyzer.data.repository.DatasheetRepository
import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HtmlSearchTest {
    private val html = """<html><body><div class="result">
        <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fti.com%2FLM383.pdf&amp;rut=abc">LM383 datasheet</a>
        <div class="result__snippet">Amplifier data sheet</div></div></body></html>"""
    private val parser = DuckDuckGoHtmlParser()

    @Test fun unwrapsDdgRedirectAndExtractsText() {
        val found = parser.parse(html).single()
        assertEquals("https://ti.com/LM383.pdf", found.url)
        assertEquals("LM383 datasheet", found.title)
        assertEquals("Amplifier data sheet", found.snippet)
    }
    @Test fun keepsDirectLinks() {
        val direct = html.replace("//duckduckgo.com/l/?uddg=https%3A%2F%2Fti.com%2FLM383.pdf&amp;rut=abc", "https://ti.com/LM383.pdf")
        assertEquals("https://ti.com/LM383.pdf", parser.parse(direct).single().url)
    }
    @Test fun skipsAdvertisingBlocks() {
        val ad = """<div class="result result--ad"><a class="result__a" href="https://ad.test/LM383.pdf">Ad</a></div>"""
        assertEquals(1, parser.parse(html.replace("</body>", "$ad</body>")).size)
    }
    @Test fun distinguishesNoResultsFromChangedHtml() {
        assertTrue(parser.parse("<div class='no-results'>No results</div>").isEmpty())
        try { parser.parse("<html>Unexpected page</html>"); fail("Expected format error") }
        catch (e: SearchFailure) { assertEquals(SearchFailureKind.FORMAT_CHANGED, e.kind) }
    }
    @Test fun detectsChallengeWithoutAttemptingBypass() {
        try { parser.parse("<form id='challenge-form'></form>"); fail("Expected block") }
        catch (e: SearchFailure) { assertEquals(SearchFailureKind.BLOCKED, e.kind) }
    }
    @Test fun doesNotTrustFakeDdgHostForRedirectUnwrapping() {
        val fake = html.replace("//duckduckgo.com/l/", "https://duckduckgo.com.evil.test/l/")
        assertTrue(parser.parse(fake).single().url.startsWith("https://duckduckgo.com.evil.test/"))
    }
    @Test fun queryUsesNormalizedQuotedPart() = runBlocking {
        var requested = ""
        val fetcher = object : HtmlFetcher { override suspend fun fetch(url: String): String { requested = url; return html } }
        val provider = KeylessHtmlSearchProvider(fetcher)
        assertEquals(1, provider.searchDatasheets(" lm 383 ").size)
        val query=URLDecoder.decode(URI(requested).rawQuery, "UTF-8")
        assertTrue(query.startsWith("q=\"LM383\" datasheet pdf"))
        assertTrue(query.contains("site:datasheetarchive.com"));assertTrue(query.contains("site:ti.com"));assertFalse(query.contains("alldatasheet"))
    }
    @Test fun throttlesRepeatedSearchWithoutExtraRequest() = runBlocking {
        var calls = 0
        var clock = 0L
        val fetcher = object : HtmlFetcher { override suspend fun fetch(url: String): String { calls++; return html } }
        val provider = KeylessHtmlSearchProvider(fetcher, clockMs = { clock })
        provider.searchDatasheets("LM383")
        try { provider.searchDatasheets("LM383"); fail("Expected throttle") }
        catch (e: SearchFailure) { assertEquals(SearchFailureKind.RATE_LIMITED, e.kind) }
        assertEquals(1, calls)
        clock = 3000
        provider.searchDatasheets("LM383")
        assertEquals(2, calls)
    }
    @Test fun cancellationPropagates() = runBlocking {
        val fetcher = object : HtmlFetcher { override suspend fun fetch(url: String): String { throw CancellationException("test") } }
        try { KeylessHtmlSearchProvider(fetcher).searchDatasheets("LM383"); fail("Expected cancellation") }
        catch (_: CancellationException) { /* Must not turn cancellation into empty results. */ }
    }
    @Test fun fixtureFlowsThroughParserProviderAndSelector() = runBlocking {
        val fetcher = object : HtmlFetcher { override suspend fun fetch(url: String): String = html }
        val result = DatasheetRepository(KeylessHtmlSearchProvider(fetcher)).search("LM383")
        assertEquals("https://ti.com/LM383.pdf", result.recommendedUrl)
        assertTrue(result.candidates.single().selectable)
    }
}
