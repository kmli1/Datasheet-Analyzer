package com.example.datasheetanalyzer.ui.workspace

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.data.workspace.*
import kotlinx.coroutines.*

@Composable
fun AnalysisSaveDialog(initial: SavedAnalysis,store: WorkspaceStore,onSaved: ()->Unit,onDismiss: ()->Unit) {
    val workspace by store.state.collectAsStateWithLifecycle();val scope=rememberCoroutineScope()
    var folder by rememberSaveable(initial.id) { mutableStateOf(initial.folderId) }
    var note by rememberSaveable(initial.id) { mutableStateOf(initial.note) }
    var tags by rememberSaveable(initial.id) { mutableStateOf(initial.tags.joinToString(", ")) }
    var menu by remember { mutableStateOf(false) };var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest={ if(!busy) onDismiss() },title={ Text("${initial.part} · Projeye kaydet") },text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Box {
                OutlinedButton(onClick={ menu=true },enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                    Text(workspace.folders.firstOrNull { it.id==folder }?.name ?: "Klasörsüz")
                }
                DropdownMenu(expanded=menu,onDismissRequest={ menu=false }) {
                    DropdownMenuItem(text={ Text("Klasörsüz") },onClick={ folder=null;menu=false })
                    workspace.folders.forEach { p -> DropdownMenuItem(text={ Text(p.name) },onClick={ folder=p.id;menu=false }) }
                }
            }
            TextButton(onClick={ folderName="";newFolder=true;error=null },enabled=!busy) { Text("+ YENİ PROJE KLASÖRÜ") }
            OutlinedTextField(note,{ note=it.take(2000) },label={ Text("Analiz notu") },minLines=3,maxLines=6,enabled=!busy,modifier=Modifier.fillMaxWidth(),supportingText={ Text("${note.length}/2000") })
            OutlinedTextField(tags,{ tags=it.take(260) },label={ Text("Etiketler") },placeholder={ Text("laboratuvar, güç kaynağı") },enabled=!busy,
                modifier=Modifier.fillMaxWidth(),supportingText={ Text("Virgülle ayır; en fazla 8 etiket, her biri 30 karakter.") })
            Text("Sonuçlar ve kaynak bilgisi bu cihazda saklanır. Kaynak PDF'nin kalıcı kopyası bu kayda dahil değildir.",style=MaterialTheme.typography.bodySmall)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) };workspace.loadError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={ TextButton(onClick={ scope.launch {
        busy=true;error=null
        try {
            val cleanTags=tags.split(',', ';').map(String::trim).filter(String::isNotBlank).distinct()
            withContext(Dispatchers.IO) { store.save(initial.copy(folderId=folder,note=note,tags=cleanTags)) }
            onSaved();onDismiss()
        } catch(e: IllegalArgumentException) { error=e.message }
          catch(_: Exception) { error="Analiz kaydedilemedi. Yeniden deneyebilirsin." }
        finally { busy=false }
    } },enabled=!busy && workspace.loadError==null) { Text(if(busy) "KAYDEDİLİYOR…" else "KAYDET") } },
        dismissButton={ TextButton(onClick=onDismiss,enabled=!busy) { Text("VAZGEÇ") } })
    if(newFolder) AlertDialog(onDismissRequest={ if(!busy) newFolder=false },title={ Text("Yeni proje") },text={
        Column { OutlinedTextField(folderName,{ folderName=it.take(60) },label={ Text("Proje adı") },singleLine=true,enabled=!busy);error?.let { Text(it,color=MaterialTheme.colorScheme.error) } }
    },confirmButton={ TextButton(onClick={ scope.launch {
        busy=true
        try { folder=withContext(Dispatchers.IO) { store.createFolder(folderName).id };newFolder=false;error=null }
        catch(e: IllegalArgumentException) { error=e.message }
        catch(_: Exception) { error="Proje oluşturulamadı." }
        finally { busy=false }
    } },enabled=!busy && folderName.isNotBlank()) { Text("OLUŞTUR") } },dismissButton={ TextButton(onClick={ newFolder=false },enabled=!busy) { Text("VAZGEÇ") } })
}
