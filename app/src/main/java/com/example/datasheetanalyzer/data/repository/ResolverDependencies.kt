package com.example.datasheetanalyzer.data.repository

import android.content.Context
import com.example.datasheetanalyzer.data.download.DownloadManager
import com.example.datasheetanalyzer.data.network.*
import com.example.datasheetanalyzer.domain.resolver.PdfLinkResolver
import com.example.datasheetanalyzer.domain.security.DocumentSecurityPipeline
import com.example.datasheetanalyzer.sandbox.IsolatedPdfClient
import com.example.datasheetanalyzer.domain.document.DocumentAnalysisPipeline
import com.example.datasheetanalyzer.ocr.DocumentOcr
import java.io.File

object ResolverDependencies {
    @Volatile private var services: LocalServices?=null
    data class LocalServices(val cache: com.example.datasheetanalyzer.data.cache.DocumentCache,
        val recent: com.example.datasheetanalyzer.data.cache.RecentSearches,
        val importer: com.example.datasheetanalyzer.data.download.LocalPdfImporter,
        val temporaryDirectories: List<File>,
        val workspace: com.example.datasheetanalyzer.data.workspace.WorkspaceStore)
    fun local(context: Context): LocalServices = services ?: synchronized(this) {
        services ?: run {
            val app=context.applicationContext
            val quarantine=File(app.cacheDir,"quarantine")
            LocalServices(com.example.datasheetanalyzer.data.cache.DocumentCache(File(app.cacheDir,"verified-documents"),quarantine),
                com.example.datasheetanalyzer.data.cache.RecentSearches(app),
                com.example.datasheetanalyzer.data.download.LocalPdfImporter(app.contentResolver,quarantine),
                listOf(quarantine,File(app.cacheDir,"ocr-pages"),File(app.cacheDir,"export-staging"),File(app.cacheDir,"figure-previews")),
                com.example.datasheetanalyzer.data.workspace.WorkspaceStore(File(app.filesDir,"analysis-workspace")))
        }.also { services=it }
    }
    private val sourceHttp by lazy { SourceHttp(OkHttpSourceTransport()) }
    private val html by lazy { SourceHtmlFetcher(sourceHttp) }
    val repository by lazy {
        DatasheetRepository(PreferredSearchProvider(ManufacturerPdfSearchProvider(sourceHttp),
            KeylessHtmlSearchProvider(OkHttpHtmlFetcher()), alternatives=listOf(
                DatasheetArchiveSearchProvider(html))))
    }
    fun pipeline(context: Context): DocumentAnalysisPipeline {
        val directory=File(context.applicationContext.cacheDir,"quarantine")
        // Only our own generated files, never user documents.
        directory.listFiles()?.filter { System.currentTimeMillis()-it.lastModified()>24*60*60*1000 }?.forEach { it.delete() }
        val client=IsolatedPdfClient(context)
        val security=DocumentSecurityPipeline(PdfLinkResolver(html),DownloadManager(sourceHttp,directory),client)
        val ocr=DocumentOcr()
        return DocumentAnalysisPipeline(security,client,ocr,cache=local(context).cache)
    }
}
