package com.example.datasheetanalyzer.sandbox

import com.example.datasheetanalyzer.domain.document.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.Writer
import com.example.datasheetanalyzer.domain.extraction.PositionedGlyph
import com.example.datasheetanalyzer.domain.extraction.PositionedTableReader

/** One pass; bounded text and position collection, exclusively in the isolated process. */
class PdfTextExtractor(private val config: ExtractionConfig=ExtractionConfig()) {
    fun extract(doc: PDDocument): PdfExtraction {
        val pages=mutableListOf<PageText>()
        var total=0
        var pageNumber=0
        var positions=0
        var clipped=false
        val text=StringBuilder()
        val glyphs=mutableListOf<PositionedGlyph>()
        val glyphSafety=NativeGlyphSafety()
        fun safeGlyph(position: TextPosition): String {
            val unicode=position.unicode
            if(glyphSafety.opaque(position.font)) return "\uFFFD"
            if(unicode.isNullOrEmpty()) return "\uFFFD"
            return unicode.map { c -> if(c.isISOControl() ||
                Character.getType(c)==Character.PRIVATE_USE.toInt()) '\uFFFD' else c }.joinToString("")
        }
        fun safeString(value: String,positions: List<TextPosition>): String {
            val out=StringBuilder();var cursor=0
            for(position in positions) {
                val original=position.unicode
                if(original.isNullOrEmpty()) { out.append('\uFFFD');continue }
                val next=value.indexOf(original,cursor)
                if(next<0) return positions.joinToString(" ") { safeGlyph(it) }
                out.append(value,cursor,next);out.append(safeGlyph(position));cursor=next+original.length
            }
            out.append(value,cursor,value.length);return out.toString()
        }
        val writer=object: Writer() {
            override fun write(chars: CharArray,offset: Int,length: Int) {
                val room=minOf(config.maxPageChars-text.length,config.maxTotalChars-total).coerceAtLeast(0)
                val count=minOf(room,length)
                if(count>0) { text.append(chars,offset,count);total+=count }
                if(count<length) clipped=true
            }
            override fun flush() {}
            override fun close() {}
        }
        val stripper=object: PDFTextStripper() {
            override fun startPage(page: PDPage) {
                super.startPage(page)
                pageNumber=currentPageNo;text.setLength(0);positions=0;clipped=false;glyphs.clear()
            }
            override fun processTextPosition(position: TextPosition) {
                // Avoid unlimited article buffers for deliberately dense text layers.
                if(++positions<=50_000) {
                    super.processTextPosition(position)
                    if(position.dir==0f) glyphs+=PositionedGlyph(safeGlyph(position),position.xDirAdj,position.yDirAdj,position.widthDirAdj,position.widthOfSpace)
                } else clipped=true
            }
            override fun writeString(value: String,textPositions: MutableList<TextPosition>) {
                // Preserve missing font glyphs in both the flat text and coordinate table paths.
                if(textPositions.any { safeGlyph(it)!=it.unicode })
                    super.writeString(safeString(value,textPositions),textPositions)
                else super.writeString(value,textPositions)
            }
            override fun endPage(page: PDPage) {
                super.endPage(page)
                val table=PositionedTableReader().tables(glyphs,PdfTableRules(page).read())
                if(table.isNotBlank()) {
                    val chars=("\n[Native table columns]\n"+table).toCharArray()
                    writer.write(chars,0,chars.size)
                }
                pages+=PageText(pageNumber,text.toString(),truncated=clipped)
            }
        }
        stripper.sortByPosition=true
        stripper.startPage=1
        stripper.endPage=minOf(doc.numberOfPages,config.maxTextPages)
        stripper.writeText(doc,writer)
        // PDFTextStripper skips pages without a content stream; keep their numbering for OCR/UI.
        val byNumber=pages.associateBy { it.pageNumber }
        val numbered=(1..minOf(doc.numberOfPages,config.maxTextPages)).map { byNumber[it] ?: PageText(it,"") }
        val info=doc.documentInformation
        return PdfExtraction(PdfMetadata(info.title.orEmpty().take(512),info.author.orEmpty().take(512)),
            doc.numberOfPages,numbered,doc.numberOfPages>config.maxTextPages || numbered.any { it.truncated })
    }
}
