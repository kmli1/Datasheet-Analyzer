package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.DatasheetSection
import com.example.datasheetanalyzer.domain.document.TextOrigin
import com.example.datasheetanalyzer.domain.model.Criterion

enum class QueryMode { STANDARD, ABSOLUTE_MAXIMUM, RECOMMENDED_OPERATING, GRAPH }
data class ParameterQuery(val requested: String,val criterion: Criterion?,val keywords: List<String>,
    val mode: QueryMode=QueryMode.STANDARD)
enum class UnitDimension { VOLTAGE,CURRENT,POWER,RESISTANCE,FREQUENCY,CAPACITANCE,TEMPERATURE,TIME,GAIN,GAIN_RATIO }
data class EngineeringUnit(val symbol: String,val dimension: UnitDimension,val scale: Double)
data class NumericValues(val minimum: Double?=null,val typical: Double?=null,val maximum: Double?=null,
    val value: Double?=null,val unit: EngineeringUnit,val structured: Boolean=false,val symmetric: Boolean=false,
    val nominal: Boolean=false)
enum class ParameterStatus { DETECTED, POSSIBLE, NOT_FOUND, DOCUMENT_UNVERIFIED }
data class ExtractedParameter(val requestedCriterion: String,val parameterName: String,
    val numbers: NumericValues?,val section: DatasheetSection?,val page: Int,val confidence: Int,
    val rawContext: String,val evidenceLine: String,val conditions: String="",
    val origin: TextOrigin=TextOrigin.TEXT_LAYER,val warnings: List<String> = emptyList(),val sourcePartScope: String="")
data class ParameterResult(val query: ParameterQuery,val status: ParameterStatus,
    val matches: List<ExtractedParameter> = emptyList(),val absoluteLimits: List<ExtractedParameter> = emptyList(),
    val explanation: String="")
data class ParameterReport(val results: List<ParameterResult>,val documentEligible: Boolean,
    val graphs: List<com.example.datasheetanalyzer.domain.document.FigureResult> = emptyList())
data class ParameterConfig(val contextLines: Int=4,val maxQueries: Int=24,val maxMatches: Int=3,
    val minimumConfidence: Int=80) {
    init { require(contextLines in 3..5 && maxQueries in 1..24 && maxMatches in 1..3 && minimumConfidence in 60..100) }
}
