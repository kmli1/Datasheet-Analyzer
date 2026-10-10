package com.example.datasheetanalyzer.features.sensoralternatives

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun SensorAlternativesScreen(initialPart: String,onBack: ()->Unit,onAnalyze: (String)->Unit,onOpenSource: ((String)->Unit)?=null) {
    val engine=remember { SensorAlternatives() }
    var input by rememberSaveable { mutableStateOf(initialPart.take(64)) }
    var submitted by rememberSaveable { mutableStateOf(initialPart.take(64)) }
    var includePartial by rememberSaveable { mutableStateOf(false) }
    var sameInterface by rememberSaveable { mutableStateOf(false) }
    var selectedCode by rememberSaveable { mutableStateOf("") }
    var catalogue by rememberSaveable { mutableStateOf(false) }
    var catalogueQuery by rememberSaveable { mutableStateOf("") }
    var catalogueMeasurementName by rememberSaveable { mutableStateOf("") }
    val listState=rememberLazyListState()
    LaunchedEffect(submitted,selectedCode) { listState.scrollToItem(0) }
    val focus=LocalFocusManager.current
    val context=LocalContext.current
    val open: (String)->Unit = onOpenSource ?: { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
            .onFailure { Toast.makeText(context,"Kaynağı açacak bir uygulama bulunamadı.",Toast.LENGTH_LONG).show() }
    }
    val search=remember(submitted,includePartial,sameInterface) { engine.find(submitted,includePartial,sameInterface) }
    val selected=search.alternatives.firstOrNull { it.sensor.code==selectedCode }
    fun back() { if(catalogue) catalogue=false else if(selectedCode.isNotEmpty()) selectedCode="" else onBack() }
    fun submit() { focus.clearFocus();submitted=input;selectedCode="" }
    BackHandler { back() }
    if(catalogue) {
        SensorCatalogueScreen(catalogueQuery,catalogueMeasurementName,
            onQuery={ catalogueQuery=it.take(64) },onMeasurement={ catalogueMeasurementName=it },
            onSelect={ code -> focus.clearFocus();input=code;submitted=code;selectedCode="";catalogue=false },
            onBack={ catalogue=false })
        return
    }
    Scaffold { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal=20.dp).testTag("sensor-screen"),
            state=listState,verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Column(Modifier.padding(top=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick={ back() }) { Text(if(selected!=null) "← ADAYLAR" else "← GERİ") }
                Text(if(selected==null) "Muadil sensörler" else "Sensör karşılaştırma",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                Text("Ölçümü karşılaştır. Uyum farklarını gör.",color=MaterialTheme.colorScheme.primary)
            } }
            if(selected!=null && search.original!=null) {
                item { CompatibilityCard(selected) }
                item { Text("${search.original.code}  /  ${selected.sensor.code}",style=MaterialTheme.typography.titleLarge) }
                item { ComparisonHeader(search.original,selected.sensor) }
                items(SensorField.entries.toList(),key={ "field:"+it.name }) { field ->
                    ComparisonRow(field,search.original,selected.sensor,open)
                }
                selected.proof?.let { proof -> item { SensorPanel {
                    Text("Üretici muadil kanıtı",fontWeight=FontWeight.Bold)
                    Text(proof.condition,style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={ open(proof.url) },modifier=Modifier.fillMaxWidth()) { Text("ÜRETİCİ MUADİL BEYANINI AÇ") }
                } } }
                item { SourcePanel(search.original,open) }
                item { SourcePanel(selected.sensor,open) }
                item { Button(onClick={ onAnalyze(selected.sensor.code) },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Text("ADAYI ANA EKRANA AL")
                } }
                item { Text("Seçim, ana ekrandaki sensör kodunu değiştirir. Mevcut kriterlerin korunur; datasheet analizini oradan başlatabilirsin.",style=MaterialTheme.typography.bodySmall) }
            } else {
                item { SensorPanel {
                    OutlinedTextField(input,onValueChange={ input=it.take(64);submitted="";selectedCode="" },
                        label={ Text("Sensör kodu") },placeholder={ Text("SHT31, BMP280, LM35…") },singleLine=true,
                        modifier=Modifier.fillMaxWidth().testTag("sensor-code"),
                        keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Characters,imeAction=ImeAction.Search),
                        keyboardActions=KeyboardActions(onSearch={ submit() }))
                    Button(onClick={ submit() },enabled=input.isNotBlank(),modifier=Modifier.fillMaxWidth()) { Text("MUADİL ADAYLARINI BUL") }
                    OutlinedButton(onClick={ focus.clearFocus();catalogue=true },modifier=Modifier.fillMaxWidth()) { Text("DESTEKLENEN SENSÖRLER (${SensorCatalog.profiles.size})") }
                    Text("Üretici belgelerinden hazırlanmış çevrimdışı katalog. Sensör IC bilgileri gösterilir; hazır modül kartlarının bağlantıları ve beslemeleri farklı olabilir.",style=MaterialTheme.typography.bodySmall)
                } }
                if(submitted.isNotBlank() && search.original==null) item { SensorPanel {
                    Text("Bu kod katalogda bulunmuyor",style=MaterialTheme.typography.titleMedium)
                    Text("Tam sensör kodunu kontrol et veya desteklenen listeden seç. Bu kod için muadil tahmini yapılmadı.")
                    OutlinedButton(onClick={ onAnalyze(input.trim()) },modifier=Modifier.fillMaxWidth()) { Text("KODU ANA EKRANA AL") }
                } }
                search.original?.let { original ->
                    item { SensorPanel {
                        Text(original.code,style=MaterialTheme.typography.headlineMedium,color=MaterialTheme.colorScheme.primary)
                        Text(original.manufacturer+" · "+original.measurementLabel)
                        Text(original.facts[SensorField.SCOPE]?.text.orEmpty(),style=MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Ortak arabirimi olanlar",modifier=Modifier.weight(1f).padding(top=12.dp))
                            Switch(checked=sameInterface,onCheckedChange={ sameInterface=it;selectedCode="" },modifier=Modifier.testTag("sensor-interface-filter"))
                        }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text("Bazı ölçümleri eksik adayları da göster",modifier=Modifier.weight(1f).padding(top=12.dp))
                            Switch(checked=includePartial,onCheckedChange={ includePartial=it;selectedCode="" },modifier=Modifier.testTag("sensor-partial-filter"))
                        }
                    } }
                    item { Text("${search.alternatives.size} aday · üretici kanıtlı muadiller önce",style=MaterialTheme.typography.titleMedium) }
                    if(search.alternatives.isEmpty()) item { Text("Seçilen filtrelere uygun aday yok. Filtreleri değiştirebilir veya desteklenen sensörleri inceleyebilirsin.") }
                    items(search.alternatives,key={ "candidate:"+it.sensor.code }) { candidate ->
                        SensorPanel(Modifier.testTag("sensor-candidate-"+candidate.sensor.code)) {
                            Text(candidate.sensor.code,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            Text(candidate.sensor.manufacturer+" · "+candidate.sensor.measurementLabel,style=MaterialTheme.typography.bodySmall)
                            Text(candidate.compatibility.label,color=MaterialTheme.colorScheme.primary)
                            Text(candidate.reasons.last(),style=MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick={ selectedCode=candidate.sensor.code },modifier=Modifier.fillMaxWidth()) { Text("YAN YANA KARŞILAŞTIR") }
                        }
                    }
                    item { Text("Aday sırası fiyat veya genel üstünlük sıralaması değildir. Doğruluk değerlerini, tipik/maksimum niteliğini ve test koşullarını birlikte incele.",style=MaterialTheme.typography.bodySmall) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SensorCatalogueScreen(query: String,measurementName: String,onQuery: (String)->Unit,
    onMeasurement: (String)->Unit,onSelect: (String)->Unit,onBack: ()->Unit) {
    val measurement=SensorMeasurement.entries.firstOrNull { it.name==measurementName }
    val filtered=remember(query,measurement) { SensorCatalogBrowser.filter(query,measurement) }
    var groupsExpanded by remember { mutableStateOf(false) }
    val focus=LocalFocusManager.current
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            TextButton(onClick=onBack,modifier=Modifier.padding(top=8.dp)) { Text("← MUADİL SENSÖRLER") }
            Text("Sensör kataloğu",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
            Text("${SensorCatalog.profiles.size} sensör · kontrol: 09.10.2026",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(query,onValueChange=onQuery,label={ Text("Kod veya üretici ara") },
                singleLine=true,modifier=Modifier.fillMaxWidth().testTag("sensor-catalogue-query"),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={ focus.clearFocus() }))
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick={ focus.clearFocus();groupsExpanded=true },modifier=Modifier.fillMaxWidth().testTag("sensor-catalogue-group")) {
                    Text("Ölçüm: "+(measurement?.label ?: "Tümü")+" ▾")
                }
                DropdownMenu(expanded=groupsExpanded,onDismissRequest={ groupsExpanded=false }) {
                    DropdownMenuItem(text={ Text("Tümü (${SensorCatalog.profiles.size})") },onClick={ onMeasurement("");groupsExpanded=false })
                    SensorCatalogBrowser.measurements.forEach { kind ->
                        val count=SensorCatalog.profiles.count { kind in it.measurements }
                        DropdownMenuItem(text={ Text("${kind.label} ($count)") },onClick={ onMeasurement(kind.name);groupsExpanded=false },
                            modifier=Modifier.testTag("sensor-catalogue-group-${kind.name}"))
                    }
                }
            }
            Text("${filtered.size} / ${SensorCatalog.profiles.size} sonuç",modifier=Modifier.testTag("sensor-catalogue-count"),style=MaterialTheme.typography.bodySmall)
            if(filtered.isEmpty()) Text("Bu arama ve ölçüm filtresine uygun sensör bulunamadı.",modifier=Modifier.testTag("sensor-catalogue-empty"))
            key(query,measurement) {
            LazyColumn(Modifier.weight(1f).testTag("sensor-catalogue-list"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                items(filtered,key={ it.code }) { sensor ->
                    OutlinedButton(onClick={ focus.clearFocus();onSelect(sensor.code) },modifier=Modifier.fillMaxWidth().testTag("sensor-catalogue-${sensor.code}")) {
                        Column { Text(sensor.code);Text(sensor.manufacturer,style=MaterialTheme.typography.bodySmall);Text(sensor.measurementLabel,style=MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SensorPanel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable
private fun CompatibilityCard(candidate: SensorAlternative) {
    SensorPanel {
        Text(candidate.compatibility.label,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        candidate.reasons.forEach { Text("• $it",style=MaterialTheme.typography.bodySmall) }
    }
}
@Composable
private fun ComparisonHeader(original: SensorProfile,candidate: SensorProfile) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Özellik",Modifier.weight(0.8f),style=MaterialTheme.typography.labelMedium)
        Text(original.code,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
        Text(candidate.code,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
    }
}
@Composable
private fun ComparisonRow(field: SensorField,original: SensorProfile,candidate: SensorProfile,open: (String)->Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(field.label,Modifier.weight(0.8f),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
        listOf(original,candidate).forEach { sensor -> Column(Modifier.weight(1f).testTag("sensor-fact-${sensor.code}-${field.name}"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            val fact=sensor.facts[field]
            Text(fact?.text ?: "Belirtilmemiş",style=MaterialTheme.typography.bodySmall)
            fact?.pages?.distinct()?.forEach { page -> SensorLinks.documentPage(sensor.source,page)?.let { url ->
                TextButton(onClick={ open(url) },contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)) { Text("Kaynak s.$page",style=MaterialTheme.typography.labelSmall) }
            } }
        } }
    }
}
@Composable
private fun SourcePanel(sensor: SensorProfile,open: (String)->Unit) {
    SensorPanel {
        Text(sensor.code+" · "+sensor.manufacturer,fontWeight=FontWeight.Bold)
        Text(sensor.source.revision+" · kontrol: 09.10.2026",style=MaterialTheme.typography.bodySmall)
        Text("Tablodaki bilgiler bu belge sürümüne aittir. Üretici bağlantısı daha yeni bir sürüm açabilir.",style=MaterialTheme.typography.bodySmall)
        val url=SensorLinks.documentPage(sensor.source,null)
        OutlinedButton(onClick={ url?.let(open) },enabled=url!=null,modifier=Modifier.fillMaxWidth()) { Text("ÜRETİCİ PDF'SİNİ AÇ") }
    }
}
