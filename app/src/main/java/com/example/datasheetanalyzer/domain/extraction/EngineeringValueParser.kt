package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.TextCleaner

data class ParsedValues(val numbers: NumericValues,val conditions: String="",val partScope: String="",val requiresReview: Boolean=false)

class EngineeringValueParser {
    private val units=buildMap<String,EngineeringUnit> {
        fun unit(symbol: String,dimension: UnitDimension,scale: Double=1.0,vararg aliases: String) {
            val u=EngineeringUnit(symbol,dimension,scale);put(symbol,u);aliases.forEach { put(it,u) }
        }
        unit("V",UnitDimension.VOLTAGE,1.0,"Vdc","VDC");unit("mV",UnitDimension.VOLTAGE,1e-3)
        unit("µV",UnitDimension.VOLTAGE,1e-6,"μV","uV")
        unit("A",UnitDimension.CURRENT,1.0,"Adc","ADC");unit("mA",UnitDimension.CURRENT,1e-3)
        unit("µA",UnitDimension.CURRENT,1e-6,"μA","uA")
        unit("nA",UnitDimension.CURRENT,1e-9);unit("pA",UnitDimension.CURRENT,1e-12)
        unit("W",UnitDimension.POWER);unit("mW",UnitDimension.POWER,1e-3)
        unit("Ω",UnitDimension.RESISTANCE,1.0,"ohm","ohms","Ω")
        unit("mΩ",UnitDimension.RESISTANCE,1e-3,"mΩ")
        unit("kΩ",UnitDimension.RESISTANCE,1e3,"kohm","kohms","kΩ")
        unit("MΩ",UnitDimension.RESISTANCE,1e6,"Mohm","Mohms")
        unit("Hz",UnitDimension.FREQUENCY);unit("kHz",UnitDimension.FREQUENCY,1e3)
        unit("MHz",UnitDimension.FREQUENCY,1e6);unit("GHz",UnitDimension.FREQUENCY,1e9)
        unit("F",UnitDimension.CAPACITANCE);unit("µF",UnitDimension.CAPACITANCE,1e-6,"μF","uF")
        unit("nF",UnitDimension.CAPACITANCE,1e-9);unit("pF",UnitDimension.CAPACITANCE,1e-12)
        unit("°C",UnitDimension.TEMPERATURE);unit("s",UnitDimension.TIME)
        unit("ms",UnitDimension.TIME,1e-3);unit("µs",UnitDimension.TIME,1e-6,"μs","us")
        unit("ns",UnitDimension.TIME,1e-9);unit("dB",UnitDimension.GAIN)
        unit("V/V",UnitDimension.GAIN_RATIO);unit("V/mV",UnitDimension.GAIN_RATIO,1e3)
    }
    private val number="""[+-]?(?:\d+(?:[.,]\d+)?|\.\d+)(?:[eE][+-]?\d+)?"""
    private val unitPattern=units.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private val range=Regex("(?<![A-Za-z0-9.])($number)\\s*($unitPattern)?\\s*(?:to|through|ile|[-–~…])\\s*($number)\\s*($unitPattern)(?![A-Za-zΩ/])")
    private val quantity=Regex("(?<![A-Za-z0-9.])($number)\\s*($unitPattern)(?![A-Za-zΩ/])")
    private fun numeric(text: String): Double? = text.trim().replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() }
    fun unit(text: String): EngineeringUnit? = units[text.trim().replace("° C","°C")]
    private fun compatible(wanted: UnitDimension?,actual: UnitDimension)=wanted==null || wanted==actual ||
        wanted==UnitDimension.GAIN && actual==UnitDimension.GAIN_RATIO
    private fun opaque(text: String)='\uFFFD' in text || text.any { Character.getType(it)==Character.PRIVATE_USE.toInt() }
    fun isValueContinuation(raw: String): Boolean {
        val line=TextCleaner().normalize(raw)
        if('=' in line || '|' in line || quantity.findAll(line).none()) return false
        val rest=line.replace(Regex("($unitPattern)(?![A-Za-zΩ/])"),"")
            .replace(Regex("""(?i)\b(?:to|through|ile|typ(?:ical)?|nom(?:inal)?|min(?:imum)?|max(?:imum)?)\b"""),"")
            .replace(Regex(number),"").replace(Regex("""[\s±~…\-]+"""),"")
        return rest.isEmpty()
    }

    fun parse(raw: String,previous: List<String> = emptyList(),dimension: UnitDimension?=null): ParsedValues? {
        val line=TextCleaner().normalize(raw).replace(Regex("""°\s+C"""),"°C")
        parsePipe(line,previous,dimension)?.let { return it }
        // A malformed structured row must not fall back to a loose scalar match.
        if('|' in line) return null
        if(opaque(line)) return null
        // An unresolved multi-part table must never become a last-column scalar.
        if(previous.takeLast(100).any { h ->
            val names=Regex("""(?i)\b(min(?:imum)?|typ(?:ical)?|max(?:imum)?|nom(?:inal)?)\b""")
                .findAll(h).map { it.value.lowercase().take(3) }.toList()
            names.size>=4 && names.distinct().size<names.size
        }) return null
        // Equation/test-condition numbers must not be reported as the parameter's value.
        if('=' in line || Regex("""(?i)\b(?:unless|test conditions|example)\b""").containsMatchIn(line)) return null
        range.find(line)?.let { match ->
            val highUnit=unit(match.groupValues[4]) ?: return@let
            val lowUnit=unit(match.groupValues[2]) ?: highUnit
            if(lowUnit.dimension!=highUnit.dimension || !compatible(dimension,lowUnit.dimension)) return@let
            val low=numeric(match.groupValues[1]) ?: return@let
            val high=(numeric(match.groupValues[3]) ?: return@let)*highUnit.scale/lowUnit.scale
            if(low<=high && high.isFinite()) return ParsedValues(NumericValues(minimum=low,maximum=high,unit=lowUnit))
        }
        val quantities=quantity.findAll(line).toList()
        if(quantities.size!=1) return null
        val q=quantities.single();val unit=unit(q.groupValues[2]) ?: return null
        if(!compatible(dimension,unit.dimension)) return null
        val beforeUnit=line.substring(0,q.groups[2]!!.range.first).trimEnd()
        val fields=previous.takeLast(12).asReversed().firstNotNullOfOrNull { header ->
            val names=Regex("""(?i)\b(min(?:imum)?|typ(?:ical)?|max(?:imum)?|nom(?:inal)?)\b""").findAll(header)
                .map { it.value.lowercase().take(3) }.toList()
            val h=SearchText.normalize(header)
            val tableHeader=names.size>=2 || h in setOf("min","minimum","typ","typical","max","maximum") ||
                (names.isNotEmpty() && Regex("""\b(?:units?|parameter|symbol)\b""").containsMatchIn(h))
            if(tableHeader && names.distinct().size==names.size) names else null
        }
        if(fields!=null) {
            val rowTokens=Regex("(?<![A-Za-z0-9.])(?:$number|[-—])(?![A-Za-z0-9.])").findAll(beforeUnit)
                .map { it.value }.toList()
            if(rowTokens.size==fields.size) {
                val values=fields.zip(rowTokens.map(::numeric)).toMap()
                val v=NumericValues(values["min"],values["typ"],values["max"],values["nom"],unit=unit,structured=true,
                    nominal=values["nom"]!=null)
                if(valid(v)) return ParsedValues(v)
            }
            // Lost blank columns are ambiguous; don't invent a Min/Typ/Max mapping.
            if(fields.size>1) return null
        }
        if(Regex("(?<![A-Za-z0-9.])($number)(?![A-Za-z0-9.])").findAll(beforeUnit
                .replace(Regex("""(?i)\(note\s+\d{1,2}\)"""),"")).count()>1) return null
        val value=numeric(q.groupValues[1]) ?: return null
        val qualifier=SearchText.normalize(line.substring(q.range.last+1))
        val before=line.substring(0,q.range.first)
        val symmetric=before.trimEnd().endsWith('±')
        val values=when {
            qualifier.startsWith("nom") || Regex("""(?i)\bnom(?:inal)?\b""").containsMatchIn(before) -> NumericValues(value=value,unit=unit,nominal=true)
            qualifier.startsWith("typ") || Regex("""(?i)\btyp(?:ical)?\b""").containsMatchIn(before) -> NumericValues(typical=value,unit=unit)
            qualifier.startsWith("max") || Regex("""(?i)\bmax(?:imum)?\b""").containsMatchIn(before) -> NumericValues(maximum=value,unit=unit)
            qualifier.startsWith("min") || Regex("""(?i)\bmin(?:imum)?\b""").containsMatchIn(before) -> NumericValues(minimum=value,unit=unit)
            else -> NumericValues(value=value,unit=unit,symmetric=symmetric)
        }
        return ParsedValues(values)
    }

    private fun parsePipe(line: String,previous: List<String>,dimension: UnitDimension?): ParsedValues? {
        if('|' !in line) return null
        val cells=line.split('|').map(String::trim)
        val header=previous.takeLast(12).asReversed().firstOrNull { h ->
            '|' in h && h.split('|').any { SearchText.normalize(it) in resultHeaders }
        }?.split('|')?.map(SearchText::normalize) ?: return null
        if(header.size!=cells.size) return null
        if(header.filter { it in resultHeaders }.let { it.isEmpty() || it.distinct().size!=it.size }) return null
        val unitIndex=header.indexOfFirst { it in setOf("unit","units") }
        if(unitIndex<0) return null
        val unit=unit(cells[unitIndex]) ?: return null
        if(!compatible(dimension,unit.dimension)) return null
        fun value(names: Set<String>): Double? {
            val index=header.indexOfFirst { it in names };return if(index<0) null else numeric(cells[index])
        }
        // Non-numeric populated result cells cannot silently become missing limits.
        header.forEachIndexed { index,name ->
            if(name in setOf("min","minimum","typ","typical","max","maximum","nom","nominal") &&
                cells[index] !in setOf("","-","—","N/A","n/a") && numeric(cells[index])==null) return null
        }
        val scalar=header.indexOf("value")
        val nom=header.indexOfFirst { it in setOf("nom","nominal") }
        if(scalar>=0 && nom>=0) return null
        val scalarValues=if(scalar>=0 && cells[scalar].isNotBlank() && cells[scalar]!="-") {
            val parsed=parse("${cells[scalar]} ${unit.symbol}",dimension=dimension)?.numbers ?: return null
            parsed.copy(structured=true)
        } else null
        val values=scalarValues ?: NumericValues(value(setOf("min","minimum")),value(setOf("typ","typical")),
            value(setOf("max","maximum")),if(nom>=0) numeric(cells[nom]) else null,unit=unit,structured=true,
            nominal=nom>=0 && numeric(cells[nom])!=null)
        if(!valid(values)) return null
        val condition=header.indexOfFirst { it.contains("condition") || it=="test" }
        val scope=header.indexOfFirst { it=="part" }
        if(scope>=0 && opaque(cells[scope])) return null
        val quality=header.indexOf("reading quality")
        if(quality>=0 && cells[quality] !in setOf("review","verified")) return null
        return ParsedValues(values,if(condition>=0) cells[condition] else "",if(scope>=0) cells[scope] else "",
            quality>=0 && cells[quality]=="review" || condition>=0 && opaque(cells[condition]) || opaque(cells.first()))
    }
    private fun valid(v: NumericValues): Boolean {
        val values=listOfNotNull(v.minimum,v.typical,v.maximum,v.value)
        return values.isNotEmpty() && values.all { it.isFinite() } &&
            (v.minimum==null || v.maximum==null || v.minimum<=v.maximum) &&
            (v.typical==null || v.minimum==null || v.typical>=v.minimum) &&
            (v.typical==null || v.maximum==null || v.typical<=v.maximum) &&
            (!v.nominal || v.value==null || v.minimum==null || v.value>=v.minimum) &&
            (!v.nominal || v.value==null || v.maximum==null || v.value<=v.maximum)
    }
    private val resultHeaders=setOf("min","minimum","typ","typical","max","maximum","nom","nominal","value")
}
