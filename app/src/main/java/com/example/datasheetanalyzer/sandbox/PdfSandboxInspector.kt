package com.example.datasheetanalyzer.sandbox

import com.example.datasheetanalyzer.domain.security.RawPdfScanner
import com.example.datasheetanalyzer.domain.security.SandboxResult
import com.example.datasheetanalyzer.domain.security.SecurityConfig
import com.example.datasheetanalyzer.domain.security.SecurityFailure
import com.tom_roush.pdfbox.cos.*
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.RandomAccessBuffer
import com.tom_roush.pdfbox.io.ScratchFile
import com.tom_roush.pdfbox.pdfparser.PDFParser
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.example.datasheetanalyzer.domain.document.PdfExtraction
import java.io.InputStream
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap

/** Call only inside PdfSandboxService. Validates the full object graph before extraction. */
class PdfSandboxInspector(private val config: SecurityConfig = SecurityConfig()) {
    fun inspect(input: InputStream): SandboxResult {
        return try {
            validated(input) { doc -> SandboxResult(true,"PASSED",doc.numberOfPages) }
        } catch(e: SecurityFailure) { SandboxResult(false,e.code) }
          catch(e: InvalidPasswordException) { SandboxResult(false,"ENCRYPTED") }
          catch(e: Exception) { SandboxResult(false,"INVALID_STRUCTURE") }
    }
    fun extract(input: InputStream): PdfExtraction = validated(input) { PdfTextExtractor().extract(it) }
    fun figures(input: InputStream,titles: List<String>): List<com.example.datasheetanalyzer.domain.document.LocatedFigure> =
        validated(input) { PdfFigureExtractor().extract(it,titles) }
    fun evidence(input: InputStream,page: Int,text: String,section: com.example.datasheetanalyzer.domain.document.DatasheetSection?): List<com.example.datasheetanalyzer.domain.document.FigureBounds> =
        validated(input) { PdfEvidenceExtractor().extract(it,page,text,section) }
    private fun <T> validated(input: InputStream,block: (PDDocument)->T): T {
            return RandomAccessBuffer(input).use { source ->
                if(source.length()>config.maxPdfSize || source.length()<8) throw SecurityFailure("SIZE","size")
                ScratchFile(MemoryUsageSetting.setupMainMemoryOnly(32L*1024*1024)).use { scratch ->
                    val parser=PDFParser(source,scratch)
                    parser.setLenient(false)
                    try {
                        parser.parse()
                        parser.pdDocument.use { doc ->
                            if(doc.isEncrypted && !doc.currentAccessPermission.canExtractContent())
                                throw SecurityFailure("EXTRACTION_NOT_PERMITTED","Belge metin çıkarmaya izin vermiyor.")
                            val cos=doc.document
                            if(cos.xrefTable.isEmpty() || cos.startXref<=0) throw SecurityFailure("XREF","xref")
                            if(cos.xrefTable.size>config.maxObjects) throw SecurityFailure("OBJECT_LIMIT","objects")
                            // Includes names stored in compressed object streams, not just raw bytes.
                            cos.dereferenceObjectStreams()
                            val pages=doc.numberOfPages
                            if(pages !in 1..config.maxPages) throw SecurityFailure("PAGE_LIMIT","pages")
                            var actual=0
                            val pageObjects=Collections.newSetFromMap(IdentityHashMap<COSDictionary,Boolean>())
                            for(page in doc.pages) {
                                if(++actual>config.maxPages) throw SecurityFailure("PAGE_LIMIT","pages")
                                pageObjects.add(page.cosObject)
                                val box=page.mediaBox
                                if(!box.width.isFinite() || !box.height.isFinite() || box.width<=0 || box.height<=0)
                                    throw SecurityFailure("PAGE_STRUCTURE","box")
                            }
                            if(actual!=pages) throw SecurityFailure("PAGE_STRUCTURE","count")
                            inspectGraph(listOf(cos.trailer)+cos.objects,doc.documentCatalog.cosObject,pageObjects)
                            block(doc)
                        }
                    } finally { runCatching { parser.document.close() } }
                }
            }
    }
    private fun inspectGraph(roots: List<COSBase>,catalog: COSDictionary,pages: Set<COSDictionary>) {
        val visited=Collections.newSetFromMap(IdentityHashMap<COSBase,Boolean>())
        val pending=ArrayDeque<COSBase>();roots.forEach { pending.add(it) }
        var operations=0
        while(pending.isNotEmpty()) {
            if(++operations>config.maxObjects*8) throw SecurityFailure("OBJECT_LIMIT","graph")
            val node=pending.removeLast()
            if(!visited.add(node)) continue
            if(visited.size>config.maxObjects) throw SecurityFailure("OBJECT_LIMIT","graph")
            when(node) {
                is COSName -> checkName(node)
                is COSObject -> node.`object`?.let { pending.add(it) }
                is COSDictionary -> node.entrySet().forEach { entry ->
                    checkName(entry.key)
                    if(entry.key.name=="OpenAction") {
                        if(node!==catalog) throw SecurityFailure("ACTIVE_CONTENT","OpenAction outside catalog")
                        PdfOpenActionPolicy.check(node.getDictionaryObject(entry.key),pages)
                    }
                    pending.add(entry.value)
                }
                is COSArray -> for(i in 0 until node.size()) pending.add(node.get(i))
            }
            if(pending.size>config.maxObjects) throw SecurityFailure("OBJECT_LIMIT","queue")
        }
    }
    private fun checkName(node: COSName) {
        val name=node.name
        if(name in RawPdfScanner.forbiddenNames) throw SecurityFailure("ACTIVE_CONTENT",name)
        // Check parsed names, never slash-delimited runs in binary stream bytes.
        // The character check bounds the temporary UTF-8 conversion as well.
        if(name.length>config.maxNameBytes || name.toByteArray(Charsets.UTF_8).size>config.maxNameBytes)
            throw SecurityFailure("NAME_LIMIT","PDF iç yapısındaki bir ad izin verilen uzunluğu aştı.")
    }
}
