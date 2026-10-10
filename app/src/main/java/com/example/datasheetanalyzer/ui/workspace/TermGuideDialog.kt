package com.example.datasheetanalyzer.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.datasheetanalyzer.domain.extraction.SearchText
import com.example.datasheetanalyzer.domain.model.*

@Composable
fun TermGuideDialog(criterion: Criterion?=null,onDismiss: ()->Unit) {
    var search by remember { mutableStateOf("") }
    val terms=remember(criterion) { (criterion?.let { listOf(ParameterGlossary.criterion(it)) } ?: Criterion.entries.map(ParameterGlossary::criterion))+ParameterGlossary.tableTerms }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Türkçe terim rehberi") },text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(criterion==null) OutlinedTextField(search,{ search=it.take(100) },label={ Text("Terim ara") },singleLine=true,modifier=Modifier.fillMaxWidth())
            terms.filter { search.isBlank() || SearchText.normalize(it.title+" "+it.definition).contains(SearchText.normalize(search)) }.forEach { term ->
                Text(term.title,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
                Text(term.definition,style=MaterialTheme.typography.bodySmall)
                Text("Örnek: ${term.example}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
            }
            Text("Bu açıklamalar eğitim içindir. Parçaya ait değer ve koşullar için datasheet'i kullan.",style=MaterialTheme.typography.labelSmall)
        }
    },confirmButton={ TextButton(onClick=onDismiss) { Text("KAPAT") } })
}
