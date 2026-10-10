package com.example.datasheetanalyzer.sandbox

import android.content.*
import android.os.*
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.security.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The app receives only bounded text/PNG data; original PDF parsing stays isolated. */
class IsolatedPdfClient(context: Context): PdfStructureValidator {
    private companion object { val requestMutex=Mutex() }
    private val app=context.applicationContext
    private val handler=Handler(app.mainLooper)
    override suspend fun validate(file: File): SandboxResult = request(30_000) { remote,done ->
        val pages=mutableListOf<PageText>()
        var metadata=PdfMetadata();var count=0;var totalChars=0;var invalid=false
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            remote.extract(fd,object: IPdfTextCallback.Stub() {
                @Synchronized override fun metadata(title: String,author: String,totalPages: Int) {
                    if(count!=0 || totalPages !in 1..1000 || title.length>512 || author.length>512) invalid=true
                    count=totalPages;metadata=PdfMetadata(title,author)
                }
                @Synchronized override fun page(pageNumber: Int,text: String,truncated: Boolean) {
                    totalChars+=text.length
                    if(pageNumber !in 1..minOf(80,count) || pages.size>=80 || text.length>20_000 || totalChars>300_000 ||
                        pageNumber!=pages.size+1) { invalid=true;return }
                    pages+=PageText(pageNumber,text,truncated=truncated)
                }
                @Synchronized override fun completed(success: Boolean,code: String,detail: String,truncated: Boolean,processId: Int) {
                    val valid=success && !invalid && processId!=Process.myPid() && pages.size==minOf(80,count) && count>0
                    done(Result.success(SandboxResult(valid,if(valid) code else if(success) "INVALID_IPC" else code,
                        count,if(valid) PdfExtraction(metadata,count,pages.toList(),truncated || pages.any { it.truncated }) else null,
                        detail.take(220))))
                }
            })
        }
    }

    suspend fun renderPage(pdf: File,pageNumber: Int): File {
        require(pageNumber in 1..80)
        val directory=File(app.cacheDir,"ocr-pages").apply { mkdirs() }
        val png=File.createTempFile("page-",".png",directory)
        var keep=false
        try {
            request<Unit>(10_000) { remote,done ->
                ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use { input ->
                    ParcelFileDescriptor.open(png,ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE).use { output ->
                        remote.render(input,pageNumber,output,object: IPdfRenderCallback.Stub() {
                            override fun completed(success: Boolean,code: String,width: Int,height: Int,processId: Int) {
                                val valid=success && processId!=Process.myPid() && width in 1..3200 && height in 1..3200 &&
                                    width.toLong()*height<=2_000_000 && png.length() in 8..8L*1024*1024
                                if(valid) done(Result.success(Unit))
                                else done(Result.failure(SecurityFailure(code,"OCR sayfası hazırlanamadı ($code).")))
                            }
                        })
                    }
                }
            }
            currentCoroutineContext().ensureActive();keep=true;return png
        } finally { if(!keep) png.delete() }
    }

    suspend fun renderImage(pdf: File): File {
        val directory=File(app.cacheDir,"ocr-pages").apply { mkdirs() }
        val png=File.createTempFile("page-",".png",directory)
        var keep=false
        try {
            request<Unit>(10_000) { remote,done ->
                ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use { input ->
                    ParcelFileDescriptor.open(png,ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE).use { output ->
                        remote.renderImage(input,output,object: IPdfRenderCallback.Stub() {
                            override fun completed(success: Boolean,code: String,width: Int,height: Int,processId: Int) {
                                val valid=success && processId!=Process.myPid() && width in 1..3200 && height in 1..3200 &&
                                    width.toLong()*height<=2_000_000 && png.length() in 8..8L*1024*1024
                                if(valid) done(Result.success(Unit))
                                else done(Result.failure(SecurityFailure(code,"OCR sayfası hazırlanamadı ($code).")))
                            }
                        })
                    }
                }
            }
            currentCoroutineContext().ensureActive();keep=true;return png
        } finally { if(!keep) png.delete() }
    }

    suspend fun findFigures(pdf: File,titles: List<String>): List<LocatedFigure> {
        require(titles.size in 1..3 && titles.all { it.length in 3..180 })
        return request(15_000) { remote,done ->
            val found=mutableListOf<LocatedFigure>();var invalid=false
            ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                remote.findFigures(fd,titles.toTypedArray(),object: IPdfFigureCallback.Stub() {
                    @Synchronized override fun match(requestIndex: Int,pageNumber: Int,title: String,left: Float,top: Float,right: Float,bottom: Float) {
                        val bounds=runCatching { FigureBounds(left,top,right,bottom) }.getOrNull()
                        if(requestIndex !in titles.indices || pageNumber !in 1..80 || title.length !in 3..180 || bounds==null ||
                            found.size>=6 || found.count { it.requestIndex==requestIndex }>=2) { invalid=true;return }
                        found+=LocatedFigure(requestIndex,FigureMatch(title,pageNumber,bounds))
                    }
                    @Synchronized override fun completed(success: Boolean,code: String,processId: Int) {
                        if(success && !invalid && processId!=Process.myPid()) done(Result.success(found.toList()))
                        else done(Result.failure(SecurityFailure(code,"Grafik başlığı araması tamamlanamadı.")))
                    }
                })
            }
        }
    }

    suspend fun findEvidence(pdf: File,page: Int,evidence: String,section: DatasheetSection?=null): List<FigureBounds> {
        require(page in 1..80 && evidence.length in 3..600)
        return request(15_000) { remote,done ->
            val rectangles=mutableListOf<FigureBounds>();var invalid=false
            ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                remote.findEvidence(fd,page,evidence,section?.name.orEmpty(),object: IPdfEvidenceCallback.Stub() {
                    @Synchronized override fun rectangle(left: Float,top: Float,right: Float,bottom: Float) {
                        val bounds=runCatching { FigureBounds(left,top,right,bottom) }.getOrNull()
                        if(bounds==null || rectangles.size>=8) invalid=true else rectangles+=bounds
                    }
                    @Synchronized override fun completed(success: Boolean,code: String,processId: Int) {
                        if(success && !invalid && processId!=Process.myPid()) done(Result.success(rectangles.toList()))
                        else done(Result.failure(SecurityFailure(code,"Kaynak satırının konumu belirlenemedi.")))
                    }
                })
            }
        }
    }
    private suspend fun <T> request(timeout: Long,operation: (IPdfSandbox,(Result<T>)->Unit)->Unit): T =
        requestMutex.withLock { withContext(Dispatchers.Main.immediate) {
            withTimeout(timeout) {
                suspendCancellableCoroutine { continuation ->
                    var bound=false;var remote: IPdfSandbox?=null;var released=false
                    lateinit var connection: ServiceConnection
                    fun release(cancel: Boolean) {
                        if(released) return
                        released=true
                        if(cancel) runCatching { remote?.cancel() }
                        if(bound) { bound=false;runCatching { app.unbindService(connection) } }
                    }
                    val done: (Result<T>)->Unit = { result -> handler.post {
                        release(false)
                        if(continuation.isActive) result.fold(continuation::resume,continuation::resumeWithException)
                    };Unit }
                    fun fail(code: String) { done(Result.failure(SecurityFailure(code,"Belge işlemi tamamlanamadı ($code)."))) }
                    connection=object: ServiceConnection {
                        override fun onServiceConnected(name: ComponentName,binder: IBinder) {
                            if(!continuation.isActive) { release(true);return }
                            remote=IPdfSandbox.Stub.asInterface(binder)
                            try { operation(remote!!,done) } catch(_: Exception) { fail("SANDBOX_BIND") }
                        }
                        override fun onServiceDisconnected(name: ComponentName) { fail("SANDBOX_DIED") }
                        override fun onBindingDied(name: ComponentName) { fail("SANDBOX_DIED") }
                        override fun onNullBinding(name: ComponentName) { fail("SANDBOX_BIND") }
                    }
                    continuation.invokeOnCancellation { handler.post { release(true) } }
                    bound=app.bindService(Intent(app,PdfSandboxService::class.java),connection,Context.BIND_AUTO_CREATE)
                    if(!bound) fail("SANDBOX_BIND")
                }
            }
        } }
}
