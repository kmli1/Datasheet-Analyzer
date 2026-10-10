package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.PartVariants
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer

object VariantResultGuard {
    fun apply(part: String,result: ParameterResult,verifiedGrades: Set<String> = emptySet()): ParameterResult {
        if(part.isBlank()) return result
        val wanted=PartNumberNormalizer.normalize(part)
        val allowed=verifiedGrades+wanted
        fun guard(hit: ExtractedParameter): ExtractedParameter {
            if(hit.numbers==null) return hit
            if(hit.sourcePartScope.isNotBlank()) {
                val row=hit.evidenceLine.substringBefore('|').substringAfter(':')+" "+hit.conditions
                val rowParts=PartVariants.codes(part,row)
                if(TablePartScope.allows(part,hit.sourcePartScope,verifiedGrades) && rowParts.all { it in allowed }) return hit
                return hit.copy(numbers=null,confidence=minOf(hit.confidence,59),warnings=hit.warnings+
                    "Bu satırın seçilen tam parça varyantına ait olduğu kesin değil; varyantı ve kaynak sütununu kontrol et.")
            }
            val direct=PartVariants.codes(part,hit.evidenceLine+" "+hit.conditions)
            val siblings=direct.filter { it!=wanted && it!=PartVariants.family(part) }
            val context=PartVariants.codes(part,hit.rawContext)
            val ambiguous=direct.isEmpty() && context.count { it!=PartVariants.family(part) }>1
            if(siblings.isEmpty() && !ambiguous) return hit
            if(direct==listOf(wanted)) return hit
            return hit.copy(numbers=null,confidence=minOf(hit.confidence,59),warnings=hit.warnings+
                "Bu satırın seçilen tam parça varyantına ait olduğu kesin değil; varyantı ve kaynak sütununu kontrol et.")
        }
        val hits=result.matches.map(::guard);val limits=result.absoluteLimits.map(::guard)
        val blocked=hits.firstOrNull()?.let { it.numbers==null && it.warnings.any { w -> w.startsWith("Bu satırın seçilen") } }==true
        return result.copy(matches=hits,absoluteLimits=limits,
            status=if(blocked && result.status==ParameterStatus.DETECTED) ParameterStatus.POSSIBLE else result.status,
            explanation=if(blocked) "Varyant eşleşmesi gerekli; farklı varyantın sayısal değeri bu parçaya uygulanmadı." else result.explanation)
    }
}
