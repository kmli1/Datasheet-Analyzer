package com.example.datasheetanalyzer.ui.search

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.datasheetanalyzer.domain.document.AnalyzedDocument
import com.example.datasheetanalyzer.domain.extraction.ParameterReport
import com.example.datasheetanalyzer.export.*
import com.example.datasheetanalyzer.figures.FigurePreviewStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

@Composable
fun ReportExportButton(part: String,document: AnalyzedDocument,report: ParameterReport,enabled: Boolean=true) {
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val path=pending;pending=null
        if(path!=null) {
            if(uri==null) { ExportFiles.discard(context,path);busy=false }
            else scope.launch {
                busy=true
                try { ExportFiles.save(context,path,uri);Toast.makeText(context,"Analiz PDF olarak kaydedildi.",Toast.LENGTH_LONG).show() }
                catch(c: CancellationException) { throw c }
                catch(_: Exception) { Toast.makeText(context,"PDF kaydedilemedi. Farklı bir konum deneyebilirsin.",Toast.LENGTH_LONG).show() }
                finally { busy=false }
            }
        }
    }
    OutlinedButton(onClick={ scope.launch {
        busy=true;var staged: File?=null
        try {
            val date=SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr")).format(Date())
            val blocks=AnalysisReportContent.build(part,document,report,date).toMutableList()
            var imageBytes=0L
            for(result in report.graphs) for(match in result.matches) {
                try {
                    val preview=FigurePreviewStore.prepare(context,document.checked.pdf.file,document.checked.pdf.sha256,match)
                    val bytes=withContext(Dispatchers.IO) { preview.file.readBytes() }
                    if(imageBytes+bytes.size<=8L*1024*1024) {
                        imageBytes+=bytes.size
                        blocks+=ReportBlock("${match.title} - kaynak s.${match.page} - ${if(preview.cropped) "grafik önizlemesi" else "tam kaynak sayfası"}",ReportStyle.IMAGE,image=ReportImage(bytes))
                    } else blocks+=ReportBlock("${match.title}, s.${match.page}: Görsel boyut sınırından dolayı eklenmedi; kaynak sayfasından veya PNG çıktısından inceleyebilirsin.",ReportStyle.NOTE)
                } catch(c: CancellationException) { throw c }
                  catch(_: Exception) { blocks+=ReportBlock("${match.title}, s.${match.page}: Görsel hazırlanamadı; kaynak sayfasından inceleyebilirsin.",ReportStyle.NOTE) }
            }
            withContext(Dispatchers.IO) { staged=ExportFiles.stagePdf(context,blocks);ensureActive() }
            pending=staged!!.absolutePath;save.launch(AnalysisReportContent.filename(part,"analiz","pdf"));staged=null
        } catch(c: CancellationException) { throw c }
          catch(_: Exception) { pending=null;Toast.makeText(context,"Rapor oluşturulamadı. Yeniden deneyebilirsin.",Toast.LENGTH_LONG).show() }
        finally { staged?.delete();busy=false }
    } },enabled=enabled && !busy && pending==null,modifier=Modifier.fillMaxWidth()) {
        Text(if(busy) "PDF HAZIRLANIYOR…" else "SONUÇLARI PDF OLARAK KAYDET")
    }
}
