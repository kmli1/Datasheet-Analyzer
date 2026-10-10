package com.example.datasheetanalyzer.ui.search

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.ParameterQuery
import com.example.datasheetanalyzer.domain.security.PdfPolicy
import com.example.datasheetanalyzer.domain.extraction.ExtractedParameter
import com.example.datasheetanalyzer.data.repository.ResolverDependencies
import com.example.datasheetanalyzer.data.workspace.SavedAnalysis
import com.example.datasheetanalyzer.ui.workspace.*
import kotlinx.coroutines.*
import android.widget.Toast

@Composable
fun ResultsScreen(part: String,queries: List<ParameterQuery>,viewModel: SearchViewModel,onBack: ()->Unit,onProjects: ()->Unit={},onCompare: ()->Unit={},onVariant: (String)->Unit={},onSensors: ()->Unit={}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val workspace=remember(context) { ResolverDependencies.local(context).workspace }
    val records by workspace.state.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope();var saveDialog by remember { mutableStateOf<SavedAnalysis?>(null) }
    var pinDialog by remember { mutableStateOf(false) };var variantDialog by remember { mutableStateOf(false) };var conditionsDialog by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) };var guide by remember { mutableStateOf(false) }
    val original=MaterialTheme.colorScheme
    val colors=original.copy(primary=Color(0xFF5FE3D3),onPrimary=Color(0xFF042E2A),primaryContainer=Color(0xFF153E3C),
        onPrimaryContainer=Color(0xFFC0FFF4),secondary=Color(0xFF9DD2FF),background=Color(0xFF07151B),surface=Color(0xFF11232B),
        surfaceContainer=Color(0xFF142B33),surfaceContainerLow=Color(0xFF102128),outlineVariant=Color(0xFF28454E))
    var preview by remember(state.analysis?.checked?.pdf?.sha256) { mutableStateOf<Int?>(null) }
    var previewHit by remember(state.analysis?.checked?.pdf?.sha256) { mutableStateOf<ExtractedParameter?>(null) }
    BackHandler(onBack=onBack)
    LaunchedEffect(part) { if(state.analysis==null) viewModel.start(part,queries) }
    MaterialTheme(colorScheme=colors) {
        Scaffold { insets ->
            LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item { Column(Modifier.padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=onBack) { Text("← KAYNAKLAR") }
                    Text("Analiz sonuçları",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text(part,color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.headlineMedium)
                } }
                val document=state.analysis
                if(document==null) item {
                    if(state.loading || state.checkProgress!=null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.error ?: "Analiz henüz hazır değil. Kaynaklardan bir belge seçebilirsin.")
                    OutlinedButton(onClick=onBack) { Text("KAYNAKLARA DÖN") }
                } else {
                    state.report?.results?.let { results -> item { ResultSummaryTable(results) } }
                    item {
                        Card { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("Analiz araçları",style=MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick={ pinDialog=true },modifier=Modifier.weight(1f)) { Text("PİN BAĞLANTILARI") }
                                OutlinedButton(onClick={ variantDialog=true },modifier=Modifier.weight(1f)) { Text("VARYANT SEÇ") }
                            }
                            OutlinedButton(onClick={ conditionsDialog=true },modifier=Modifier.fillMaxWidth()) { Text("KOŞULLARIMI KONTROL ET") }
                            OutlinedButton(onClick=onSensors,modifier=Modifier.fillMaxWidth()) { Text("MUADİL SENSÖRLER") }
                        } }
                    }
                    item {
                        Card(border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary)) {
                            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(if(document.verification.automaticEligible) "Belge kimliği doğrulandı" else "Belge doğrulaması gerekli",fontWeight=FontWeight.Bold)
                                Text(when(document.sourceKind) {
                                    DocumentSource.ARCHIVE_HTML -> "Eski arşiv kaydı"
                                    DocumentSource.LOCAL_PDF -> "Telefondan seçilen PDF · izole kontrol"
                                    DocumentSource.PDF -> "İndirilen PDF · izole kontrol"
                                })
                                if(document.fromCache) Text("Kayıtlı belgeden açıldı · çevrimdışı kullanım",color=MaterialTheme.colorScheme.primary)
                                Text("${document.extraction.pages.size}/${document.extraction.totalPages} sayfa incelendi",style=MaterialTheme.typography.bodySmall)
                                if(document.extraction.pages.size<document.extraction.totalPages) Text("Bu sonuçlar okunan sayfaları kapsar.",style=MaterialTheme.typography.bodySmall)
                                state.report?.let { report ->
                                    ReportExportButton(part,document,report,enabled=!state.graphLoading)
                                    CsvExportButton("SONUÇLARI CSV OLARAK KAYDET",com.example.datasheetanalyzer.export.AnalysisReportContent.filename(part,"analiz","csv"),enabled=!state.graphLoading) {
                                        com.example.datasheetanalyzer.export.AnalysisCsvWriter.analyses(listOf(SavedAnalysis.capture(part,document,report)))
                                    }
                                }
                                state.report?.let { report ->
                                    val captured=remember(part,document,report) { SavedAnalysis.capture(part,document,report) }
                                    fun existing()=records.analyses.firstOrNull { it.part==captured.part && it.sha256==captured.sha256 && it.report==captured.report }
                                    OutlinedButton(onClick={ saveDialog=existing() ?: captured },enabled=!state.graphLoading && !saving,modifier=Modifier.fillMaxWidth()) { Text("PROJEYE KAYDET / NOT EKLE") }
                                    OutlinedButton(onClick={ scope.launch {
                                        saving=true
                                        try { val saved=existing() ?: withContext(Dispatchers.IO) { workspace.save(captured) };workspace.selectForComparison(saved.id);onCompare() }
                                        catch(e: IllegalArgumentException) { Toast.makeText(context,e.message ?: "Kayıt seçilemedi.",Toast.LENGTH_LONG).show() }
                                        catch(_: Exception) { Toast.makeText(context,"Karşılaştırma için kayıt oluşturulamadı.",Toast.LENGTH_LONG).show() }
                                        finally { saving=false }
                                    } },enabled=!state.graphLoading && !saving,modifier=Modifier.fillMaxWidth()) { Text(if(saving) "KAYDEDİLİYOR…" else "KARŞILAŞTIRMAYA EKLE") }
                                }
                                Row { TextButton(onClick=onProjects) { Text("PROJELERİM") };TextButton(onClick={ guide=true }) { Text("TERİM REHBERİ") } }
                                Text("Grafik görseli için ilgili kaynak sayfasını aç; sayfanın tamamını veya seçtiğin alanı PNG olarak kaydedebilirsin.",style=MaterialTheme.typography.bodySmall)
                                if(document.verification.automaticEligible) TextButton(onClick={
                                    val summary=buildString {
                                        append("Datasheet Analyzer · $part\n")
                                        state.report?.results?.forEach { result ->
                                            append("\n${result.query.requested}: ${result.explanation}\n")
                                            result.matches.forEach { hit -> append("${hit.evidenceLine}\n${hit.section?.label ?: "Kaynak"}, s.${hit.page}\n") }
                                        }
                                        append("\nKaynak: ${document.candidate.canonicalUrl}\nPDF/HTML kaynak değerlerini doğrulayın.")
                                    }.take(15000)
                                    val send=Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,summary) }
                                    runCatching { context.startActivity(Intent.createChooser(send,"Sonuç metnini paylaş")) }
                                }) { Text("SONUÇ METNİNİ PAYLAŞ") }
                            }
                        }
                    }
                    state.report?.results?.let { results ->
                        items(results,key={ it.query.requested+it.query.mode }) { ParameterResultCard(it,onPage={ page -> previewHit=null;preview=page },document=document,
                            onEvidence={ hit -> previewHit=hit;preview=hit.page }) }
                    }
                    if(state.graphLoading) item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Grafik başlıkları aranıyor…");LinearProgressIndicator(Modifier.fillMaxWidth())
                    } }
                    state.report?.graphs?.let { figures -> items(figures,key={ "graph:"+it.requested }) {
                        FigureResultCard(it,document,part,onPage={ page -> previewHit=null;preview=page })
                    } }
                    item { DocumentResultCard(document,onPage={ previewHit=null;preview=it }) }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
        state.analysis?.let { doc -> preview?.let { number ->
            if(doc.sourceKind!=DocumentSource.ARCHIVE_HTML) PdfPageDialog(doc.checked.pdf.file,number,doc.checked.pages,part=part,
                evidence=previewHit?.evidenceLine.orEmpty().take(600),section=previewHit?.section,allowGraphReading=doc.verification.automaticEligible) { preview=null;previewHit=null }
            else Dialog(onDismissRequest={ preview=null }) {
                Surface(shape=MaterialTheme.shapes.large) {
                    Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("Arşiv kaynak sayfası $number",style=MaterialTheme.typography.titleLarge)
                        Text("Aşağıdaki metin arşivin sayfa katmanından okundu; PDF önizlemesi değildir.",style=MaterialTheme.typography.bodySmall)
                        val url=doc.sourcePages[number]
                        if(url!=null) OutlinedButton(onClick={
                            runCatching { PdfPolicy.source(url);context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
                        }) { Text("ORİJİNAL SAYFAYI TARAYICIDA AÇ") }
                        val sourceText=doc.extraction.pages.firstOrNull { it.pageNumber==number }?.text ?: "Sayfa metni bu analizde bulunmuyor."
                        val needle=previewHit?.evidenceLine.orEmpty()
                        val first=if(needle.isNotBlank()) sourceText.indexOf(needle,ignoreCase=true) else -1
                        val unique=first>=0 && sourceText.indexOf(needle,first+1,ignoreCase=true)<0
                        if(needle.isNotBlank() && !unique) Text("Kaynak satırının konumu kesin belirlenemedi; metni inceleyebilirsin.",style=MaterialTheme.typography.bodySmall)
                        val highlighted=androidx.compose.ui.text.buildAnnotatedString {
                            append(sourceText)
                            if(unique) addStyle(androidx.compose.ui.text.SpanStyle(background=Color(0x66FFCA28)),first,first+needle.length)
                        }
                        SelectionContainer { Text(highlighted,style=MaterialTheme.typography.bodySmall) }
                        TextButton(onClick={ preview=null }) { Text("KAPAT") }
                    }
                }
            }
        } }
        saveDialog?.let { saved -> AnalysisSaveDialog(saved,workspace,onSaved={ Toast.makeText(context,"Analiz ve notlar kaydedildi.",Toast.LENGTH_SHORT).show() },onDismiss={ saveDialog=null }) }
        state.analysis?.let { doc ->
            if(pinDialog) PinoutDialog(doc,part,onPage={ previewHit=null;preview=it },onDismiss={ pinDialog=false })
            if(variantDialog) PartVariantDialog(part,remember(doc,part) { PartVariants.find(doc,part) },onSelect=onVariant,onDismiss={ variantDialog=false })
            if(conditionsDialog) ConditionsDialog(doc,onPage={ previewHit=null;preview=it },onDismiss={ conditionsDialog=false })
        }
        if(guide) TermGuideDialog(onDismiss={ guide=false })
    }
}
