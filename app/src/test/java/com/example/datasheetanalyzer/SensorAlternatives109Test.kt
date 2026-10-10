package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.features.sensoralternatives.*
import org.junit.Assert.*
import org.junit.Test

class SensorAlternatives109Test {
    private val engine=SensorAlternatives()
    private fun sensor(code: String)=engine.profile(code)!!
    private fun candidate(original: String,code: String,partial: Boolean=false)=engine.find(original,partial).alternatives.single { it.sensor.code==code }

    @Test fun catalogueHasCompleteManufacturerEvidenceForEveryComparisonField() {
        assertEquals(160,SensorCatalog.profiles.size)
        assertEquals(154,SensorCatalog.profiles.map { it.source }.distinct().size)
        SensorCatalog.profiles.forEach { profile ->
            assertTrue(profile.code,profile.isUsable())
            SensorField.entries.forEach { field ->
                val fact=profile.facts.getValue(field)
                fact.pages.forEach { page -> assertTrue(SensorLinks.documentPage(profile.source,page)!!.endsWith("#page=$page")) }
            }
        }
    }
    @Test fun onlyExplicitAliasesAndHarmlessSeparatorsAreAccepted() {
        assertEquals("SHT31",engine.profile(" sht31-dis ")?.code)
        assertEquals("LM35D",engine.profile("lm35dz")?.code)
        assertEquals("TMP112",engine.profile("tmp112a")?.code)
        assertEquals("BMP280",engine.profile("b m p-280")?.code)
    }
    @Test fun suffixVariantsAndUnknownCodesDoNotInheritAnotherPartsFacts() {
        listOf("LM35C","TMP112D","TMP112-Q1","TMP117M","BMP280BOARD","MQ135","SHT40-AD1F","SHT31xyz").forEach {
            assertNull(it,engine.find(it).original)
            assertTrue(it,engine.find(it).alternatives.isEmpty())
        }
    }
    @Test fun malformedOrUnboundedInputDoesNotProduceCandidates() {
        listOf("", " ", "A".repeat(65),"SHT31/../", "<TMP102>","SHT31\n", "ŞHT31").forEach {
            assertNull(engine.find(it).original);assertTrue(engine.find(it).alternatives.isEmpty())
        }
    }
    @Test fun ambiguousAliasFailsClosedInsteadOfTakingFirstProfile() {
        val profiles=listOf(sensor("TMP102"),sensor("TMP117").copy(aliases=setOf("TMP102")))
        val result=SensorAlternatives(profiles,emptyList()).find("TMP102")
        assertNull(result.original);assertTrue(result.alternatives.isEmpty())
    }
    @Test fun directedProducerProofHasPriorityAndRetainsItsConditions() {
        val results=engine.find("TMP102").alternatives
        assertEquals("TMP112",results.first().sensor.code)
        assertEquals(SensorCompatibility.DIRECT,results.first().compatibility)
        assertTrue(results.first().proof!!.condition.contains("DRL/SOT563"))
        assertTrue(results.first().reasons.any { it.contains("Q1/D/N/X2SON") })
        assertFalse(results.any { it.sensor.code=="TMP102" })
        assertEquals(results,engine.find("TMP102").alternatives)
    }
    @Test fun aProducerUpgradeStatementDoesNotProveTheReverseReplacement() {
        val reverse=candidate("TMP112","TMP102")
        assertEquals(SensorCompatibility.FUNCTIONAL,reverse.compatibility);assertNull(reverse.proof)
    }
    @Test fun sameInterfaceAndPowerRangeAloneNeverGrantDirectCompatibility() {
        val result=SensorAlternatives(listOf(sensor("TMP102"),sensor("TMP112")),emptyList()).find("TMP102").alternatives.single()
        assertEquals(SensorCompatibility.FUNCTIONAL,result.compatibility);assertNull(result.proof)
    }
    @Test fun analogSensitivityDoesNotHideOutputOffsetOrPowerDifferences() {
        val result=candidate("LM35","TMP36")
        assertEquals(SensorCompatibility.FUNCTIONAL,result.compatibility)
        assertTrue(result.sensor.facts.getValue(SensorField.INTERFACE).text.contains("500 mV"))
        assertTrue(result.reasons.any { it.contains("tüm besleme") })
    }
    @Test fun limitedGradeRetainsItsOwnTemperatureRange() {
        assertNotEquals(sensor("LM35").facts.getValue(SensorField.RANGE),sensor("LM35D").facts.getValue(SensorField.RANGE))
        assertTrue(sensor("LM35D").facts.getValue(SensorField.RANGE).text.contains("100"))
    }
    @Test fun allMeasurementsAreRequiredInDefaultCombinedSensorSearch() {
        val result=engine.find("BME280")
        assertEquals(setOf("BME680","BME688"),result.alternatives.map { it.sensor.code }.toSet())
        assertFalse(result.alternatives.any { it.sensor.code=="BMP280" })
    }
    @Test fun partialMeasurementCandidateNamesTheMissingChannelAndNeedsReview() {
        val result=candidate("BME280","BMP280",partial=true)
        assertEquals(SensorCompatibility.REVIEW,result.compatibility)
        assertTrue(result.reasons.any { it.contains("Eksik ölçüm: Nem") });assertNull(result.proof)
    }
    @Test fun gasChannelIsNotSilentlyDiscarded() {
        assertFalse(engine.find("BME680").alternatives.any { it.sensor.code=="BME280" })
        val result=candidate("BME680","BME280",partial=true)
        assertEquals(SensorCompatibility.REVIEW,result.compatibility)
        assertTrue(result.reasons.any { it.contains("Gaz direnci") })
        assertTrue(sensor("BME688").facts.getValue(SensorField.SCOPE).text.contains("ppm"))
    }
    @Test fun communicationFilterExcludesCandidatesRequiringAnotherInterface() {
        assertTrue(engine.find("LM35").alternatives.any { it.sensor.code=="TMP102" })
        val filtered=engine.find("LM35",sameInterface=true)
        assertFalse(filtered.alternatives.any { it.sensor.code=="TMP102" })
        assertTrue(filtered.alternatives.any { it.sensor.code=="TMP36" })
    }
    @Test fun i2cHumidityDevicesRemainFunctionalAndExposePinAndPowerDifferences() {
        val result=candidate("SHT31","SHT40")
        assertEquals(SensorCompatibility.FUNCTIONAL,result.compatibility)
        assertNotEquals(sensor("SHT31").facts.getValue(SensorField.PINOUT).text,result.sensor.facts.getValue(SensorField.PINOUT).text)
        assertTrue(result.reasons.any { it.contains("tüm besleme") })
    }
    @Test fun disjointPowerRangesExplicitlyRequireANewPowerDesign() {
        val other=sensor("TMP112").copy(voltage=OperatingVoltage(4.0,5.0))
        val result=SensorAlternatives(listOf(sensor("TMP102"),other)).find("TMP102").alternatives.single()
        assertEquals(SensorCompatibility.FUNCTIONAL,result.compatibility)
        assertTrue(result.reasons.any { it.contains("örtüşmüyor") });assertNull(result.proof)
    }
    @Test fun missingFactPreventsCompatibilityApprovalEvenWithProducerProof() {
        val other=sensor("TMP112").copy(facts=sensor("TMP112").facts-SensorField.PINOUT)
        val result=SensorAlternatives(listOf(sensor("TMP102"),other)).find("TMP102").alternatives.single()
        assertEquals(SensorCompatibility.REVIEW,result.compatibility);assertNull(result.proof)
    }
    @Test fun unavailableSourcePagePreventsCompatibilityApproval() {
        val base=sensor("TMP112")
        val other=base.copy(facts=base.facts+(SensorField.PINOUT to SensorFact("Pins",listOf(base.source.pageCount+1))))
        val result=SensorAlternatives(listOf(sensor("TMP102"),other)).find("TMP102").alternatives.single()
        assertEquals(SensorCompatibility.REVIEW,result.compatibility)
    }
    @Test fun untrustedSourceCannotGrantCompatibility() {
        val base=sensor("TMP112");val other=base.copy(source=base.source.copy(url="https://example.com/chip.pdf"))
        val result=SensorAlternatives(listOf(sensor("TMP102"),other)).find("TMP102").alternatives.single()
        assertEquals(SensorCompatibility.REVIEW,result.compatibility);assertNull(result.proof)
    }
    @Test fun untrustedProofIsIgnoredEvenWhenBothDatasheetsAreComplete() {
        val proof=SensorCatalog.proofs.single().copy(url="https://www.ti.com.evil.example/proof")
        val result=SensorAlternatives(listOf(sensor("TMP102"),sensor("TMP112")),listOf(proof)).find("TMP102").alternatives.single()
        assertEquals(SensorCompatibility.FUNCTIONAL,result.compatibility);assertNull(result.proof)
    }
    @Test fun conflictingProofsDoNotPickOneAsVerified() {
        val proof=SensorCatalog.proofs.single()
        val result=SensorAlternatives(listOf(sensor("TMP102"),sensor("TMP112")),listOf(proof,proof.copy(condition="Other condition"))).find("TMP102").alternatives.single()
        assertEquals(SensorCompatibility.FUNCTIONAL,result.compatibility)
    }
    @Test fun sourceLinksRejectUntrustedOrAmbiguousDestinations() {
        listOf("http://www.ti.com/chip.pdf","https://www.ti.com.evil.com/chip.pdf","https://user@www.ti.com/chip.pdf", "https://www.ti.com:8443/chip.pdf", "https://www.ti.com/a/../chip.pdf","https://www.ti.com/a%2fchip.pdf","javascript:alert(1)","https://www.ti.com/chip.pdf#page=1").forEach { assertFalse(it,SensorLinks.isManufacturerUrl(it)) }
    }
    @Test fun invalidSourcePagesAndNonPdfLinksCannotOpenAsEvidence() {
        val source=sensor("TMP102").source
        assertNull(SensorLinks.documentPage(source,0));assertNull(SensorLinks.documentPage(source,source.pageCount+1))
        assertNull(SensorLinks.documentPage(source.copy(url="https://www.ti.com/product/TMP102"),1))
        assertNull(SensorLinks.documentPage(source.copy(sha256="invalid"),1))
    }
    @Test fun invalidVoltageBoundsAreRejected() {
        listOf(0.0 to 1.0,-1.0 to 2.0,3.0 to 2.0,Double.NaN to 3.0,1.0 to Double.POSITIVE_INFINITY).forEach { (a,b) ->
            assertThrows(IllegalArgumentException::class.java) { OperatingVoltage(a,b) }
        }
    }
    @Test fun sharedSingleVoltagePointIsNotMistakenForFullRangeCoverage() {
        val a=OperatingVoltage(1.4,3.6);val b=OperatingVoltage(3.6,5.5)
        assertEquals(OperatingVoltage(3.6,3.6),a.overlap(b));assertFalse(b.covers(a))
        assertNull(a.overlap(OperatingVoltage(4.0,5.0)))
    }
}
