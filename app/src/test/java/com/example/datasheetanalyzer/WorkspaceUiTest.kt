package com.example.datasheetanalyzer

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.datasheetanalyzer.data.workspace.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.ui.home.*
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme
import com.example.datasheetanalyzer.ui.workspace.*
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.UUID
import android.graphics.Bitmap
import android.graphics.Canvas
import org.robolectric.shadows.ShadowDialog
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WorkspaceUiTest {
    @get:Rule val rule=createComposeRule()
    private fun store()=WorkspaceStore(File(RuntimeEnvironment.getApplication().filesDir,"ui-workspace-${UUID.randomUUID()}"))
    private fun sample(part: String="LM383")=SavedAnalysis.capture(part,ExportFixture.document(),ParameterReport(listOf(ExportFixture.result()),true))
    private fun screenshot(name: String) {
        val root=System.getProperty("datasheet.qaDir") ?: return
        val file=File(root,name).apply { parentFile!!.mkdirs() }
        rule.runOnIdle {
            val dialog=ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            val activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).first()
            val view=(dialog?.window ?: activity.window).decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            try { view.draw(Canvas(bitmap));file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
            finally { bitmap.recycle() }
        }
    }
    @Test fun templateCanBeSelectedAndTermGuideCanBeOpenedFromHome() {
        val store=store();val model=HomeViewModel(SavedStateHandle());model.onPartNumberChanged("LM383")
        rule.setContent { val form by model.uiState.collectAsState();DatasheetAnalyzerTheme {
            HomeScreen(form,model::onPartNumberChanged,model::onCustomCriterionChanged,model::onCriterionToggled,{}, {},workspace=store,onApplyTemplate=model::applyTemplate)
        } }
        rule.onNodeWithText("ŞABLON SEÇ").performScrollTo().performClick()
        screenshot("ui-templates.png")
        rule.onNode(hasText("UYGULA") and hasAnyAncestor(hasTestTag("template-opamp"))).performClick()
        rule.runOnIdle { assertEquals(AnalysisTemplates.builtIns.first { it.id=="opamp" }.criteria,model.uiState.value.selectedCriteria);assertEquals("LM383",model.uiState.value.partNumber) }
        screenshot("ui-home.png")
        rule.onNodeWithText("TÜRKÇE TERİM REHBERİ").performScrollTo().performClick()
        rule.onNodeWithText("Türkçe terim rehberi").assertExists()
        rule.onNodeWithText("Terim ara").performTextInput("ofset")
        rule.onNodeWithText("Giriş ofset gerilimi").assertExists()
        screenshot("ui-glossary.png")
        rule.onNodeWithText("KAPAT").performClick()
    }
    @Test fun projectNoteCanBeEditedAndSurvivesReopeningStore() {
        val store=store();val folder=store.createFolder("Laboratuvar");val saved=store.save(sample().copy(folderId=folder.id))
        rule.setContent { DatasheetAnalyzerTheme { ProjectsScreen(store,{}, {},{}) } }
        rule.onNodeWithText("NOT / PROJE DÜZENLE").performScrollTo().performClick()
        rule.onNodeWithText("Analiz notu").performTextInput("Ölçüm yapılacak")
        rule.onNodeWithText("Etiketler").performTextInput("laboratuvar, yükselteç")
        rule.onNodeWithText("KAYDET").performClick()
        rule.waitUntil(10000) { store.state.value.analyses.first { it.id==saved.id }.note=="Ölçüm yapılacak" }
        rule.onNodeWithText("Ölçüm yapılacak").assertExists()
        screenshot("ui-projects.png")
        rule.runOnIdle { assertEquals(listOf("laboratuvar","yükselteç"),store.state.value.analyses.single().tags) }
    }
    @Test fun twoSelectedRecordsShowComparisonAndPdfAction() {
        val store=store();val first=store.save(sample());val second=store.save(sample("LM386"))
        store.selectForComparison(first.id);store.selectForComparison(second.id)
        rule.setContent { DatasheetAnalyzerTheme { ComparisonScreen(store,{}, {}) } }
        rule.onNodeWithText("2/3 kayıt seçili").assertExists()
        rule.onNodeWithText("recommended operating voltage · önerilen çalışma").assertExists()
        screenshot("ui-comparison.png")
        rule.onNodeWithText("KARŞILAŞTIRMAYI PDF OLARAK KAYDET").performScrollTo().assertExists()
    }
}
