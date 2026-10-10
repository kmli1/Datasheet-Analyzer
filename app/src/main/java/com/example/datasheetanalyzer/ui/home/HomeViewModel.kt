package com.example.datasheetanalyzer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val restoredCriteria =
        savedStateHandle.get<ArrayList<String>>(KEY_CRITERIA).orEmpty()

    private val _uiState = MutableStateFlow(
        HomeUiState(
            partNumber = savedStateHandle[KEY_PART] ?: "",
            customCriterion = savedStateHandle[KEY_CUSTOM] ?: "",
            graphTitles = savedStateHandle[KEY_GRAPHS] ?: "",
            selectedCriteria = Criterion.entries.filter { it.name in restoredCriteria }.toSet()
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // UI callbacks run on the main thread; persist only small form values.
    private fun updateForm(next: HomeUiState) {
        savedStateHandle[KEY_PART] = next.partNumber
        savedStateHandle[KEY_CUSTOM] = next.customCriterion
        savedStateHandle[KEY_GRAPHS] = next.graphTitles
        savedStateHandle[KEY_CRITERIA] = ArrayList(next.selectedCriteria.map { it.name })
        _uiState.value = next
    }

    fun onPartNumberChanged(value: String) {
        updateForm(_uiState.value.copy(partNumber = value.take(64), error = null))
    }

    fun onCustomCriterionChanged(value: String) {
        updateForm(_uiState.value.copy(customCriterion = value.take(500), error = null))
    }
    fun onGraphTitlesChanged(value: String) {
        updateForm(_uiState.value.copy(graphTitles=value.take(500),error=null))
    }

    fun onCriterionToggled(criterion: Criterion) {
        val current = _uiState.value
        val next = if (criterion in current.selectedCriteria) {
            current.selectedCriteria - criterion
        } else {
            current.selectedCriteria + criterion
        }
        updateForm(current.copy(selectedCriteria = next, error = null))
    }

    // Both typed and OCR-confirmed inputs use this same form and normalization.
    fun prepareSearch(): Boolean {
        val current = _uiState.value
        if (current.loading) return false
        val normalizedPart = PartNumberNormalizer.normalize(current.partNumber)

        val error = when {
            current.partNumber.isBlank() -> "Lütfen komponent kodunu gir. Örnek: LM383"
            !PartNumberNormalizer.isValid(normalizedPart) ->
                "Komponent kodunda yalnızca A–Z, 0–9, - ve / kullan."
            current.selectedCriteria.isEmpty() && current.customCriterion.isBlank() && current.graphTitles.isBlank() ->
                "En az bir kriter seç, istediğin bilgiyi veya bir grafik başlığını yaz."
            else -> null
        }

        if (error != null) {
            _uiState.value = current.copy(error = error)
            return false
        }

        updateForm(
            current.copy(
                partNumber = normalizedPart,
                customCriterion = current.customCriterion.trim(),
                graphTitles = current.graphTitles.trim(),
                error = null
            )
        )
        return true
    }

    fun restore(record: com.example.datasheetanalyzer.data.cache.SavedSearch) {
        updateForm(_uiState.value.copy(partNumber=record.part,selectedCriteria=record.criteria,customCriterion=record.custom,graphTitles=record.graphTitles,error=null))
    }
    fun applyTemplate(template: com.example.datasheetanalyzer.domain.model.AnalysisTemplate) {
        updateForm(_uiState.value.copy(selectedCriteria=template.criteria,customCriterion=template.custom,graphTitles=template.graphTitles,error=null))
    }
    fun restoreAnalysis(record: com.example.datasheetanalyzer.data.workspace.SavedAnalysis) {
        val selected=record.report.results.filter { q -> q.query.criterion!=null &&
            q.query.mode==(if(q.query.criterion in setOf(Criterion.MAXIMUM_CURRENT,Criterion.ABSOLUTE_MAXIMUMS))
                com.example.datasheetanalyzer.domain.extraction.QueryMode.ABSOLUTE_MAXIMUM else com.example.datasheetanalyzer.domain.extraction.QueryMode.STANDARD) }.mapNotNull { it.query.criterion }.toSet()
        val custom=record.report.results.filter { it.query.criterion !in selected }.joinToString("; ") { it.query.requested }.take(500)
        updateForm(_uiState.value.copy(partNumber=record.part,selectedCriteria=selected,customCriterion=custom,
            graphTitles=record.report.graphs.joinToString("\n") { it.requested }.take(500),error=null))
    }
    private companion object {
        const val KEY_PART = "part_number"
        const val KEY_CUSTOM = "custom_criterion"
        const val KEY_GRAPHS = "graph_titles"
        const val KEY_CRITERIA = "selected_criteria"
    }
}
