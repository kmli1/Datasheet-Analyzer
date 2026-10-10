package com.example.datasheetanalyzer.domain.extraction

import kotlin.math.abs

/** Drawn, axis-aligned borders in the same top-left coordinates as the text layer. */
data class TableRule(val x1: Float,val y1: Float,val x2: Float,val y2: Float)
data class TableCell(val left: Float,val top: Float,val right: Float,val bottom: Float) {
    val height get()=bottom-top
}

/** Text is shared only inside a closed cell, never merely because it is nearby. */
class TableRuleGrid(rules: List<TableRule>) {
    private val horizontal=merge(rules.take(4000).filter { abs(it.y1-it.y2)<0.5f && abs(it.x2-it.x1)>=0.5f }
        .map { TableRule(minOf(it.x1,it.x2),it.y1,maxOf(it.x1,it.x2),it.y1) }
        ,true).filter { it.x2-it.x1>=8f }
    private val vertical=merge(rules.take(4000).filter { abs(it.x1-it.x2)<0.5f && abs(it.y2-it.y1)>=0.5f }
        .map { TableRule(it.x1,minOf(it.y1,it.y2),it.x1,maxOf(it.y1,it.y2)) }
        ,false).filter { it.y2-it.y1>=8f }
    private fun merge(lines: List<TableRule>,horizontal: Boolean): List<TableRule> {
        fun axis(line: TableRule)=if(horizontal) line.y1 else line.x1
        fun low(line: TableRule)=if(horizontal) line.x1 else line.y1
        fun high(line: TableRule)=if(horizontal) line.x2 else line.y2
        val groups=mutableListOf<MutableList<TableRule>>()
        for(line in lines.sortedBy(::axis)) {
            val group=groups.lastOrNull()
            if(group==null || abs(axis(group.first())-axis(line))>0.25f) groups+=mutableListOf(line) else group+=line
        }
        return groups.flatMap { group ->
            val result=mutableListOf<TableRule>()
            for(line in group.sortedBy(::low)) {
                val last=result.lastOrNull()
                if(last!=null && low(line)<=high(last)+0.25f) {
                    result[result.lastIndex]=if(horizontal) last.copy(x2=maxOf(last.x2,line.x2)) else last.copy(y2=maxOf(last.y2,line.y2))
                } else result+=line
            }
            result
        }
    }
    fun cell(x: Float,y: Float): TableCell? {
        if(!x.isFinite() || !y.isFinite()) return null
        var left: TableRule?=null;var right: TableRule?=null
        for(rule in vertical) if(y>rule.y1+0.5f && y<rule.y2-0.5f) {
            if(rule.x1<x-0.5f && (left==null || rule.x1>left.x1)) left=rule
            if(rule.x1>x+0.5f && (right==null || rule.x1<right.x1)) right=rule
        }
        val l=left ?: return null;val r=right ?: return null
        var top: Float?=null;var bottom: Float?=null
        for(rule in horizontal) if(rule.x1<=l.x1+1f && rule.x2>=r.x1-1f) {
            if(rule.y1<y-0.5f && (top==null || rule.y1>top)) top=rule.y1
            if(rule.y1>y+0.5f && (bottom==null || rule.y1<bottom)) bottom=rule.y1
        }
        val t=top ?: return null;val b=bottom ?: return null
        if(l.y1>t+1f || r.y1>t+1f || l.y2<b-1f || r.y2<b-1f) return null
        if(r.x1-l.x1>500f || b-t>140f) return null
        return TableCell(l.x1,t,r.x1,b)
    }
}
