package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.*
import kotlin.math.abs

/** Frozen 1.0.5 benchmark reader; optional manufacturer-PDF regressions compare its output. */
internal class Baseline105TableReader {
    private data class Word(val text: String,val x: Float,val end: Float) { val center get()=(x+end)/2 }
    private data class Row(val y: Float,val words: List<Word>) { val text get()=words.joinToString(" ") { it.text } }
    private val parser=EngineeringValueParser()
    fun tables(glyphs: List<PositionedGlyph>): String {
        val groups=mutableListOf<MutableList<PositionedGlyph>>()
        for(g in glyphs.sortedWith(compareBy<PositionedGlyph> { it.y }.thenBy { it.x })) {
            val row=groups.lastOrNull()
            if(row==null || abs(row.first().y-g.y)>1.5f) groups+=mutableListOf(g) else row+=g
        }
        val rows=groups.map { group ->
            val words=mutableListOf<Word>();var word="";var x=0f;var end=0f
            for(g in group.sortedBy { it.x }) {
                if(g.text.isBlank()) {
                    if(word.isNotEmpty()) { words+=Word(word,x,end);word="" };continue
                }
                if(word.isNotEmpty() && (g.x-end>3f || g.x<end-2f)) { words+=Word(word,x,end);word="" }
                if(word.isEmpty()) x=g.x
                word+=g.text;end=g.x+g.width
            }
            if(word.isNotEmpty()) words+=Word(word,x,end)
            Row(group.first().y,words)
        }
        val result=StringBuilder()
        var section: DatasheetSection?=null
        var centers: Map<String,Float>?=null
        var headerY=0f
        var conditionX: Float?=null
        for(row in rows) {
            val heading=DatasheetSectionDetector().detect(listOf(PageText(1,row.text))).lastOrNull()?.section
            if(heading!=null) { section=heading;centers=null }
            val columns=row.words.mapNotNull { w ->
                val name=SearchText.normalize(w.text)
                val key=when(name) { "min","minimum" -> "min";"typ","typical" -> "typ";"max","maximum" -> "max";"unit","units" -> "units";else -> null }
                key?.let { it to w.center }
            }.toMap()
            if(columns.keys.containsAll(listOf("min","typ","max","units")) && columns.values.distinct().size==4) {
                centers=columns;headerY=row.y
                conditionX=row.words.firstOrNull { SearchText.normalize(it.text) in setOf("conditions","condition","test") }?.x
                continue
            }
            val header=centers ?: continue
            // A new region or distant continuation needs its own complete column header.
            if(row.y-headerY>240f) { centers=null;continue }
            val sorted=header.entries.sortedBy { it.value }
            if(sorted.zipWithNext().any { it.second.value-it.first.value<10f }) continue
            val firstBoundary=sorted.first().value-(sorted[1].value-sorted.first().value)/2
            val labelWords=row.words.filter { it.center<firstBoundary }
            if(labelWords.none { it.text.any(Char::isLetter) }) continue
            val label=labelWords.filter { conditionX==null || it.x<conditionX!!-3f }.joinToString(" ") { it.text }
            if(label.isBlank()) continue
            val condition=labelWords.filter { conditionX!=null && it.x>=conditionX!!-3f }.joinToString(" ") { it.text }
            val cells=sorted.associate { column ->
                val index=sorted.indexOf(column)
                val low=if(index==0) firstBoundary else (sorted[index-1].value+column.value)/2
                val high=if(index==sorted.lastIndex) column.value+(column.value-sorted[index-1].value)/2 else (column.value+sorted[index+1].value)/2
                column.key to row.words.filter { it.center>=low && it.center<high }.joinToString(" ") { it.text }
            }
            val unit=cells.getValue("units")
            if(parser.unit(unit)==null) continue
            val numeric=listOf("min","typ","max").map { cells.getValue(it) }
            if(numeric.all { it.isBlank() || it=="-" || it=="—" }) continue
            if(numeric.any { it.isNotBlank() && it !in setOf("-","—") && it.replace(',','.').toDoubleOrNull()==null }) continue
            // Label/conditions and blank cells stay in their original columns.
            result.append('\n').append(section?.aliases?.firstOrNull().orEmpty()).append('\n')
            result.append("Parameter | Test conditions | Min | Typ | Max | Units\n")
            result.append(label.replace('|',' ')).append(" | ").append(condition.replace('|',' ')).append(" | ")
                .append(numeric.joinToString(" | ")).append(" | ").append(unit).append('\n')
            if(result.length>=8000) break
        }
        return result.toString().take(8000)
    }
}
