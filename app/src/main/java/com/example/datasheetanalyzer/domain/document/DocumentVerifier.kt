package com.example.datasheetanalyzer.domain.document

import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.security.DocumentStatus

class DocumentVerifier(private val config: ExtractionConfig=ExtractionConfig()) {
    private val matcher=DocumentPartMatcher()
    fun verify(part: String,candidate: RankedCandidate,extraction: PdfExtraction,sections: List<SectionHit>): DocumentVerification {
        val early=extraction.pages.filter { it.pageNumber<=3 }
        val bodyExact=early.filter { !it.truncated && matcher.match(part,it.rawText)==PartMatch.EXACT }
        val bodyFamily=early.filter { matcher.match(part,it.rawText)==PartMatch.FAMILY_VARIANT }
        val metadataMatch=matcher.match(part,extraction.metadata.title)
        val match=when {
            bodyExact.isNotEmpty() || metadataMatch==PartMatch.EXACT -> PartMatch.EXACT
            bodyFamily.isNotEmpty() || metadataMatch==PartMatch.FAMILY_VARIANT -> PartMatch.FAMILY_VARIANT
            else -> PartMatch.NONE
        }
        val maker=ManufacturerDetector().detect(candidate.source,extraction.metadata,extraction.pages)
        val evidence=(bodyExact.ifEmpty { bodyFamily }).take(3).map { page ->
            val line=page.rawText.lines().firstOrNull { matcher.match(part,it)!=PartMatch.NONE }.orEmpty()
            TextEvidence(page.pageNumber,line.take(220),page.origin)
        } + if(metadataMatch!=PartMatch.NONE) listOf(TextEvidence(null,extraction.metadata.title.take(220))) else emptyList()
        val reasons=mutableListOf<String>()
        val trusted=candidate.source.level in setOf(TrustLevel.MANUFACTURER,TrustLevel.ARCHIVE,TrustLevel.DISTRIBUTOR,TrustLevel.LOCAL)
        var score=if(trusted) 30 else 0
        reasons+="${if(candidate.source.level==TrustLevel.LOCAL) "Kullanıcının seçtiği yerel belge" else "Kayıtlı kaynak"}: ${if(trusted) 30 else 0}"
        val exactScore=if(match==PartMatch.EXACT) 25 else if(match==PartMatch.FAMILY_VARIANT) 15 else 0
        score+=exactScore;reasons+="Parça eşleşmesi: $exactScore"
        val bodyScore=if(bodyExact.isNotEmpty()) 15 else if(bodyFamily.isNotEmpty()) 10 else 0
        score+=bodyScore;reasons+="İlk üç sayfadaki kod kanıtı: $bodyScore"
        val makerScore=if(maker.consistency==ManufacturerConsistency.CONSISTENT) 10 else 0
        score+=makerScore;reasons+="Üretici tutarlılığı: $makerScore"
        val readableSections=sections.filter { hit -> extraction.pages.any { it.pageNumber==hit.pageNumber && !it.truncated } }
        val structureScore=minOf(20,readableSections.map { it.section }.distinct().size*5)
        score+=structureScore;reasons+="Datasheet bölümleri: $structureScore"
        var code="MATCHED"
        when {
            match==PartMatch.NONE -> {
                val readable=early.sumOf { it.rawText.count(Char::isLetterOrDigit) }>=config.sparsePageChars*3
                code=if(readable) "WRONG_DOCUMENT" else "IDENTITY_UNREADABLE"
                score=minOf(score,59)
                reasons+=if(readable) "İlk üç sayfada veya başlıkta aranan kod bulunamadı."
                    else "Belge kimliği okunamadı; kısa/eksik metinden yanlış belge sonucu çıkarılmaz."
            }
            maker.consistency==ManufacturerConsistency.CONFLICT -> { code="WRONG_MANUFACTURER";score=minOf(score,59);reasons+="Üretici alan adı ile belge üreticisi uyuşmuyor." }
            match==PartMatch.FAMILY_VARIANT -> { code="FAMILY_VARIANT";score=minOf(score,74);reasons+="Yalnızca aile/varyant eşleşmesi var; elle inceleme gerekir." }
            bodyExact.isEmpty() -> { code="METADATA_ONLY";score=minOf(score,74);reasons+="Kod yalnızca metadata'da bulundu; içerik kanıtı yok." }
            sections.isEmpty() -> { code="STRUCTURE_UNCERTAIN";score=minOf(score,74);reasons+="Datasheet bölüm başlıkları tespit edilemedi." }
        }
        if(extraction.truncated) reasons+="Sayfa/metin sınırı nedeniyle belge kısmen işlendi; sonuçlar yalnızca okunan kaynak sayfalarını kapsar."
        if(extraction.pages.any { it.origin==TextOrigin.OCR }) reasons+="OCR metni hata içerebilir; ham metni kontrol et."
        val status=when {
            code=="IDENTITY_UNREADABLE" -> DocumentStatus.REVIEW_REQUIRED
            score>=90 -> DocumentStatus.VERIFIED
            score>=75 -> DocumentStatus.LIKELY_VALID
            score>=60 -> DocumentStatus.REVIEW_REQUIRED
            else -> DocumentStatus.REJECTED
        }
        val eligible=score>=config.automaticThreshold && match==PartMatch.EXACT && bodyExact.isNotEmpty() &&
            readableSections.isNotEmpty() &&
            maker.consistency!=ManufacturerConsistency.CONFLICT
        return DocumentVerification(code,status,score,match,maker,eligible,evidence,reasons)
    }
}
