package com.example.datasheetanalyzer.export

import kotlin.math.*

data class NormalizedCrop(val left: Float,val top: Float,val right: Float,val bottom: Float) {
    init { require(listOf(left,top,right,bottom).all { it.isFinite() } && left>=0 && top>=0 && right<=1 && bottom<=1 && left<right && top<bottom) }
}
data class PixelCrop(val left: Int,val top: Int,val width: Int,val height: Int)
data class ImageFit(val left: Float,val top: Float,val width: Float,val height: Float)

object PreviewCrop {
    fun fit(viewWidth: Float,viewHeight: Float,imageWidth: Int,imageHeight: Int): ImageFit {
        require(viewWidth>0 && viewHeight>0 && viewWidth.isFinite() && viewHeight.isFinite() && imageWidth>0 && imageHeight>0)
        val scale=min(viewWidth/imageWidth,viewHeight/imageHeight)
        val w=imageWidth*scale;val h=imageHeight*scale
        return ImageFit((viewWidth-w)/2,(viewHeight-h)/2,w,h)
    }
    fun selection(fit: ImageFit,x1: Float,y1: Float,x2: Float,y2: Float): NormalizedCrop? {
        if(!listOf(x1,y1,x2,y2).all { it.isFinite() }) return null
        val left=((min(x1,x2)-fit.left)/fit.width).coerceIn(0f,1f)
        val right=((max(x1,x2)-fit.left)/fit.width).coerceIn(0f,1f)
        val top=((min(y1,y2)-fit.top)/fit.height).coerceIn(0f,1f)
        val bottom=((max(y1,y2)-fit.top)/fit.height).coerceIn(0f,1f)
        return if(right-left<0.005f || bottom-top<0.005f) null else NormalizedCrop(left,top,right,bottom)
    }
    fun pixels(crop: NormalizedCrop,width: Int,height: Int): PixelCrop {
        require(width>0 && height>0)
        val left=floor(crop.left*width).toInt().coerceIn(0,width-1)
        val top=floor(crop.top*height).toInt().coerceIn(0,height-1)
        val right=ceil(crop.right*width).toInt().coerceIn(left+1,width)
        val bottom=ceil(crop.bottom*height).toInt().coerceIn(top+1,height)
        return PixelCrop(left,top,right-left,bottom-top)
    }
}
