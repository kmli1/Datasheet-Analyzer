package com.example.datasheetanalyzer.ui.search

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.datasheetanalyzer.export.ExportFiles
import kotlinx.coroutines.*
import java.io.File

@Composable
fun CsvExportButton(label: String,filename: String,enabled: Boolean=true,content: ()->String) {
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) };var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val path=pending;pending=null
        if(path!=null) {
            if(uri==null) ExportFiles.discard(context,path) else scope.launch {
                busy=true
                try { ExportFiles.save(context,path,uri);Toast.makeText(context,"CSV kaydedildi.",Toast.LENGTH_LONG).show() }
                catch(c: CancellationException) { throw c }
                catch(_: Exception) { Toast.makeText(context,"CSV kaydedilemedi. Başka konum deneyebilirsin.",Toast.LENGTH_LONG).show() }
                finally { busy=false }
            }
        }
    }
    OutlinedButton(onClick={ scope.launch {
        busy=true;var staged: File?=null
        try {
            val text=content()
            withContext(Dispatchers.IO) { staged=ExportFiles.stageCsv(context,text);ensureActive() }
            pending=staged!!.path;picker.launch(filename);staged=null
        } catch(c: CancellationException) { throw c }
          catch(_: Exception) { pending=null;Toast.makeText(context,"CSV hazırlanamadı.",Toast.LENGTH_LONG).show() }
        finally { staged?.delete();busy=false }
    } },enabled=enabled && !busy && pending==null,modifier=Modifier.fillMaxWidth()) { Text(if(busy) "CSV HAZIRLANIYOR…" else label) }
}
