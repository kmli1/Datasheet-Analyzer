package com.example.datasheetanalyzer.sandbox

import com.example.datasheetanalyzer.domain.document.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.Writer

/** Optional, bounded caption search. Runs only inside the isolated PDF service. */
class PdfFigureExtractor {
    fun extract(document: PDDocument,titles: List<String>): List<LocatedFigure> {
        require(titles.size in 1..3 && titles.all { it.length in 3..180 })
        val out=mutableListOf<LocatedFigure>();val glyphs=mutableListOf<FigureGlyph>()
        var pagePositions=0;var totalPositions=0
        val stripper=object: PDFTextStripper() {
            override fun startPage(page: PDPage) { super.startPage(page);glyphs.clear();pagePositions=0 }
            override fun processTextPosition(position: TextPosition) {
                if(++totalPositions<=300_000 && ++pagePositions<=50_000 && position.dir==0f)
                    glyphs+=FigureGlyph(position.unicode.orEmpty(),position.xDirAdj,position.yDirAdj,position.widthDirAdj,position.heightDir.coerceAtLeast(1f))
                // No article buffer is needed for a caption lookup.
            }
            override fun endPage(page: PDPage) {
                if(page.rotation%360==0 && totalPositions<=300_000 && pagePositions<=50_000) {
                    val remaining=titles.mapIndexed { index,title -> if(out.count { it.requestIndex==index }>=2) "" else title }
                    val found=NativeFigureLocator().locate(glyphs,page.cropBox.width,page.cropBox.height,currentPageNo,remaining)
                    out+=found.filter { value -> out.count { it.requestIndex==value.requestIndex }<2 }
                }
                super.endPage(page)
            }
        }
        stripper.startPage=1;stripper.endPage=minOf(80,document.numberOfPages);stripper.sortByPosition=true
        stripper.writeText(document,object: Writer() {
            override fun write(cbuf: CharArray,off: Int,len: Int) {}
            override fun flush() {}
            override fun close() {}
        })
        return out.take(6)
    }
}
