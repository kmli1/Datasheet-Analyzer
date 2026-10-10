package com.example.datasheetanalyzer.ui.workspace

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.data.workspace.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.export.*
import com.example.datasheetanalyzer.ui.search.ResultTable
import kotlinx.coroutines.*
import java.io.File

@Composable
fun ComparisonScreen(store: WorkspaceStore,onBack: ()->Unit,onNewAnalysis: ()->Unit) {
    val state by store.state.collectAsStateWithLifecycle()
    val selected by store.comparisonSelection.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val analyses=selected.mapNotNull { id -> state.analyses.firstOrNull { it.id==id } }
    val rows=remember(analyses) { if(analyses.size>=2) AnalysisComparison.rows(analyses) else emptyList() }
    Scaffold { insets -> LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Column(Modifier.padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=onBack) { Text("← GERİ") };Text("Komponent karşılaştırma",style=MaterialTheme.typography.headlineLarge)
            Text("Sonuç ekranında analizleri kaydet; burada iki veya üç kayıt seç. Değerlerin yanında bölüm, birim, kaynak sayfası ve test koşulları bulunur.",style=MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick=onNewAnalysis,modifier=Modifier.fillMaxWidth()) { Text("YENİ KOMPONENT ANALİZ ET") }
            Text("${analyses.size}/3 kayıt seçili",style=MaterialTheme.typography.titleSmall)
            if(analyses.size>=2) {
                Text("Tabloyu yana kaydırarak diğer parçaları ve koşulları görebilirsin.",style=MaterialTheme.typography.labelSmall)
                ResultTable(listOf("Kriter")+analyses.map { it.part }+"Koşullar",rows.map { listOf(it.title)+it.cells+it.conditionNote },
                    listOf(150.dp)+analyses.map { 230.dp }+220.dp)
                Text("Tabloyu yana kaydır. Farklı koşullardaki değerlerden otomatik üstünlük veya muadil sonucu çıkarılmaz.",style=MaterialTheme.typography.bodySmall)
                ComparisonExportButton(analyses)
                com.example.datasheetanalyzer.ui.search.CsvExportButton("KARŞILAŞTIRMAYI CSV OLARAK KAYDET","DatasheetAnalyzer-karsilastirma.csv") {
                    AnalysisCsvWriter.analyses(analyses)
                }
            }
            OutlinedTextField(search,{ search=it.take(100) },label={ Text("Kayıt ara") },singleLine=true,modifier=Modifier.fillMaxWidth())
            state.loadError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        } }
        if(state.analyses.isEmpty()) item { Text("Henüz kaydedilmiş analiz yok. Önce iki komponenti analiz edip sonuçlarını kaydet.") }
        items(state.analyses.filter { search.isBlank() || SearchText.normalize(it.part+" "+it.note+" "+it.tags.joinToString(" ")).contains(SearchText.normalize(search)) },key={ it.id }) { a ->
            OutlinedCard { Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Checkbox(checked=a.id in selected,onCheckedChange={ store.toggleComparison(a.id) },enabled=a.id in selected || selected.size<3)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(a.part,style=MaterialTheme.typography.titleMedium)
                    Text((state.folders.firstOrNull { it.id==a.folderId }?.name ?: "Klasörsüz")+" · "+a.sourceLabel,style=MaterialTheme.typography.bodySmall)
                    Text(if(a.report.documentEligible) "Belge kimliği doğrulanmış kayıt" else "İnceleme gerekli",style=MaterialTheme.typography.labelSmall)
                    if(a.note.isNotBlank()) Text(a.note.take(100),style=MaterialTheme.typography.bodySmall)
                }
            } }
        }
        item { Spacer(Modifier.height(16.dp)) }
    } }
}

@Composable
private fun ComparisonExportButton(analyses: List<SavedAnalysis>) {
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) };var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val path=pending;pending=null
        if(path!=null) {
            if(uri==null) ExportFiles.discard(context,path) else scope.launch {
                busy=true
                try { ExportFiles.save(context,path,uri);Toast.makeText(context,"Karşılaştırma PDF olarak kaydedildi.",Toast.LENGTH_LONG).show() }
                catch(c: CancellationException) { throw c }
                catch(_: Exception) { Toast.makeText(context,"PDF kaydedilemedi.",Toast.LENGTH_LONG).show() }
                finally { busy=false }
            }
        }
    }
    OutlinedButton(onClick={ scope.launch {
        busy=true;var staged: File?=null
        try { withContext(Dispatchers.IO) { staged=ExportFiles.stagePdf(context,ComparisonReportContent.build(analyses));ensureActive() }
            pending=staged!!.path;save.launch("DatasheetAnalyzer-karsilastirma.pdf");staged=null
        } catch(c: CancellationException) { throw c }
          catch(_: Exception) { pending=null;Toast.makeText(context,"Karşılaştırma PDF'si hazırlanamadı.",Toast.LENGTH_LONG).show() }
        finally { staged?.delete();busy=false }
    } },enabled=!busy && pending==null,modifier=Modifier.fillMaxWidth()) { Text(if(busy) "PDF HAZIRLANIYOR…" else "KARŞILAŞTIRMAYI PDF OLARAK KAYDET") }
}
