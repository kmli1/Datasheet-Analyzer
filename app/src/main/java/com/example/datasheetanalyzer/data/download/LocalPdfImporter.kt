package com.example.datasheetanalyzer.data.download

import android.content.ContentResolver
import android.net.Uri
import com.example.datasheetanalyzer.domain.security.*
import com.example.datasheetanalyzer.data.cache.DocumentCache
import kotlinx.coroutines.*
import java.io.File

class LocalPdfImporter(private val resolver: ContentResolver,private val directory: File) {
    suspend fun import(uri: Uri): QuarantinedPdf = withContext(Dispatchers.IO) {
        if(uri.scheme!="content") throw SecurityFailure("LOCAL_URI","Sistem dosya seçicisinden bir PDF seç.")
        directory.mkdirs();val file=File.createTempFile("import-",".pdf",directory)
        var keep=false
        try {
            withTimeout(30_000) {
                // Streams are capped regardless of MIME, extension, provider or reported size.
                val scan=RawPdfScanner();var size=0L
                (resolver.openInputStream(uri) ?: throw SecurityFailure("LOCAL_OPEN","Dosya açılamadı.")).use { input ->
                    file.outputStream().use { output ->
                        val header=ByteArray(8);java.io.DataInputStream(input).readFully(header);PdfPolicy.magic(header);scan.accept(header);output.write(header);size=header.size.toLong()
                        val buffer=ByteArray(8192)
                        while(true) {
                            currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0) break
                            size+=n;if(size>20L*1024*1024) throw SecurityFailure("SIZE","PDF en fazla 20 MB olabilir.")
                            scan.accept(buffer,n);output.write(buffer,0,n)
                        }
                        scan.finish()
                    }
                }
                val sha=DocumentCache.digest(file)
                currentCoroutineContext().ensureActive();keep=true
                QuarantinedPdf(file,"local://document/$sha",size,sha)
            }
        } finally { if(!keep) file.delete() }
    }
}
