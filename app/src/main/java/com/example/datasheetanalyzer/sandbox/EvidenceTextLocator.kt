package com.example.datasheetanalyzer.sandbox

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.SearchText
import java.text.Normalizer
import java.util.Locale
import kotlin.math.*

/** Exact normalized source text only. Each character retains its original glyph coordinate. */
class EvidenceTextLocator {
    private data class Line(val text: String,val glyphs: List<FigureGlyph>,val owners: List<Int>,val section: DatasheetSection?)
    fun locate(glyphs: List<FigureGlyph>,width: Float,height: Float,evidence: String,section: DatasheetSection?=null): List<FigureBounds> {
        require(width.isFinite() && height.isFinite() && width>0 && height>0 && evidence.length<=600)
        val needle=SearchText.normalize(evidence)
        if(needle.length<3) return emptyList()
        val groups=mutableListOf<MutableList<FigureGlyph>>()
        glyphs.take(50_000).filter { it.text.length<=100 && it.x.isFinite() && it.y.isFinite() && it.width.isFinite() && it.height.isFinite() && it.width>=0 && it.height>0 }
            .sortedWith(compareBy<FigureGlyph> { it.y }.thenBy { it.x }).forEach { g ->
                val last=groups.lastOrNull();if(last==null || abs(last.first().y-g.y)>2f) groups+=mutableListOf(g) else last+=g
            }
        var current: DatasheetSection?=null
        val lines=groups.take(1000).map { row ->
            val sorted=row.sortedBy { it.x };val text=StringBuilder();val owners=mutableListOf<Int>();var end: Float?=null
            fun space(index: Int) { if(text.isNotEmpty() && text.last()!=' ') { text.append(' ');owners+=index } }
            sorted.forEachIndexed { index,g ->
                if(end!=null && g.x-end!!>2.5f) space(index)
                val normalized=Normalizer.normalize(g.text,Normalizer.Form.NFKD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace('ı','i')
                normalized.forEach { c -> if(c in 'a'..'z' || c in '0'..'9' || c=='+') { text.append(c);owners+=index } else space(index) }
                end=g.x+g.width
            }
            while(text.endsWith(" ")) { text.setLength(text.length-1);owners.removeAt(owners.lastIndex) }
            DatasheetSectionDetector().detect(listOf(PageText(1,text.toString()))).firstOrNull()?.let { current=it.section }
            Line(text.toString(),sorted,owners,current)
        }
        val pattern=Regex("(?<![a-z0-9])"+Regex.escape(needle)+"(?![a-z0-9])")
        val candidates=mutableListOf<Pair<DatasheetSection?,List<FigureBounds>>>()
        for(i in lines.indices) {
            var combined="";val map=mutableListOf<Pair<Int,Int>>()
            for(j in i..minOf(i+2,lines.lastIndex)) {
                if(j>i && (lines[j].glyphs.first().y-lines[j-1].glyphs.first().y>22f ||
                    lines[j].section!=lines[i].section)) break
                if(combined.isNotEmpty()) { combined+=" ";map+=j to 0 }
                combined+=lines[j].text;map+=lines[j].owners.map { j to it }
                val match=pattern.find(combined) ?: continue
                val selected=match.range.map { map[it] }.distinct().groupBy { it.first }
                val rectangles=selected.mapNotNull { (line,indices) ->
                    val selectedGlyphs=indices.map { lines[line].glyphs[it.second] }
                    val left=((selectedGlyphs.minOf { it.x }-1.5f)/width).coerceIn(0f,1f)
                    val right=((selectedGlyphs.maxOf { it.x+it.width }+1.5f)/width).coerceIn(0f,1f)
                    val top=((selectedGlyphs.minOf { it.y-it.height }-1.5f)/height).coerceIn(0f,1f)
                    val bottom=((selectedGlyphs.maxOf { it.y }+3f)/height).coerceIn(0f,1f)
                    if(right<=left || bottom<=top) null else FigureBounds(left,top,right,bottom)
                }
                if(rectangles.isNotEmpty()) candidates+=lines[i].section to rectangles
                break
            }
        }
        val unique=candidates.distinctBy { it.second }
        val preferred=if(section==null) unique else unique.filter { it.first==section }.ifEmpty { unique.filter { it.first==null } }
        // Repeated text without a unique section match is ambiguous: do not guess a source row.
        return if(preferred.size==1) preferred.single().second.take(8) else emptyList()
    }
}
