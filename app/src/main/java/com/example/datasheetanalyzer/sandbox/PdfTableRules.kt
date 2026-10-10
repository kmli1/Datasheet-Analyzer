package com.example.datasheetanalyzer.sandbox

import android.graphics.Path
import android.graphics.PointF
import com.example.datasheetanalyzer.domain.extraction.TableRule
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.util.Vector
import java.io.IOException
import java.util.ArrayDeque
import kotlin.math.abs

/** Optional bounded geometry pass. Unsupported/rotated/clipped pages retain the text-only reader. */
internal class PdfTableRules(private val source: PDPage): PDFGraphicsStreamEngine(source) {
    private val drawn=mutableListOf<TableRule>()
    private val pending=mutableListOf<Pair<PointF,PointF>>()
    private var point: PointF?=null
    private var start: PointF?=null
    private var unsupported=false
    private var subpaths=0
    private data class Clip(val left: Float,val bottom: Float,val right: Float,val top: Float,val supported: Boolean=true) {
        fun contains(p: PointF)=supported && p.x>=left-0.5f && p.x<=right+0.5f && p.y>=bottom-0.5f && p.y<=top+0.5f
    }
    private var clipping=source.cropBox.let { Clip(it.lowerLeftX,it.lowerLeftY,it.upperRightX,it.upperRightY) }
    private val clips=ArrayDeque<Clip>()
    private var operations=0
    internal var failure: String?=null
        private set
    fun read(): List<TableRule> {
        if(source.rotation!=0) return emptyList()
        return try { processPage(source);drawn.toList() }
            catch(e: IOException) { failure=e.message?.take(100);emptyList() }
            catch(e: RuntimeException) { failure=e.javaClass.simpleName;emptyList() }
    }
    override fun processOperator(operator: Operator,operands: MutableList<COSBase>) {
        if(++operations>100_000 || drawn.size+pending.size>4000) throw IOException("Table geometry limit")
        when(operator.name) {
            "q" -> { if(clips.size>=64) throw IOException("Table state limit");clips.addLast(clipping) }
            "Q" -> { if(clips.isNotEmpty()) clipping=clips.removeLast() }
        }
        super.processOperator(operator,operands)
    }
    override fun operatorException(operator: Operator,operands: MutableList<COSBase>,error: IOException) { throw error }
    override fun moveTo(x: Float,y: Float) { subpaths++;point=PointF(x,y);start=point }
    override fun lineTo(x: Float,y: Float) {
        val next=PointF(x,y);point?.let { pending+=it to next };point=next
    }
    override fun appendRectangle(p0: PointF,p1: PointF,p2: PointF,p3: PointF) {
        pending+=listOf(p0 to p1,p1 to p2,p2 to p3,p3 to p0);point=p0;start=p0
        subpaths++
    }
    override fun curveTo(x1: Float,y1: Float,x2: Float,y2: Float,x3: Float,y3: Float) { unsupported=true;point=PointF(x3,y3) }
    override fun getCurrentPoint(): PointF?=point
    override fun closePath() { val p=point;val s=start;if(p!=null && s!=null) pending+=p to s;point=s }
    override fun endPath() { pending.clear();unsupported=false;subpaths=0;point=null;start=null }
    override fun strokePath() {
        val state=graphicsState
        val m=state.currentTransformationMatrix
        val width=state.lineWidth*maxOf(abs(m.scaleX),abs(m.scaleY))
        if(!unsupported && state.alphaConstant>=0.99 && width<=2.5f) {
            val box=source.cropBox
            pending.forEach { (a,b) ->
                if(clipping.contains(a) && clipping.contains(b) && listOf(a.x,a.y,b.x,b.y).all(Float::isFinite) &&
                    (abs(a.x-b.x)<0.5f || abs(a.y-b.y)<0.5f))
                    drawn+=TableRule(a.x-box.lowerLeftX,box.upperRightY-a.y,b.x-box.lowerLeftX,box.upperRightY-b.y)
            }
        }
        endPath()
    }
    override fun fillPath(windingRule: Path.FillType) { endPath() }
    override fun fillAndStrokePath(windingRule: Path.FillType) { strokePath() }
    override fun clip(windingRule: Path.FillType) {
        val points=pending.flatMap { listOf(it.first,it.second) }
        if(points.isEmpty() || points.any { !it.x.isFinite() || !it.y.isFinite() }) { clipping=clipping.copy(supported=false);return }
        val l=points.minOf { it.x };val r=points.maxOf { it.x };val b=points.minOf { it.y };val t=points.maxOf { it.y }
        val corners=listOf(l to b,l to t,r to b,r to t)
        val closing=point?.let { p -> start?.let { s -> abs(p.x-s.x)<0.01f || abs(p.y-s.y)<0.01f } }==true
        val rectangle=!unsupported && subpaths==1 && pending.size in 3..12 && closing && r>l && t>b &&
            pending.all { (a,z) -> abs(a.x-z.x)<0.01f || abs(a.y-z.y)<0.01f } &&
            points.all { p -> abs(p.x-l)<0.01f || abs(p.x-r)<0.01f || abs(p.y-b)<0.01f || abs(p.y-t)<0.01f } &&
            corners.all { (x,y) -> points.any { abs(it.x-x)<0.01f && abs(it.y-y)<0.01f } }
        if(!rectangle) { clipping=clipping.copy(supported=false);return }
        clipping=Clip(maxOf(clipping.left,l),maxOf(clipping.bottom,b),minOf(clipping.right,r),minOf(clipping.top,t),clipping.supported)
    }
    // A separate text pass supplies glyphs. Glyph outlines/forms must not masquerade as table borders.
    override fun showForm(form: PDFormXObject) {}
    override fun showGlyph(textRenderingMatrix: Matrix,font: PDFont,code: Int,unicode: String?,displacement: Vector) {}
    override fun drawImage(pdImage: PDImage) {}
    override fun shadingFill(shadingName: COSName) {}
}
