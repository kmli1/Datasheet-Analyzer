package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.Criterion

enum class ConditionField(val title: String,val criterion: Criterion,val unit: EngineeringUnit) {
    SUPPLY("Besleme gerilimi",Criterion.SUPPLY_VOLTAGE,EngineeringUnit("V",UnitDimension.VOLTAGE,1.0)),
    INPUT("Giriş gerilimi",Criterion.INPUT_VOLTAGE,EngineeringUnit("V",UnitDimension.VOLTAGE,1.0)),
    OUTPUT_CURRENT("Çıkış akımı",Criterion.OUTPUT_CURRENT,EngineeringUnit("A",UnitDimension.CURRENT,1.0)),
    AMBIENT("Ortam sıcaklığı",Criterion.OPERATING_TEMPERATURE,EngineeringUnit("°C",UnitDimension.TEMPERATURE,1.0))
}
enum class ConditionVerdict { WITHIN, BELOW, ABOVE, ABSOLUTE_EXCEEDED, INCOMPLETE, UNKNOWN }
data class ConditionCheck(val verdict: ConditionVerdict,val message: String,val sources: List<ExtractedParameter> = emptyList())

/** Checks explicit source bounds, never typical values or inferred operating ranges. */
object OperatingConditions {
    fun report(document: AnalyzedDocument): ParameterReport = ParameterExtractionEngine().extract(document,
        ConditionField.entries.flatMap { f -> listOf(
            ParameterQuery(f.title,f.criterion,CriterionDictionary.aliases.getValue(f.criterion),QueryMode.RECOMMENDED_OPERATING),
            ParameterQuery(f.title+" · mutlak sınır",f.criterion,CriterionDictionary.aliases.getValue(f.criterion),QueryMode.ABSOLUTE_MAXIMUM)) })
    fun parse(value: String): Double?=value.trim().replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() }
    fun check(report: ParameterReport,field: ConditionField,value: Double): ConditionCheck {
        if(!value.isFinite() || !report.documentEligible) return ConditionCheck(ConditionVerdict.UNKNOWN,"Doğrulanmış belge ve geçerli bir sayı gerekli.")
        val results=report.results.filter { it.query.criterion==field.criterion }
        fun eligible(hit: ExtractedParameter): Boolean {
            val n=hit.numbers ?: return false
            if(hit.confidence<80 || hit.origin!=TextOrigin.TEXT_LAYER || n.unit.dimension!=field.unit.dimension ||
                !n.unit.scale.isFinite() || n.unit.scale<=0 || n.symmetric) return false
            if(field==ConditionField.AMBIENT && Regex("(?i)junction|\\btj\\b|jonksiyon").containsMatchIn(hit.evidenceLine+" "+hit.conditions)) return false
            if(listOfNotNull(n.minimum,n.maximum).any { !it.isFinite() }) return false
            return n.minimum==null || n.maximum==null || n.minimum<=n.maximum
        }
        val absolute=results.flatMap { it.matches+it.absoluteLimits }.filter { eligible(it) && it.section==DatasheetSection.ABSOLUTE_MAXIMUM }.distinct()
        fun below(h: ExtractedParameter)=h.numbers!!.minimum?.let { value<it*h.numbers.unit.scale }==true
        fun above(h: ExtractedParameter)=h.numbers!!.maximum?.let { value>it*h.numbers.unit.scale }==true
        val exceeded=absolute.filter { below(it) || above(it) }
        if(exceeded.isNotEmpty()) return ConditionCheck(ConditionVerdict.ABSOLUTE_EXCEEDED,
            "Mutlak sınır aşılıyor. Bu sınır önerilen çalışma değeri değildir.",exceeded)
        val recommended=results.filter { it.status==ParameterStatus.DETECTED && it.query.mode==QueryMode.RECOMMENDED_OPERATING }
            .flatMap { it.matches }.filter { eligible(it) && it.section==DatasheetSection.RECOMMENDED_OPERATING &&
                (it.numbers!!.minimum!=null || it.numbers.maximum!=null) }.distinct()
        if(recommended.isEmpty()) return ConditionCheck(ConditionVerdict.UNKNOWN,
            if(absolute.isNotEmpty()) "Yalnız mutlak sınır var; normal çalışma uygunluğu belirlenemedi."
            else "Bu koşul için doğrulanmış önerilen min/maks sınırı yok. Tipik veya tek değer sınır kabul edilmez.",absolute)
        val signatures=recommended.map { h -> listOf(h.numbers!!.minimum?.times(h.numbers.unit.scale),h.numbers.maximum?.times(h.numbers.unit.scale),SearchText.normalize(h.conditions)) }.distinct()
        if(signatures.size!=1) return ConditionCheck(ConditionVerdict.UNKNOWN,"Farklı değerler veya test koşulları var; hangi satırın geçerli olduğu kaynakta seçilmeli.",recommended)
        val hit=recommended.first();val n=hit.numbers!!
        if(below(hit)) return ConditionCheck(ConditionVerdict.BELOW,"Önerilen minimumun altında.",listOf(hit))
        if(above(hit)) return ConditionCheck(ConditionVerdict.ABOVE,"Önerilen maksimumun üzerinde.",listOf(hit))
        if(n.minimum==null || n.maximum==null) return ConditionCheck(ConditionVerdict.INCOMPLETE,"Bilinen sınır aşılmıyor; aralığın diğer sınırı bulunmadığı için tam kontrol yapılamadı.",listOf(hit))
        return ConditionCheck(ConditionVerdict.WITHIN,"Belirtilen önerilen aralık içinde. Diğer test koşulları ve paket şartları ayrıca geçerli olmalı.",listOf(hit))
    }
}
