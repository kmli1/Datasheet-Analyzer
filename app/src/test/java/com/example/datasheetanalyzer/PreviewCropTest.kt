package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.export.*
import org.junit.Assert.*
import org.junit.Test

class PreviewCropTest {
    @Test fun portraitPageHasHorizontalMarginsAndCropIgnoresThem() {
        val fit=PreviewCrop.fit(400f,400f,200,400)
        assertEquals(ImageFit(100f,0f,200f,400f),fit)
        val crop=PreviewCrop.selection(fit,150f,100f,250f,300f)!!
        assertEquals(NormalizedCrop(.25f,.25f,.75f,.75f),crop)
        assertEquals(PixelCrop(50,100,100,200),PreviewCrop.pixels(crop,200,400))
    }
    @Test fun landscapePageHasVerticalMarginsAndReverseDragIsIdentical() {
        val fit=PreviewCrop.fit(400f,400f,400,200)
        assertEquals(ImageFit(0f,100f,400f,200f),fit)
        assertEquals(PreviewCrop.selection(fit,50f,125f,350f,275f),PreviewCrop.selection(fit,350f,275f,50f,125f))
    }
    @Test fun outsideDragIsClippedAndEmptyMarginDragIgnored() {
        val fit=PreviewCrop.fit(400f,400f,200,400)
        assertEquals(NormalizedCrop(0f,0f,1f,1f),PreviewCrop.selection(fit,-100f,-100f,500f,500f))
        assertNull(PreviewCrop.selection(fit,0f,0f,80f,300f))
        assertNull(PreviewCrop.selection(fit,100f,10f,100.01f,20f))
        assertNull(PreviewCrop.selection(fit,Float.NaN,0f,500f,500f))
    }
    @Test fun edgePixelsStayInsideSourceAndNeverHaveZeroSize() {
        assertEquals(PixelCrop(0,0,17,29),PreviewCrop.pixels(NormalizedCrop(0f,0f,1f,1f),17,29))
        assertEquals(PixelCrop(16,28,1,1),PreviewCrop.pixels(NormalizedCrop(.999f,.999f,1f,1f),17,29))
    }
}
