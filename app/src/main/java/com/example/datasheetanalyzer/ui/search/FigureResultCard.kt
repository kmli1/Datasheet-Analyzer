package com.example.datasheetanalyzer.ui.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.export.*
import com.example.datasheetanalyzer.figures.FigurePreviewStore
import kotlinx.coroutines.*
import java.io.File

@Composable
fun FigureResultCard(result: FigureResult,document: AnalyzedDocument,part: String,onPage: (Int)->Unit) {
    Card {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(result.requested,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(if(result.matches.isEmpty()) "Grafik bulunamadı" else "Başlık eşleşti",color=MaterialTheme.colorScheme.primary)
            if(result.matches.isEmpty()) Text(result.explanation,style=MaterialTheme.typography.bodySmall)
            result.matches.forEach { match -> key(match.page,match.titleBounds) { SourceFigurePreview(match,document,part,onPage) } }
        }
    }
}

@Composable
fun SourceFigurePreview(match: FigureMatch,document: AnalyzedDocument,part: String,onPage: (Int)->Unit,kind: String="Grafik",allowReading: Boolean=true) {
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) };var cropped by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) };var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) };var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val path=pending;pending=null
        if(path!=null) {
            if(uri==null) ExportFiles.discard(context,path) else scope.launch {
                busy=true
                try { ExportFiles.save(context,path,uri);Toast.makeText(context,"Görsel PNG olarak kaydedildi.",Toast.LENGTH_LONG).show() }
                catch(c: CancellationException) { throw c }
                catch(_: Exception) { Toast.makeText(context,"Görsel kaydedilemedi. Farklı bir konum deneyebilirsin.",Toast.LENGTH_LONG).show() }
                finally { busy=false }
            }
        }
    }
    LaunchedEffect(document.checked.pdf.sha256,match) {
        loading=true;error=null
        try {
            val preview=FigurePreviewStore.prepare(context,document.checked.pdf.file,document.checked.pdf.sha256,match)
            val decoded=withContext(Dispatchers.IO) { BitmapFactory.decodeFile(preview.file.path) ?: error("PNG_DECODE") }
            bitmap=decoded;cropped=preview.cropped
        } catch(c: CancellationException) { throw c }
          catch(_: Exception) { error="Önizleme açılamadı; kaynak sayfasından grafiği inceleyebilirsin." }
        finally { loading=false }
    }
    Text("${match.title} · kaynak s.${match.page}",style=MaterialTheme.typography.bodySmall)
    if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    error?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
    bitmap?.let { image ->
        Text(if(cropped) "$kind önizlemesi" else "$kind için tam kaynak sayfası gösteriliyor. İlgili alanı kaynak sayfasında seçebilirsin.",style=MaterialTheme.typography.labelSmall)
        Image(image.asImageBitmap(),contentDescription="${match.title}, kaynak sayfa ${match.page}",contentScale=ContentScale.Fit,
            modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=360.dp))
        OutlinedButton(onClick={ scope.launch {
            busy=true;var staged: File?=null
            try {
                withContext(Dispatchers.IO) { staged=ExportFiles.stagePng(context,image,null);ensureActive() }
                pending=staged!!.path;picker.launch(AnalysisReportContent.filename(part,"s${match.page}-${if(cropped) "grafik" else "sayfa"}","png"));staged=null
            } catch(c: CancellationException) { throw c }
              catch(_: Exception) { pending=null;Toast.makeText(context,"Görsel hazırlanamadı.",Toast.LENGTH_LONG).show() }
            finally { staged?.delete();busy=false }
        } },enabled=!busy && pending==null,modifier=Modifier.fillMaxWidth()) { Text(if(busy) "GÖRSEL HAZIRLANIYOR…" else "PNG OLARAK KAYDET") }
    }
    if(allowReading && bitmap!=null && document.verification.automaticEligible) {
        OutlinedButton(onClick={ reading=true },modifier=Modifier.fillMaxWidth()) { Text("GRAFİKTEN YAKLAŞIK DEĞER OKU") }
        if(reading) GraphReadingDialog(bitmap!!,match.title,match.page,part,onDismiss={ reading=false })
    }
    OutlinedButton(onClick={ onPage(match.page) },modifier=Modifier.fillMaxWidth()) { Text("KAYNAK SAYFAYI AÇ · s.${match.page}") }
}
