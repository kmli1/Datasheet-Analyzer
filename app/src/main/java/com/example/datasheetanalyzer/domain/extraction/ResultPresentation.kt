package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.PartMatch
import java.math.BigDecimal

/** Screen and exported PDF use the same value strings, including empty original cells. */
object ResultPresentation {
    fun number(value: Double?)=value?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: "-"
    fun status(value: ParameterStatus)=when(value) {
        ParameterStatus.DETECTED -> "Tespit edildi"
        ParameterStatus.POSSIBLE -> "Olası sonuç"
        ParameterStatus.NOT_FOUND -> "Bulunamadı"
        ParameterStatus.DOCUMENT_UNVERIFIED -> "İnceleme gerekli"
    }
    fun values(n: NumericValues)=listOf(number(n.minimum),number(n.typical),number(n.maximum),
        (if(n.nominal && n.value!=null) "Nominal " else if(n.symmetric && n.value!=null) "±" else "")+number(n.value),n.unit.symbol)
    fun brief(result: ParameterResult): String {
        if(result.status==ParameterStatus.DOCUMENT_UNVERIFIED) return if(result.matches.isEmpty()) "-" else "Kaynak alıntısı"
        val hit=result.matches.firstOrNull() ?: return "-"
        val n=hit.numbers ?: return "Kaynak metni"
        return when {
            n.minimum!=null && n.maximum!=null -> "${number(n.minimum)} – ${number(n.maximum)} ${n.unit.symbol}"
            n.typical!=null -> "Tipik ${number(n.typical)} ${n.unit.symbol}"
            n.value!=null -> "${if(n.nominal) "Nominal " else if(n.symmetric) "±" else ""}${number(n.value)} ${n.unit.symbol}"
            n.maximum!=null -> "Maks ${number(n.maximum)} ${n.unit.symbol}"
            n.minimum!=null -> "Min ${number(n.minimum)} ${n.unit.symbol}"
            else -> "-"
        }
    }
}

data class SourceTableRow(val label: String,val numbers: NumericValues,val conditions: String,val evidence: String)
/** Only explicit, unit-bearing rows are formatted; absent or lost columns are never guessed. */
class SourceSectionTableReader {
    fun rows(hit: ExtractedParameter,part: String): List<SourceTableRow> {
        if(hit.numbers!=null) return emptyList()
        val lines=hit.rawContext.lines()
        val parser=EngineeringValueParser();val detector=DatasheetSectionDetector()
        val out=mutableListOf<SourceTableRow>()
        for((index,line) in lines.withIndex()) {
            val heading=detector.detect(listOf(PageText(1,line))).firstOrNull()
            if(heading!=null && hit.section!=null && heading.section!=hit.section) break
            if(part.isNotBlank() && DocumentPartMatcher().match(part,line)==PartMatch.FAMILY_VARIANT) continue
            if(line.length>500 || line.startsWith("[Native")) continue
            val known=CriterionDictionary.aliases.filterKeys { it !in CriterionDictionary.textCriteria }.entries
                .firstOrNull { (_,aliases) -> aliases.any { SearchText.phrase(line,it) } }
            val parsed=parser.parse(line,lines.subList(maxOf(0,index-12),index),known?.key?.let(CriterionDictionary::dimension)) ?: continue
            if(parsed.requiresReview) continue
            if(!TablePartScope.allows(part,parsed.partScope)) continue
            val label=if('|' in line) line.substringBefore('|').trim()
                else line.substringBefore(Regex("(?<![A-Za-z0-9])[-+±]?\\d+(?:[.,]\\d+)?\\s*(?:V|mV|µV|A|mA|µA|W|mW|°C|Hz|kHz|MHz|Ω|kΩ|dB)\\b").find(line)?.value ?: "\u0000").trim()
            if(label.isBlank() || !label.any(Char::isLetter)) continue
            val scopeCheck=VariantResultGuard.apply(part,ParameterResult(ParameterQuery(hit.requestedCriterion,null,emptyList()),
                ParameterStatus.DETECTED,listOf(hit.copy(numbers=parsed.numbers,evidenceLine=line,conditions=parsed.conditions,sourcePartScope=parsed.partScope.ifBlank { hit.sourcePartScope }))))
            if(scopeCheck.matches.single().numbers==null) continue
            out+=SourceTableRow(known?.key?.label ?: label.take(100),parsed.numbers,parsed.conditions,line.take(350))
            if(out.size>=24) break
        }
        return out.distinctBy { Triple(it.label,it.numbers,it.conditions) }
    }
}
