package com.example.datasheetanalyzer.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.data.workspace.WorkspaceStore
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.ui.home.HomeUiState
import kotlinx.coroutines.*

@Composable
fun TemplateControls(form: HomeUiState,store: WorkspaceStore,onApply: (AnalysisTemplate)->Unit) {
    val workspace by store.state.collectAsStateWithLifecycle()
    var manage by remember { mutableStateOf(false) };var naming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) };val scope=rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={ manage=true;error=null },modifier=Modifier.weight(1f)) { Text("ŞABLON SEÇ") }
        TextButton(onClick={ name="";error=null;naming=true },modifier=Modifier.weight(1f),
            enabled=form.selectedCriteria.isNotEmpty() || form.customCriterion.isNotBlank() || form.graphTitles.isNotBlank()) { Text("ŞABLON KAYDET") }
    }
    if(manage) AlertDialog(onDismissRequest={ if(!busy) manage=false },title={ Text("Analiz şablonları") },text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Şablon kriterleri ve grafik başlıklarını doldurur. Parça kodunu değiştirmez.",style=MaterialTheme.typography.bodySmall)
            workspace.loadError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            (AnalysisTemplates.builtIns+workspace.templates).forEach { template ->
                Card(Modifier.fillMaxWidth().testTag("template-${template.id}")) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(template.name,style=MaterialTheme.typography.titleSmall)
                    Text("${template.criteria.size} kriter"+if(template.custom.isNotBlank()) " + özel aramalar" else "",style=MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(onClick={ onApply(template);manage=false },enabled=!busy) { Text("UYGULA") }
                        if(!template.builtIn) TextButton(onClick={ scope.launch {
                            busy=true
                            try { withContext(Dispatchers.IO) { store.removeTemplate(template.id) } }
                            catch(_: Exception) { error="Şablon silinemedi." }
                            finally { busy=false }
                        } },enabled=!busy) { Text("SİL") }
                    }
                } }
            }
        }
    },confirmButton={ TextButton(onClick={ manage=false },enabled=!busy) { Text("KAPAT") } })
    if(naming) AlertDialog(onDismissRequest={ if(!busy) naming=false },title={ Text("Kendi şablonunu kaydet") },text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{ name=it.take(60) },label={ Text("Şablon adı") },singleLine=true,enabled=!busy)
            Text("Seçili kriterler, özel aramalar ve grafik başlıkları saklanır.",style=MaterialTheme.typography.bodySmall)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={ TextButton(onClick={ scope.launch {
        busy=true
        try { withContext(Dispatchers.IO) { store.saveTemplate(name,form.selectedCriteria,form.customCriterion,form.graphTitles) };naming=false }
        catch(e: IllegalArgumentException) { error=e.message }
        catch(_: Exception) { error="Şablon kaydedilemedi." }
        finally { busy=false }
    } },enabled=!busy && name.isNotBlank()) { Text("KAYDET") } },dismissButton={ TextButton(onClick={ naming=false },enabled=!busy) { Text("VAZGEÇ") } })
}
