package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.security.PdfPolicy
import com.example.datasheetanalyzer.domain.security.SecurityFailure
import java.io.Closeable
import java.io.InputStream
import java.net.URI

data class SafeRequest(val url: String, val method: String = "GET", val headers: Map<String,String> = emptyMap())
class SafeResponse(val status: Int, val headers: Map<String,String>, val body: InputStream,
                   private val closeAction: () -> Unit = {}) : Closeable {
    fun header(key: String) = headers.entries.firstOrNull { it.key.equals(key,true) }?.value
    override fun close() { try { body.close() } finally { closeAction() } }
}
fun interface SafeHttpTransport { suspend fun execute(request: SafeRequest): SafeResponse }
data class FollowedResponse(val url: String, val response: SafeResponse)
class SourceHttp(private val transport: SafeHttpTransport, private val redirects: Int = 3) {
    suspend fun request(initial: SafeRequest): FollowedResponse {
        var request=initial.copy(url=PdfPolicy.source(initial.url).canonical)
        repeat(redirects+1) { step ->
            val response=transport.execute(request)
            if (response.status !in setOf(301,302,303,307,308)) return FollowedResponse(request.url,response)
            val location=response.header("Location")
            response.close()
            if (step==redirects || location==null) throw SecurityFailure("REDIRECT", "Yönlendirme sınırı veya eksik hedef.")
            val target=runCatching { URI(request.url).resolve(location).toString() }.getOrElse {
                throw SecurityFailure("REDIRECT", "Geçersiz yönlendirme hedefi.")
            }
            request=request.copy(url=PdfPolicy.source(target).canonical)
        }
        throw SecurityFailure("REDIRECT", "Yönlendirme tamamlanamadı.")
    }
}
