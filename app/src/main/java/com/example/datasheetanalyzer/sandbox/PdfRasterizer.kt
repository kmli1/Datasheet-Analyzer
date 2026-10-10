package com.example.datasheetanalyzer.sandbox

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.FilterOutputStream
import java.io.OutputStream
import kotlin.math.sqrt

/** PdfRenderer owns the read descriptor. Only a bounded PNG leaves the sandbox. */
object PdfRasterizer {
    fun render(input: ParcelFileDescriptor,pageNumber: Int,output: ParcelFileDescriptor): Pair<Int,Int> {
        ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream ->
            PdfRenderer(input).use { renderer ->
                require(pageNumber in 1..renderer.pageCount && pageNumber<=80)
                renderer.openPage(pageNumber-1).use { page ->
                    require(page.width in 1..20000 && page.height in 1..20000)
                    val scale=minOf(2.5,sqrt(2_000_000.0/(page.width.toDouble()*page.height)),
                        3200.0/maxOf(page.width,page.height))
                    val width=maxOf(1,(page.width*scale).toInt())
                    val height=maxOf(1,(page.height*scale).toInt())
                    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        val matrix=Matrix().apply { setScale(width.toFloat()/page.width,height.toFloat()/page.height) }
                        page.render(bitmap,null,matrix,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        check(bitmap.compress(Bitmap.CompressFormat.PNG,100,LimitedOutput(stream)))
                        stream.flush()
                    } finally { bitmap.recycle() }
                    return width to height
                }
            }
        }
    }
    private class LimitedOutput(out: OutputStream): FilterOutputStream(out) {
        private var count=0
        override fun write(value: Int) { require(++count<=8*1024*1024);out.write(value) }
        override fun write(bytes: ByteArray,offset: Int,length: Int) {
            count+=length;require(count<=8*1024*1024);out.write(bytes,offset,length)
        }
    }
}
