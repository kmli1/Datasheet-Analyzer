package com.example.datasheetanalyzer

import androidx.lifecycle.SavedStateHandle
import com.example.datasheetanalyzer.data.workspace.*
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.ui.home.HomeViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class WorkspaceFeaturesTest {
    private fun analysis(part: String="LM383",eligible: Boolean=true)=SavedAnalysis.capture(part,ExportFixture.document(eligible),
        ParameterReport(listOf(ExportFixture.result(if(eligible) ParameterStatus.DETECTED else ParameterStatus.DOCUMENT_UNVERIFIED)),eligible))
    private inline fun withStore(block: (WorkspaceStore,File)->Unit) {
        val folder=Files.createTempDirectory("workspace-test-").toFile()
        try { block(WorkspaceStore(folder),folder) } finally { folder.deleteRecursively() }
    }
    @Test fun folderNotesTagsAndNumericColumnsSurviveRestart()=withStore { store,folder ->
        val project=store.createFolder("Güç kaynağı")
        val initial=analysis().copy(folderId=project.id,note="Besleme koşulunu tekrar ölç.",tags=listOf("laboratuvar","regülatör"))
        store.save(initial)
        val loaded=WorkspaceStore(folder).state.value
        assertNull(loaded.loadError);assertEquals(initial,loaded.analyses.single());assertEquals(project,loaded.folders.single())
        val numbers=loaded.analyses.single().report.results.single().matches.single().numbers!!
        assertNull(numbers.value);assertEquals(5.0,numbers.minimum!!,0.0);assertEquals(12.0,numbers.typical!!,0.0);assertEquals(20.0,numbers.maximum!!,0.0)
        store.renameFolder(project.id,"Yeni proje")
        assertEquals("Yeni proje",WorkspaceStore(folder).state.value.folders.single().name)
        store.removeFolder(project.id)
        val retained=WorkspaceStore(folder).state.value.analyses.single()
        assertNull(retained.folderId);assertEquals(initial.note,retained.note);assertEquals(initial.tags,retained.tags)
    }
    @Test fun customTemplatesPersistAndApplyWithoutChangingPartCode()=withStore { store,folder ->
        val criteria=setOf(Criterion.SUPPLY_VOLTAGE,Criterion.OPERATING_TEMPERATURE)
        store.saveTemplate("Sensör kartı",criteria,"recommended operating voltage","Gain vs Frequency")
        val template=WorkspaceStore(folder).state.value.templates.single()
        val handle=SavedStateHandle();val model=HomeViewModel(handle)
        model.onPartNumberChanged("LM383");model.applyTemplate(template)
        assertEquals("LM383",model.uiState.value.partNumber);assertEquals(criteria,model.uiState.value.selectedCriteria)
        assertEquals(template.custom,model.uiState.value.customCriterion);assertEquals(template.graphTitles,model.uiState.value.graphTitles)
        val restored=HomeViewModel(handle).uiState.value
        assertEquals(model.uiState.value,restored)
        store.removeTemplate(template.id);assertTrue(WorkspaceStore(folder).state.value.templates.isEmpty())
    }
    @Test fun invalidMetadataDoesNotReplaceExistingSavedAnalysis()=withStore { store,_ ->
        val initial=analysis();store.save(initial)
        assertTrue(runCatching { store.save(initial.copy(note="x".repeat(2001))) }.isFailure)
        assertTrue(runCatching { store.save(initial.copy(folderId="missing")) }.isFailure)
        assertTrue(runCatching { store.save(initial.copy(tags=List(9) { "tag$it" })) }.isFailure)
        assertEquals(initial,store.state.value.analyses.single())
        store.createFolder("Kart");assertTrue(runCatching { store.createFolder("kart") }.isFailure)
    }
    @Test fun unreadableWorkspaceIsPreservedInsteadOfSilentlyOverwritten()=withStore { _,folder ->
        val file=File(folder,"workspace.json").apply { writeText("{ invalid json }") }
        val store=WorkspaceStore(folder)
        assertNotNull(store.state.value.loadError)
        assertTrue(runCatching { store.createFolder("Yeni") }.isFailure)
        assertEquals("{ invalid json }",file.readText())
        store.clear();assertNull(WorkspaceStore(folder).state.value.loadError)
    }
    @Test fun comparisonRetainsConditionsEmptyColumnsAndSeparateAbsoluteLimits() {
        val first=analysis()
        val r=ExportFixture.result().let { it.copy(matches=listOf(it.matches.single().copy(conditions="TA = 70 °C",numbers=NumericValues(typical=9.0,unit=ExportFixture.unit)) )) }
        val second=analysis("LM386").copy(report=ParameterReport(listOf(r),true))
        val row=AnalysisComparison.rows(listOf(first,second)).single()
        assertTrue(row.conditionNote.contains("farklı"));assertTrue(row.cells[0].contains("Min: 5"))
        assertTrue(row.cells[1].contains("Min: -"));assertTrue(row.cells[1].contains("Tipik: 9"));assertTrue(row.cells[1].contains("Maks: -"))
        assertFalse(row.cells[0].contains("28"));assertTrue(row.cells[0].contains("Ayrı mutlak maksimum"))
        assertTrue(row.cells[0].contains("s.1"));assertTrue(row.cells[1].contains("70 °C"))
    }
    @Test fun unverifiedSnapshotCannotExposeApprovedNumericComparison() {
        val unverified=analysis().copy(part="LM386",report=ParameterReport(listOf(ExportFixture.result()),false))
        val row=AnalysisComparison.rows(listOf(analysis(),unverified)).single()
        assertTrue(row.cells[1].contains("İnceleme gerekli"));assertFalse(row.cells[1].contains("Min:"));assertFalse(row.cells[1].contains("28"))
    }
    @Test fun comparisonDoesNotMergeDifferentSectionsOrQueryModes() {
        val recommended=analysis()
        val absoluteQuery=ExportFixture.result().query.copy(mode=QueryMode.ABSOLUTE_MAXIMUM)
        val absolute=analysis("LM386").copy(report=ParameterReport(listOf(ExportFixture.result().copy(query=absoluteQuery)),true))
        val rows=AnalysisComparison.rows(listOf(recommended,absolute))
        assertEquals(2,rows.size);assertTrue(rows.any { it.title.contains("mutlak sınır") });assertTrue(rows.all { it.cells.any { cell -> cell=="Bu analizde aranmadı" } })
    }
    @Test fun comparisonPreservesOriginalUnitsWithoutInventingRankings() {
        val r=ExportFixture.result().copy(query=ParameterQuery("Supply Current",Criterion.SUPPLY_CURRENT,listOf("supply current")),absoluteLimits=emptyList())
        val ma=EngineeringUnit("mA",UnitDimension.CURRENT,.001)
        val a=EngineeringUnit("A",UnitDimension.CURRENT,1.0)
        val first=analysis().copy(report=ParameterReport(listOf(r.copy(matches=listOf(r.matches.single().copy(numbers=NumericValues(value=50.0,unit=ma))))),true))
        val second=analysis("LM386").copy(report=ParameterReport(listOf(r.copy(matches=listOf(r.matches.single().copy(numbers=NumericValues(value=.05,unit=a))))),true))
        val row=AnalysisComparison.rows(listOf(first,second)).single()
        assertTrue(row.cells[0].contains("50 mA"));assertTrue(row.cells[1].contains("0.05 A"))
    }
    @Test fun comparisonSelectionIsBoundedAndTracksDeletedRecords()=withStore { store,_ ->
        val samples=listOf("LM383","LM386","LM358","LM741").map { store.save(analysis(it)) }
        samples.take(3).forEach { store.selectForComparison(it.id) }
        assertTrue(runCatching { store.selectForComparison(samples[3].id) }.isFailure)
        assertEquals(3,store.comparisonSelection.value.size)
        store.removeAnalysis(samples[0].id);assertEquals(2,store.comparisonSelection.value.size)
        store.selectForComparison(samples[3].id);assertEquals(3,store.comparisonSelection.value.size)
        store.toggleComparison(samples[3].id);assertEquals(2,store.comparisonSelection.value.size)
    }
    @Test fun descriptiveSourceTablesAndGraphReferencesSurviveRestart()=withStore { store,folder ->
        val r=ExportFixture.result().let { it.copy(matches=listOf(it.matches.single().copy(numbers=null,
            rawContext="Recommended Operating Conditions\nSupply voltage 5 to 20 V")),absoluteLimits=emptyList()) }
        val graphs=listOf(FigureResult("Gain vs Frequency",listOf(FigureMatch("Gain vs Frequency",1,FigureBounds(.1f,.1f,.4f,.2f)))))
        val captured=SavedAnalysis.capture("LM383",ExportFixture.document(),ParameterReport(listOf(r),true,graphs))
        store.save(captured)
        val loaded=WorkspaceStore(folder).state.value.analyses.single()
        assertEquals(captured,loaded)
        val row=SourceSectionTableReader().rows(loaded.report.results.single().matches.single(),"LM383").single()
        assertEquals(5.0,row.numbers.minimum!!,0.0);assertEquals(20.0,row.numbers.maximum!!,0.0)
        assertEquals("Gain vs Frequency",loaded.report.graphs.single().requested)
    }
}
