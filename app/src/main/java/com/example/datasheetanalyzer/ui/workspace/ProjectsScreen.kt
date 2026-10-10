package com.example.datasheetanalyzer.ui.workspace

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.data.workspace.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.ui.search.ResultTable
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProjectsScreen(store: WorkspaceStore,onBack: ()->Unit,onOpen: (SavedAnalysis)->Unit,onCompare: ()->Unit) {
    val state by store.state.collectAsStateWithLifecycle();val scope=rememberCoroutineScope()
    var folder by rememberSaveable { mutableStateOf("") };var search by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<SavedAnalysis?>(null) };var details by remember { mutableStateOf<SavedAnalysis?>(null) }
    var deleting by remember { mutableStateOf<SavedAnalysis?>(null) }
    var naming by remember { mutableStateOf(false) };var renameId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") };var removeProject by remember { mutableStateOf<ProjectFolder?>(null) }
    var error by remember { mutableStateOf<String?>(null) };var busy by remember { mutableStateOf(false) }
    fun mutate(operation: ()->Unit) { scope.launch { busy=true;error=null;try { withContext(Dispatchers.IO) { operation() } }
        catch(e: IllegalArgumentException) { error=e.message } catch(_: Exception) { error="Kayıt değiştirilemedi." } finally { busy=false } } }
    val visible=state.analyses.filter { a -> (folder=="" || if(folder=="_") a.folderId==null else a.folderId==folder) &&
        (search.isBlank() || SearchText.normalize(a.part+" "+a.note+" "+a.tags.joinToString(" ")).contains(SearchText.normalize(search))) }
    Scaffold { insets -> LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Column(Modifier.padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=onBack) { Text("← GERİ") };Text("Projelerim",style=MaterialTheme.typography.headlineLarge)
            Text("Kaydedilen sonuçlar, notlar ve etiketler bu cihazda kalır. Kaynak belgeyi açmak için yeniden analiz yapabilirsin.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(search,{ search=it.take(100) },label={ Text("Parça, not veya etiket ara") },singleLine=true,modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={ name="";renameId=null;naming=true;error=null },enabled=!busy) { Text("+ YENİ PROJE") }
                TextButton(onClick=onCompare) { Text("KARŞILAŞTIR") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(folder=="",{ folder="" },label={ Text("Tümü") });FilterChip(folder=="_",{ folder="_" },label={ Text("Klasörsüz") })
                state.folders.forEach { p -> FilterChip(folder==p.id,{ folder=p.id },label={ Text(p.name) }) }
            }
            state.folders.firstOrNull { it.id==folder }?.let { p -> Row {
                TextButton(onClick={ name=p.name;renameId=p.id;naming=true;error=null },enabled=!busy) { Text("PROJE ADINI DEĞİŞTİR") }
                TextButton(onClick={ removeProject=p },enabled=!busy) { Text("PROJEYİ KALDIR") }
            } }
            state.loadError?.let { Text(it,color=MaterialTheme.colorScheme.error) };error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        } }
        if(visible.isEmpty()) item { Text("Bu görünümde analiz yok. Sonuç ekranında PROJEYE KAYDET ile bir kayıt oluştur.") }
        items(visible,key={ it.id }) { a -> Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(a.part,style=MaterialTheme.typography.titleLarge)
            Text((state.folders.firstOrNull { it.id==a.folderId }?.name ?: "Klasörsüz")+" · "+
                SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr")).format(Date(a.createdAt)),style=MaterialTheme.typography.bodySmall)
            Text(a.sourceLabel+" · "+if(a.report.documentEligible) "Belge kimliği doğrulanmış kayıt" else "İnceleme gerekli kayıt",style=MaterialTheme.typography.bodySmall)
            if(a.note.isNotBlank()) Text(a.note.take(240),style=MaterialTheme.typography.bodySmall)
            if(a.tags.isNotEmpty()) Text(a.tags.joinToString(" · ") { "#$it" },style=MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick={ details=a },modifier=Modifier.fillMaxWidth()) { Text("KAYITLI SONUÇLARI GÖSTER") }
            Row { TextButton(onClick={ editor=a },enabled=!busy) { Text("NOT / PROJE DÜZENLE") };TextButton(onClick={ deleting=a },enabled=!busy) { Text("SİL") } }
        } } }
        item { Spacer(Modifier.height(16.dp)) }
    } }
    editor?.let { a -> AnalysisSaveDialog(a,store,onSaved={},onDismiss={ editor=null }) }
    details?.let { a -> SavedAnalysisDialog(a,onDismiss={ details=null },onOpen={ details=null;onOpen(a) }) }
    deleting?.let { a -> AlertDialog(onDismissRequest={ deleting=null },title={ Text("Analiz kaydı silinsin mi?") },text={ Text("${a.part} sonuçları, notu ve etiketleri bu kayıttan kaldırılır.") },
        confirmButton={ TextButton(onClick={ deleting=null;mutate { store.removeAnalysis(a.id) } }) { Text("SİL") } },dismissButton={ TextButton(onClick={ deleting=null }) { Text("VAZGEÇ") } }) }
    removeProject?.let { p -> AlertDialog(onDismissRequest={ removeProject=null },title={ Text("Proje klasörü kaldırılsın mı?") },text={ Text("${p.name} kaldırılır. Analizler, notlar ve etiketler Klasörsüz altında korunur.") },
        confirmButton={ TextButton(onClick={ removeProject=null;folder="";mutate { store.removeFolder(p.id) } }) { Text("KALDIR") } },dismissButton={ TextButton(onClick={ removeProject=null }) { Text("VAZGEÇ") } }) }
    if(naming) AlertDialog(onDismissRequest={ if(!busy) naming=false },title={ Text(if(renameId==null) "Yeni proje" else "Proje adını değiştir") },text={
        Column { OutlinedTextField(name,{ name=it.take(60) },label={ Text("Proje adı") },singleLine=true);error?.let { Text(it,color=MaterialTheme.colorScheme.error) } }
    },confirmButton={ TextButton(onClick={ scope.launch {
        busy=true;error=null
        try { withContext(Dispatchers.IO) { val id=renameId;if(id==null) store.createFolder(name) else store.renameFolder(id,name) };naming=false }
        catch(e: IllegalArgumentException) { error=e.message } catch(_: Exception) { error="Proje kaydedilemedi." } finally { busy=false }
    } },enabled=!busy && name.isNotBlank()) { Text("KAYDET") } },dismissButton={ TextButton(onClick={ naming=false },enabled=!busy) { Text("VAZGEÇ") } })
}

