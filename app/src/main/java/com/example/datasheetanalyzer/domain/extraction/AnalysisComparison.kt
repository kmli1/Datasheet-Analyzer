package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.data.workspace.SavedAnalysis
import com.example.datasheetanalyzer.domain.model.CriterionGroups

data class ComparisonRow(val title: String,val cells: List<String>,val conditionNote: String)
object AnalysisComparison {
    private fun key(q: ParameterQuery)=(q.criterion?.name ?: SearchText.normalize(q.requested))+":"+q.mode.name
    fun rows(analyses: List<SavedAnalysis>): List<ComparisonRow> {
        require(analyses.size in 2..3)
        val queries=analyses.flatMap { it.report.results }.map { it.query }.filter { it.mode!=QueryMode.GRAPH }
            .distinctBy(::key).sortedBy { it.criterion?.let(CriterionGroups::index) ?: 100 }
        return queries.map { q ->
            val results=analyses.map { a -> a.report.results.firstOrNull { key(it.query)==key(q) } }
            val conditions=analyses.zip(results).map { (a,r) -> if(a.report.documentEligible && r?.status!=ParameterStatus.DOCUMENT_UNVERIFIED)
                r?.matches.orEmpty().map { SearchText.normalize(it.conditions) }.toSet() else emptySet() }
            val warning=when {
                conditions.any { it.isEmpty() || "" in it } -> "Bazı test koşulları veya sonuçlar eksik; doğrudan üstünlük karşılaştırması yapılamaz."
                conditions.distinct().size>1 -> "Test koşulları farklı; değerler doğrudan eşdeğer kabul edilmez."
                else -> "Test koşulu metinleri aynı görünüyor; kaynak ayrıntılarını kontrol et."
            }
            ComparisonRow(q.requested+(when(q.mode) { QueryMode.ABSOLUTE_MAXIMUM -> " · mutlak sınır";QueryMode.RECOMMENDED_OPERATING -> " · önerilen çalışma";else -> "" }),
                analyses.zip(results).map { (a,r) -> cell(a,r) },warning)
        }
    }
    private fun cell(a: SavedAnalysis,r: ParameterResult?): String {
        if(r==null) return "Bu analizde aranmadı"
        if(!a.report.documentEligible || r.status==ParameterStatus.DOCUMENT_UNVERIFIED) return "İnceleme gerekli · sayısal karşılaştırma yapılmadı"
        if(r.matches.isEmpty()) return "Bulunamadı"
        return buildString {
            append(ResultPresentation.status(r.status))
            for(hit in r.matches) {
                append("\n").append(hit.section?.label ?: "Kaynak").append(" · s.").append(hit.page)
                val n=hit.numbers
                if(n==null) {
                    val sourceRows=SourceSectionTableReader().rows(hit,a.part)
                    if(sourceRows.isEmpty()) append("\nKaynak alıntısı: ").append(hit.evidenceLine.take(180)) else {
                        sourceRows.take(8).forEach { row ->
                            val cells=ResultPresentation.values(row.numbers)
                            append("\n${row.label}: Min ${cells[0]} · Tipik ${cells[1]} · Maks ${cells[2]} · Değer ${cells[3]} ${cells[4]}")
                            if(row.conditions.isNotBlank()) append("\nKoşul: ").append(row.conditions)
                        }
                        if(sourceRows.size>8) append("\nDiğer satırlar kayıt ayrıntılarında.")
                    }
                } else {
                    val cells=ResultPresentation.values(n)
                    append("\nMin: ${cells[0]} · Tipik: ${cells[1]} · Maks: ${cells[2]} · Değer: ${cells[3]} ${cells[4]}")
                }
                append("\nKoşul: ").append(hit.conditions.ifBlank { "Belirtilmedi" })
            }
            if(r.absoluteLimits.isNotEmpty()) append("\nAyrı mutlak maksimum kaydı var; çalışma değeriyle birleştirilmedi.")
        }
    }
}
