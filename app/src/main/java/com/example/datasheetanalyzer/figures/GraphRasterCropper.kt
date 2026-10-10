package com.example.datasheetanalyzer.figures

import android.graphics.Bitmap
import com.example.datasheetanalyzer.domain.document.FigureBounds
import com.example.datasheetanalyzer.export.NormalizedCrop
import kotlin.math.*

/** Finds a nearby connected plot with long horizontal AND vertical ink strokes. Falls back to the source page. */
object GraphRasterCropper {
    fun find(image: Bitmap,title: FigureBounds?): NormalizedCrop? {
        if(title==null || image.width<=0 || image.height<=0 || image.width.toLong()*image.height>2_000_000) return null
        val scale=min(500.0/image.width,900.0/image.height).coerceAtMost(1.0)
        val w=max(1,(image.width*scale).roundToInt());val h=max(1,(image.height*scale).roundToInt())
        val pixels=IntArray(image.width*image.height);image.getPixels(pixels,0,image.width,0,0,image.width,image.height)
        val ink=BooleanArray(w*h)
        for(y in 0 until image.height) for(x in 0 until image.width) {
            val color=pixels[y*image.width+x]
            if((color ushr 24)>128 && ((color ushr 16) and 255)+((color ushr 8) and 255)+(color and 255)<570) {
                val gx=min(w-1,(x*scale).toInt());val gy=min(h-1,(y*scale).toInt());ink[gy*w+gx]=true
            }
        }
        val stride=w+1;val sum=IntArray((w+1)*(h+1))
        for(y in 0 until h) { var row=0;for(x in 0 until w) { if(ink[y*w+x]) row++;sum[(y+1)*stride+x+1]=sum[y*stride+x+1]+row } }
        val mask=BooleanArray(w*h);val radius=3
        for(y in 0 until h) for(x in 0 until w) {
            val l=max(0,x-radius);val r=min(w,x+radius+1);val t=max(0,y-radius);val b=min(h,y+radius+1)
            mask[y*w+x]=sum[b*stride+r]-sum[t*stride+r]-sum[b*stride+l]+sum[t*stride+l]>0
        }
        val queue=IntArray(w*h);val candidates=mutableListOf<NormalizedCrop>()
        val tx=((title.left+title.right)/2*w);val ty=((title.top+title.bottom)/2*h)
        for(start in mask.indices) {
            if(!mask[start]) continue
            var front=0;var back=0;queue[back++]=start;mask[start]=false
            var left=w;var right=0;var top=h;var bottom=0
            while(front<back) {
                val index=queue[front++];val x=index%w;val y=index/w
                left=min(left,x);right=max(right,x);top=min(top,y);bottom=max(bottom,y)
                fun add(next: Int) { if(mask[next]) { mask[next]=false;queue[back++]=next } }
                if(x>0) add(index-1);if(x<w-1) add(index+1);if(y>0) add(index-w);if(y<h-1) add(index+w)
            }
            val cw=right-left+1;val ch=bottom-top+1
            if(cw<w*.12 || ch<h*.07 || cw>w*.94 || ch>h*.85) continue
            // The plot must touch its caption, or lie immediately below/above it in the same column.
            if(tx<left-12 || tx>right+12 || ty<top-24 || ty>bottom+24) continue
            var horizontal=0;var vertical=0
            for(y in top..bottom) { var run=0;for(x in left..right) { run=if(ink[y*w+x]) run+1 else 0;horizontal=max(horizontal,run) } }
            for(x in left..right) { var run=0;for(y in top..bottom) { run=if(ink[y*w+x]) run+1 else 0;vertical=max(vertical,run) } }
            if(horizontal<max(20.0,cw*.35) || vertical<max(18.0,ch*.35)) continue
            val l=max(0f,min(left.toFloat()-5,title.left*w-5)/w)
            val r=min(1f,max(right.toFloat()+6,title.right*w+5)/w)
            val t=max(0f,min(top.toFloat()-5,title.top*h-5)/h)
            val b=min(1f,max(bottom.toFloat()+6,title.bottom*h+5)/h)
            if((r-l)*(b-t)>.88f) continue
            candidates+=NormalizedCrop(l,t,r,b)
        }
        return candidates.minByOrNull { (it.right-it.left)*(it.bottom-it.top) }
    }
}
