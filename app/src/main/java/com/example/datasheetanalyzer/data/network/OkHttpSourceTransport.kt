package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.security.PdfPolicy
import com.example.datasheetanalyzer.domain.security.SecurityFailure
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
class OkHttpSourceTransport : SafeHttpTransport {
    private val client=OkHttpClient.Builder().connectTimeout(6,TimeUnit.SECONDS)
        .readTimeout(8,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .dns(object: Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val addresses=Dns.SYSTEM.lookup(hostname)
                if (addresses.isEmpty() || addresses.any { !PdfPolicy.publicAddress(it) })
                    throw UnknownHostException("Non-public destination rejected")
                return addresses
            }
        }).build()

    override suspend fun execute(request: SafeRequest): SafeResponse = suspendCancellableCoroutine { c ->
        PdfPolicy.source(request.url)
        val builder=Request.Builder().url(request.url).method(request.method,null)
            .header("User-Agent","DatasheetAnalyzer/0.4 (Android; datasheet retrieval)")
            .header("Accept-Encoding","identity")
        request.headers.forEach { (k,v) -> builder.header(k,v) }
        val call=client.newCall(builder.build())
        // Keep cancellation attached until the streaming response is closed, not merely until headers arrive.
        val registration=c.context[Job]?.invokeOnCompletion(onCancelling=true, invokeImmediately=true) { cause ->
            if (cause!=null) call.cancel()
        }
        c.invokeOnCancellation { call.cancel(); registration?.dispose() }
        call.enqueue(object: Callback {
            override fun onFailure(call: Call,e: IOException) {
                registration?.dispose()
                if(c.isActive) c.resumeWithException(SecurityFailure("NETWORK","Kaynak bağlantısı kurulamadı veya kesildi."))
            }
            override fun onResponse(call: Call,response: Response) {
                val result=SafeResponse(response.code,response.headers.toMultimap().mapValues { it.value.joinToString(",") },
                    response.body?.byteStream() ?: ByteArrayInputStream(byteArrayOf())) {
                    response.close(); registration?.dispose()
                }
                if(c.isActive) c.resume(result) { _, value, _ -> value.close() } else result.close()
            }
        })
    }
}
