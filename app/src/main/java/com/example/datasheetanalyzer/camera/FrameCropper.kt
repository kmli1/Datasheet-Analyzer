package com.example.datasheetanalyzer.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy

object FrameCropper {
    /** Caller owns the returned bitmap. ImageProxy is closed by the analyzer. */
    fun crop(image: ImageProxy): Bitmap {
        val allocated = mutableListOf<Bitmap>()
        var result: Bitmap? = null
        try {
            val full = image.toBitmap().also(allocated::add)
            val viewportRect = Rect(image.cropRect)
            require(viewportRect.intersect(0, 0, full.width, full.height))
            val viewport = Bitmap.createBitmap(
                full, viewportRect.left, viewportRect.top,
                viewportRect.width(), viewportRect.height()
            ).also(allocated::add)
            val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            val upright = Bitmap.createBitmap(
                viewport, 0, 0, viewport.width, viewport.height, matrix, true
            ).also(allocated::add)
            val left = (upright.width * ScanRegion.LEFT).toInt()
            val top = (upright.height * ScanRegion.TOP).toInt()
            val right = (upright.width * ScanRegion.RIGHT).toInt().coerceAtLeast(left + 1)
            val bottom = (upright.height * ScanRegion.BOTTOM).toInt().coerceAtLeast(top + 1)
            result = Bitmap.createBitmap(upright, left, top, right - left, bottom - top)
            return result
        } finally {
            // createBitmap can return its input; recycle each distinct object once.
            allocated.distinct().filter { it !== result }.forEach { it.recycle() }
        }
    }
}
