package com.example.datasheetanalyzer.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.datasheetanalyzer.domain.extraction.*

@Composable
fun ResultTable(headers: List<String>,rows: List<List<String>>,widths: List<Dp>?=null) {
    val content: @Composable ()->Unit={
        Column(Modifier.fillMaxWidth()) {
            fun rowModifier()=Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            Row(rowModifier().background(MaterialTheme.colorScheme.primaryContainer)) {
                headers.forEachIndexed { i,text -> Text(text,
                    modifier=(if(widths==null) Modifier.weight(1f) else Modifier.width(widths[i])).padding(10.dp),
                    style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold) }
            }
            rows.forEachIndexed { index,row ->
                Row(rowModifier().background(if(index%2==0) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)) {
                    row.forEachIndexed { i,text -> SelectionContainer(
                        modifier=(if(widths==null) Modifier.weight(1f) else Modifier.width(widths[i])).padding(10.dp)) {
                        Text(text,style=MaterialTheme.typography.bodySmall)
                    } }
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if(widths==null) content() else Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Box(Modifier.width(widths.fold(0.dp) { sum,value -> sum+value })) { content() }
    }
}

@Composable
fun ResultSummaryTable(results: List<ParameterResult>) {
    if(results.isEmpty()) return
    Card {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Sonuç özeti",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            ResultTable(listOf("Kriter","Durum","Sonuç"),results.map {
                listOf(it.query.requested,ResultPresentation.status(it.status),ResultPresentation.brief(it))
            },listOf(160.dp,112.dp,170.dp))
            Text("Tabloyu yana kaydırabilirsin. Koşullar ve kaynak sayfaları aşağıdaki ayrıntılarda.",style=MaterialTheme.typography.labelSmall)
        }
    }
}
