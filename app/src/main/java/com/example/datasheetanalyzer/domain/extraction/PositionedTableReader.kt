package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import kotlin.math.abs

/** Coordinate cells, explicit headers and bounded local continuations; never guess missing columns. */
data class PositionedGlyph(val text: String,val x: Float,val y: Float,val width: Float,val spaceWidth: Float=6f)
class PositionedTableReader {
    private data class Word(val text: String,val x: Float,val end: Float) { val center get()=(x+end)/2 }
    private data class Row(val y: Float,val words: List<Word>) { val text get()=words.joinToString(" ") { it.text } }
    private data class Column(val name: String,val center: Float)
    private data class Header(val index: Int,val columns: List<Column>,val groups: List<List<Int>>,
        val scopes: List<String>,val conditionX: Float?,val left: Float,val right: Float,
        val section: DatasheetSection?,val captionScope: String,val parameterX: Float)
    private val parser=EngineeringValueParser()
    private val cleaner=TextCleaner()
    private val detector=DatasheetSectionDetector()
    private val symbols=setOf("vos","v os","vio","v io","vios","icc","idd","vcc","vdd","v+","aol","avol")
    private fun headerName(text: String)=SearchText.normalize(text.replace(Regex("""\s*\([0-9]{1,2}\)$"""),""))
    private fun key(text: String)=when(headerName(text)) {
        "min","minimum" -> "Min";"typ","typical" -> "Typ";"max","maximum" -> "Max"
        "nom","nominal" -> "Nom";"value" -> "Value";"unit","units" -> "Units";else -> null
    }
    private fun rows(glyphs: List<PositionedGlyph>): List<Row> {
        val groups=mutableListOf<MutableList<PositionedGlyph>>()
        for(g in glyphs.asSequence().filter { it.text.isNotBlank() && it.x.isFinite() && it.y.isFinite() && it.width.isFinite() && it.width>=0 }
            .take(50_000).sortedWith(compareBy<PositionedGlyph> { it.y }.thenBy { it.x })) {
            val row=groups.lastOrNull()
            if(row==null || abs(row.first().y-g.y)>2f) groups+=mutableListOf(g) else row+=g
        }
        return groups.map { group ->
            val words=mutableListOf<Word>();var word="";var x=0f;var end=0f
            fun flush() { if(word.isNotEmpty()) words+=Word(cleaner.normalize(word),x,end);word="" }
            for(g in group.sortedBy { it.x }) {
                val content=if(g.text.any(Char::isISOControl)) g.text.map { if(it.isISOControl()) '\uFFFD' else it }.joinToString("") else g.text
                if(content.isBlank()) { flush();continue }
                val gap=if(g.spaceWidth.isFinite() && g.spaceWidth>0) (g.spaceWidth*0.45f).coerceIn(0.75f,3f) else 3f
                if(word.isNotEmpty() && (g.x-end>gap || g.x<end-2f)) flush()
                if(word.isEmpty()) x=g.x
                word+=content;end=g.x+g.width
            }
            flush();Row(group.first().y,words)
        }.filter { it.words.isNotEmpty() }
    }
    private fun header(rows: List<Row>,index: Int,section: DatasheetSection?,scope: String,grid: TableRuleGrid): Header? {
        val row=rows[index]
        if(row.words.any { key(it.text)==null &&
                Regex("""^(?:min(?:imum)?|typ(?:ical)?|max(?:imum)?|nom(?:inal)?)(?:\s|$)""").containsMatchIn(headerName(it.text)) }) return null
        val numeric=row.words.mapNotNull { w -> key(w.text)?.takeIf { it!="Units" }?.let { Column(it,w.center) } }
        if(numeric.isEmpty() || numeric.size>12) return null
        // A unit heading may be vertically centred on a two-level header, never inferred from a data unit.
        val units=(maxOf(0,index-1)..minOf(rows.lastIndex,index+1)).flatMap { n ->
            if(abs(rows[n].y-row.y)>15f) emptyList() else rows[n].words.filter { key(it.text)=="Units" }
        }.distinctBy { it.center }
        if(units.size!=1 || units.single().center<=numeric.last().center+10f) return null
        val cols=numeric+Column("Units",units.single().center)
        if(cols.zipWithNext().any { it.second.center-it.first.center<10f }) return null
        val groups=mutableListOf<MutableList<Int>>()
        for(i in numeric.indices) {
            if(groups.isEmpty() || groups.last().any { cols[it].name==cols[i].name }) groups.add(mutableListOf())
            groups.last()+=i
        }
        if(groups.any { group -> group.size>4 || group.any { cols[it].name=="Value" } && group.size!=1 }) return null
        val groupScopes=if(groups.size==1) listOf(scope) else {
            // Repeated Min/Max sets require one literal part heading centred over each group.
            val above=rows.subList(maxOf(0,index-2),index).filter { row.y-it.y<=24f }.flatMap { it.words }
                .filter { PartNumberNormalizer.isValid(it.text) && it.text.any(Char::isDigit) && it.text.any(Char::isLetter) }
            val assigned=groups.map { group ->
                val low=cols[group.first()].center-12f;val high=cols[group.last()].center+12f
                above.filter { it.center in low..high }.singleOrNull()?.text ?: return null
            }
            if(assigned.distinct().size!=groups.size) return null
            assigned
        }
        val spacing=cols[1].center-cols.first().center
        val firstCell=grid.cell(cols.first().center,row.y)
        val left=firstCell?.left?.takeIf { cols.first().center-it in 1f..40f } ?: (cols.first().center-minOf(25f,spacing/2))
        val right=cols.last().center+minOf(30f,(cols.last().center-cols[cols.lastIndex-1].center)/2)
        val conditionWord=(maxOf(0,index-1)..index).flatMap { rows[it].words.map { w -> rows[it].y to w } }
            .firstOrNull { (_,w) -> headerName(w.text) in setOf("conditions","condition","test","test conditions","test condition","testconditions","testcondition") }
        val condition=conditionWord?.let { (y,w) -> grid.cell(w.center,y)?.left ?: w.x }
        val parameterX=(maxOf(0,index-1)..index).flatMap { rows[it].words }
            .firstOrNull { headerName(it.text)=="parameter" }?.center ?: (condition?.div(2) ?: left/2)
        val measured=if(condition==null) null else rows.drop(index+1).take(12).filter { it.y-row.y<=120f }.mapNotNull { body ->
            if(body.words.none { it.center>=left && it.center<right && it.text.any(Char::isDigit) }) null else {
                val eq=body.words.indexOfFirst { it.x<left && '=' in it.text }
                if(eq<0) null else {
                    val word=body.words[eq]
                    if(word.text=="=") body.words.getOrNull(eq-1)?.x else word.x
                }
            }
        }.minOrNull()
        // The condition heading is often centred; explicit equations show its actual left edge.
        return Header(index,cols,groups,groupScopes,if(measured!=null) minOf(condition!!,measured) else condition,left,right,section,scope,parameterX)
    }
    private fun cells(row: Row,h: Header): List<String> = h.columns.mapIndexed { i,col ->
        val low=if(i==0) h.left else (h.columns[i-1].center+col.center)/2
        val high=if(i==h.columns.lastIndex) h.right else (col.center+h.columns[i+1].center)/2
        row.words.filter { it.center>=low && it.center<high }.joinToString(" ") { it.text }
    }
    private fun label(row: Row,h: Header)=row.words.filter { it.center<h.left && (h.conditionX==null || it.x<h.conditionX-3f) }
        .joinToString(" ") { it.text }
    private fun condition(row: Row,h: Header)=row.words.filter { it.center<h.left && h.conditionX!=null && it.x>=h.conditionX-3f }
        .joinToString(" ") { it.text }
    private fun meaningful(label: String)=label.any(Char::isLetter) && '=' !in label &&
        (label.split(' ').any { it.count(Char::isLetter)>=4 } || SearchText.normalize(label) in symbols)
    private fun footnote(text: String)=text.replace(Regex("""\s*\([0-9]{1,2}\)$"""),"").trim()
    fun tables(glyphs: List<PositionedGlyph>,rules: List<TableRule> = emptyList()): String {
        val rows=rows(glyphs);val out=StringBuilder();val grid=TableRuleGrid(rules)
        fun cellText(cell: TableCell?,low: Float,high: Float): String {
            if(cell==null || cell.height>140f) return ""
            return rows.filter { it.y>cell.top+1f && it.y<cell.bottom-0.5f }.flatMap { r ->
                r.words.filter { it.center>=maxOf(low,cell.left) && it.center<minOf(high,cell.right) }
            }.joinToString(" ") { it.text }
        }
        var section: DatasheetSection?=null;var captionScope="";var h: Header?=null;var lastY=0f
        var lastBlockHeader="";var emittedRows=0
        for((index,row) in rows.withIndex()) {
            val heading=detector.detect(listOf(PageText(1,row.text+"\n"+rows.getOrNull(index+1)?.text.orEmpty())))
                .firstOrNull { it.lineIndex==0 }?.section
            if(heading!=null) { section=heading;captionScope=TablePartScope.fromHeading(row.text).ifBlank {
                TablePartScope.fromHeading(row.text+" "+rows.getOrNull(index+1)?.text.orEmpty()) };h=null }
            else if(Regex("""^\d+(?:\.\d+)+\s+[A-Za-z]""").containsMatchIn(row.text)) {
                section=null;captionScope="";h=null
            }
            val next=header(rows,index,section,captionScope,grid)
            if(next!=null) { h=next;lastY=row.y;continue }
            val table=h ?: continue
            if(table.section==null) continue
            // Stay within this page/table and stop at notes, distant regions or headers.
            if(row.y-lastY>36f || index-table.index>100 ||
                Regex("""(?i)^(?:note:|copyright|product folder|submit document|www\.)""").containsMatchIn(row.text) ||
                row.text.length>80 && Regex("""^(?:\([0-9]+\)|[0-9]+\.)\s+[A-Za-z]""").containsMatchIn(row.text)) {
                h=null;continue
            }
            lastY=row.y
            val values=cells(row,table)
            if(values.dropLast(1).all { it.isBlank() || it=="-" }) continue
            var name=label(row,table);var tests=condition(row,table)
            var rowPartScope="";var testsWithoutPart=""
            val labelCell=grid.cell(table.parameterX,row.y)
            val provedLabel=cellText(labelCell,0f,table.conditionX ?: table.left)
            val provedName=if(table.conditionX==null) provedLabel.substringBefore(',') else provedLabel
            if(meaningful(provedName)) name=provedLabel
            val conditionLeft=table.conditionX ?: labelCell?.right?.takeIf { it<table.left-8f }
            if(table.conditionX==null) {
                val split=Regex(""",\s*(?=[^|]*[=<>≤≥])""").find(name)
                if(split!=null) { tests=name.substring(split.range.first+1).trim();name=name.substring(0,split.range.first).trim() }
            }
            val neighbours=(maxOf(table.index+1,index-2)..minOf(rows.lastIndex,index+2)).filter { n ->
                n!=index && abs(rows[n].y-row.y)<=10f &&
                    detector.detect(listOf(PageText(1,rows[n].text))).isEmpty()
            }
            if(!meaningful(name)) {
                val anchors=neighbours.filter { meaningful(label(rows[it],table)) &&
                    cells(rows[it],table).dropLast(1).all(String::isBlank) }
                // Only a local, uniquely named wrapped label is usable. No carry to another parameter/page.
                val names=anchors.map { label(rows[it],table) }.distinct()
                if(names.size!=1) continue
                name=names.single()
            } else {
                val above=neighbours.filter { it<index && cells(rows[it],table).all(String::isBlank) &&
                    meaningful(label(rows[it],table)) }.maxOrNull()
                if(above!=null && CriterionDictionary.aliases.filterKeys { it !in CriterionDictionary.textCriteria }.values
                    .flatten().none { SearchText.phrase(name,it) }) name=label(rows[above],table)+" "+name
            }
            // A wrapped test condition has no populated result cells of its own.
            val continuations=neighbours.filter { cells(rows[it],table).all(String::isBlank) &&
                label(rows[it],table).isBlank() && condition(rows[it],table).isNotBlank() }
            val region=maxOf(table.index+1,index-3)..minOf(rows.lastIndex,index+3)
            val ambiguous=continuations.filter { n -> region.any { other -> other!=index && other!=n &&
                cells(rows[other],table).dropLast(1).any { Regex("""^[+\-±]?(?:\d|\.\d)""").containsMatchIn(it) } &&
                abs(rows[other].y-rows[n].y)<=abs(row.y-rows[n].y)+0.75f } }
            val clear=continuations-ambiguous.toSet()
            var review=ambiguous.isNotEmpty()
            tests=(clear.filter { it<index }.map { condition(rows[it],table) }+tests+
                clear.filter { it>index }.map { condition(rows[it],table) }).filter(String::isNotBlank).joinToString(" ")
            if(conditionLeft!=null && rules.isNotEmpty()) {
                val conditionCells=rows.subList(maxOf(table.index+1,index-12),minOf(rows.size,index+13))
                    .asSequence().filter { abs(it.y-row.y)<=140f }.flatMap { it.words }
                    .filter { it.center>=conditionLeft-1f && it.center<table.left }
                    .take(250)
                    .mapNotNull { grid.cell(it.center,row.y) }.distinct()
                    .filter { it.left>=conditionLeft-1f && it.right<=table.left+3f && it.height<=140f }
                    .toList()
                if(conditionCells.isNotEmpty()) {
                    val provedConditions=conditionCells.sortedBy { it.left }.map { cellText(it,conditionLeft,table.left) }
                        .filter(String::isNotBlank).distinct()
                    rowPartScope=provedConditions.map(TablePartScope::fromPartCell).filter(String::isNotBlank)
                        .flatMap { it.split(' ') }.distinct().joinToString(" ")
                    testsWithoutPart=(listOf(tests.takeIf { table.conditionX==null }.orEmpty())+
                        provedConditions.filter { TablePartScope.fromPartCell(it).isBlank() })
                        .filter(String::isNotBlank).distinct().joinToString(" ")
                    tests=(listOf(tests.takeIf { table.conditionX==null }.orEmpty())+provedConditions)
                        .filter(String::isNotBlank).distinct().joinToString(" ")
                    review=false
                }
            }
            var unit=values.last()
            if(unit.isBlank()) {
                val proved=cellText(grid.cell(table.columns.last().center,row.y),table.columns.last().center-25f,table.right)
                if(parser.unit(proved)!=null) unit=proved
            }
            if(unit.isBlank()) {
                // Unit-only next/previous baseline belongs to this row only when there is no intervening numeric row.
                val candidates=neighbours.filter { n -> cells(rows[n],table).dropLast(1).all(String::isBlank) &&
                    (label(rows[n],table).isBlank() || label(rows[n],table)==name) &&
                    // A floating unit between two numeric rows must not be assigned to both.
                    neighbours.none { other -> other!=n &&
                        cells(rows[other],table).dropLast(1).any(String::isNotBlank) &&
                        abs(rows[other].y-rows[n].y)<=abs(row.y-rows[n].y) } }
                    .map { cells(rows[it],table).last() }.filter { parser.unit(it)!=null }.distinct()
                if(candidates.size==1) unit=candidates.single()
            }
            if(parser.unit(unit)==null) continue
            for((g,indices) in table.groups.withIndex()) {
                val declared=table.scopes[g]
                val scopedRow=rowPartScope.isNotBlank() && (declared.isBlank() ||
                    rowPartScope.split(' ').all { it in declared.split(' ') })
                val part=if(scopedRow) rowPartScope else declared
                val scopedName=if(part.isBlank()) name else "$part: $name"
                val columns=listOf("Parameter","Test conditions","Part")+(if(review) listOf("Reading quality") else emptyList())+indices.map { table.columns[it].name }+"Units"
                val data=listOf(scopedName,if(scopedRow) testsWithoutPart else tests,part)+(if(review) listOf("review") else emptyList())+indices.map { footnote(values[it]) }+unit
                val safe=data.map { it.replace('|',' ') }
                val line=safe.joinToString(" | ")
                if(parser.parse(line,listOf(columns.joinToString(" | ")))==null) continue
                // Preserve the old compact header for a normal unscoped Min/Typ/Max table.
                val compact=!review && part.isBlank() && indices.map { table.columns[it].name }==listOf("Min","Typ","Max")
                val headingText=table.section?.aliases?.firstOrNull().orEmpty()
                val blockHeader="$headingText\n"+(if(compact) columns.filter { it!="Part" } else columns).joinToString(" | ")
                val repeat=blockHeader!=lastBlockHeader || emittedRows>=8
                val block=(if(repeat) "\n$blockHeader\n" else "")+
                    (if(compact) safe.filterIndexed { i,_ -> i!=2 } else safe).joinToString(" | ")+"\n"
                if(out.length+block.length>8000) return out.toString()
                out.append(block)
                emittedRows=if(repeat) 1 else emittedRows+1;lastBlockHeader=blockHeader
            }
        }
        return out.toString()
    }
}

