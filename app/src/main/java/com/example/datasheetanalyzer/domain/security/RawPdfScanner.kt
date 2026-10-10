package com.example.datasheetanalyzer.domain.security

/**
 * Bounded early scan for exact forbidden names. Raw bytes can also be image/font streams;
 * a long run after '/' is not evidence of an oversized PDF name. Actual decoded names,
 * including names in compressed objects, are checked by the isolated structural parser.
 */
class RawPdfScanner {
    private var name: StringBuilder? = null
    fun accept(bytes: ByteArray, count: Int = bytes.size) {
        for (i in 0 until count) {
            val c=(bytes[i].toInt() and 255).toChar()
            if (c=='/') { finish(); name=StringBuilder() }
            else if (c.code<=32 || c in "()<>[]{}%") finish()
            else name?.let {
                if (it.length>=MAX_RAW_NAME_BYTES) name=null else it.append(c)
            }
        }
    }
    fun finish() {
        val raw=name?.toString() ?: return
        name=null
        val decoded=Regex("#([0-9A-Fa-f]{2})").replace(raw) { it.groupValues[1].toInt(16).toChar().toString() }
        if (decoded in forbiddenNames) throw SecurityFailure("ACTIVE_CONTENT", "PDF'de engellenen içerik türü bulundu: $decoded.")
    }
    companion object {
        // Only bounds this heuristic's buffer. It is not a document acceptance limit.
        private const val MAX_RAW_NAME_BYTES = 128
        val forbiddenNames = setOf("JavaScript","JS","AA","Launch","EmbeddedFile",
            "EmbeddedFiles","Filespec","EF","RichMedia","XFA","GoToR","GoToE",
            "SubmitForm","ImportData","Movie","Sound","Rendition","3D")
    }
}
