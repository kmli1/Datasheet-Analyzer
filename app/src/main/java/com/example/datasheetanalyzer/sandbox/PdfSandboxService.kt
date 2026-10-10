package com.example.datasheetanalyzer.sandbox

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.example.datasheetanalyzer.domain.security.SecurityFailure
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PdfSandboxService : Service() {
    private val busy=AtomicBoolean(false)
    private val worker=Executors.newSingleThreadExecutor()
    private val timer=Executors.newSingleThreadScheduledExecutor()
    override fun onCreate() { super.onCreate();PDFBoxResourceLoader.init(applicationContext) }
    override fun onBind(intent: Intent): IBinder = binder
    private val binder=object: IPdfSandbox.Stub() {
        override fun inspect(descriptor: ParcelFileDescriptor,callback: IPdfSandboxCallback) {
            if(!busy.compareAndSet(false,true)) {
                descriptor.close();callback.completed(false,"BUSY",0,Process.myPid());return
            }
            // A hard watchdog also handles parsers that ignore thread interruption.
            val watchdog=timer.schedule({ Process.killProcess(Process.myPid()) },8,TimeUnit.SECONDS)
            worker.execute {
                try {
                    val result=ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
                        PdfSandboxInspector().inspect(it)
                    }
                    callback.completed(result.passed,result.code,result.pages,Process.myPid())
                } catch(_: Throwable) {
                    runCatching { descriptor.close() }
                    runCatching { callback.completed(false,"SANDBOX_FAILURE",0,Process.myPid()) }
                } finally { watchdog.cancel(false);busy.set(false) }
            }
        }
        override fun extract(descriptor: ParcelFileDescriptor,callback: IPdfTextCallback) {
            if(!busy.compareAndSet(false,true)) {
                descriptor.close();callback.completed(false,"BUSY","",false,Process.myPid());return
            }
            val watchdog=timer.schedule({ Process.killProcess(Process.myPid()) },25,TimeUnit.SECONDS)
            worker.execute {
                try {
                    val result=ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { PdfSandboxInspector().extract(it) }
                    callback.metadata(result.metadata.title,result.metadata.author,result.totalPages)
                    result.pages.forEach { callback.page(it.pageNumber,it.rawText,it.truncated) }
                    watchdog.cancel(false)
                    busy.set(false)
                    callback.completed(true,"PASSED","",result.truncated,Process.myPid())
                } catch(e: Throwable) {
                    runCatching { descriptor.close() }
                    val code=when(e) { is SecurityFailure -> e.code;is InvalidPasswordException -> "ENCRYPTED";else -> "EXTRACTION_FAILED" }
                    watchdog.cancel(false);busy.set(false)
                    val detail=if(e is SecurityFailure) e.message.orEmpty().take(220) else ""
                    runCatching { callback.completed(false,code,detail,false,Process.myPid()) }
                } finally { watchdog.cancel(false) }
            }
        }
        override fun render(descriptor: ParcelFileDescriptor,pageNumber: Int,output: ParcelFileDescriptor,callback: IPdfRenderCallback) {
            if(!busy.compareAndSet(false,true)) {
                descriptor.close();output.close();callback.completed(false,"BUSY",0,0,Process.myPid());return
            }
            val watchdog=timer.schedule({ Process.killProcess(Process.myPid()) },8,TimeUnit.SECONDS)
            worker.execute {
                try {
                    val size=PdfRasterizer.render(descriptor,pageNumber,output)
                    watchdog.cancel(false);busy.set(false)
                    callback.completed(true,"RENDERED",size.first,size.second,Process.myPid())
                } catch(_: Throwable) {
                    runCatching { descriptor.close() };runCatching { output.close() }
                    watchdog.cancel(false);busy.set(false)
                    runCatching { callback.completed(false,"RENDER_FAILED",0,0,Process.myPid()) }
                } finally { watchdog.cancel(false) }
            }
        }
        override fun renderImage(descriptor: ParcelFileDescriptor,output: ParcelFileDescriptor,callback: IPdfRenderCallback) {
            if(!busy.compareAndSet(false,true)) { descriptor.close();output.close();callback.completed(false,"BUSY",0,0,Process.myPid());return }
            val watchdog=timer.schedule({ Process.killProcess(Process.myPid()) },8,TimeUnit.SECONDS)
            worker.execute {
                try { val size=ImageRasterizer.render(descriptor,output);watchdog.cancel(false);busy.set(false);callback.completed(true,"RENDERED",size.first,size.second,Process.myPid()) }
                catch(_: Throwable) { runCatching { descriptor.close() };runCatching { output.close() };watchdog.cancel(false);busy.set(false);runCatching { callback.completed(false,"IMAGE_FAILED",0,0,Process.myPid()) } }
                finally { watchdog.cancel(false) }
            }
        }
        override fun findFigures(descriptor: ParcelFileDescriptor,titles: Array<out String>,callback: IPdfFigureCallback) {
            if(!busy.compareAndSet(false,true)) {
                descriptor.close();callback.completed(false,"BUSY",Process.myPid());return
            }
            val watchdog=timer.schedule({ Process.killProcess(Process.myPid()) },12,TimeUnit.SECONDS)
            worker.execute {
                try {
                    require(titles.size in 1..3 && titles.all { it.length in 3..180 })
                    val results=ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { PdfSandboxInspector().figures(it,titles.toList()) }
                    results.forEach { result -> result.match.let { figure -> requireNotNull(figure.titleBounds).let { bounds ->
                        callback.match(result.requestIndex,figure.page,figure.title,bounds.left,bounds.top,bounds.right,bounds.bottom)
                    } } }
                    watchdog.cancel(false);busy.set(false);callback.completed(true,"FIGURES_SEARCHED",Process.myPid())
                } catch(_: Throwable) {
                    runCatching { descriptor.close() };watchdog.cancel(false);busy.set(false)
                    runCatching { callback.completed(false,"FIGURE_SEARCH_FAILED",Process.myPid()) }
                } finally { watchdog.cancel(false) }
            }
        }
        override fun findEvidence(descriptor: ParcelFileDescriptor,pageNumber: Int,evidence: String,section: String,callback: IPdfEvidenceCallback) {
            if(!busy.compareAndSet(false,true)) { descriptor.close();callback.completed(false,"BUSY",Process.myPid());return }
            val watchdog=timer.schedule({ Process.killProcess(Process.myPid()) },12,TimeUnit.SECONDS)
            worker.execute {
                try {
                    require(pageNumber in 1..80 && evidence.length in 3..600 && section.length<=60)
                    val target=section.takeIf(String::isNotBlank)?.let(com.example.datasheetanalyzer.domain.document.DatasheetSection::valueOf)
                    val rectangles=ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { PdfSandboxInspector().evidence(it,pageNumber,evidence,target) }
                    rectangles.forEach { r -> callback.rectangle(r.left,r.top,r.right,r.bottom) }
                    watchdog.cancel(false);busy.set(false);callback.completed(true,"EVIDENCE_SEARCHED",Process.myPid())
                } catch(_: Throwable) {
                    runCatching { descriptor.close() };watchdog.cancel(false);busy.set(false)
                    runCatching { callback.completed(false,"EVIDENCE_SEARCH_FAILED",Process.myPid()) }
                } finally { watchdog.cancel(false) }
            }
        }
        override fun cancel() { Process.killProcess(Process.myPid()) }
    }
    override fun onDestroy() {
        worker.shutdownNow();timer.shutdownNow();super.onDestroy()
        Process.killProcess(Process.myPid())
    }
}
