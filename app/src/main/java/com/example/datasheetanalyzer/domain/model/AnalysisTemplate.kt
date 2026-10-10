package com.example.datasheetanalyzer.domain.model

data class AnalysisTemplate(val id: String,val name: String,val criteria: Set<Criterion>,val custom: String="",val graphTitles: String="",val builtIn: Boolean=false)
object AnalysisTemplates {
    val builtIns=listOf(
        AnalysisTemplate("general","Genel inceleme",setOf(Criterion.SUPPLY_VOLTAGE,Criterion.SUPPLY_CURRENT,Criterion.OPERATING_TEMPERATURE,Criterion.RECOMMENDED_OPERATING_CONDITIONS,Criterion.ABSOLUTE_MAXIMUMS,Criterion.PINOUT,Criterion.PACKAGE),builtIn=true),
        AnalysisTemplate("opamp","Op-amp",setOf(Criterion.SUPPLY_VOLTAGE,Criterion.SUPPLY_CURRENT,Criterion.INPUT_OFFSET_VOLTAGE,Criterion.GAIN,Criterion.BANDWIDTH,Criterion.OPERATING_TEMPERATURE,Criterion.PINOUT,Criterion.RECOMMENDED_OPERATING_CONDITIONS),builtIn=true),
        AnalysisTemplate("regulator","Gerilim regülatörü",setOf(Criterion.INPUT_VOLTAGE,Criterion.OUTPUT_VOLTAGE,Criterion.OUTPUT_CURRENT,Criterion.SUPPLY_CURRENT,Criterion.POWER_DISSIPATION,Criterion.OPERATING_TEMPERATURE,Criterion.PINOUT,Criterion.RECOMMENDED_OPERATING_CONDITIONS),"dropout voltage",builtIn=true),
        AnalysisTemplate("mosfet","MOSFET",setOf(Criterion.MAXIMUM_CURRENT,Criterion.POWER_DISSIPATION,Criterion.OPERATING_TEMPERATURE,Criterion.ABSOLUTE_MAXIMUMS,Criterion.PINOUT,Criterion.PACKAGE),"drain source voltage; gate source voltage; on resistance",builtIn=true)
    )
}
