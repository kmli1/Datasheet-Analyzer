package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.extraction.SearchText

data class FigureBounds(val left: Float,val top: Float,val right: Float,val bottom: Float) {
    init { require(listOf(left,top,right,bottom).all { it.isFinite() } && left>=0 && top>=0 && right<=1 && bottom<=1 && left<right && top<bottom) }
}
data class FigureMatch(val title: String,val page: Int,val titleBounds: FigureBounds?=null)
data class FigureResult(val requested: String,val matches: List<FigureMatch> = emptyList(),val explanation: String="")
data class LocatedFigure(val requestIndex: Int,val match: FigureMatch)
object FigureQuery {
    fun clean(value: String)=value.trim().replace(Regex("(?i)^\\s*(?:grafik|graph|chart|curve)\\s*[:=-]?\\s*"),"").trim().take(180)
    fun normalize(value: String)=SearchText.normalize(clean(value)).replace(Regex("\\bversus\\b"),"vs")
    fun explicit(value: String)=Regex("(?i)^\\s*(?:grafik|graph|chart|curve)\\b|\\b(?:vs|versus)\\b").containsMatchIn(value)
}