/** Scope is read from a literal table/section caption, not from filenames or package guesses. */
object TablePartScope {
    fun fromPartCell(text: String): String {
        if(text.length>500) return ""
        val code=Regex("""(?i)(?<![A-Z0-9])[A-Z][A-Z0-9-]{2,30}(?![A-Z0-9])""")
        val found=code.findAll(text).filter { it.value.any(Char::isDigit) && PartNumberNormalizer.isValid(it.value) }.toList()
        if(found.size !in 1..8) return ""
        var rest=text
        found.asReversed().forEach { rest=rest.removeRange(it.range) }
        if(rest.replace(Regex("""(?i)\b(?:and|or)\b|[\s,/]+"""),"").isNotBlank()) return ""
        return found.map { it.value.uppercase(java.util.Locale.ROOT) }.distinct().joinToString(" ")
    }
    fun fromHeading(line: String): String {
        val tail=Regex("""(?i)\b(?:electrical characteristics|electrical specifications|recommended operating conditions)\s*(?::|of\b)\s*(.+)""")
            .find(line)?.groupValues?.get(1) ?: return ""
        return Regex("""(?i)(?<![A-Z0-9])[A-Z][A-Z0-9-]{2,30}(?![A-Z0-9])""").findAll(tail)
            .map { it.value.uppercase(java.util.Locale.ROOT) }.filter { it.any(Char::isDigit) && PartNumberNormalizer.isValid(it) }
            .distinct().take(8).joinToString(" ")
    }
    fun allows(part: String,scope: String,verifiedGrades: Set<String> = emptySet())=scope.isBlank() || part.isNotBlank() &&
        (DocumentPartMatcher().match(part,scope)==com.example.datasheetanalyzer.domain.model.PartMatch.EXACT ||
            verifiedGrades.any { DocumentPartMatcher().match(it,scope)==com.example.datasheetanalyzer.domain.model.PartMatch.EXACT })
}
