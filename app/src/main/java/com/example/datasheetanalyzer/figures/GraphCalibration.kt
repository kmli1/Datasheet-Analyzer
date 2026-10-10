package com.example.datasheetanalyzer.figures

import com.example.datasheetanalyzer.domain.document.FigureBounds
import kotlin.math.*

enum class AxisScale { LINEAR, LOGARITHMIC }
data class CalibratedAxis(val minimum: Double,val maximum: Double,val unit: String,val scale: AxisScale=AxisScale.LINEAR) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum<maximum && unit.length<=40)
        require(scale!=AxisScale.LOGARITHMIC || minimum>0)
    }
    fun interpolate(fraction: Double): Double {
        require(fraction.isFinite() && fraction in 0.0..1.0)
        return if(scale==AxisScale.LINEAR) (1-fraction)*minimum+fraction*maximum
        else exp((1-fraction)*ln(minimum)+fraction*ln(maximum))
    }
}
data class ApproximateGraphPoint(val x: Double,val y: Double,val xLow: Double,val xHigh: Double,val yLow: Double,val yHigh: Double)
data class GraphCalibration(val plot: FigureBounds,val x: CalibratedAxis,val y: CalibratedAxis) {
    fun read(nx: Float,ny: Float,width: Int,height: Int): ApproximateGraphPoint? {
        if(!nx.isFinite() || !ny.isFinite() || width<1 || height<1 ||
            nx !in plot.left..plot.right || ny !in plot.top..plot.bottom) return null
        val fx=((nx-plot.left)/(plot.right-plot.left)).toDouble().coerceIn(0.0,1.0)
        val fy=((plot.bottom-ny)/(plot.bottom-plot.top)).toDouble().coerceIn(0.0,1.0)
        val dx=2.0/width/(plot.right-plot.left);val dy=2.0/height/(plot.bottom-plot.top)
        val point=ApproximateGraphPoint(x.interpolate(fx),y.interpolate(fy),
            x.interpolate((fx-dx).coerceIn(0.0,1.0)),x.interpolate((fx+dx).coerceIn(0.0,1.0)),
            y.interpolate((fy-dy).coerceIn(0.0,1.0)),y.interpolate((fy+dy).coerceIn(0.0,1.0)))
        return point.takeIf { listOf(it.x,it.y,it.xLow,it.xHigh,it.yLow,it.yHigh).all(Double::isFinite) }
    }
}
