package com.example.datasheetanalyzer.export

import com.example.datasheetanalyzer.data.workspace.SavedAnalysis
import com.example.datasheetanalyzer.domain.extraction.AnalysisComparison

object ComparisonReportContent {
    fun build(analyses: List<SavedAnalysis>): List<ReportBlock> {
        val rows=AnalysisComparison.rows(analyses)
        return buildList {
            add(ReportBlock("Datasheet Analyzer",ReportStyle.TITLE));add(ReportBlock("Komponent karşılaştırması",ReportStyle.HEADING))
            add(ReportBlock("Kaydedilmiş analizlerin karşılaştırmasıdır. Eksik sonuçlar ve farklı test koşulları eşdeğer kabul edilmez; mutlak sınırlar çalışma değerlerinden ayrıdır.",ReportStyle.NOTE))
            for(a in analyses) {
                add(ReportBlock(a.part,ReportStyle.HEADING))
                add(ReportBlock(style=ReportStyle.TABLE,table=ReportTable(listOf("Kaynak bilgisi","Açıklama"),
                    listOf(listOf("Kaynak",a.sourceLabel),listOf("Adres",a.sourceUrl),listOf("Durum",if(a.report.documentEligible) "Belge kimliği doğrulanmış kayıt" else "İnceleme gerekli")),listOf(1f,3f))))
            }
            add(ReportBlock(style=ReportStyle.TABLE,table=ReportTable(listOf("Kriter")+analyses.map { it.part }+"Test koşulları",
                rows.map { listOf(it.title)+it.cells+it.conditionNote },listOf(1.6f)+analyses.map { 2.5f }+2f)))
            add(ReportBlock("Otomatik muadil veya devre uygunluğu kararı verilmez. Teknik değerleri kaynak belgelerde doğrulayın.",ReportStyle.NOTE))
        }
    }
}
