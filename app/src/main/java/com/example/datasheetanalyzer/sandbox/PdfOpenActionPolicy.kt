package com.example.datasheetanalyzer.sandbox

import com.example.datasheetanalyzer.domain.security.SecurityFailure
import com.tom_roush.pdfbox.cos.*

/** Only a view destination within this document is allowed. No action is ever executed. */
object PdfOpenActionPolicy {
    fun check(value: COSBase?,pages: Set<COSDictionary>) {
        val destination=when(value) {
            is COSArray -> value
            is COSDictionary -> {
                // /Next could chain a harmless GoTo to a URL, JavaScript or Launch action.
                if(value.getNameAsString(COSName.S)!="GoTo" ||
                    value.keySet().any { it.name !in setOf("Type","S","D") } ||
                    value.getNameAsString(COSName.TYPE)?.let { it!="Action" }==true) reject()
                value.getDictionaryObject(COSName.D) as? COSArray ?: reject()
            }
            else -> reject()
        }
        if(destination.size()<2) reject()
        val page=destination.getObject(0) as? COSDictionary ?: reject()
        if(page !in pages) reject()
        val mode=(destination.getObject(1) as? COSName)?.name ?: reject()
        val length=when(mode) { "XYZ" -> 5;"Fit","FitB" -> 2;"FitH","FitV","FitBH","FitBV" -> 3;"FitR" -> 6;else -> reject() }
        if(destination.size()!=length) reject()
        for(i in 2 until destination.size()) {
            // getObject() converts COSNull to Java null; preserve literal null view parameters.
            val parameter=destination.get(i).let { if(it is COSObject) it.`object` else it }
            if(parameter is COSNumber && parameter.doubleValue().isFinite()) continue
            if(parameter is COSNull && mode!="FitR") continue
            reject()
        }
    }
    private fun reject(): Nothing = throw SecurityFailure("ACTIVE_CONTENT",
        "PDF açılışında dış bağlantı/çalıştırma eylemi veya geçersiz hedef bulundu (OpenAction).")
}
