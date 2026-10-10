package com.example.datasheetanalyzer.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.datasheetanalyzer.domain.document.AnalyzedDocument
import com.example.datasheetanalyzer.domain.extraction.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConditionsDialog(document: AnalyzedDocument,onPage: (Int)->Unit,onDismiss: ()->Unit) {
    var report by remember(document) { mutableStateOf<ParameterReport?>(null) }
    var supply by rememberSaveable(document.checked.pdf.sha256) { mutableStateOf("") }
    var input by rememberSaveable(document.checked.pdf.sha256) { mutableStateOf("") }
    var current by rememberSaveable(document.checked.pdf.sha256) { mutableStateOf("") }
    var temperature by rememberSaveable(document.checked.pdf.sha256) { mutableStateOf("") }
    var evaluated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(document) { report=withContext(Dispatchers.Default) { OperatingConditions.report(document) } }
    ConditionsForm(report,listOf(supply,input,current,temperature),onValue={ i,s ->
        evaluated=false;when(i) { 0->supply=s;1->input=s;2->current=s;3->temperature=s }
    },evaluated,onEvaluate={ evaluated=true },onPage={ onDismiss();onPage(it) },onDismiss=onDismiss)
}

@Composable
fun ConditionsForm(report: ParameterReport?,values: List<String>,onValue: (Int,String)->Unit,evaluated: Boolean,
    onEvaluate: ()->Unit,onPage: (Int)->Unit,onDismiss: ()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Koşullarımı kontrol et",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));TextButton(onClick=onDismiss) { Text("KAPAT") }
                }
                Text("İlgili alanları doldur. Kontrol önerilen min/maks aralığını kullanır; tipik değer, jonksiyon sıcaklığı ve mutlak maksimum çalışma aralığı sayılmaz.",style=MaterialTheme.typography.bodySmall)
                ConditionField.entries.forEachIndexed { i,f ->
                    OutlinedTextField(values[i],{ onValue(i,it.take(30)) },label={ Text("${f.title} (${f.unit.symbol})") },
                        singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Text),modifier=Modifier.fillMaxWidth())
                }
                if(report==null) LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick=onEvaluate,enabled=report!=null && values.any(String::isNotBlank),modifier=Modifier.fillMaxWidth()) { Text("KOŞULLARI KONTROL ET") }
                if(evaluated && report!=null) ConditionField.entries.forEachIndexed { i,f ->
                    if(values[i].isNotBlank()) {
                        val value=OperatingConditions.parse(values[i])
                        val check=value?.let { OperatingConditions.check(report,f,it) }
                        Card {
                            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text("${f.title}: ${values[i]} ${f.unit.symbol}",style=MaterialTheme.typography.titleMedium)
                                Text(check?.message ?: "Geçerli bir sayı gir; ondalık virgül veya nokta kullanabilirsin.")
                                check?.sources?.take(3)?.forEach { hit ->
                                    hit.numbers?.let { n -> ResultTable(listOf("Min","Tipik","Maks","Değer","Birim"),listOf(ResultPresentation.values(n)),
                                        listOf(75.dp,75.dp,75.dp,75.dp,65.dp)) }
                                    Text(hit.section?.label.orEmpty()+" · s.${hit.page}",style=MaterialTheme.typography.labelSmall)
                                    if(hit.conditions.isNotBlank()) Text("Kaynak koşulları: ${hit.conditions}",style=MaterialTheme.typography.bodySmall)
                                    Text(hit.evidenceLine,style=MaterialTheme.typography.bodySmall)
                                    TextButton(onClick={ onPage(hit.page) }) { Text("KAYNAKTA KONTROL ET") }
                                }
                            }
                        }
                    }
                }
                Text("Bu ekran yalnız girilen alanları sınırlarla karşılaştırır. Isıl tasarım, geçici darbeler ve diğer kaynak koşulları ayrıca incelenmelidir.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
