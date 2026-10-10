package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.domain.model.SearchCandidate

fun interface SearchProvider {
    suspend fun searchDatasheets(partNumber: String): List<SearchCandidate>
}
