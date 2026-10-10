package com.example.datasheetanalyzer.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*

object ExportFiles {
    private fun directory(context: Context)=File(context.cacheDir,"export-staging").apply {
        mkdirs()
        listFiles().orEmpty().filter { System.currentTimeMillis()-it.lastModified()>24*60*60*1000 }.forEach { it.delete() }
    }
    fun stagePdf(context: Context,blocks: List<ReportBlock>): File {
        val file=File.createTempFile("report-",".pdf",directory(context))
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            context.assets.open(AnalysisPdfWriter.FONT_ASSET).use { font ->
                file.outputStream().use { AnalysisPdfWriter.write(blocks,it,font) }
            }
            require(file.length() in 8..12L*1024*1024);return file
        } catch(e: Exception) { file.delete();throw e }
    }
    fun stageCsv(context: Context,text: String): File {
        val bytes=text.toByteArray(Charsets.UTF_8)
        require(bytes.size in 8..4*1024*1024)
        val file=File.createTempFile("report-",".csv",directory(context))
        try { file.writeBytes(bytes);return file } catch(e: Exception) { file.delete();throw e }
    }
    fun stagePng(context: Context,image: Bitmap,crop: NormalizedCrop?): File {
        require(image.width in 1..3200 && image.height in 1..3200 && image.width.toLong()*image.height<=2_000_000)
        val dir=directory(context)
        val file=File.createTempFile("page-",".png",dir)
        var selected: Bitmap?=null
        try {
            selected=if(crop==null) image else PreviewCrop.pixels(crop,image.width,image.height).let {
                Bitmap.createBitmap(image,it.left,it.top,it.width,it.height)
            }
            file.outputStream().use { check(selected.compress(Bitmap.CompressFormat.PNG,100,it)) }
            require(file.length() in 8..12L*1024*1024);return file
        } catch(e: Exception) { file.delete();throw e }
          finally { if(selected!=null && selected!==image) selected.recycle() }
    }
    suspend fun save(context: Context,path: String,uri: Uri)=withContext(Dispatchers.IO) {
        val file=File(path).canonicalFile
        require(file.parentFile==directory(context).canonicalFile &&
            ((file.name.startsWith("page-") && file.extension=="png") || (file.name.startsWith("report-") && file.extension in setOf("pdf","csv"))))
        try {
            require(file.length() in 8..12L*1024*1024)
            val stream=context.contentResolver.openOutputStream(uri,"wt") ?: throw IOException("Dosya açılamadı.")
            stream.use { output -> file.inputStream().use { input ->
                val b=ByteArray(8192)
                while(true) { ensureActive();val n=input.read(b);if(n<0) break;output.write(b,0,n) }
            } }
        } finally { file.delete() }
    }
    fun discard(context: Context,path: String?) {
        if(path==null) return
        runCatching { File(path).canonicalFile.takeIf { it.parentFile==directory(context).canonicalFile &&
            (it.name.startsWith("page-") || it.name.startsWith("report-")) }?.delete() }
    }
}
