package com.example.datasheetanalyzer.figures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.datasheetanalyzer.domain.document.FigureMatch
import com.example.datasheetanalyzer.export.PreviewCrop
import com.example.datasheetanalyzer.sandbox.IsolatedPdfClient
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FigurePreview(val file: File,val cropped: Boolean)
/** Private PNG cache; originals are rendered only in isolation. */
object FigurePreviewStore {
    private val mutex=Mutex()
    suspend fun prepare(context: Context,pdf: File,sha: String,match: FigureMatch): FigurePreview=mutex.withLock {
        withContext(Dispatchers.IO) {
            val dir=File(context.cacheDir,"figure-previews").apply { mkdirs() }
            dir.listFiles().orEmpty().filter { System.currentTimeMillis()-it.lastModified()>24*60*60*1000 }.forEach { it.delete() }
            val key=MessageDigest.getInstance("SHA-256").digest("$sha|${match.page}|${match.titleBounds}".toByteArray()).joinToString("") { "%02x".format(it) }
            for(cropped in listOf(true,false)) File(dir,"$key-${if(cropped) "crop" else "page"}.png").takeIf { it.length() in 8..8L*1024*1024 }
                ?.let { return@withContext FigurePreview(it,cropped) }
            var rendered: File?=null;var image: Bitmap?=null;var selected: Bitmap?=null;var staged: File?=null
            try {
                rendered=IsolatedPdfClient(context).renderPage(pdf,match.page)
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(rendered.absolutePath,bounds)
                require(bounds.outWidth in 1..3200 && bounds.outHeight in 1..3200 && bounds.outWidth.toLong()*bounds.outHeight<=2_000_000)
                image=BitmapFactory.decodeFile(rendered.absolutePath) ?: error("PNG_DECODE")
                val crop=GraphRasterCropper.find(image,match.titleBounds)
                selected=if(crop==null) image else PreviewCrop.pixels(crop,image.width,image.height).let {
                    Bitmap.createBitmap(image,it.left,it.top,it.width,it.height)
                }
                val destination=File(dir,"$key-${if(crop==null) "page" else "crop"}.png")
                staged=File.createTempFile("figure-",".tmp",dir)
                staged.outputStream().use { check(selected.compress(Bitmap.CompressFormat.PNG,100,it)) }
                require(staged.length() in 8..8L*1024*1024);ensureActive();check(staged.renameTo(destination));staged=null
                // At most 12 source previews / 40 MB; the user can regenerate an evicted preview.
                val files=dir.listFiles().orEmpty().filter { it.extension=="png" }.sortedByDescending { it.lastModified() }
                var total=0L;files.forEachIndexed { index,file -> total+=file.length();if(index>=12 || total>40L*1024*1024) file.delete() }
                FigurePreview(destination,crop!=null)
            } finally {
                staged?.delete();rendered?.delete();if(selected!=null && selected!==image) selected.recycle();image?.recycle()
            }
        }
    }
}
