package com.example.datasheetanalyzer.domain.model

data class CriterionGroup(val label: String,val criteria: List<Criterion>)
object CriterionGroups {
    val groups=listOf(
        CriterionGroup("Gerilim",listOf(Criterion.SUPPLY_VOLTAGE,Criterion.INPUT_VOLTAGE,Criterion.OUTPUT_VOLTAGE,Criterion.INPUT_OFFSET_VOLTAGE)),
        CriterionGroup("Akım ve güç",listOf(Criterion.SUPPLY_CURRENT,Criterion.OUTPUT_CURRENT,Criterion.MAXIMUM_CURRENT,Criterion.POWER_DISSIPATION)),
        CriterionGroup("Sıcaklık ve frekans",listOf(Criterion.OPERATING_TEMPERATURE,Criterion.STORAGE_TEMPERATURE,Criterion.FREQUENCY,Criterion.BANDWIDTH,Criterion.GAIN)),
        CriterionGroup("Belge ve bağlantılar",listOf(Criterion.RECOMMENDED_OPERATING_CONDITIONS,Criterion.ABSOLUTE_MAXIMUMS,
            Criterion.ELECTRICAL_CHARACTERISTICS,Criterion.PINOUT,Criterion.PACKAGE,Criterion.FEATURES,Criterion.APPLICATIONS)))
    val ordered=groups.flatMap { it.criteria }
    fun index(value: Criterion)=ordered.indexOf(value)
}
