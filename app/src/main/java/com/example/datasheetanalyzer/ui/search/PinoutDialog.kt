package com.example.datasheetanalyzer.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.datasheetanalyzer.domain.document.*

@Composable
fun PinoutDialog(document: AnalyzedDocument,part: String,onPage: (Int)->Unit,onDismiss: ()->Unit) {
    val sources=remember(document) { PinoutSources.find(document) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Görsel pin bağlantıları",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));TextButton(onClick=onDismiss) { Text("KAPAT") }
                }
                Text("$part · kaynak belgedeki pin görünümü")
                Text("Kullandığın tam varyantı, paket adını ve çizimin üstten/alttan görünümünü kaynakta kontrol et. Farklı paketlerin pin numaraları birleştirilmez.",style=MaterialTheme.typography.bodySmall)
                if(sources.isEmpty()) Text("Okunan ve doğrulanmış sayfalarda pin bağlantısı başlığı bulunamadı. Pin sırası üretilmedi; kaynak sayfalarını inceleyebilirsin.")
                sources.forEach { pin ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("${pin.title} · s.${pin.page}",style=MaterialTheme.typography.titleMedium)
                            if(document.sourceKind!=DocumentSource.ARCHIVE_HTML)
                                SourceFigurePreview(FigureMatch(pin.title,pin.page),document,part,onPage={ onDismiss();onPage(it) },kind="Pin bağlantısı",allowReading=false)
                            else TextButton(onClick={ onDismiss();onPage(pin.page) }) { Text("KAYNAK SAYFAYI AÇ") }
                            var expanded by remember { mutableStateOf(false) }
                            TextButton(onClick={ expanded=!expanded }) { Text(if(expanded) "KAYNAK METNİNİ GİZLE" else "PİN AÇIKLAMALARINI GÖSTER") }
                            if(expanded) SelectionContainer { Text(pin.text,style=MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
