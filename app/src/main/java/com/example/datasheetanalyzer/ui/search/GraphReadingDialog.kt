package com.example.datasheetanalyzer.ui.search

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.datasheetanalyzer.domain.document.FigureBounds
import com.example.datasheetanalyzer.domain.extraction.OperatingConditions
import com.example.datasheetanalyzer.export.*
import com.example.datasheetanalyzer.figures.*
import java.util.Locale

@Composable
fun GraphReadingDialog(image: Bitmap,title: String,page: Int,part: String,onDismiss: ()->Unit) {
    var xmin by rememberSaveable { mutableStateOf("") };var xmax by rememberSaveable { mutableStateOf("") }
    var ymin by rememberSaveable { mutableStateOf("") };var ymax by rememberSaveable { mutableStateOf("") }
    var xunit by rememberSaveable { mutableStateOf("") };var yunit by rememberSaveable { mutableStateOf("") }
    var xlog by rememberSaveable { mutableStateOf(false) };var ylog by rememberSaveable { mutableStateOf(false) }
    var axes by remember { mutableStateOf<Pair<CalibratedAxis,CalibratedAxis>?>(null) }
    var lowerLeft by remember { mutableStateOf<Offset?>(null) };var upperRight by remember { mutableStateOf<Offset?>(null) }
    var calibration by remember { mutableStateOf<GraphCalibration?>(null) }
    var points by remember { mutableStateOf(emptyList<ApproximateGraphPoint>()) };var selected by remember { mutableStateOf<Offset?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun display(v: Double)=String.format(Locale.US,"%.5g",v).replace('.',',')
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Grafikten yaklaşık okuma",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                    TextButton(onClick=onDismiss) { Text("KAPAT") }
                }
                Text("$title · kaynak s.$page",style=MaterialTheme.typography.bodySmall,maxLines=2)
                if(axes==null) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("Grafikteki eksen sınırlarını ve birimleri gir. Soldan sağa ve alttan üste artan iki eksenli grafikleri destekler. Değerler otomatik tahmin edilmez.",style=MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(xmin,{ xmin=it.take(30) },label={ Text("X min") },singleLine=true,modifier=Modifier.weight(1f))
                            OutlinedTextField(xmax,{ xmax=it.take(30) },label={ Text("X maks") },singleLine=true,modifier=Modifier.weight(1f))
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(ymin,{ ymin=it.take(30) },label={ Text("Y min") },singleLine=true,modifier=Modifier.weight(1f))
                            OutlinedTextField(ymax,{ ymax=it.take(30) },label={ Text("Y maks") },singleLine=true,modifier=Modifier.weight(1f))
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(xunit,{ xunit=it.take(40) },label={ Text("X birim") },singleLine=true,modifier=Modifier.weight(1f))
                            OutlinedTextField(yunit,{ yunit=it.take(40) },label={ Text("Y birim") },singleLine=true,modifier=Modifier.weight(1f))
                        }
                        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(xlog,{ xlog=it });Text("X logaritmik") }
                        Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(ylog,{ ylog=it });Text("Y logaritmik") }
                        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                        OutlinedButton(onClick={
                            try {
                                axes=CalibratedAxis(OperatingConditions.parse(xmin) ?: error("X"),OperatingConditions.parse(xmax) ?: error("X"),xunit,
                                    if(xlog) AxisScale.LOGARITHMIC else AxisScale.LINEAR) to
                                    CalibratedAxis(OperatingConditions.parse(ymin) ?: error("Y"),OperatingConditions.parse(ymax) ?: error("Y"),yunit,
                                        if(ylog) AxisScale.LOGARITHMIC else AxisScale.LINEAR)
                                error=null
                            } catch(_: IllegalArgumentException) { error="Min, maks değerinden küçük olmalı; logaritmik eksende iki sınır da pozitif olmalı." }
                              catch(_: IllegalStateException) { error="Dört eksen sınırını geçerli sayılarla doldur." }
                        },modifier=Modifier.fillMaxWidth()) { Text("EKSENLERİ AYARLA") }
                    }
                } else {
                    Text(when {
                        lowerLeft==null -> "1. Grafikte X min / Y min kesişimine (sol alt) dokun."
                        upperRight==null -> "2. X maks / Y maks kesişimine (sağ üst) dokun."
                        else -> "3. Okumak istediğin eğrinin üstündeki bir noktaya dokun."
                    },style=MaterialTheme.typography.bodySmall)
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                    points.lastOrNull()?.let { p -> Text("≈ X ${display(p.x)} $xunit · Y ${display(p.y)} $yunit",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.titleMedium) }
                    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                        Image(image.asImageBitmap(),contentDescription="Okuma için kaynak grafik",contentScale=ContentScale.Fit,
                            modifier=Modifier.fillMaxSize().pointerInput(image,axes,lowerLeft,upperRight,calibration,points) {
                                detectTapGestures { at ->
                                    if(size.width<=0 || size.height<=0) return@detectTapGestures
                                    val fit=PreviewCrop.fit(size.width.toFloat(),size.height.toFloat(),image.width,image.height)
                                    val nx=(at.x-fit.left)/fit.width;val ny=(at.y-fit.top)/fit.height
                                    if(nx !in 0f..1f || ny !in 0f..1f) return@detectTapGestures
                                    val p=Offset(nx,ny);error=null
                                    if(lowerLeft==null) lowerLeft=p
                                    else if(upperRight==null) {
                                        val first=lowerLeft!!
                                        if(p.x-first.x<.02f || first.y-p.y<.02f) error="Sağ üst nokta sol altın sağında ve üstünde olmalı."
                                        else {
                                            upperRight=p;calibration=GraphCalibration(FigureBounds(first.x,p.y,p.x,first.y),axes!!.first,axes!!.second)
                                        }
                                    } else {
                                        val read=calibration!!.read(nx,ny,image.width,image.height)
                                        if(read==null) error="Kalibre edilen grafik alanının içindeki bir noktaya dokun."
                                        else if(points.size>=20) error="20 nokta sınırına ulaşıldı. CSV kaydet veya son noktayı kaldır."
                                        else { points=points+read;selected=p }
                                    }
                                }
                            })
                        Canvas(Modifier.fillMaxSize()) {
                            if(size.width<=0 || size.height<=0) return@Canvas
                            val fit=PreviewCrop.fit(size.width,size.height,image.width,image.height)
                            calibration?.plot?.let { r ->
                                drawRect(Color(0xFFFFCA28),Offset(fit.left+r.left*fit.width,fit.top+r.top*fit.height),
                                    androidx.compose.ui.geometry.Size((r.right-r.left)*fit.width,(r.bottom-r.top)*fit.height),style=Stroke(2.dp.toPx()))
                            }
                            listOfNotNull(lowerLeft,upperRight,selected).forEach { p ->
                                val x=fit.left+p.x*fit.width;val y=fit.top+p.y*fit.height
                                drawLine(Color(0xFFFFCA28),Offset(x-8.dp.toPx(),y),Offset(x+8.dp.toPx(),y),2.dp.toPx())
                                drawLine(Color(0xFFFFCA28),Offset(x,y-8.dp.toPx()),Offset(x,y+8.dp.toPx()),2.dp.toPx())
                            }
                        }
                    }
                    if(points.isNotEmpty()) {
                        Text("${points.size}/20 yaklaşık nokta · 2 kaynak pikseli payı; çizgi kalınlığı ve dokunma hatası daha büyük olabilir.",style=MaterialTheme.typography.labelSmall)
                        CsvExportButton("YAKLAŞIK NOKTALARI CSV KAYDET",AnalysisReportContent.filename(part,"s$page-yaklasik","csv")) {
                            AnalysisCsvWriter.graph(part,title,page,calibration!!,points)
                        }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        TextButton(onClick={ axes=null;lowerLeft=null;upperRight=null;calibration=null;points=emptyList();selected=null;error=null },modifier=Modifier.weight(1f)) { Text("YENİDEN KALİBRE ET") }
                        if(points.isNotEmpty()) TextButton(onClick={ points=points.dropLast(1);selected=null },modifier=Modifier.weight(1f)) { Text("SON NOKTAYI SİL") }
                    }
                }
                Text("Elle seçilen yaklaşık grafik okuması; datasheet'in sayısal sonuç tablosuna eklenmez.",style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}
