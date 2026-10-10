package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.SearchProvider
import java.net.URLEncoder
import org.jsoup.Jsoup

class DatasheetArchiveSearchProvider(private val fetcher: HtmlFetcher): SearchProvider {
    override suspend fun searchDatasheets(partNumber: String): List<SearchCandidate> {
        val part=PartNumberNormalizer.normalize(partNumber)
        require(PartNumberNormalizer.isValid(part))
        return parse(fetcher.fetch("https://www.datasheetarchive.com/?q="+URLEncoder.encode(part,"UTF-8")))
    }
    companion object {
        fun parse(html: String): List<SearchCandidate> {
            val doc=Jsoup.parse(html)
            if(doc.select("#challenge-form, .g-recaptcha, form[action*=challenge]").isNotEmpty())
                throw SearchFailure(SearchFailureKind.BLOCKED,"Arşiv doğrulama istedi.")
            return doc.select("tr[data-mfrpartnumber][data-mfr]").mapNotNull { row ->
                val part=PartNumberNormalizer.normalize(row.attr("data-mfrpartnumber"))
                val maker=row.attr("data-mfr").trim()
                val type=row.attr("data-sheettype")
                if(!PartNumberNormalizer.isValid(part) || maker.length !in 2..80 || maker.equals("Unknown",true) ||
                    type !in setOf("Original","Scan")) return@mapNotNull null
                fun segment(value: String)=URLEncoder.encode(value,"UTF-8").replace("+","%20")
                val code=segment(part);val company=segment(maker.replace(Regex("\\s+"),"-"))
                SearchCandidate("$part Datasheet — $maker",
                    "https://www.datasheetarchive.com/datasheet/$code/$company?term=$code",
                    row.attr("data-mfrpartdesc").take(300)+" ($type)")
            }.distinctBy { it.url }.take(20)
        }
    }
}
