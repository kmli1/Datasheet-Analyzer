package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.domain.model.CriterionGroups
import com.example.datasheetanalyzer.domain.document.FigureQuery
import java.text.Normalizer
import java.util.Locale

object SearchText {
    fun normalize(value: String): String = Normalizer.normalize(value,Normalizer.Form.NFKD)
        .replace(Regex("""\p{M}+"""),"").lowercase(Locale.ROOT).replace('ı','i')
        .replace(Regex("""[^a-z0-9+]+""")," ").trim().replace(Regex("""\s+""")," ")
    fun phrase(text: String,phrase: String): Boolean = Regex("(?<![a-z0-9])"+
        Regex.escape(normalize(phrase))+"(?![a-z0-9])").containsMatchIn(normalize(text))
}

object CriterionDictionary {
    val aliases=mapOf(
        Criterion.SUPPLY_VOLTAGE to listOf("recommended operating supply voltage","recommended operating voltage","recommended operation voltage","recommendation operation voltage","recommended supply voltage","recommended voltage","onerilen calisma gerilimi","onerilen besleme gerilimi","supply voltage","operating supply voltage","operating voltage","operation voltage","supply range","voltage supply range","besleme gerilimi","besleme voltaji","calisma gerilimi","vcc","vdd","v+"),
        Criterion.SUPPLY_CURRENT to listOf("supply current","quiescent current","idle current","besleme akimi","bos akim","icc","idd"),
        Criterion.MAXIMUM_CURRENT to listOf("maximum current","max current","peak current","current limit","current capability","maksimum akim","azami akim"),
        Criterion.OUTPUT_CURRENT to listOf("output current","output drive current","cikis akimi"),
        Criterion.INPUT_VOLTAGE to listOf("input voltage","input voltage range","common mode voltage range","common-mode input range","giris gerilimi","giris voltaji","giris gerilim araligi"),
        Criterion.OUTPUT_VOLTAGE to listOf("output voltage","output voltage swing","voltage output swing","output swing","cikis gerilimi","cikis voltaji","cikis gerilim salinimi"),
        Criterion.POWER_DISSIPATION to listOf("power dissipation","dissipation power","power consumption","guc kaybi","guc tuketimi","harcanan guc"),
        Criterion.OPERATING_TEMPERATURE to listOf("operating temperature","operating ambient temperature","operating junction temperature","operating temperature range","calisma sicakligi"),
        Criterion.STORAGE_TEMPERATURE to listOf("storage temperature","storage temperature range","depolama sicakligi"),
        Criterion.FREQUENCY to listOf("frequency","operating frequency","frekans"),
        Criterion.BANDWIDTH to listOf("bandwidth","gain bandwidth","unity gain bandwidth","bant genisligi"),
        Criterion.GAIN to listOf("voltage gain","open loop gain","large signal voltage gain","gain","kazanc","aol","avol"),
        Criterion.INPUT_OFFSET_VOLTAGE to listOf("input offset voltage","input offset","offset voltage","giris ofset gerilimi","giris ofset voltaji","giris offset gerilimi","giris offset voltaji","vos","v_os","vio","v_io","vios"),
        Criterion.PINOUT to listOf("pinout","pin configuration","pin description","pin functions","connection diagram","pinler","bacaklar","bacak baglantilari"),
        Criterion.PACKAGE to listOf("package information","package dimensions","package outline","package","kilif","paket"),
        Criterion.FEATURES to listOf("key features","features","ozellikler"),
        Criterion.APPLICATIONS to listOf("applications","application information","uygulamalar"),
        Criterion.ELECTRICAL_CHARACTERISTICS to listOf("electrical characteristics","electrical specifications","elektriksel ozellikler"),
        Criterion.ABSOLUTE_MAXIMUMS to listOf("absolute maximum ratings","absolute maximums","absolute maximum","mutlak maksimum degerler","mutlak maksimum"),
        Criterion.RECOMMENDED_OPERATING_CONDITIONS to listOf("recommended operating conditions","recommended operation","onerilen calisma kosullari")
    )
    val textCriteria=setOf(Criterion.PINOUT,Criterion.PACKAGE,Criterion.FEATURES,Criterion.APPLICATIONS,
        Criterion.ELECTRICAL_CHARACTERISTICS,Criterion.ABSOLUTE_MAXIMUMS,Criterion.RECOMMENDED_OPERATING_CONDITIONS)
    fun dimension(criterion: Criterion?): UnitDimension? = when(criterion) {
        Criterion.SUPPLY_VOLTAGE,Criterion.INPUT_VOLTAGE,Criterion.OUTPUT_VOLTAGE,Criterion.INPUT_OFFSET_VOLTAGE -> UnitDimension.VOLTAGE
        Criterion.SUPPLY_CURRENT,Criterion.MAXIMUM_CURRENT,Criterion.OUTPUT_CURRENT -> UnitDimension.CURRENT
        Criterion.POWER_DISSIPATION -> UnitDimension.POWER
        Criterion.OPERATING_TEMPERATURE,Criterion.STORAGE_TEMPERATURE -> UnitDimension.TEMPERATURE
        Criterion.FREQUENCY,Criterion.BANDWIDTH -> UnitDimension.FREQUENCY
        Criterion.GAIN -> UnitDimension.GAIN
        else -> null
    }
}