@Composable
private fun SavedAnalysisDialog(a: SavedAnalysis,onDismiss: ()->Unit,onOpen: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={ Text("${a.part} · Kayıtlı analiz") },text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(a.sourceLabel,style=MaterialTheme.typography.bodySmall);Text(a.sourceUrl,style=MaterialTheme.typography.bodySmall)
            if(!a.report.documentEligible) Text("İnceleme gerekli; sayısal alanlar gösterilmedi.",color=MaterialTheme.colorScheme.error)
            a.report.results.forEach { r ->
                Text(r.query.requested,style=MaterialTheme.typography.titleSmall)
                Text(ResultPresentation.status(r.status),style=MaterialTheme.typography.bodySmall)
                if(r.matches.isEmpty()) Text(r.explanation,style=MaterialTheme.typography.bodySmall)
                (r.matches+r.absoluteLimits).forEach { hit ->
                    Text("${hit.section?.label ?: "Kaynak"} · s.${hit.page}",style=MaterialTheme.typography.labelSmall)
                    if(a.report.documentEligible && r.status!=ParameterStatus.DOCUMENT_UNVERIFIED) hit.numbers?.let { n ->
                        ResultTable(listOf("Alan","Değer"),listOf("Min","Tipik","Maks","Değer").zip(ResultPresentation.values(n).take(4)).map { (label,value) -> listOf(label,"$value ${n.unit.symbol}") })
                    }
                    if(a.report.documentEligible && r.status!=ParameterStatus.DOCUMENT_UNVERIFIED && hit.numbers==null) {
                        val sourceRows=SourceSectionTableReader().rows(hit,a.part)
                        if(sourceRows.isNotEmpty()) ResultTable(listOf("Parametre","Min","Tipik","Maks","Değer","Birim"),sourceRows.map { listOf(it.label)+ResultPresentation.values(it.numbers) },
                            listOf(150.dp,70.dp,70.dp,70.dp,70.dp,60.dp))
                    }
                    Text(hit.evidenceLine,style=MaterialTheme.typography.bodySmall)
                    if(hit.conditions.isNotBlank()) Text("Koşul: ${hit.conditions}",style=MaterialTheme.typography.bodySmall)
                    hit.warnings.forEach { Text(it,style=MaterialTheme.typography.labelSmall) }
                };HorizontalDivider()
            }
            if(a.note.isNotBlank()) Text("Not: ${a.note}",style=MaterialTheme.typography.bodySmall)
            if(a.report.graphs.isNotEmpty()) ResultTable(listOf("Grafik başlığı","Kaynak sayfası"),a.report.graphs.map {
                listOf(it.requested,it.matches.joinToString { m -> "s.${m.page}" }.ifBlank { "Bulunamadı" })
            })
        }
    },confirmButton={ TextButton(onClick=onOpen) { Text("BELGEYİ YENİDEN ANALİZ ET") } },dismissButton={ TextButton(onClick=onDismiss) { Text("KAPAT") } })
}
