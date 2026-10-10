package com.example.datasheetanalyzer.features.sensoralternatives

import java.text.Normalizer
import java.util.Locale

/** Local browsing only; the strict, exact sensor identity lookup is unchanged. */
object SensorCatalogBrowser {
    private fun searchable(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ı','i'),Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"),"").replace(Regex("[^a-z0-9]"),"")

    fun filter(query: String="",measurement: SensorMeasurement?=null): List<SensorProfile> {
        val text=query.trim().take(64)
        val needle=searchable(text)
        if(text.isNotEmpty() && needle.isEmpty()) return emptyList()
        return SensorCatalog.profiles.asSequence().filter { measurement==null || measurement in it.measurements }
            .filter { sensor -> needle.isEmpty() || (sensor.aliases+sensor.code+sensor.manufacturer+sensor.measurementLabel)
                .any { needle in searchable(it) } }
            .sortedBy { it.code }.toList()
    }

    val measurements: List<SensorMeasurement> get()=SensorMeasurement.entries.filter { kind -> SensorCatalog.profiles.any { kind in it.measurements } }
}
