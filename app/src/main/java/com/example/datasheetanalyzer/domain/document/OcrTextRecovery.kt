package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.model.PartMatch

object OcrTextRecovery {
    private fun nativeText(page: PageText)= (page.nativeRawText ?: page.rawText).substringBefore("[Native table columns]")
    fun damaged(page: PageText): Boolean {
        if(page.origin!=TextOrigin.TEXT_LAYER) return false
        val raw=nativeText(page)
        val missing=raw.count { it=='\uFFFD' }
        return missing>=2 && missing.toDouble()/raw.length.coerceAtLeast(1)>=0.002 ||
            Regex("""�[AVΩ]""").containsMatchIn(raw)
    }
    fun choose(old: PageText?,ocr: String,part: String,config: ExtractionConfig): PageText? {
        val matcher=DocumentPartMatcher()
        val oldMatch=matcher.match(part,old?.rawText.orEmpty());val newMatch=matcher.match(part,ocr)
        val betterIdentity=newMatch==PartMatch.EXACT && oldMatch!=PartMatch.EXACT ||
            newMatch==PartMatch.FAMILY_VARIANT && oldMatch==PartMatch.NONE
        val clearer=old!=null && damaged(old) && ocr.count { it=='\uFFFD' }<nativeText(old).count { it=='\uFFFD' } &&
            ocr.count(Char::isLetterOrDigit)>=maxOf(40,nativeText(old).count(Char::isLetterOrDigit)/2) &&
            !(oldMatch==PartMatch.EXACT && newMatch!=PartMatch.EXACT)
        val longer=ocr.count(Char::isLetterOrDigit)>(old?.rawText?.count(Char::isLetterOrDigit) ?: 0)
        if(!betterIdentity && !clearer && !longer) return null
        val native=old?.nativeRawText ?: old?.rawText
        val blocks=old?.rawText?.substringAfter("[Native table columns]","").orEmpty()
        val combined=ocr+(if(blocks.isNotBlank()) "\n[Native table columns]\n$blocks" else "")
        return PageText(old?.pageNumber ?: 1,combined.take(config.maxPageChars),origin=TextOrigin.OCR,
            truncated=old?.truncated==true || combined.length>config.maxPageChars,nativeRawText=native)
    }
}
