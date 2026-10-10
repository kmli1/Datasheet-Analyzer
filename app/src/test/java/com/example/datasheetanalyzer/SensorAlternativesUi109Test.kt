package com.example.datasheetanalyzer

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.features.sensoralternatives.*
import com.example.datasheetanalyzer.ui.home.HomeViewModel
import com.example.datasheetanalyzer.ui.navigation.DatasheetApp
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w390dp-h840dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SensorAlternativesUi109Test {
    @get:Rule val rule=createComposeRule()
    private fun scrollTo(matcher: SemanticsMatcher) { rule.onNodeWithTag("sensor-screen").performScrollToNode(matcher) }
    private fun compare(code: String) {
        scrollTo(hasTestTag("sensor-candidate-$code"))
        rule.onNode(hasText("YAN YANA KARŞILAŞTIR") and hasAnyAncestor(hasTestTag("sensor-candidate-$code"))).performClick()
    }
    private fun screenshot(name: String) {
        val root=System.getProperty("datasheet.qaDir") ?: return
        rule.runOnIdle {
            val view=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first().window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try { view.draw(Canvas(bitmap));File(root,name).apply { parentFile!!.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
            finally { bitmap.recycle() }
        }
    }
    @Test fun comparisonOpensTheRightManufacturerPageAndEditedCodeClearsOldResults() {
        val opened=mutableListOf<String>()
        rule.setContent { DatasheetAnalyzerTheme { SensorAlternativesScreen("TMP102",{}, {},opened::add) } }
        scrollTo(hasTestTag("sensor-candidate-TMP112"));screenshot("ui-sensors-list.png")
        compare("TMP112")
        rule.onNodeWithText("Sensör karşılaştırma").assertExists()
        screenshot("ui-sensors-compare.png")
        val field=SensorField.PINOUT
        val sensor=SensorAlternatives().profile("TMP112")!!
        val page=sensor.facts.getValue(field).pages.first()
        val cell=hasTestTag("sensor-fact-TMP112-${field.name}")
        scrollTo(cell)
        rule.onNode(hasText("Kaynak s.$page") and hasAnyAncestor(cell)).performClick()
        rule.runOnIdle { assertEquals(listOf(sensor.source.url+"#page=$page"),opened) }
        screenshot("ui-sensors-table.png")
        scrollTo(hasText("← ADAYLAR"));rule.onNodeWithText("← ADAYLAR").performClick()
        scrollTo(hasTestTag("sensor-code"));rule.onNodeWithTag("sensor-code").performTextReplacement("UNKNOWN123")
        rule.onNodeWithText("MUADİL ADAYLARINI BUL").performClick()
        scrollTo(hasText("Bu kod katalogda bulunmuyor"))
        rule.onNodeWithText("Bu kod katalogda bulunmuyor").assertExists()
        rule.onNodeWithTag("sensor-candidate-TMP112").assertDoesNotExist()
        screenshot("ui-sensors-unknown.png")
    }
    @Test fun missingMeasurementCandidateIsExplicitlyMarkedForReview() {
        rule.setContent { DatasheetAnalyzerTheme { SensorAlternativesScreen("BME280",{}, {}) } }
        scrollTo(hasTestTag("sensor-partial-filter"));rule.onNodeWithTag("sensor-partial-filter").performClick()
        compare("BMP280")
        rule.onNodeWithText("İnceleme gerekiyor").assertExists()
        rule.onNodeWithText("• Eksik ölçüm: Nem").assertExists()
        screenshot("ui-sensors-partial.png")
    }
    @Test fun takingCandidateHomeOnlyChangesPartAndDoesNotStartAnalysis() {
        val model=HomeViewModel(SavedStateHandle())
        model.onPartNumberChanged("BMP280");model.onCriterionToggled(Criterion.entries.first())
        model.onCustomCriterionChanged("Supply Voltage");model.onGraphTitlesChanged("Temperature accuracy")
        val before=model.uiState.value
        rule.setContent { DatasheetAnalyzerTheme { DatasheetApp(model) } }
        rule.onNodeWithText("MUADİL SENSÖRLER").performScrollTo().performClick()
        compare("BME280")
        scrollTo(hasText("ADAYI ANA EKRANA AL"));rule.onNodeWithText("ADAYI ANA EKRANA AL").performClick()
        rule.onNodeWithText("MUADİL SENSÖRLER").assertExists()
        rule.runOnIdle {
            val after=model.uiState.value
            assertEquals("BME280",after.partNumber)
            assertEquals(before.selectedCriteria,after.selectedCriteria)
            assertEquals(before.customCriterion,after.customCriterion)
            assertEquals(before.graphTitles,after.graphTitles)
            assertFalse(after.loading)
        }
        rule.onNodeWithText("Kaynaklar").assertDoesNotExist()
    }
    @Test fun catalogueSelectionAndBackDoNotOverwriteTheMainForm() {
        val model=HomeViewModel(SavedStateHandle());model.onPartNumberChanged("UNKNOWN123")
        model.onCustomCriterionChanged("Input Offset Voltage")
        val before=model.uiState.value
        rule.setContent { DatasheetAnalyzerTheme { DatasheetApp(model) } }
        rule.onNodeWithText("MUADİL SENSÖRLER").performScrollTo().performClick()
        rule.onNodeWithText("DESTEKLENEN SENSÖRLER (${SensorCatalog.profiles.size})").performClick()
        rule.onNodeWithTag("sensor-catalogue-query").performTextReplacement("LM35")
        rule.onNodeWithTag("sensor-catalogue-LM35").performClick()
        rule.onNodeWithText("← GERİ").performClick()
        rule.onNodeWithText("MUADİL SENSÖRLER").assertExists()
        rule.runOnIdle { assertEquals(before,model.uiState.value) }
    }
    @Test fun expandedCatalogueSupportsSearchGroupFilterAndNoResults() {
        rule.setContent { DatasheetAnalyzerTheme { SensorAlternativesScreen("",{}, {}) } }
        rule.onNodeWithText("DESTEKLENEN SENSÖRLER (${SensorCatalog.profiles.size})").performClick()
        rule.onNodeWithTag("sensor-catalogue-count").assertTextEquals("160 / 160 sonuç")
        screenshot("ui-sensors-catalogue.png")
        rule.onNodeWithTag("sensor-catalogue-query").performTextReplacement("HDC302")
        rule.onNodeWithTag("sensor-catalogue-count").assertTextEquals("3 / 160 sonuç")
        screenshot("ui-sensors-search.png")
        rule.onNodeWithTag("sensor-catalogue-group").performClick()
        rule.onNodeWithTag("sensor-catalogue-group-DISTANCE").performClick()
        rule.onNodeWithTag("sensor-catalogue-empty").assertExists()
        rule.onNodeWithTag("sensor-catalogue-count").assertTextEquals("0 / 160 sonuç")
        rule.onNodeWithTag("sensor-catalogue-query").performTextClearance()
        rule.onNodeWithTag("sensor-catalogue-count").assertTextEquals("9 / 160 sonuç")
        screenshot("ui-sensors-distance.png")
        rule.onNodeWithTag("sensor-catalogue-list").performScrollToNode(hasTestTag("sensor-catalogue-VL53L4CD"))
        rule.onNodeWithTag("sensor-catalogue-VL53L4CD").performClick()
        rule.onNodeWithTag("sensor-code").assertTextContains("VL53L4CD")
        rule.onNodeWithTag("sensor-catalogue-query").assertDoesNotExist()
    }
    @Test fun expandedComparisonOpensReviewedVoltageEvidence() {
        val opened=mutableListOf<String>()
        rule.setContent { DatasheetAnalyzerTheme { SensorAlternativesScreen("ADXL345",{}, {},opened::add) } }
        compare("LIS2DW12")
        val cell=hasTestTag("sensor-fact-LIS2DW12-SUPPLY")
        scrollTo(cell)
        rule.onNode(hasText("Kaynak s.7") and hasAnyAncestor(cell)).performClick()
        rule.runOnIdle { assertEquals(listOf("https://www.st.com/resource/en/datasheet/lis2dw12.pdf#page=7"),opened) }
        screenshot("ui-sensors-expanded-compare.png")
    }
}
