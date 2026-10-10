package com.example.datasheetanalyzer.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.security.DocumentStatus

@Composable
fun DocumentResultCard(result: AnalyzedDocument, onPage: (Int)->Unit) {
    var details by rememberSaveable(result.checked.pdf.sha256) { mutableStateOf(false) }
    var tab by rememberSaveable(result.checked.pdf.sha256) { mutableIntStateOf(0) }
    var pageIndex by rememberSaveable(result.checked.pdf.sha256) { mutableIntStateOf(0) }
    var raw by rememberSaveable(result.checked.pdf.sha256) { mutableStateOf(false) }
    val verification=result.verification
    val pages=result.extraction.pages
    Card(border=BorderStroke(1.dp,MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("BELGE ANALİZİ",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)
            val status=when(verification.status) {
                DocumentStatus.VERIFIED -> "Doğrulandı"
                DocumentStatus.LIKELY_VALID -> "Büyük olasılıkla uygun"
                DocumentStatus.REVIEW_REQUIRED -> "İnceleme gerekli"
                DocumentStatus.REJECTED -> "Reddedildi"
                DocumentStatus.UNKNOWN -> "Belirsiz"
            }
            Text(status,fontWeight=FontWeight.Bold,
                style=MaterialTheme.typography.headlineSmall)
            Text("${result.candidate.host} · ${result.extraction.pages.size}/${result.checked.pages} sayfa okundu",color=MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("ÖZET","BÖLÜMLER","METİN").forEachIndexed { index,label ->
                    TextButton(onClick={ tab=index },modifier=Modifier.weight(1f)) {
                        Text(label,color=if(tab==index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            when(tab) {
                0 -> {
                    Text(if(verification.automaticEligible) "Belge kimliği ve bölümleri doğrulandı." else "Belge kimliği henüz okunamadı veya yeterince doğrulanmadı.")
                    Text("Üretici: ${verification.manufacturer.detected.joinToString().ifEmpty { "Tespit edilemedi" }}")
                    Text(if(verification.automaticEligible) "Otomatik devam eşiğini karşılıyor."
                        else "Otomatik devam için yeterli kanıt yok; elle incele.")
                    OutlinedButton(onClick={ onPage(1) }) { Text(if(result.sourceKind==DocumentSource.ARCHIVE_HTML) "KAYNAK SAYFAYI GÖR" else "PDF’Yİ İNCELE") }
                    Text("Puan bir olasılık yüzdesi değildir.",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={ details=!details }) { Text(if(details) "AYRINTILARI GİZLE" else "KONTROL AYRINTILARI") }
                    if(details) {
                        Text("Belge kanıt puanı: ${verification.score}/100",style=MaterialTheme.typography.bodySmall)
                        verification.evidence.forEach { evidence -> Text("${evidence.pageNumber?.let { "Sayfa $it" } ?: "Başlık"}: ${evidence.text}",style=MaterialTheme.typography.bodySmall) }
                        verification.reasons.forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
                        result.attempts.forEach { Text(it,style=MaterialTheme.typography.bodySmall) }
                    }
                    Text(if(result.sourceKind==DocumentSource.ARCHIVE_HTML) "HTTPS kaynağı ve HTML sayfa kimliği kontrol edildi. PDF içerik taraması yapılmadı."
                        else "PDF kaynak/başlık/boyut ve izole yapı kontrolleri geçti. Bu kontroller antivirus garantisi değildir.",style=MaterialTheme.typography.bodySmall)

                }
                1 -> {
                    if(result.sections.isEmpty()) Text("Bölüm başlığı tespit edilemedi.")
                    result.sections.forEach { section ->
                        OutlinedButton(onClick={
                            pageIndex=pages.indexOfFirst { it.pageNumber==section.pageNumber }.coerceAtLeast(0)
                            raw=false;tab=2
                        },modifier=Modifier.fillMaxWidth()) {
                            Text("${section.section.label} · s.${section.pageNumber}")
                        }
                    }
                }
                2 -> {
                    val page=pages.getOrNull(pageIndex)
                    if(page==null) Text("Gösterilecek metin yok.") else {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            TextButton(onClick={ pageIndex-- },enabled=pageIndex>0) { Text("←") }
                            Text("Sayfa ${page.pageNumber}/${result.extraction.totalPages}",modifier=Modifier.padding(top=12.dp))
                            TextButton(onClick={ pageIndex++ },enabled=pageIndex<pages.lastIndex) { Text("→") }
                        }
                        OutlinedButton(onClick={ onPage(page.pageNumber) }) { Text("DATASHEET’TE GÖSTER · s.${page.pageNumber}") }
                        Text(if(page.origin==TextOrigin.OCR) "OCR ile okundu" else "PDF metin katmanı",
                            color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Switch(checked=raw,onCheckedChange={ raw=it })
                            Text("Ham metni göster",modifier=Modifier.padding(start=8.dp))
                        }
                        if(page.truncated) Text("Metin sınır nedeniyle kısaltıldı.",color=MaterialTheme.colorScheme.error)
                        SelectionContainer {
                            Text((if(raw) page.rawText else page.text).ifBlank { "Bu sayfada okunabilir metin bulunamadı." },
                                fontFamily=FontFamily.Monospace,style=MaterialTheme.typography.bodySmall)
                        }
                        if(raw && page.nativeRawText!=null) {
                            Text("OCR öncesi PDF metin katmanı:",style=MaterialTheme.typography.labelMedium)
                            SelectionContainer { Text(page.nativeRawText.ifBlank { "Boş" },style=MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
