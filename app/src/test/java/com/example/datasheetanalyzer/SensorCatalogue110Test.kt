package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.features.sensoralternatives.*
import org.junit.Assert.*
import org.junit.Test

class SensorCatalogue110Test {
    private val engine=SensorAlternatives()
    private fun sensor(code: String)=engine.profile(code)!!

    @Test fun expandedCatalogueHas160DistinctIdentitiesAndNoAliasCollisions() {
        val identities=SensorCatalog.profiles.flatMap { p -> (p.aliases+p.code).map { SensorAlternatives.normalize(it)!! to p.code } }
        assertEquals(160,SensorCatalog.profiles.map { it.code }.toSet().size)
        identities.groupBy { it.first }.forEach { (key,matches) -> assertEquals(key,1,matches.map { it.second }.toSet().size) }
        SensorCatalog.profiles.forEach { assertEquals(it,engine.profile(it.code.lowercase())) }
        assertNull(engine.profile("LIS2DUX12TR"));assertNull(engine.profile("LM50HV"))
    }
    @Test fun catalogueBrowsingFindsManufacturersModelsAliasesAndTurkishLabels() {
        assertEquals(listOf("HDC3020","HDC3021","HDC3022"),SensorCatalogBrowser.filter(" hdc-302 ").map { it.code })
        assertEquals(listOf("SHT31"),SensorCatalogBrowser.filter("SHT31-DIS").map { it.code })
        assertTrue(SensorCatalogBrowser.filter("analog devices").all { it.manufacturer=="Analog Devices" })
        assertEquals(18,SensorCatalogBrowser.filter("Analog Devices").size)
        assertTrue(SensorCatalogBrowser.filter("SICAKLIK").isNotEmpty())
        assertTrue(SensorCatalogBrowser.filter("SICAKLIK").all { SensorMeasurement.TEMPERATURE in it.measurements })
    }
    @Test fun browseResultsHaveStableOrderAndCombinedFiltersKeepOnlyTheirIntersection() {
        val result=SensorCatalogBrowser.filter("st",SensorMeasurement.ANGULAR_RATE)
        assertTrue(result.isNotEmpty());assertTrue(result.all { it.manufacturer=="STMicroelectronics" && SensorMeasurement.ANGULAR_RATE in it.measurements })
        assertEquals(result.sortedBy { it.code },result)
        assertEquals(result,SensorCatalogBrowser.filter("ST",SensorMeasurement.ANGULAR_RATE))
        assertTrue(SensorCatalogBrowser.filter("HDC3020",SensorMeasurement.DISTANCE).isEmpty())
    }
    @Test fun unsupportedAndPunctuationQueriesProduceEmptyResults() {
        listOf("UNKNOWN_SENSOR","!!!","---","<script>").forEach { assertTrue(it,SensorCatalogBrowser.filter(it).isEmpty()) }
        assertEquals(160,SensorCatalogBrowser.filter("  ").size)
        assertTrue(SensorCatalogBrowser.filter("X".repeat(10000)).isEmpty())
    }
    @Test fun catalogueGroupListContainsOnlySupportedMeasurements() {
        assertEquals(SensorCatalog.profiles.flatMap { it.measurements }.toSet(),SensorCatalogBrowser.measurements.toSet())
        SensorCatalogBrowser.measurements.forEach { kind -> assertTrue(SensorCatalogBrowser.filter(measurement=kind).isNotEmpty()) }
    }
    @Test fun magneticSwitchIsNotTreatedAsAContinuousFieldSensor() {
        assertEquals(SensorMeasurement.MAGNETIC_SWITCH,sensor("DRV5032").measurements.single())
        assertTrue(engine.find("DRV5032").alternatives.all { it.sensor.code in setOf("DRV5023","DRV5033") })
        assertFalse(engine.find("TMAG5273",includePartial=true).alternatives.any { it.sensor.code=="DRV5032" })
    }
    @Test fun opticalIrradianceIsNotConfusedWithLuxOrColor() {
        assertEquals(setOf(SensorMeasurement.OPTICAL_IRRADIANCE),sensor("OPT3002").measurements)
        assertFalse(engine.find("OPT3001",includePartial=true).alternatives.any { it.sensor.code=="OPT3002" })
        assertTrue(engine.find("OPT4048").alternatives.isEmpty())
        val partial=engine.find("OPT4048",includePartial=true).alternatives.single { it.sensor.code=="OPT3001" }
        assertEquals(SensorCompatibility.REVIEW,partial.compatibility)
        assertTrue(partial.reasons.any { it.contains("Renk") })
    }
    @Test fun sixAxisImuRequiresItsGyroscopeChannelInDefaultSearch() {
        val result=engine.find("LSM6DSOX")
        assertTrue(result.alternatives.any { it.sensor.code=="LSM6DSO" })
        assertFalse(result.alternatives.any { it.sensor.code=="LIS3DH" || it.sensor.code=="L3GD20" })
        val partial=engine.find("LSM6DSOX",includePartial=true).alternatives.single { it.sensor.code=="LIS3DH" }
        assertEquals(SensorCompatibility.REVIEW,partial.compatibility)
        assertTrue(partial.reasons.any { it.contains("Eksik ölçüm: Açısal hız") })
    }
    @Test fun nineAxisImuDoesNotSilentlyLoseItsMagnetometer() {
        val result=engine.find("LSM9DS1")
        assertEquals(setOf("LSM9DS0"),result.alternatives.map { it.sensor.code }.toSet())
        val partial=engine.find("LSM9DS1",includePartial=true).alternatives.single { it.sensor.code=="LSM6DSOX" }
        assertEquals(SensorCompatibility.REVIEW,partial.compatibility)
        assertTrue(partial.reasons.any { it.contains("Manyetik alan") })
    }
    @Test fun fewerAccelerationAxesNeedExplicitPartialSearchAndReview() {
        assertFalse(engine.find("ADXL345").alternatives.any { it.sensor.code=="IIS2ICLX" })
        val partial=engine.find("ADXL345",includePartial=true).alternatives.single { it.sensor.code=="IIS2ICLX" }
        assertEquals(SensorCompatibility.REVIEW,partial.compatibility)
        assertTrue(partial.reasons.any { it.contains("Eksik / doğrulanmamış eksen") })
        assertTrue(engine.find("IIS2ICLX").alternatives.any { it.sensor.code=="ADXL345" })
    }
    @Test fun oneAxisHallDeviceDoesNotReplaceAThreeAxisMagnetometerByDefault() {
        assertFalse(engine.find("TMAG5273").alternatives.any { it.sensor.code=="DRV5055" })
        val partial=engine.find("TMAG5273",includePartial=true).alternatives.single { it.sensor.code=="DRV5055" }
        assertEquals(SensorCompatibility.REVIEW,partial.compatibility)
        assertTrue(partial.reasons.any { it.contains("3 eksen, aday 1") })
    }
    @Test fun auxiliaryTemperatureChannelsDoNotBecomeStandaloneTemperatureAlternatives() {
        val codes=engine.find("TMP102",includePartial=true).alternatives.map { it.sensor.code }.toSet()
        assertFalse(codes.any { it in setOf("LSM6DSOX","ADXL362","TMAG5170","LPS22HB") })
        assertTrue(codes.contains("ADT7410"));assertTrue(codes.contains("TMP110"))
    }
    @Test fun oneWireAndUartProtocolsAreKeptSeparateFromI2c() {
        val result=engine.find("TMP1826",sameInterface=true)
        assertFalse(result.alternatives.any { it.sensor.code=="TMP104" || it.sensor.code=="TMP102" })
        assertFalse(engine.find("TMP104",sameInterface=true).alternatives.any { it.sensor.code=="TMP102" })
        assertTrue(engine.find("TMP104",sameInterface=true).alternatives.any { it.sensor.code=="TMP144" })
    }
    @Test fun disconnectedHallVoltageBandsDoNotCoverTheGap() {
        val original=sensor("DRV5053").copy(voltage=OperatingVoltage(4.0,4.2))
        val alternative=sensor("DRV5055")
        val result=SensorAlternatives(listOf(original,alternative),emptyList()).find(original.code).alternatives.single()
        assertTrue(result.reasons.any { it.contains("örtüşmüyor") })
        assertEquals(listOf(OperatingVoltage(3.0,3.63),OperatingVoltage(4.5,5.5)),alternative.voltageRanges)
        assertEquals(listOf(OperatingVoltage(3.0,3.6),OperatingVoltage(4.5,5.5)),sensor("DRV5056").voltageRanges)
    }
    @Test fun fullTemperaturePowerScopeAndAnalogGainsRetainModelDifferences() {
        assertEquals(OperatingVoltage(4.5,10.0),sensor("LM50").voltage)
        assertEquals(OperatingVoltage(2.6,3.3),sensor("TMP105").voltage)
        assertEquals(OperatingVoltage(1.8,5.5),sensor("TMP119").voltage)
        assertTrue(sensor("LMT84").facts.getValue(SensorField.INTERFACE).text.contains("−5.5"))
        assertTrue(sensor("TMP236").facts.getValue(SensorField.INTERFACE).text.contains("19.5"))
    }
    @Test fun expandedProfilesNeverGrantDirectReplacementWithoutDirectedProducerProof() {
        SensorCatalog.profiles.forEach { original ->
            engine.find(original.code,includePartial=true).alternatives.filter { it.compatibility==SensorCompatibility.DIRECT }.forEach {
                assertEquals("TMP102",original.code);assertEquals("TMP112",it.sensor.code);assertNotNull(it.proof)
            }
        }
    }
}
