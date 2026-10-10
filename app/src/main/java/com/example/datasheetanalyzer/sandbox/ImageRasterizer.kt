package com.example.datasheetanalyzer.sandbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import kotlin.math.sqrt

/** Preview images are decoded in the same disposable process as PDF pages. */
object ImageRasterizer {
    fun render(input: ParcelFileDescriptor,output: ParcelFileDescriptor): Pair<Int,Int> {
        input.use {
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFileDescriptor(input.fileDescriptor,null,bounds)
            require(bounds.outWidth in 1..10000 && bounds.outHeight in 1..10000 && bounds.outWidth.toLong()*bounds.outHeight<=20_000_000)
            val sample=BitmapFactory.Options().apply {
                inSampleSize=1
                while(bounds.outWidth/inSampleSize>3200 || bounds.outHeight/inSampleSize>3200 || (bounds.outWidth/inSampleSize).toLong()*(bounds.outHeight/inSampleSize)>2_000_000) inSampleSize*=2
            }
            val bitmap=requireNotNull(BitmapFactory.decodeFileDescriptor(input.fileDescriptor,null,sample))
            try {
                require(bitmap.width.toLong()*bitmap.height<=2_000_000 && bitmap.width<=3200 && bitmap.height<=3200)
                ParcelFileDescriptor.AutoCloseOutputStream(output).use { stream -> require(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream)) }
                return bitmap.width to bitmap.height
            } finally { bitmap.recycle() }
        }
    }
}
