package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import com.example.datasheetanalyzer.domain.security.SecurityConfig
import com.example.datasheetanalyzer.domain.security.SecurityFailure
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class SourceHtmlFetcher(private val http: SourceHttp, private val config: SecurityConfig = SecurityConfig()) : HtmlFetcher {
    suspend fun isPdfEndpoint(url: String): Boolean {
        val result=http.request(SafeRequest(url,"HEAD"))
        return result.response.use { r ->
            val mime=r.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
            r.status==200 && mime in setOf("application/pdf","application/octet-stream")
        }
    }
    override suspend fun fetch(url: String): String {
        val result=http.request(SafeRequest(url,headers=mapOf("Accept" to "text/html")))
        return result.response.use { r ->
            if(r.status in setOf(202,403,429,503)) throw SearchFailure(SearchFailureKind.BLOCKED,"Kaynak erişimi sınırladı. Daha sonra yeniden dene.")
            if(r.status!=200) throw SecurityFailure("HTTP","Kaynak sayfasına ulaşılamadı (HTTP ${r.status}).")
            if(r.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase() !in setOf("text/html","application/xhtml+xml"))
                throw SecurityFailure("HTML","Kaynak HTML sayfası döndürmedi.")
            val output=ByteArrayOutputStream();val b=ByteArray(8192)
            while(true) {
                currentCoroutineContext().ensureActive()
                val n=r.body.read(b);if(n<0) break
                if(output.size()+n>config.maxHtmlBytes) throw SecurityFailure("HTML_SIZE","Kaynak sayfası boyut sınırını aştı.")
                output.write(b,0,n)
            }
            output.toString("UTF-8")
        }
    }
}
