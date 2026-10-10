package com.example.datasheetanalyzer.ui.home

import com.example.datasheetanalyzer.domain.model.Criterion

data class HomeUiState(
    val partNumber: String = "",
    val customCriterion: String = "",
    val selectedCriteria: Set<Criterion> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
    val graphTitles: String = ""
)
