package com.example.datasheetanalyzer.export

import com.example.datasheetanalyzer.data.workspace.SavedAnalysis
import com.example.datasheetanalyzer.domain.document.DatasheetSection
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.figures.*
import java.math.BigDecimal

/** UTF-8 BOM + semicolons + CRLF for Turkish Excel; source text cannot become a formula. */
object AnalysisCsvWriter {
    fun cell(value: String): String {
        val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@') ||
            value.startsWith('\t') || value.startsWith('\r') || value.startsWith('\n')) "'"+value else value
        return "\""+safe.replace("\"","\"\"")+"\""
    }
    private fun number(value: Double?)=value?.takeIf(Double::isFinite)?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.',',') }.orEmpty()
    private fun row(values: List<String>,numeric: Set<Int> = emptySet())=values.mapIndexed { i,s ->
        if(i in numeric && s.isNotEmpty()) "\"$s\"" else cell(s)
    }.joinToString(";")+"\r\n"
    fun analyses(analyses: List<SavedAnalysis>): String {
        require(analyses.size in 1..3)
        return buildString {
            append('\uFEFF');append(row(listOf("Parça","Kriter","Durum","Parametre","Min","Tipik","Maks","Değer","Birim","Bölüm","Kaynak sayfa","Test koşulları","Kanıt","Metin kaynağı","Kaynak URL","Belge SHA-256","Ayrı mutlak sınır","Açıklama")))
            for(a in analyses) for(stored in a.report.results) {
                val result=VariantResultGuard.apply(a.part,stored)
                val rawMatches=(result.matches.map { it to false }+result.absoluteLimits.map { it to true })
                val matches=rawMatches.flatMap { (hit,absolute) ->
                    val rows=if(a.report.documentEligible && result.status==ParameterStatus.DETECTED && hit.confidence>=80)
                        SourceSectionTableReader().rows(hit,a.part) else emptyList()
                    if(rows.isEmpty()) listOf(hit to absolute) else rows.map { row ->
                        hit.copy(parameterName=row.label,numbers=row.numbers,conditions=row.conditions,evidenceLine=row.evidence) to absolute
                    }
                }
                if(matches.isEmpty()) append(row(listOf(a.part,result.query.requested,ResultPresentation.status(result.status))+
                    List(11) { "" }+listOf(a.sourceUrl,a.sha256,"",result.explanation)))
                for((hit,absolute) in matches) {
                    val approved=a.report.documentEligible && result.status!=ParameterStatus.DOCUMENT_UNVERIFIED && hit.confidence>=80 && (result.status==ParameterStatus.DETECTED ||
                        absolute && hit.section==DatasheetSection.ABSOLUTE_MAXIMUM)
                    val n=hit.numbers.takeIf { approved }
                    append(row(listOf(a.part,result.query.requested,
                        if(absolute) "Ayrı mutlak sınır" else ResultPresentation.status(result.status),hit.parameterName,
                        number(n?.minimum),number(n?.typical),number(n?.maximum),
                        (if(n?.symmetric==true && n.value!=null) "±" else "")+number(n?.value),n?.unit?.symbol.orEmpty(),
                        hit.section?.label.orEmpty(),hit.page.toString(),hit.conditions,hit.evidenceLine,hit.origin.name,
                        a.sourceUrl,a.sha256,if(absolute) "Evet" else "Hayır",result.explanation+
                            if(n?.nominal==true && n.value!=null) " Değer sütunu nominal değerdir; tipik değer değildir." else ""),setOf(4,5,6,7,10)))
                }
            }
        }
    }
    private fun approximate(value: Double)=BigDecimal.valueOf(value).round(java.math.MathContext(5)).stripTrailingZeros().toPlainString().replace('.',',')
    fun graph(part: String,title: String,page: Int,calibration: GraphCalibration,points: List<ApproximateGraphPoint>): String {
        require(points.size in 1..20)
        return buildString {
            append('\uFEFF');append(row(listOf("Parça","Grafik başlığı","Kaynak sayfa","Tür","X yaklaşık","X birim","Y yaklaşık","Y birim","X alt (2 kaynak pikseli)","X üst (2 kaynak pikseli)","Y alt (2 kaynak pikseli)","Y üst (2 kaynak pikseli)","X ölçek","Y ölçek","X eksen min","X eksen maks","Y eksen min","Y eksen maks","Alan sol","Alan üst","Alan sağ","Alan alt")))
            for(p in points) append(row(listOf(part,title,page.toString(),"Elle kalibre edilmiş yaklaşık okuma",
                approximate(p.x),calibration.x.unit,approximate(p.y),calibration.y.unit,approximate(p.xLow),approximate(p.xHigh),approximate(p.yLow),approximate(p.yHigh),
                calibration.x.scale.name,calibration.y.scale.name,approximate(calibration.x.minimum),approximate(calibration.x.maximum),
                approximate(calibration.y.minimum),approximate(calibration.y.maximum),approximate(calibration.plot.left.toDouble()),approximate(calibration.plot.top.toDouble()),
                approximate(calibration.plot.right.toDouble()),approximate(calibration.plot.bottom.toDouble())),setOf(2,4,6,8,9,10,11,14,15,16,17,18,19,20,21)))
        }
    }
}
