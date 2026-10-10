package com.example.datasheetanalyzer.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.datasheetanalyzer.domain.document.AnalyzedDocument
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.ui.workspace.TermGuideDialog

@Composable
fun ParameterResultCard(result: ParameterResult,onPage: (Int)->Unit,document: AnalyzedDocument?=null,onEvidence: ((ExtractedParameter)->Unit)?=null) {
    var guide by remember { mutableStateOf(false) }
    Card(border=BorderStroke(1.dp,if(result.status==ParameterStatus.DETECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(result.query.requested,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
            Text(ResultPresentation.status(result.status),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelLarge)
            result.query.criterion?.let { TextButton(onClick={ guide=true }) { Text("BU KRİTER NE ANLAMA GELİR?") } }
            if(result.matches.isEmpty() || result.status!=ParameterStatus.DETECTED) Text(result.explanation,style=MaterialTheme.typography.bodySmall)
            result.matches.forEach { ParameterEvidence(it,onPage,document,result.status!=ParameterStatus.DOCUMENT_UNVERIFIED,onEvidence) }
            if(result.absoluteLimits.isNotEmpty()) {
                HorizontalDivider()
                Text("MUTLAK MAKSİMUM SINIRLAR",color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
                Text("Normal çalışma değeri değildir.",style=MaterialTheme.typography.bodySmall)
                result.absoluteLimits.forEach { ParameterEvidence(it,onPage,document,result.status!=ParameterStatus.DOCUMENT_UNVERIFIED,onEvidence) }
            }
        }
    }
    if(guide) TermGuideDialog(result.query.criterion,onDismiss={ guide=false })
}

@Composable
private fun ParameterEvidence(hit: ExtractedParameter,onPage: (Int)->Unit,document: AnalyzedDocument?,eligible: Boolean,onEvidence: ((ExtractedParameter)->Unit)?) {
    var expanded by rememberSaveable(hit.page,hit.evidenceLine) { mutableStateOf(false) }
    val sectionRows=remember(hit,document?.requestedPart,eligible) {
        if(eligible && document?.verification?.automaticEligible==true) SourceSectionTableReader().rows(hit,document.requestedPart) else emptyList()
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        hit.numbers?.takeIf { eligible && document?.verification?.automaticEligible!=false }?.let { n ->
            val fields=listOf("Min" to n.minimum,"Tipik" to n.typical,"Maks" to n.maximum,
                (if(n.nominal) "Nominal" else "Değer") to n.value)
                .filter { it.second!=null }.map { (label,value) -> listOf(label,"${if(n.symmetric && label in setOf("Değer","Nominal")) "±" else ""}${ResultPresentation.number(value)} ${n.unit.symbol}") }
            ResultTable(listOf("Alan","Değer"),fields)
        }
        if(sectionRows.isNotEmpty()) ResultTable(listOf("Parametre","Min","Tipik","Maks","Değer","Birim"),
            sectionRows.map { listOf(it.label)+ResultPresentation.values(it.numbers) },
            listOf(160.dp,72.dp,72.dp,72.dp,78.dp,64.dp))
        ResultTable(listOf("Kaynak bilgisi","Açıklama"),buildList {
            add(listOf("Bölüm",hit.section?.label ?: "Bölüm belirlenemedi"));add(listOf("Sayfa","${hit.page}"))
            if(hit.sourcePartScope.isNotBlank()) add(listOf("Tablo kapsamı",hit.sourcePartScope))
            if(hit.conditions.isNotBlank()) add(listOf("Koşullar",hit.conditions))
            add(listOf("Kanıt",hit.evidenceLine))
            if(hit.numbers==null && sectionRows.isEmpty()) add(listOf("İçerik","Kaynak metni / alıntı"))
        })
        hit.warnings.take(2).forEach { Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick={ if(onEvidence!=null) onEvidence(hit) else onPage(hit.page) },modifier=Modifier.fillMaxWidth()) { Text("KAYNAKTA GÖSTER · s.${hit.page}") }
        TextButton(onClick={ expanded=!expanded }) { Text(if(expanded) "KAYNAK AYRINTILARINI GİZLE" else "KAYNAK METNİ VE KANIT") }
        if(expanded) {
            Text("Kanıt puanı ${hit.confidence}/100 · doğruluk yüzdesi değildir.",style=MaterialTheme.typography.labelSmall)
            SelectionContainer { Text(hit.rawContext,style=MaterialTheme.typography.bodySmall) }
        }
    }
}
