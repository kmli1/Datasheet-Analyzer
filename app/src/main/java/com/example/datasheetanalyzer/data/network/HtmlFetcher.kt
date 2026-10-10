package com.example.datasheetanalyzer.data.network

interface HtmlFetcher {
    suspend fun fetch(url: String): String
}
