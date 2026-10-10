package com.example.datasheetanalyzer.sandbox

import com.example.datasheetanalyzer.domain.document.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.Writer

class PdfEvidenceExtractor {
    fun extract(document: PDDocument,pageNumber: Int,evidence: String,section: DatasheetSection?): List<FigureBounds> {
        require(pageNumber in 1..minOf(80,document.numberOfPages) && evidence.length in 3..600)
        val page=document.getPage(pageNumber-1)
        if(page.rotation%360!=0) return emptyList()
        val glyphs=mutableListOf<FigureGlyph>();var positions=0
        val stripper=object: PDFTextStripper() {
            override fun processTextPosition(p: TextPosition) {
                if(++positions<=50_000 && p.dir==0f) glyphs+=FigureGlyph(p.unicode.orEmpty(),p.xDirAdj,p.yDirAdj,p.widthDirAdj,p.heightDir.coerceAtLeast(1f))
            }
        }
        stripper.startPage=pageNumber;stripper.endPage=pageNumber
        stripper.writeText(document,object: Writer() {
            override fun write(cbuf: CharArray,off: Int,len: Int) {}
            override fun flush() {}
            override fun close() {}
        })
        if(positions>50_000) return emptyList()
        return EvidenceTextLocator().locate(glyphs,page.cropBox.width,page.cropBox.height,evidence,section)
    }
}
