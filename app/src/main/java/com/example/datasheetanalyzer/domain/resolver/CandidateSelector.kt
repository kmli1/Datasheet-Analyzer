package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.domain.model.CandidateSelection
import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.RankedCandidate
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.TrustLevel
import java.util.Locale

class CandidateSelector(
    private val registry: TrustedDomainRegistry = TrustedDomainRegistry(),
    private val matcher: PartNumberMatcher = PartNumberMatcher()
) {
    fun select(partNumber: String, candidates: List<SearchCandidate>): CandidateSelection {
        var discarded = 0
        val ranked = candidates.take(50).mapNotNull { candidate ->
            val url = CandidateUrlPolicy.parse(candidate.url)
            if (url == null || registry.isExcluded(url)) { discarded++; return@mapNotNull null }
            val source = registry.classify(url)
            val titleMatch = matcher.match(partNumber, candidate.title)
            val urlMatch = matcher.matchUrl(partNumber, url)
            val exact = titleMatch == PartMatch.EXACT || urlMatch == PartMatch.EXACT
            val partMatch = when {
                exact -> PartMatch.EXACT
                titleMatch == PartMatch.FAMILY_VARIANT || urlMatch == PartMatch.FAMILY_VARIANT -> PartMatch.FAMILY_VARIANT
                else -> PartMatch.NONE
            }
            val isPdfHint = url.path.lowercase(Locale.ROOT).endsWith(".pdf")
            val datasheetHint = Regex("\\b(?:datasheet|data\\s+sheet)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(candidate.title + " " + url.path + " " + candidate.snippet)
            val trusted = source.level == TrustLevel.MANUFACTURER || source.level == TrustLevel.DISTRIBUTOR || source.level == TrustLevel.ARCHIVE
            val reasons = mutableListOf<String>()
            var score = when (source.level) {
                TrustLevel.MANUFACTURER -> 40.also { reasons += "Üretici alan adı +40" }
                TrustLevel.ARCHIVE -> 35.also { reasons += "Datasheet arşivi +35" }
                TrustLevel.DISTRIBUTOR -> 30.also { reasons += "Distribütör alan adı +30" }
                TrustLevel.BLOCKED -> (-50).also { reasons += "Bu kaynak türü seçime kapalı −50" }
                TrustLevel.LOCAL -> (-50).also { reasons += "Yerel belge ağ kaynağı olarak seçilemez" }
                TrustLevel.UNKNOWN -> (-30).also { reasons += "Kaynak kayıtlı değil −30" }
            }
            if (titleMatch == PartMatch.EXACT) { score += 25; reasons += "Başlıkta tam kod +25" }
            if (urlMatch == PartMatch.EXACT) { score += 15; reasons += "URL'de tam kod +15" }
            if (datasheetHint) { score += 10; reasons += "Datasheet ipucu +10" }
            if (isPdfHint) { score += 5; reasons += "PDF uzantısı ipucu +5" }
            if (partMatch == PartMatch.FAMILY_VARIANT) reasons += "Farklı varyant; tam kodla yeniden ara"
            if (partMatch == PartMatch.NONE) reasons += "Başlık/URL içinde parça eşleşmesi yok"
            if (!isPdfHint && !datasheetHint) reasons += "Datasheet olduğuna ilişkin ipucu yok"
            val variantConflict = urlMatch == PartMatch.FAMILY_VARIANT && titleMatch == PartMatch.EXACT
            if (variantConflict) reasons += "Başlık ile URL varyantı uyuşmuyor"
            val selectable = trusted && exact && !variantConflict && (isPdfHint || datasheetHint) && score >= 65
            RankedCandidate(candidate, url.canonical, url.host, source, partMatch, score, selectable, reasons)
        }.sortedWith(compareByDescending<RankedCandidate> { it.selectable }
            .thenByDescending { it.match == PartMatch.EXACT }
            .thenByDescending { it.score }.thenBy { it.canonicalUrl })
            .distinctBy { it.canonicalUrl }
        return CandidateSelection(ranked, ranked.firstOrNull { it.selectable }?.canonicalUrl, discarded)
    }
}
