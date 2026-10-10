package com.example.datasheetanalyzer.export

import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.*

/** New report only: embedded Unicode text, repeating table headers, bounded source PNG images. */
object AnalysisPdfWriter {
    const val FONT_ASSET="com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"
    fun write(blocks: List<ReportBlock>,output: OutputStream,fontInput: InputStream) {
        require(blocks.size<=1500)
        PDDocument().use { pdf ->
            val font=PDType0Font.load(pdf,fontInput,true)
            pdf.documentInformation.creator="Datasheet Analyzer 1.0.8"
            pdf.documentInformation.title=blocks.getOrNull(1)?.text?.take(160) ?: "Analiz raporu"
            val width=595f;val height=842f;val margin=42f;val bottom=height-48f;val contentWidth=width-2*margin
            var stream: PDPageContentStream?=null;var pageNumber=0;var y=0f
            val supported=mutableMapOf<Int,Boolean>()
            val accent=floatArrayOf(13/255f,113/255f,108/255f)
            val body=floatArrayOf(26/255f,40/255f,50/255f)
            val muted=floatArrayOf(82/255f,98/255f,109/255f)
            fun safe(value: String)=buildString {
                value.take(6000).codePoints().forEach { point ->
                    if(point==10) append('\n') else if(Character.isISOControl(point)) append(' ') else {
                        val chars=String(Character.toChars(point))
                        val valid=supported.getOrPut(point) { runCatching { font.encode(chars);true }.getOrDefault(false) }
                        append(if(valid) chars else "?")
                    }
                }
            }
            fun drawText(text: String,size: Float,x: Float,baseline: Float,color: FloatArray) {
                val s=checkNotNull(stream)
                s.setNonStrokingColor(color[0],color[1],color[2]);s.beginText()
                try { s.setFont(font,size);s.newLineAtOffset(x,height-baseline);s.showText(text) } finally { s.endText() }
            }
            fun finish() {
                if(stream!=null) {
                    drawText("Datasheet Analyzer",9f,margin,height-24f,muted)
                    drawText("$pageNumber",9f,width-margin-18f,height-24f,muted)
                    stream!!.close();stream=null
                }
            }
            fun nextPage() {
                finish();require(pageNumber<100) { "Rapor sayfa sınırını aştı." }
                val page=PDPage(PDRectangle(width,height));pdf.addPage(page);pageNumber++
                stream=PDPageContentStream(pdf,page)
                stream!!.setStrokingColor(accent[0],accent[1],accent[2]);stream!!.setLineWidth(3f)
                stream!!.moveTo(margin,height-25f);stream!!.lineTo(width-margin,height-25f);stream!!.stroke();y=margin+12f
            }
            fun fit(text: String,size: Float,available: Float): Int {
                var lo=1;var hi=text.length;var count=1
                while(lo<=hi) {
                    val mid=(lo+hi)/2
                    val end=if(mid<text.length && Character.isHighSurrogate(text[mid-1])) mid-1 else mid
                    if(end>0 && font.getStringWidth(text.take(end))*size/1000<=available) { count=end;lo=mid+1 } else hi=mid-1
                }
                return count
            }
            fun wrap(value: String,size: Float,available: Float): List<String> = buildList {
                for(paragraph in safe(value).split('\n')) {
                    if(paragraph.isBlank()) { add("");continue }
                    var remaining=paragraph.trim()
                    while(remaining.isNotEmpty()) {
                        var count=fit(remaining,size,available)
                        if(count<remaining.length) { val space=remaining.lastIndexOf(' ',count-1);if(space>0) count=space }
                        add(remaining.take(count));remaining=remaining.drop(count).trimStart()
                    }
                }
            }
            fun table(value: ReportTable) {
                val sizes=value.weights.map { it/value.weights.sum()*contentWidth }
                val cellSize=9.5f;val leading=13.2f
                fun paintRow(cells: List<List<String>>,header: Boolean,index: Int) {
                    val lines=cells.maxOfOrNull { it.size } ?: 1
                    val rowHeight=lines*leading+12f
                    val s=checkNotNull(stream)
                    if(header) s.setNonStrokingColor(.89f,.96f,.95f) else if(index%2==0) s.setNonStrokingColor(.97f,.98f,.985f) else s.setNonStrokingColor(1f,1f,1f)
                    s.addRect(margin,height-y-rowHeight,contentWidth,rowHeight);s.fill()
                    s.setStrokingColor(.78f,.85f,.87f);s.setLineWidth(.5f)
                    var x=margin
                    cells.forEachIndexed { column,textLines ->
                        textLines.forEachIndexed { line,text -> drawText(text,cellSize,x+7,y+cellSize+6+line*leading,if(header) accent else body) }
                        s.addRect(x,height-y-rowHeight,sizes[column],rowHeight);s.stroke();x+=sizes[column]
                    };y+=rowHeight
                }
                val header=value.headers.mapIndexed { i,text -> wrap(text,cellSize,sizes[i]-14) }
                val headerHeight=header.maxOf { it.size }*leading+12f
                if(y+headerHeight+38>bottom) nextPage()
                y+=4;paintRow(header,true,0)
                value.rows.forEachIndexed { index,row ->
                    val all=row.mapIndexed { i,text -> wrap(text,cellSize,sizes[i]-14) }
                    val count=all.maxOfOrNull { it.size } ?: 1
                    var offset=0
                    while(offset<count) {
                        var room=floor((bottom-y-12)/leading).toInt()
                        if(room<1) { nextPage();paintRow(header,true,0);room=floor((bottom-y-12)/leading).toInt() }
                        val take=min(room,count-offset)
                        val portion=all.map { it.drop(offset).take(take).ifEmpty { listOf("") } }
                        paintRow(portion,false,index);offset+=take
                    }
                };y+=8
            }
            fun image(block: ReportBlock) {
                val bytes=checkNotNull(block.image).png
                require(bytes.take(8)==listOf(137.toByte(),80,78,71,13,10,26,10).map { it.toByte() })
                val dimensions=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,dimensions)
                require(dimensions.outWidth in 1..3200 && dimensions.outHeight in 1..3200 && dimensions.outWidth.toLong()*dimensions.outHeight<=2_000_000)
                val caption=wrap(block.text,11f,contentWidth)
                val scale=min(contentWidth/dimensions.outWidth,550f/dimensions.outHeight)
                val w=dimensions.outWidth*scale;val h=dimensions.outHeight*scale
                if(y+h+caption.size*16+20>bottom) nextPage()
                caption.forEach { drawText(it,11f,margin,y,accent);y+=16 }
                val source=PDImageXObject.createFromByteArray(pdf,bytes,"source-graph")
                stream!!.drawImage(source,margin+(contentWidth-w)/2,height-y-h,w,h);y+=h+15
            }
            try {
                nextPage()
                for(block in blocks) {
                    if(block.style==ReportStyle.TABLE) { table(requireNotNull(block.table));continue }
                    if(block.style==ReportStyle.IMAGE) { image(block);continue }
                    val size=when(block.style) { ReportStyle.TITLE->22f;ReportStyle.HEADING->14f;ReportStyle.NOTE->9.5f;else->11f }
                    val color=when(block.style) { ReportStyle.TITLE,ReportStyle.HEADING->accent;ReportStyle.NOTE->muted;else->body }
                    val lineHeight=size*1.38f;val before=if(block.style==ReportStyle.HEADING) 12f else 3f
                    if(y+before+lineHeight*2>bottom) nextPage();y+=before
                    for(line in wrap(block.text,size,contentWidth)) {
                        if(y+lineHeight>bottom) nextPage()
                        drawText(line,size,margin,y,color);y+=lineHeight
                    };y+=4f
                }
                finish();pdf.save(output)
            } finally { stream?.close() }
        }
    }
}
