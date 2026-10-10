package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.resolver.SearchProvider
import com.example.datasheetanalyzer.domain.security.PdfPolicy
import com.example.datasheetanalyzer.domain.security.SecurityConfig
import java.util.Locale
import kotlinx.coroutines.*

/** Probe the exact requested code. A URL hint is never returned unless it actually serves a PDF. */
class ManufacturerPdfSearchProvider(private val http: SourceHttp): SearchProvider {
    override suspend fun searchDatasheets(partNumber: String): List<SearchCandidate> {
        val part=PartNumberNormalizer.normalize(partNumber)
        require(PartNumberNormalizer.isValid(part))
        if(!Regex("[A-Z0-9-]{3,64}").matches(part)) return emptyList()
        val code=part.lowercase(Locale.ROOT)
        val hints=buildList {
            if(Regex("^(LM|NE|SN|TL|TPS|OPA|INA|UCC|TMS|DS|CD|ISO|ADS|DAC|MSP)[A-Z0-9-]*[0-9][A-Z0-9-]*$").matches(part))
                add("Texas Instruments" to "https://www.ti.com/lit/ds/symlink/$code.pdf")
            if(Regex("^(LM|NE|TL|STM|ST|L[0-9]|TS|TDA|ULN|VIPER)[A-Z0-9-]*$").matches(part))
                add("STMicroelectronics" to "https://www.st.com/resource/en/datasheet/$code.pdf")
            if(Regex("^(NCP|NCV|MC|2N|BC|BD|MMBT|MPS|LM)[A-Z0-9-]*[0-9][A-Z0-9-]*$").matches(part))
                add("onsemi" to "https://www.onsemi.com/pdf/datasheet/$code-d.pdf")
        }
        return coroutineScope {
            hints.map { (maker,url) -> async { probe(part,maker,url) } }.awaitAll().filterNotNull()
        }
    }
    private suspend fun probe(part: String,maker: String,url: String): SearchCandidate? = try {
        withTimeout(8_000) { withContext(Dispatchers.IO) {
            val followed=http.request(SafeRequest(url,headers=mapOf("Range" to "bytes=0-1023",
                "Accept" to "application/pdf", "Accept-Encoding" to "identity")))
            followed.response.use { response ->
                if(response.status !in setOf(200,206)) return@withContext null
                PdfPolicy.pdfMime(response.header("Content-Type"))
                PdfPolicy.length(response.header("Content-Length"),SecurityConfig().maxPdfSize)
                if(response.status==206 && response.header("Content-Range")?.startsWith("bytes 0-")!=true)
                    return@withContext null
                if(response.header("Content-Encoding")?.let { !it.equals("identity",true) }==true)
                    return@withContext null
                val bytes=ByteArray(1024);var count=0
                while(count<bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val n=response.body.read(bytes,count,bytes.size-count)
                    if(n<0) break
                    if(n==0) continue
                    count+=n
                }
                PdfPolicy.magic(bytes.copyOf(count))
                SearchCandidate("$part Datasheet — $maker",followed.url,
                    "Üreticinin PDF başlığı alındı. İçerik, güvenlik ve parça kimliği analizde doğrulanır.")
            }
        } }
    } catch(e: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive();null
    } catch(e: CancellationException) { throw e }
      catch(_: Exception) { null }
}
