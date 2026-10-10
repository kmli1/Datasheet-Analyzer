package com.example.datasheetanalyzer.data.repository

import com.example.datasheetanalyzer.domain.model.CandidateSelection
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.resolver.SearchProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatasheetRepository(
    private val provider: SearchProvider,
    private val selector: CandidateSelector = CandidateSelector()
) {
    suspend fun search(partNumber: String): CandidateSelection = withContext(Dispatchers.IO) {
        selector.select(partNumber, provider.searchDatasheets(partNumber))
    }
}
