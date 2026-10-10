package com.example.datasheetanalyzer.ui.search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.datasheetanalyzer.data.repository.*
import com.example.datasheetanalyzer.data.cache.DocumentCache
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SearchUiState(val loading: Boolean=false,val selection: CandidateSelection?=null,val error: String?=null,
    val selectedUrl: String?=null,val checkProgress: CheckProgress?=null,val checked: CheckedDocument?=null,
    val analysis: AnalyzedDocument?=null,val report: ParameterReport?=null,val checkError: String?=null,val graphLoading: Boolean=false)

class SearchViewModel(private val repository: DatasheetRepository,private val pipeline: DocumentAnalysisPipeline,
    private val local: ResolverDependencies.LocalServices): ViewModel() {
    private val flow=MutableStateFlow(SearchUiState());val state=flow.asStateFlow()
    private var part="";private var queries: List<ParameterQuery> = emptyList()
    private var searchJob: Job?=null;private var checkJob: Job?=null
    private var graphJob: Job?=null
    private var generation=0L;private var forceRefresh=false
    fun start(requestedPart: String,requestedQueries: List<ParameterQuery> = queries,retry: Boolean=false) {
        if(flow.value.loading) return
        val changed=queries!=requestedQueries;queries=requestedQueries
        if(part==requestedPart && !retry) {
            if(changed) flow.value.analysis?.let { document -> viewModelScope.launch(Dispatchers.Default) {
                val report=ParameterExtractionEngine().extract(document,queries)
                flow.update { if(it.analysis===document) it.copy(report=report) else it }
                startGraphs(document)
            } };return
        }
        searchJob?.cancel();clearCheck();part=requestedPart;forceRefresh=retry
        val active=++generation;flow.value=SearchUiState(loading=true)
        searchJob=viewModelScope.launch {
            var owned: AnalyzedDocument?=null;var published=false
            try {
                if(!retry) {
                    val cached=withContext(Dispatchers.IO) { local.cache.load(part).also { owned=it } }
                    if(cached!=null) {
                        val report=withContext(Dispatchers.Default) { ParameterExtractionEngine().extract(cached,queries) }
                        ensureActive();if(active!=generation) return@launch
                        flow.value=SearchUiState(selection=CandidateSelection(listOf(cached.candidate),cached.candidate.canonicalUrl,0),
                            selectedUrl=cached.candidate.canonicalUrl,checked=cached.checked,analysis=cached,report=report)
                        published=true;startGraphs(cached);return@launch
                    }
                }
                val selection=withTimeout(20_000) { repository.search(part) }
                if(active==generation) flow.value=SearchUiState(selection=selection)
            } catch(_: TimeoutCancellationException) { if(active==generation) flow.value=SearchUiState(error="Arama zaman aşımına uğradı. Yeniden dene veya telefondan PDF seç.") }
              catch(c: CancellationException) { throw c }
              catch(e: SearchFailure) { if(active==generation) flow.value=SearchUiState(error=e.message) }
              catch(_: Exception) { if(active==generation) flow.value=SearchUiState(error="Kaynaklara ulaşılamadı. Bağlantını kontrol et veya telefondan PDF seç.") }
            finally { if(!published) owned?.checked?.pdf?.file?.delete() }
        }
    }
    fun select(url: String) {
        if(checkJob?.isActive==true) return
        val candidates=flow.value.selection?.candidates ?: return
        val candidate=candidates.firstOrNull { it.canonicalUrl==url && it.selectable } ?: return
        if(candidate.source.level==TrustLevel.LOCAL) {
            launchCheck(url) { progress -> local.cache.load(part,url) ?: throw SecurityFailure("CACHE_MISSING","Yerel PDF kaydı bulunamadı; dosyayı yeniden seç.") }
        } else launchCheck(url) { progress -> pipeline.analyze(part,candidate,candidates,progress,forceRefresh=forceRefresh) }
    }
    fun importPdf(uri: Uri) {
        if(checkJob?.isActive==true) return
        searchJob?.cancel()
        launchCheck(null) { progress ->
            var imported: QuarantinedPdf?=null;var handed=false
            try {
                imported=local.importer.import(uri)
                val candidate=DocumentCache.localCandidate(part,imported.finalUrl)
                val result=pipeline.analyze(part,candidate,listOf(candidate),progress,localPdf=imported)
                handed=true;result
            } finally { if(!handed) imported?.file?.delete() }
        }
    }
    private fun launchCheck(url: String?,operation: suspend ((CheckProgress)->Unit)->AnalyzedDocument) {
        clearCheck();val active=++generation
        flow.value=flow.value.copy(loading=false,selectedUrl=url,checkProgress=CheckProgress(CheckPhase.VERIFYING_SOURCE),checkError=null)
        checkJob=viewModelScope.launch {
            var owned: AnalyzedDocument?=null;var published=false
            try {
                val result=withContext(Dispatchers.IO) { operation { progress ->
                    flow.update { if(active==generation) it.copy(checkProgress=progress) else it }
                }.also { owned=it } }
                val report=withContext(Dispatchers.Default) { ParameterExtractionEngine().extract(result,queries) }
                ensureActive();if(active!=generation) return@launch
                flow.value=flow.value.copy(checkProgress=null,checked=result.checked,analysis=result,report=report,selectedUrl=result.candidate.canonicalUrl)
                published=true;forceRefresh=false;startGraphs(result)
            } catch(_: TimeoutCancellationException) { if(active==generation) flow.value=flow.value.copy(checkProgress=null,checkError="Belge işlemi zaman aşımına uğradı. Farklı kaynak veya yerel PDF deneyebilirsin.") }
              catch(c: CancellationException) { throw c }
              catch(e: SecurityFailure) { if(active==generation) flow.value=flow.value.copy(checkProgress=null,checkError=e.message) }
              catch(e: SearchFailure) { if(active==generation) flow.value=flow.value.copy(checkProgress=null,checkError=e.message) }
              catch(_: Exception) { if(active==generation) flow.value=flow.value.copy(checkProgress=null,checkError="Belge işlenemedi. Başka bir kaynak veya PDF seçebilirsin.") }
            finally { if(!published) owned?.checked?.pdf?.file?.delete() }
        }
    }
    private fun startGraphs(document: AnalyzedDocument) {
        graphJob?.cancel()
        val graphQueries=queries.filter { it.mode==QueryMode.GRAPH }.toList()
        if(graphQueries.isEmpty()) { flow.update { it.copy(graphLoading=false) };return }
        val active=generation
        flow.update { if(it.analysis===document) it.copy(graphLoading=true) else it }
        graphJob=viewModelScope.launch {
            try {
                val figures=pipeline.figures(document,graphQueries)
                ensureActive()
                flow.update { if(active==generation && it.analysis===document)
                    it.copy(report=it.report?.copy(graphs=figures),graphLoading=false) else it }
            } catch(c: CancellationException) { throw c }
              catch(_: Exception) { flow.update { if(active==generation && it.analysis===document)
                  it.copy(report=it.report?.copy(graphs=graphQueries.map { q -> FigureResult(q.requested,explanation="Grafik araması tamamlanamadı; kaynak sayfasını incele.") }),graphLoading=false) else it } }
        }
    }
    fun cancelCheck() { generation++;checkJob?.cancel();flow.value=flow.value.copy(checkProgress=null,checkError="Belge işlemi iptal edildi.") }
    private fun clearCheck() { generation++;graphJob?.cancel();checkJob?.cancel();flow.value.checked?.pdf?.file?.delete();flow.value=flow.value.copy(checked=null,analysis=null,report=null,checkProgress=null,graphLoading=false) }
    fun cancel() { searchJob?.cancel();clearCheck();flow.value=flow.value.copy(loading=false) }
    override fun onCleared() { cancel();pipeline.close();super.onCleared() }
    class Factory(private val repository: DatasheetRepository,private val pipelineProvider: () -> DocumentAnalysisPipeline,
        private val local: ResolverDependencies.LocalServices): ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SearchViewModel::class.java));return SearchViewModel(repository,pipelineProvider(),local) as T
        }
    }
}