class QueryNormalizer(private val config: ParameterConfig=ParameterConfig()) {
    fun queries(selected: Set<Criterion>,custom: String,graphTitles: String=""): List<ParameterQuery> {
        val results=selected.sortedBy(CriterionGroups::index).map { ParameterQuery(it.label,it,CriterionDictionary.aliases.getValue(it),
            if(it==Criterion.MAXIMUM_CURRENT || it==Criterion.ABSOLUTE_MAXIMUMS) QueryMode.ABSOLUTE_MAXIMUM else QueryMode.STANDARD) }.toMutableList()
        custom.take(500).split(Regex("""(?i)[,;\n·]+|\s+(?:and|ve|ile)\s+""")).filter { it.isNotBlank() }.forEach { segment ->
            if(FigureQuery.explicit(segment)) {
                val title=FigureQuery.clean(segment)
                if(title.isNotBlank()) results+=ParameterQuery(title,null,listOf(title),QueryMode.GRAPH)
                return@forEach
            }
            val normalized=SearchText.normalize(segment)
            val matches=CriterionDictionary.aliases.mapNotNull { (criterion,aliases) ->
                aliases.filter { SearchText.phrase(normalized,it) }.maxByOrNull { it.length }?.let { criterion to it.length }
            }.sortedByDescending { it.second }
            // Prefer the most specific phrase (input offset voltage over the generic input voltage).
            val criterion=matches.firstOrNull()?.first
            val mode=if(Regex("""\b(?:max|maximum|absolute|maksimum|azami|mutlak)\b""").containsMatchIn(normalized))
                QueryMode.ABSOLUTE_MAXIMUM else if(Regex("""\b(?:recommended|recommendation|onerilen)\b""").containsMatchIn(normalized))
                QueryMode.RECOMMENDED_OPERATING else QueryMode.STANDARD
            val words=normalized.split(' ').filter { it !in setOf("what","is","the","kac","nedir","degeri","nasil","olan") }
            if(words.isNotEmpty()) results+=ParameterQuery(segment.trim(),criterion,
                criterion?.let { CriterionDictionary.aliases.getValue(it) } ?: listOf(words.joinToString(" ")),mode)
        }
        graphTitles.take(500).split(Regex("[;\\n]+" )).map(FigureQuery::clean).filter(String::isNotBlank).take(3)
            .forEach { results+=ParameterQuery(it,null,listOf(it),QueryMode.GRAPH) }
        val unique=results.distinctBy { (it.criterion?.name ?: SearchText.normalize(it.requested)) to it.mode }
        val graphs=unique.filter { it.mode==QueryMode.GRAPH }.take(3)
        return unique.filter { it.mode!=QueryMode.GRAPH }.take(config.maxQueries-graphs.size)+graphs
    }
}
