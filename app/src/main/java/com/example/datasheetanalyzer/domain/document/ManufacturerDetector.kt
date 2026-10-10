package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.model.SourceTrust
import com.example.datasheetanalyzer.domain.model.TrustLevel

class ManufacturerDetector {
    private val brands=linkedMapOf(
        "Texas Instruments" to Regex("""(?i)\b(?:Texas Instruments|National Semiconductor)\b"""),
        "STMicroelectronics" to Regex("""(?i)\bST\s*Microelectronics\b"""),
        "Analog Devices" to Regex("""(?i)\bAnalog Devices\b"""),
        "onsemi" to Regex("""(?i)\b(?:onsemi|ON Semiconductor)\b"""),
        "NXP" to Regex("""(?i)\bNXP(?: Semiconductors)?\b"""),
        "Infineon" to Regex("""(?i)\bInfineon(?: Technologies)?\b"""),
        "Microchip" to Regex("""(?i)\bMicrochip(?: Technology)?\b"""),
        "Renesas" to Regex("""(?i)\bRenesas(?: Electronics)?\b"""),
        "Vishay" to Regex("""(?i)\bVishay\b"""), "ROHM" to Regex("""(?i)\bROHM\b"""),
        "Toshiba" to Regex("""(?i)\bToshiba\b""")
    )
    fun detect(source: SourceTrust,metadata: PdfMetadata,pages: List<PageText>): ManufacturerFinding {
        val first=(pages.firstOrNull { it.pageNumber==1 }?.rawText.orEmpty().take(8000)+" "+metadata.author)
        val early=pages.filter { it.pageNumber<=3 }.joinToString(" ") { it.rawText.take(8000) }
        val detected=brands.filterValues { it.containsMatchIn(first+" "+early) }.keys.toList()
        val expected=source.label
        val consistency=if(source.level==TrustLevel.MANUFACTURER) {
            when {
                expected in detected -> ManufacturerConsistency.CONSISTENT
                brands.any { (_,rx) -> rx.containsMatchIn(first) } -> ManufacturerConsistency.CONFLICT
                else -> ManufacturerConsistency.UNKNOWN
            }
        } else if(detected.size==1) ManufacturerConsistency.CONSISTENT else ManufacturerConsistency.UNKNOWN
        return ManufacturerFinding(detected,consistency)
    }
}
