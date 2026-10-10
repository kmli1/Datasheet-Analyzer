package com.example.datasheetanalyzer.ui.search

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.datasheetanalyzer.export.*
import com.example.datasheetanalyzer.sandbox.IsolatedPdfClient
import com.example.datasheetanalyzer.domain.document.*
import kotlinx.coroutines.*
import java.io.File

/** The saved PNG is the source raster (or user-selected crop), never a reconstructed chart. */
@Composable
fun PdfPageDialog(pdf: File,initialPage: Int,totalPages: Int,part: String="datasheet",evidence: String="",section: DatasheetSection?=null,allowGraphReading: Boolean=false,onDismiss: ()->Unit) {
    val context=LocalContext.current.applicationContext
    val client=remember(context) { IsolatedPdfClient(context) };val scope=rememberCoroutineScope()
    val maximum=minOf(totalPages,80).coerceAtLeast(1)
    var page by remember(pdf,initialPage) { mutableIntStateOf(initialPage.coerceIn(1,maximum)) }
    var readingImage by remember(page) { mutableStateOf<Bitmap?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var zoom by remember(page) { mutableFloatStateOf(1f) }
    var offsetX by remember(page) { mutableFloatStateOf(0f) }
    var offsetY by remember(page) { mutableFloatStateOf(0f) }
    var cropMode by remember(page) { mutableStateOf(false) }
    var crop by remember(page) { mutableStateOf<NormalizedCrop?>(null) }
    var dragStart by remember(page) { mutableStateOf(Offset.Zero) }
    var dragEnd by remember(page) { mutableStateOf(Offset.Zero) }
    var exporting by remember { mutableStateOf(false) }
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var highlights by remember(page,evidence) { mutableStateOf(emptyList<FigureBounds>()) }
    var highlightLoading by remember(page,evidence) { mutableStateOf(false) }
    var highlightMessage by remember(page,evidence) { mutableStateOf<String?>(null) }
    LaunchedEffect(pdf,page,evidence,section,bitmap) {
        highlights=emptyList();highlightMessage=null
        if(page!=initialPage || evidence.length<3 || bitmap==null) { highlightLoading=false;return@LaunchedEffect }
        highlightLoading=true
        try {
            highlights=client.findEvidence(pdf,page,evidence.take(600),section)
            highlightMessage=if(highlights.isEmpty()) "Bu kaynakta metin konumu kesin belirlenemedi; kanıt satırını kaynak sayfasında incele." else "Eşleşen kaynak satırı sarı çerçeveyle işaretlendi."
        } catch(c: CancellationException) { throw c }
          catch(_: Exception) { highlightMessage="Kaynak konumu belirlenemedi; sayfayı inceleyebilirsin." }
        finally { highlightLoading=false }
    }
    val activeReading=readingImage
    DisposableEffect(activeReading) { val original=bitmap;onDispose { if(activeReading!=null && activeReading!==original) activeReading.recycle() } }
    if(activeReading!=null) GraphReadingDialog(activeReading,"Seçilen kaynak grafik bölgesi",page,part,onDismiss={ readingImage=null })
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val path=pending;pending=null
        if(path!=null) {
            if(uri==null) { ExportFiles.discard(context,path);exporting=false }
            else scope.launch {
                exporting=true
                try { ExportFiles.save(context,path,uri);Toast.makeText(context,"PNG görseli kaydedildi.",Toast.LENGTH_LONG).show() }
                catch(c: CancellationException) { throw c }
                catch(_: Exception) { Toast.makeText(context,"Görsel kaydedilemedi. Farklı bir konum deneyebilirsin.",Toast.LENGTH_LONG).show() }
                finally { exporting=false }
            }
        }
    }
    LaunchedEffect(pdf,page) {
        bitmap=null;error=null;loading=true
        var png: File?=null
        try {
            val decoded=withContext(Dispatchers.IO) {
                try {
                    png=client.renderPage(pdf,page)
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeFile(png!!.absolutePath,bounds)
                    require(bounds.outWidth in 1..3200 && bounds.outHeight in 1..3200 &&
                        bounds.outWidth.toLong()*bounds.outHeight<=2_000_000 && png!!.length()<=8L*1024*1024)
                    BitmapFactory.decodeFile(png!!.absolutePath) ?: error("PNG_DECODE")
                } finally { png?.delete() }
            }
            bitmap=decoded
        } catch(c: CancellationException) { throw c }
          catch(_: Exception) { error="Sayfa görüntülenemedi. Tekrar açmayı deneyebilirsin." }
        finally { loading=false;png?.delete() }
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Datasheet · s.$page/$totalPages",style=MaterialTheme.typography.titleMedium)
                    TextButton(onClick=onDismiss) { Text("KAPAT") }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    OutlinedButton(onClick={ page-- },enabled=page>1 && !exporting && pending==null) { Text("← ÖNCEKİ") }
                    TextButton(onClick={ zoom=1f;offsetX=0f;offsetY=0f }) { Text("SIĞDIR") }
                    OutlinedButton(onClick={ page++ },enabled=page<maximum && !exporting && pending==null) { Text("SONRAKİ →") }
                }
                Text(if(cropMode) "Grafiğin etrafında parmağınla bir alan çiz." else "İki parmakla yakınlaştır ve kaydır.",style=MaterialTheme.typography.bodySmall)
                if(page==initialPage && evidence.isNotBlank()) Text("Kanıt: $evidence",style=MaterialTheme.typography.labelSmall,maxLines=3)
                if(highlightLoading) Text("Kaynak satırının konumu bulunuyor…",style=MaterialTheme.typography.labelSmall)
                highlightMessage?.let { Text(it,style=MaterialTheme.typography.labelSmall) }
                if(totalPages>80) Text("Önizleme ilk 80 sayfayla sınırlı.",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={ cropMode=!cropMode;crop=null;zoom=1f;offsetX=0f;offsetY=0f },
                        enabled=bitmap!=null && !exporting && pending==null,modifier=Modifier.weight(1f)) {
                        Text(if(cropMode) "TÜM SAYFA" else "GRAFİK ALANI SEÇ")
                    }
                    OutlinedButton(onClick={
                        val image=bitmap ?: return@OutlinedButton
                        val selected=if(cropMode) crop else null
                        val selectedPage=page
                        scope.launch {
                            exporting=true;var staged: File?=null
                            try {
                                withContext(Dispatchers.IO) { staged=ExportFiles.stagePng(context,image,selected);ensureActive() }
                                pending=staged!!.absolutePath
                                save.launch(AnalysisReportContent.filename(part,"s$selectedPage-${if(selected==null) "sayfa" else "grafik"}","png"));staged=null
                            } catch(c: CancellationException) { throw c }
                              catch(_: Exception) { pending=null;Toast.makeText(context,"Görsel hazırlanamadı. Yeniden deneyebilirsin.",Toast.LENGTH_LONG).show() }
                            finally { staged?.delete();exporting=false }
                        }
                    },enabled=bitmap!=null && !loading && !exporting && pending==null && (!cropMode || crop!=null),modifier=Modifier.weight(1f)) {
                        Text(if(exporting) "KAYDEDİLİYOR…" else "PNG KAYDET")
                    }
                }
                if(allowGraphReading) TextButton(onClick={
                    val original=bitmap ?: return@TextButton
                    val selected=if(cropMode) crop else null
                    readingImage=if(selected==null) original else PreviewCrop.pixels(selected,original.width,original.height).let {
                        Bitmap.createBitmap(original,it.left,it.top,it.width,it.height)
                    }
                },enabled=bitmap!=null && !loading && (!cropMode || crop!=null) && !exporting && pending==null,
                    modifier=Modifier.fillMaxWidth()) { Text("GRAFİKTEN YAKLAŞIK DEĞER OKU") }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(),contentAlignment=Alignment.Center) {
                    if(loading) CircularProgressIndicator()
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    bitmap?.let { image ->
                        Image(image.asImageBitmap(),contentDescription="Datasheet sayfa $page",contentScale=ContentScale.Fit,
                            modifier=Modifier.fillMaxSize().pointerInput(page,cropMode,image) {
                                if(cropMode) {
                                    if(size.width<=0 || size.height<=0) return@pointerInput
                                    val fit=PreviewCrop.fit(size.width.toFloat(),size.height.toFloat(),image.width,image.height)
                                    detectDragGestures(onDragStart={ dragStart=it;dragEnd=it;crop=null },onDrag={ change,_ ->
                                        change.consume();dragEnd=change.position
                                        crop=PreviewCrop.selection(fit,dragStart.x,dragStart.y,dragEnd.x,dragEnd.y)
                                    })
                                } else detectTransformGestures { _,pan,scale,_ ->
                                    zoom=(zoom*scale).coerceIn(1f,4f)
                                    offsetX=(offsetX+pan.x).coerceIn(-size.width*(zoom-1)/2,size.width*(zoom-1)/2)
                                    offsetY=(offsetY+pan.y).coerceIn(-size.height*(zoom-1)/2,size.height*(zoom-1)/2)
                                }
                            }.graphicsLayer { scaleX=zoom;scaleY=zoom;translationX=offsetX;translationY=offsetY })
                        val accent=MaterialTheme.colorScheme.primary
                        if(!cropMode && highlights.isNotEmpty()) Canvas(Modifier.fillMaxSize().graphicsLayer {
                            scaleX=zoom;scaleY=zoom;translationX=offsetX;translationY=offsetY
                        }) {
                            if(size.width<=0 || size.height<=0) return@Canvas
                            val fit=PreviewCrop.fit(size.width,size.height,image.width,image.height)
                            highlights.forEach { r ->
                                val point=Offset(fit.left+r.left*fit.width,fit.top+r.top*fit.height)
                                val box=Size((r.right-r.left)*fit.width,(r.bottom-r.top)*fit.height)
                                drawRect(androidx.compose.ui.graphics.Color(0xFFFFCA28).copy(alpha=.18f),point,box)
                                drawRect(androidx.compose.ui.graphics.Color(0xFFFFCA28),point,box,style=Stroke(2.dp.toPx()))
                            }
                        }
                        if(cropMode) Canvas(Modifier.fillMaxSize()) {
                            if(size.width<=0 || size.height<=0) return@Canvas
                            val fit=PreviewCrop.fit(size.width,size.height,image.width,image.height)
                            crop?.let { selection ->
                                val point=Offset(fit.left+selection.left*fit.width,fit.top+selection.top*fit.height)
                                val selectedSize=Size((selection.right-selection.left)*fit.width,(selection.bottom-selection.top)*fit.height)
                                drawRect(accent.copy(alpha=0.15f),point,selectedSize)
                                drawRect(accent,point,selectedSize,style=Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }
}
