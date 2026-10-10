package com.example.datasheetanalyzer.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer

@Composable
fun PartVariantDialog(part: String,variants: List<PartVariant>,onSelect: (String)->Unit,onDismiss: ()->Unit) {
    var input by rememberSaveable(part) { mutableStateOf("") }
    val normalized=PartNumberNormalizer.normalize(input)
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Parça varyantı seç",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));TextButton(onClick=onDismiss) { Text("KAPAT") }
                }
                Text("Şu an: $part")
                Text("Tam kodu seçince kriterlerin korunur ve o kod için belge yeniden aranıp doğrulanır. Sonekten paket, pin sırası veya sıcaklık sınıfı tahmin edilmez.",style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(input,{ input=it.take(64) },label={ Text("Tam varyant kodu") },singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedButton(onClick={ onDismiss();onSelect(normalized) },
                    enabled=PartNumberNormalizer.isValid(normalized) && normalized!=part,modifier=Modifier.fillMaxWidth()) { Text("BU VARYANTI ARA") }
                Text("Belgede görülen kodlar",style=MaterialTheme.typography.titleMedium)
                if(variants.isEmpty()) Text("Okunan sayfalarda başka doğrulanmış varyant kodu bulunamadı. Tam kodu yukarıdan girebilirsin.")
                variants.forEach { v ->
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(v.code,style=MaterialTheme.typography.titleMedium)
                            Text("Kaynak s.${v.page}: ${v.evidence}",style=MaterialTheme.typography.bodySmall)
                            TextButton(onClick={ onDismiss();onSelect(v.code) }) { Text("VARYANTI SEÇ VE ARA") }
                        }
                    }
                }
            }
        }
    }
}
