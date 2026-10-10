package com.example.datasheetanalyzer.sandbox

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.SearchText
import kotlin.math.*

data class FigureGlyph(val text: String,val x: Float,val y: Float,val width: Float,val height: Float)
/** Matches source captions using native coordinates; rejects ordinary numeric table rows. */
class NativeFigureLocator {
    private data class Line(val text: String,val left: Float,val top: Float,val right: Float,val bottom: Float,val tableLike: Boolean)
    fun locate(glyphs: List<FigureGlyph>,width: Float,height: Float,page: Int,titles: List<String>): List<LocatedFigure> {
        require(width.isFinite() && height.isFinite() && width>0 && height>0 && titles.size<=3)
        val groups=mutableListOf<MutableList<FigureGlyph>>()
        glyphs.take(50_000).filter { it.text.length<=100 && it.x.isFinite() && it.y.isFinite() && it.width.isFinite() && it.height.isFinite() && it.width>=0 && it.height>0 }
            .sortedWith(compareBy<FigureGlyph> { it.y }.thenBy { it.x }).forEach { glyph ->
                val row=groups.lastOrNull()
                if(row==null || abs(row.first().y-glyph.y)>2f) groups+=mutableListOf(glyph) else row+=glyph
            }
        val numericUnit=Regex("(?i)(?<![a-z])[-+]?\\d+(?:[.,]\\d+)?\\s*(?:m?V|µV|m?A|µA|m?W|°C|k?Hz|MHz|Ω|kΩ|dB)(?![a-z])")
        val lines=groups.flatMap { row ->
            val sorted=row.sortedBy { it.x };val full=join(sorted)
            val table=numericUnit.containsMatchIn(full) && !Regex("(?i)\\b(?:vs|versus)\\b").containsMatchIn(full)
            val segments=mutableListOf<MutableList<FigureGlyph>>()
            sorted.forEach { g ->
                val last=segments.lastOrNull()
                if(last==null || g.x-(last.last().x+last.last().width)>24f) segments+=mutableListOf(g) else last+=g
            }
            segments.mapNotNull { segment ->
                val text=join(segment).trim()
                if(text.length !in 3..220) null else Line(text,segment.minOf { it.x },segment.minOf { it.y-it.height },
                    segment.maxOf { it.x+it.width },segment.maxOf { it.y+2 },table)
            }
        }.take(600)
        val out=mutableListOf<LocatedFigure>()
        for((request,title) in titles.withIndex()) {
            val wanted=FigureQuery.normalize(title)
            if(wanted.length<3) continue
            for((i,line) in lines.withIndex()) {
                if(line.tableLike) continue
                val candidates=mutableListOf(line)
                for(next in lines.drop(i+1).take(8)) {
                    if(next.top-line.bottom>20f) break
                    if(next.tableLike || next.top<line.bottom-3 || abs((next.left+next.right)/2-(line.left+line.right)/2)>max(24f,(line.right-line.left)*.45f)) continue
                    candidates+=Line(line.text+" "+next.text,min(line.left,next.left),line.top,max(line.right,next.right),next.bottom,false)
                }
                val hit=candidates.firstOrNull { SearchText.phrase(FigureQuery.normalize(it.text),wanted) } ?: continue
                val l=(hit.left/width).coerceIn(0f,1f);val r=(hit.right/width).coerceIn(0f,1f)
                val t=(hit.top/height).coerceIn(0f,1f);val b=(hit.bottom/height).coerceIn(0f,1f)
                if(r-l<.005f || b-t<.001f) continue
                out+=LocatedFigure(request,FigureMatch(hit.text.take(180),page,FigureBounds(l,t,r,b)))
                if(out.count { it.requestIndex==request }>=2) break
            }
        }
        return out.distinctBy { Triple(it.requestIndex,it.match.page,it.match.titleBounds) }
    }
    private fun join(glyphs: List<FigureGlyph>): String=buildString {
        var end: Float?=null
        glyphs.sortedBy { it.x }.forEach { g ->
            if(end!=null && g.x-end!!>2.5f && isNotEmpty() && last()!=' ') append(' ')
            append(g.text);end=g.x+g.width
        }
    }
}
