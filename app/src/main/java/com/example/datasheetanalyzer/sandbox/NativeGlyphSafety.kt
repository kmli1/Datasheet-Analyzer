package com.example.datasheetanalyzer.sandbox

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import java.util.IdentityHashMap

/** Legacy symbolic subsets may decode a printed µ as ASCII m. Do not guess the symbol. */
internal class NativeGlyphSafety {
    private val fonts=IdentityHashMap<PDFont,Boolean>()
    fun opaque(font: PDFont): Boolean {
        fonts[font]?.let { return it }
        val descriptor=font.fontDescriptor
        val name=font.name.orEmpty().substringAfter('+')
        val legacy=font.subType=="Type1" && !font.cosObject.containsKey(COSName.TO_UNICODE) &&
            descriptor?.isSymbolic==true && Regex("III[0-9]{4,6}[A-Z]?").matches(name)
        val charset=if(legacy) descriptor?.cosObject?.getDictionaryObject(COSName.CHAR_SET) as? COSString else null
        val unsafe=legacy && charset!=null && (charset.bytes.size>1024 ||
            descriptor?.charSet.orEmpty().split('/').count(String::isNotBlank) in 1..12)
        if(fonts.size<128) fonts[font]=unsafe
        return unsafe
    }
}
