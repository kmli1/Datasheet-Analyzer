package com.example.datasheetanalyzer.export

import com.example.datasheetanalyzer.domain.document.AnalyzedDocument
import com.example.datasheetanalyzer.domain.extraction.*
import java.math.BigDecimal
import com.example.datasheetanalyzer.domain.document.FigureResult

enum class ReportStyle { TITLE, HEADING, BODY, NOTE, EVIDENCE, TABLE, IMAGE }
data class ReportTable(val headers: List<String>,val rows: List<List<String>>,val weights: List<Float> = List(headers.size) { 1f }) {
    init {
        require(headers.size in 1..8 && rows.size<=300 && rows.all { it.size==headers.size } && weights.size==headers.size && weights.all { it.isFinite() && it>0 })
    }
}
data class ReportImage(val png: ByteArray) { init { require(png.size in 8..8*1024*1024) } }
data class ReportBlock(val text: String="",val style: ReportStyle=ReportStyle.BODY,val table: ReportTable?=null,val image: ReportImage?=null)

/** One content model keeps the exported report consistent with the on-screen result. */
object AnalysisReportContent {
    fun status(value: ParameterStatus)=when(value) {
        ParameterStatus.DETECTED -> "Tespit edildi"
        ParameterStatus.POSSIBLE -> "Olası sonuç - kaynak kontrolü gerekli"
        ParameterStatus.NOT_FOUND -> "Bulunamadı"
        ParameterStatus.DOCUMENT_UNVERIFIED -> "Belge doğrulaması gerekli - kaynak alıntısı"
    }
    private fun number(value: Double)=BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    fun build(part: String,document: AnalyzedDocument,report: ParameterReport,date: String): List<ReportBlock> = buildList {
        fun table(headers: List<String>,rows: List<List<String>>,weights: List<Float> = List(headers.size) { 1f }) {
            if(rows.isNotEmpty()) add(ReportBlock(style=ReportStyle.TABLE,table=ReportTable(headers,rows,weights)))
        }
        add(ReportBlock("Datasheet Analyzer",ReportStyle.TITLE))
        add(ReportBlock("$part - Analiz raporu",ReportStyle.HEADING))
        table(listOf("Belge bilgisi","Açıklama"),buildList {
            add(listOf("Oluşturulma",date));add(listOf("Belge durumu",if(report.documentEligible) "Belge kimliği doğrulandı" else "Belge doğrulaması tamamlanmadı; sayısal sonuçlar otomatik onaylanmadı."))
            add(listOf("Kaynak",document.candidate.source.label));add(listOf("Kaynak adresi",document.candidate.canonicalUrl))
            add(listOf("İncelenen sayfalar","${document.extraction.pages.size}/${document.extraction.totalPages}"))
        },listOf(1f,3f))
        if(document.extraction.truncated) add(ReportBlock("Belge kısmen okundu. Rapor yalnızca incelenen sayfalardaki eşleşmeleri kapsar.",ReportStyle.NOTE))
        if(!report.documentEligible) document.verification.reasons.take(8).forEach { add(ReportBlock(it,ReportStyle.NOTE)) }
        if(report.results.isNotEmpty()) {
            add(ReportBlock("Sonuç özeti",ReportStyle.HEADING))
            table(listOf("Kriter","Durum","Sonuç"),report.results.map {
                listOf(it.query.requested,ResultPresentation.status(it.status),ResultPresentation.brief(it))
            },listOf(2f,1.2f,2f))
        }
        for(result in report.results) {
            add(ReportBlock(result.query.requested,ReportStyle.HEADING))
            add(ReportBlock(status(result.status),ReportStyle.NOTE))
            if(result.matches.isEmpty()) add(ReportBlock(result.explanation))
            fun evidence(hit: ExtractedParameter,absolute: Boolean) {
                if(absolute) add(ReportBlock("Mutlak maksimum sınır - normal çalışma değeri değildir",ReportStyle.NOTE))
                val numericAllowed=report.documentEligible && result.status!=ParameterStatus.DOCUMENT_UNVERIFIED
                if(numericAllowed && hit.numbers!=null) {
                    table(listOf("Min","Tipik","Maks","Değer","Birim"),listOf(ResultPresentation.values(hit.numbers)))
                }
                val sourceRows=if(numericAllowed) SourceSectionTableReader().rows(hit,part) else emptyList()
                if(sourceRows.isNotEmpty()) table(listOf("Parametre","Min","Tipik","Maks","Değer","Birim"),
                    sourceRows.map { listOf(it.label)+ResultPresentation.values(it.numbers) },listOf(3f,1f,1f,1f,1f,1f))
                table(listOf("Kaynak bilgisi","Açıklama"),buildList {
                    add(listOf("Bölüm",hit.section?.label ?: "Bölüm belirlenemedi"));add(listOf("Sayfa","kaynak s.${hit.page}"))
                    if(hit.sourcePartScope.isNotBlank()) add(listOf("Tablo kapsamı",hit.sourcePartScope))
                    if(hit.conditions.isNotBlank()) add(listOf("Koşullar",hit.conditions))
                    add(listOf("Kanıt",hit.evidenceLine))
                },listOf(1f,3f))
                if(hit.numbers==null && sourceRows.isEmpty()) {
                    val textRows=hit.rawContext.lines().filter(String::isNotBlank).take(35).map { listOf(it.take(350),"s.${hit.page}") }
                    table(listOf("Kaynak metni / alıntı","Sayfa"),textRows,listOf(7f,1f))
                }
                hit.warnings.distinct().take(4).forEach { add(ReportBlock(it,ReportStyle.NOTE)) }
            }
            result.matches.forEach { evidence(it,false) }
            result.absoluteLimits.forEach { evidence(it,true) }
        }
        if(report.graphs.isNotEmpty()) {
            add(ReportBlock("Grafikler",ReportStyle.HEADING))
            table(listOf("Aranan başlık","Sonuç","Kaynak sayfası"),report.graphs.map {
                listOf(it.requested,if(it.matches.isEmpty()) "Bulunamadı" else "Başlık eşleşti",it.matches.joinToString { m -> "s.${m.page}" }.ifBlank { "-" })
            },listOf(3f,2f,1f))
            report.graphs.filter { it.matches.isEmpty() }.forEach { add(ReportBlock(it.explanation,ReportStyle.NOTE)) }
        }
        add(ReportBlock("Kanıt puanı doğruluk yüzdesi değildir. Teknik değerleri ve test koşullarını orijinal datasheet sayfasında doğrulayın.",ReportStyle.NOTE))
    }
    fun filename(part: String,suffix: String,extension: String): String {
        val safe=part.replace(Regex("[^A-Za-z0-9_-]"),"_").take(64).ifBlank { "datasheet" }
        return "$safe-$suffix.$extension"
    }
}
