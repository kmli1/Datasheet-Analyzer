package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.model.RankedCandidate
import com.example.datasheetanalyzer.domain.model.TrustLevel

/** Keep the chosen source first, then reserve attempts for independent sources. */
object DocumentCandidatePlanner {
    fun plan(selected: RankedCandidate,candidates: List<RankedCandidate>,limit: Int): List<RankedCandidate> {
        require(limit>0)
        val pool=candidates.filter { it.selectable }.distinctBy { documentKey(it.canonicalUrl) }
        val result=mutableListOf(selected)
        fun add(candidate: RankedCandidate?) {
            if(candidate!=null && result.none { documentKey(it.canonicalUrl)==documentKey(candidate.canonicalUrl) }) result+=candidate
        }
        add(pool.firstOrNull { it.source.level==TrustLevel.MANUFACTURER && it.source.label!=selected.source.label })
        add(pool.firstOrNull { c -> result.none { it.source.label==c.source.label } })
        pool.forEach { add(it) }
        return result.take(limit)
    }
    fun documentKey(url: String): String =
        com.example.datasheetanalyzer.domain.resolver.CandidateUrlPolicy.parse(url)?.canonical ?: url
}
