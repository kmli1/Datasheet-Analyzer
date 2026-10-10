package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import com.example.datasheetanalyzer.domain.security.SecurityStatus

data class PackageScopeProof(val grade: String,val packageCode: String,val page: Int)

/** Only an explicit package caption + order list + literal electrical table scopes can bind a suffix. */
object VerifiedPackageScope {
    fun find(document: AnalyzedDocument): PackageScopeProof? {
        if(!document.verification.automaticEligible || document.checked.security!=SecurityStatus.PASSED ||
            document.verification.manufacturer.consistency==ManufacturerConsistency.CONFLICT) return null
        val wanted=PartNumberNormalizer.normalize(document.requestedPart)
        if(wanted.isBlank()) return null
        val native=document.extraction.pages.filter { !it.truncated }.mapNotNull { page ->
            val original=if(page.origin==TextOrigin.TEXT_LAYER) page.rawText else page.nativeRawText
            original?.let { page.copy(rawText=it,text=TextCleaner().normalize(it),origin=TextOrigin.TEXT_LAYER) }
        }
        val grades=native.flatMap { page -> page.text.lines().filter { '|' in it }.mapNotNull { line ->
            val prefix=line.substringBefore(':')
            prefix.takeIf { ':' in line && !prefix.contains(' ') && PartNumberNormalizer.isValid(prefix) && prefix.any(Char::isDigit) }
        } }.toSet()
        if(grades.isEmpty()) return null
        val code=Regex("""(?i)(?<![A-Z0-9])[A-Z][A-Z0-9-]{2,30}(?![A-Z0-9])""")
        val proofs=mutableListOf<PackageScopeProof>()
        for(page in native) {
            val lines=page.text.lines()
            for(i in lines.indices) {
                val packageCode=Regex("""(?i)\bpackage\s*\(([A-Z]{1,3})\)\s*$""").find(lines[i])?.groupValues?.get(1)?.uppercase() ?: continue
                val order=(i+1..minOf(i+3,lines.lastIndex)).firstOrNull { Regex("""(?i)^order (?:number|numbers|code)\b""").containsMatchIn(lines[it].trim()) } ?: continue
                val orderLine=lines[order]+" "+lines.getOrNull(order+1).orEmpty().takeIf { !it.contains("Package",true) && !it.contains("Order",true) }.orEmpty()
                val parts=code.findAll(orderLine).map { it.value.uppercase() }
                    .filter { it.any(Char::isDigit) && PartNumberNormalizer.isValid(it) }.distinct().toList()
                if(wanted !in parts || parts.size !in 3..8 || parts.any { !it.endsWith(packageCode) }) continue
                val mapped=parts.map { it.removeSuffix(packageCode) }
                if(mapped.distinct().size!=parts.size || mapped.any { it !in grades }) continue
                proofs+=PackageScopeProof(wanted.removeSuffix(packageCode),packageCode,page.pageNumber)
            }
        }
        return proofs.distinct().singleOrNull()
    }
}
