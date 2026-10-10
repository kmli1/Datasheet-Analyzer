package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpHtmlFetcher : HtmlFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    private val hosts = setOf("html.duckduckgo.com", "duckduckgo.com")
    @Volatile private var retryAtMs = 0L

    override suspend fun fetch(url: String): String {
        if (System.currentTimeMillis() < retryAtMs) {
            throw SearchFailure(SearchFailureKind.RATE_LIMITED, "Arama kaynağı bekleme istedi. Daha sonra yeniden dene.")
        }
        var current = url
        repeat(3) { redirectIndex ->
            val uri = URI(current)
            require(uri.scheme == "https" && uri.host in hosts && uri.rawUserInfo == null &&
                (uri.port == -1 || uri.port == 443))
            val result = request(current)
            if (result.status in setOf(301, 302, 303, 307, 308)) {
                val location = result.location ?: throw SearchFailure(SearchFailureKind.HTTP, "Arama yönlendirmesi eksik.")
                if (redirectIndex == 2) throw SearchFailure(SearchFailureKind.HTTP, "Arama kaynağı çok fazla yönlendirme yaptı.")
                current = uri.resolve(location).toString()
            } else {
                when (result.status) {
                    200 -> return result.html
                    202, 403 -> {
                        retryAtMs = System.currentTimeMillis() + 60_000
                        throw SearchFailure(SearchFailureKind.BLOCKED,
                            "Arama kaynağı erişimi sınırladı veya doğrulama istedi. En az bir dakika sonra yeniden dene.")
                    }
                    429, 503 -> {
                        val seconds = retrySeconds(result.retryAfter)
                        retryAtMs = System.currentTimeMillis() + seconds * 1000
                        throw SearchFailure(SearchFailureKind.RATE_LIMITED,
                            "Arama kaynağı yoğun. Yaklaşık $seconds saniye sonra yeniden dene.")
                    }
                    else -> throw SearchFailure(SearchFailureKind.HTTP, "Arama kaynağı yanıt vermedi (HTTP ${result.status}).")
                }
            }
        }
        throw SearchFailure(SearchFailureKind.HTTP, "Arama yönlendirmesi tamamlanamadı.")
    }

    private fun retrySeconds(header: String?): Long {
        val numeric = header?.toLongOrNull()
        val dateSeconds = runCatching {
            (ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() -
                System.currentTimeMillis()) / 1000
        }.getOrNull()
        return (numeric ?: dateSeconds ?: 60L).coerceIn(3L, 3600L)
    }

    private suspend fun request(url: String): HtmlResponse = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url)
            .header("User-Agent", "DatasheetAnalyzer/0.3 (Android; datasheet discovery)")
            .header("Accept", "text/html").get().build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val failure = when (e) {
                    is UnknownHostException -> SearchFailure(SearchFailureKind.CONNECTION, "İnternet veya DNS bağlantısı kurulamadı.")
                    is SocketTimeoutException -> SearchFailure(SearchFailureKind.TIMEOUT, "Arama zaman aşımına uğradı.")
                    is SSLException -> SearchFailure(SearchFailureKind.CONNECTION, "Güvenli bağlantı kurulamadı.")
                    else -> SearchFailure(SearchFailureKind.CONNECTION, "Arama bağlantısı kesildi. İnternetini kontrol et.")
                }
                if (continuation.isActive) continuation.resumeWithException(failure)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val html = if (it.code == 200) readHtml(it) else ""
                        HtmlResponse(it.code, it.header("Location"), it.header("Retry-After"), html)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun readHtml(response: Response): String {
        val body = response.body ?: throw SearchFailure(SearchFailureKind.HTTP, "Arama yanıtı boş.")
        val type = body.contentType()
        if (type?.subtype != "html" && type?.subtype != "xhtml+xml") {
            throw SearchFailure(SearchFailureKind.FORMAT_CHANGED, "Arama kaynağı HTML sayfası döndürmedi.")
        }
        val limit = 512 * 1024
        if (body.contentLength() > limit) throw SearchFailure(SearchFailureKind.RESPONSE_TOO_LARGE, "Arama sayfası boyut sınırını aştı.")
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size() + count > limit) throw SearchFailure(SearchFailureKind.RESPONSE_TOO_LARGE, "Arama sayfası boyut sınırını aştı.")
                output.write(buffer, 0, count)
            }
        }
        return output.toString(type?.charset(Charsets.UTF_8)?.name() ?: "UTF-8")
    }

    private data class HtmlResponse(val status: Int, val location: String?, val retryAfter: String?, val html: String)
}
