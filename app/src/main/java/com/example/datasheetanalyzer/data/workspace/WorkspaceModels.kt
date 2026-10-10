package com.example.datasheetanalyzer.data.workspace

import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.document.AnalyzedDocument
import com.example.datasheetanalyzer.domain.model.*
import java.util.UUID

data class ProjectFolder(val id: String,val name: String)
data class SavedAnalysis(val id: String,val part: String,val createdAt: Long,val sourceLabel: String,val sourceUrl: String,
    val sha256: String,val report: ParameterReport,val folderId: String?=null,val note: String="",val tags: List<String> = emptyList()) {
    companion object {
        fun capture(part: String,document: AnalyzedDocument,report: ParameterReport)=SavedAnalysis(UUID.randomUUID().toString(),
            part,System.currentTimeMillis(),document.candidate.source.label,document.candidate.canonicalUrl,document.checked.pdf.sha256,
            report.copy(graphs=report.graphs.take(3).map { it.copy(explanation=it.explanation.take(500),matches=it.matches.take(2).map { m -> m.copy(titleBounds=null) }) },results=report.results.take(24).map { r ->
                fun bounded(h: ExtractedParameter)=h.copy(rawContext=if(h.numbers==null) h.rawContext.take(4000) else "",evidenceLine=h.evidenceLine.take(500),conditions=h.conditions.take(500),
                    warnings=if(h.numbers==null && h.rawContext.length>4000) h.warnings.take(3).map { it.take(300) }+"Kaynak alıntısı bu kayıtta kısaltıldı; orijinal belgeyi incele."
                        else h.warnings.take(4).map { it.take(300) })
                r.copy(matches=r.matches.take(3).map(::bounded),absoluteLimits=r.absoluteLimits.take(3).map(::bounded),explanation=r.explanation.take(500))
            }))
    }
}
data class WorkspaceState(val folders: List<ProjectFolder> = emptyList(),val analyses: List<SavedAnalysis> = emptyList(),
    val templates: List<AnalysisTemplate> = emptyList(),val loadError: String?=null)
