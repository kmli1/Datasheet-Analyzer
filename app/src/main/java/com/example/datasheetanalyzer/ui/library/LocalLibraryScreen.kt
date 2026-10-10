package com.example.datasheetanalyzer.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.data.cache.SavedSearch
import com.example.datasheetanalyzer.data.repository.ResolverDependencies
import kotlinx.coroutines.*

@Composable
fun LocalLibraryScreen(local: ResolverDependencies.LocalServices,onOpen: (SavedSearch)->Unit,onBack: ()->Unit,onPrivacy: ()->Unit) {
    val searches by local.recent.searches.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();var bytes by remember { mutableLongStateOf(0) }
    var confirm by remember { mutableStateOf(false) };var favoritesOnly by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var cleanupError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(searches) { bytes=withContext(Dispatchers.IO) { local.cache.bytes() } }
    Scaffold { insets -> LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Column(Modifier.padding(top=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick=onBack) { Text("← GERİ") }
            Text("Kayıtlarım",style=MaterialTheme.typography.headlineLarge)
            Text("Son aramaların ve favorilerin bu cihazda saklanır. Doğrulanmış belgeler 7 gün, en fazla 100 MB tutulur.",style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!favoritesOnly,onClick={ favoritesOnly=false },label={ Text("Son aramalar") })
                FilterChip(selected=favoritesOnly,onClick={ favoritesOnly=true },label={ Text("Favoriler") })
            }
        } }
        val visible=searches.filter { !favoritesOnly || it.favorite }
        if(visible.isEmpty()) item { Text("Henüz kayıt yok. Bir komponent aradıktan sonra burada görünür.") }
        items(visible,key={ it.part }) { record -> Card {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(record.part,style=MaterialTheme.typography.titleLarge)
                Text((record.criteria.map { it.label }+listOf(record.custom).filter { it.isNotBlank() }).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick={ onOpen(record) },enabled=!deleting) { Text("TEKRAR AÇ") }
                Row { TextButton(onClick={ local.recent.favorite(record.part) },enabled=!deleting) { Text(if(record.favorite) "★ FAVORİ" else "☆ FAVORİYE EKLE") }
                    TextButton(onClick={ local.recent.remove(record.part) },enabled=!deleting) { Text("KAYDI SİL") } }
            }
        } }
        item { Column(Modifier.padding(vertical=18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Belge önbelleği: ${bytes/1024/1024} MB",style=MaterialTheme.typography.bodySmall)
            cleanupError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick={ confirm=true },enabled=!deleting,modifier=Modifier.fillMaxWidth()) { Text("KAYITLARI VE ÖNBELLEĞİ TEMİZLE") }
            TextButton(onClick=onPrivacy) { Text("GİZLİLİK VE UYGULAMA BİLGİSİ") }
        } }
    } }
    if(confirm) AlertDialog(onDismissRequest={ confirm=false },title={ Text("Kayıtlar silinsin mi?") },text={ Text("Son aramalar, favoriler, proje klasörleri, analizler, notlar, özel şablonlar ve belge önbelleği silinir. Telefonda seçtiğin asıl PDF dosyalarına dokunulmaz.") },
        confirmButton={ TextButton(onClick={ confirm=false;deleting=true;cleanupError=null;scope.launch { try { withContext(Dispatchers.IO) { local.workspace.clear();local.cache.clear();local.recent.clear();local.temporaryDirectories.forEach { directory -> directory.listFiles().orEmpty().forEach { it.delete() } } };bytes=0 }
            catch(c: CancellationException) { throw c }
            catch(_: Exception) { cleanupError="Bazı kayıtlar temizlenemedi. Yeniden deneyebilirsin." }
            finally { deleting=false } } }) { Text("SİL") } },
        dismissButton={ TextButton(onClick={ confirm=false }) { Text("VAZGEÇ") } })
}
