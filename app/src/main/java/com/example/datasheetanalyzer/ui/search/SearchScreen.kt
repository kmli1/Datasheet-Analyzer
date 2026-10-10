package com.example.datasheetanalyzer.ui.search

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.datasheetanalyzer.domain.extraction.ParameterQuery
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.RankedCandidate
import com.example.datasheetanalyzer.domain.model.TrustLevel
import com.example.datasheetanalyzer.ui.theme.ElectricOrange
import com.example.datasheetanalyzer.domain.security.CheckPhase

@Composable
fun SearchScreen(part: String, criteria: String, queries: List<ParameterQuery>, viewModel: SearchViewModel, onBack: () -> Unit,onResult: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(part,queries) { viewModel.start(part,queries) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importPdf) }
    var shownResult by rememberSaveable { mutableStateOf("") }
    val resultKey=state.analysis?.checked?.pdf?.file?.absolutePath.orEmpty()
    LaunchedEffect(resultKey) { if(resultKey.isNotBlank() && shownResult!=resultKey) { shownResult=resultKey;onResult() } }
    val back = { viewModel.cancel(); onBack() }
    BackHandler(onBack = back)
    Scaffold { insets ->
        LazyColumn(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = back) { Text("← GERİ") }
                    Text("Kaynaklar", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(part, color = ElectricOrange, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.headlineSmall)
                    Text(criteria, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Belge doğrulanır; seçtiğin kriterler kaynak sayfalarıyla analiz edilir.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.loading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = ElectricOrange)
                        Text("Datasheet adayları aranıyor…")
                        OutlinedButton(onClick = back) { Text("İPTAL ET") }
                    }
                }
            }
            state.error?.let { error ->
                item {
                    Card {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Arama tamamlanamadı", style = MaterialTheme.typography.titleMedium)
                            Text(error, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.start(part, retry = true) }) { Text("YENİDEN DENE") }
                        }
                    }
                }
            }
            state.checkProgress?.let { progress ->
                item {
                    Card {
                        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(when(progress.phase) {
                                CheckPhase.VERIFYING_SOURCE -> "Kaynak doğrulanıyor…"
                                CheckPhase.DOWNLOADING -> "Belge indiriliyor…"
                                CheckPhase.CHECKING_DOCUMENT -> "Belge kontrol ediliyor…"
                                CheckPhase.EXTRACTING_TEXT -> "PDF metni çıkarılıyor…"
                                CheckPhase.READING_ARCHIVE -> "Arşivin sayfa metni okunuyor…"
                                CheckPhase.READING_CACHE -> "Kayıtlı belge kontrol ediliyor…"
                                CheckPhase.OCR -> "Taranmış sayfa okunuyor…"
                                CheckPhase.VERIFYING_DOCUMENT -> "Parça kodu ve bölümler doğrulanıyor…"
                            })
                            LinearProgressIndicator(Modifier.fillMaxWidth(),color=ElectricOrange)
                            if(progress.bytes>0) Text("${progress.bytes/1024} KiB",style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick=viewModel::cancelCheck) { Text("İPTAL ET") }
                        }
                    }
                }
            }
            state.checkError?.let { error -> item { Text(error,color=MaterialTheme.colorScheme.error) } }
            item {
                OutlinedButton(onClick={ picker.launch(arrayOf("application/pdf")) },enabled=state.checkProgress==null,modifier=Modifier.fillMaxWidth()) { Text("TELEFONDAN PDF SEÇ") }
                if(state.analysis!=null) Button(onClick=onResult,modifier=Modifier.fillMaxWidth()) { Text("SONUÇLARI GÖR") }
            }
            state.selection?.let { selection ->
                item {
                    val available = selection.candidates.count { it.selectable }
                    Text(if (available > 0) "$available seçilebilir kaynak adayı"
                        else "Seçim ölçütlerini karşılayan kaynak bulunamadı.",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Belge içindeki kod ayrıca doğrulanır. Yanlış belge bulunursa en fazla üç uygun aday denenir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selection.discardedCount > 0) Text(
                        "${selection.discardedCount} uygun olmayan URL elendi.",
                        style = MaterialTheme.typography.bodySmall)
                }
                val visible=selection.candidates.filter { it.source.level in setOf(TrustLevel.MANUFACTURER,TrustLevel.ARCHIVE,TrustLevel.DISTRIBUTOR) }
                items(visible, key = { it.canonicalUrl }) { candidate ->
                    CandidateCard(candidate,
                        recommended = candidate.canonicalUrl == selection.recommendedUrl,
                        selected = candidate.canonicalUrl == state.selectedUrl,
                        busy = state.checkProgress != null, onSelect = { viewModel.select(candidate.canonicalUrl) })
                }
                item {
                    OutlinedButton(onClick = { viewModel.start(part, retry = true) },
                        modifier = Modifier.fillMaxWidth(),enabled=state.checkProgress==null) { Text("ARAMAYI YENİLE") }
                    Text("Yalnızca kayıtlı kaynaklar gösterilir. Seçtiğin belge özel karantinaya indirilip kontrol edilir.",
                        modifier = Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: RankedCandidate, recommended: Boolean, selected: Boolean, busy: Boolean, onSelect: () -> Unit) {
    var details by rememberSaveable(candidate.canonicalUrl) { mutableStateOf(false) }
    Card(
        border = BorderStroke(1.dp, if (selected || recommended) ElectricOrange
            else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (recommended) Text("ÖNE ÇIKAN ADAY", color = ElectricOrange,
                style = MaterialTheme.typography.labelSmall)
            Text(candidate.candidate.title.ifBlank { "Başlıksız bağlantı" },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(candidate.host, color = ElectricOrange, fontFamily = FontFamily.Monospace)
            val type = when (candidate.source.level) {
                TrustLevel.MANUFACTURER -> "Üretici alan adı"
                TrustLevel.ARCHIVE -> "Tercih edilen datasheet arşivi"
                TrustLevel.DISTRIBUTOR -> "Distribütör alan adı"
                TrustLevel.UNKNOWN -> "Kayıtlı olmayan alan adı"
                TrustLevel.BLOCKED -> "Seçime kapalı kaynak"
                TrustLevel.LOCAL -> "Telefondan seçilen PDF"
            }
            Text("$type · ${candidate.source.label}", style = MaterialTheme.typography.bodySmall)
            val matchText = when (candidate.match) {
                PartMatch.EXACT -> "Başlık/URL'de tam kod eşleşmesi"
                PartMatch.FAMILY_VARIANT -> "Aile / varyant eşleşmesi"
                PartMatch.NONE -> "Kod eşleşmesi yok"
            }
            Text(matchText, style = MaterialTheme.typography.bodySmall)
            if (candidate.selectable) {
                Button(onClick = onSelect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selected) "BELGEYİ YENİDEN KONTROL ET" else "OKU VE ANALİZ ET")
                }
            } else {
                Text("Seçim ölçütlerini karşılamıyor.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Text("Kaynak seçildi. Belge kontrol sonucu üstte gösterilir.",
                style = MaterialTheme.typography.bodySmall, color = ElectricOrange)
            TextButton(onClick = { details = !details }) {
                Text(if (details) "AYRINTILARI GİZLE" else "EŞLEŞME VE BAĞLANTI")
            }
            if (details) {
                Text("Sıralama puanı: ${candidate.score} — doğruluk yüzdesi değildir.",
                    style = MaterialTheme.typography.bodySmall)
                candidate.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                SelectionContainer { Text(candidate.canonicalUrl, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
