package com.example.datasheetanalyzer.domain.extraction

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.Criterion
import kotlin.math.abs

class ParameterExtractionEngine(private val config: ParameterConfig=ParameterConfig()) {
    private val parser=EngineeringValueParser()
    fun extract(document: AnalyzedDocument,requestedQueries: List<ParameterQuery>): ParameterReport {
        val queries=requestedQueries.filter { it.mode!=QueryMode.GRAPH }
        if(!document.verification.automaticEligible) {
            val v=document.verification
            val safeExcerpt=document.checked.security==com.example.datasheetanalyzer.domain.security.SecurityStatus.PASSED &&
                document.candidate.source.level in setOf(com.example.datasheetanalyzer.domain.model.TrustLevel.MANUFACTURER,
                    com.example.datasheetanalyzer.domain.model.TrustLevel.ARCHIVE,com.example.datasheetanalyzer.domain.model.TrustLevel.DISTRIBUTOR,
                    com.example.datasheetanalyzer.domain.model.TrustLevel.LOCAL) &&
                document.requestedPart.isNotBlank() && v.match==com.example.datasheetanalyzer.domain.model.PartMatch.EXACT &&
                v.manufacturer.consistency!=ManufacturerConsistency.CONFLICT &&
                document.extraction.pages.any { it.pageNumber<=3 && !it.truncated &&
                    DocumentPartMatcher().match(document.requestedPart,it.rawText)==com.example.datasheetanalyzer.domain.model.PartMatch.EXACT }
            return ParameterReport(queries.take(config.maxQueries).map { query ->
                val excerpts=if(safeExcerpt) find(document,query).matches.map { hit ->
                    hit.copy(numbers=null,confidence=minOf(hit.confidence,59),
                        warnings=hit.warnings+"Bu bir kaynak alıntısıdır; sayısal sonuç otomatik onaylanmadı.")
                } else emptyList()
                ParameterResult(query,ParameterStatus.DOCUMENT_UNVERIFIED,excerpts,
                    explanation=if(excerpts.isEmpty()) "Belge kimliği/yapısı yeterince doğrulanmadı. Neden: ${v.reasons.joinToString(" ")}. Kaynak sayfasını inceleyebilirsin."
                        else "Belge doğrulaması tamamlanmadı. Aranan kod ve kaynak metni bulundu; aşağıdaki alıntıyı kaynak sayfasında incele. Min/tipik/maks sonucu onaylanmadı.")
            },false)
        }
        return ParameterReport(queries.take(config.maxQueries).map { query -> find(document,query) },true)
    }
    private fun find(doc: AnalyzedDocument,query: ParameterQuery): ParameterResult {
        val hits=mutableListOf<ExtractedParameter>()
        val textCriterion=query.criterion in CriterionDictionary.textCriteria
        val packageProof=VerifiedPackageScope.find(doc)
        val verifiedGrades=packageProof?.let { setOf(it.grade) }.orEmpty()
        for(page in doc.extraction.pages) {
            val lines=page.text.lines()
            var captionScope=""
            for((index,line) in lines.withIndex()) {
                val nextLine=lines.getOrNull(index+1).orEmpty()
                if(doc.sections.any { it.pageNumber==page.pageNumber && it.lineIndex==index })
                    captionScope=TablePartScope.fromHeading(line).ifBlank { TablePartScope.fromHeading("$line $nextLine") }
                // A generic family PDF can contain a whole table for a different grade.
                if(!TablePartScope.allows(doc.requestedPart,captionScope,verifiedGrades)) continue
                if(doc.requestedPart.isNotBlank() && DocumentPartMatcher().match(doc.requestedPart,line)==com.example.datasheetanalyzer.domain.model.PartMatch.FAMILY_VARIANT &&
                    verifiedGrades.none { DocumentPartMatcher().match(it,line)==com.example.datasheetanalyzer.domain.model.PartMatch.EXACT }) continue
                val joinedLabel=if(query.criterion!=null && '|' !in line && '=' !in line &&
                    doc.sections.none { it.pageNumber==page.pageNumber && it.lineIndex==index } &&
                    !line.any(Char::isDigit) && line.trim().split(Regex("\\s+")).size<=5 &&
                    !Regex("""(?i)\b(?:min|typ|max|nom|units?)\b""").containsMatchIn(line) &&
                    nextLine.firstOrNull()?.isLetter()==true && '=' !in nextLine &&
                    line.length+nextLine.length<180 && doc.sections.none { it.pageNumber==page.pageNumber && it.lineIndex==index+1 })
                    "$line $nextLine" else line
                val section=section(doc.sections,page.pageNumber,index)
                val technical=section in setOf(DatasheetSection.ELECTRICAL,DatasheetSection.RECOMMENDED_OPERATING,DatasheetSection.ABSOLUTE_MAXIMUM)
                val exact=CriterionLabelMatcher.matches(joinedLabel,query,allowSymbols=technical || textCriterion)
                val fuzzy=!exact && query.criterion==null && overlaps(CriterionLabelMatcher.label(line).substringBefore('='),query.keywords.firstOrNull().orEmpty())
                if(!exact && !fuzzy) continue
                val start=maxOf(0,index-config.contextLines)
                val nextHeader=doc.sections.filter { it.pageNumber==page.pageNumber && it.lineIndex>index }
                    .minOfOrNull { it.lineIndex } ?: lines.size
                val end=minOf(lines.size,index+config.contextLines+1,nextHeader)
                val context=lines.subList(start,end).joinToString("\n").take(3500)
                val sectionStart=doc.sections.filter { it.pageNumber==page.pageNumber && it.lineIndex<=index }.maxOfOrNull { it.lineIndex } ?: 0
                val previous=lines.subList(maxOf(sectionStart,index-100),index)
                var evidence=joinedLabel
                var parsed=if(textCriterion) null else parser.parse(line,previous,CriterionDictionary.dimension(query.criterion))
                if(parsed==null && !line.any(Char::isDigit) && query.criterion!=null && !textCriterion) {
                    // A bounded numeric/unit continuation; another label/equation cannot supply the value.
                    for(offset in 1..2) {
                        val following=lines.getOrNull(index+offset) ?: break
                        if(doc.sections.any { it.pageNumber==page.pageNumber && it.lineIndex==index+offset }) break
                        val prefix=if(joinedLabel!=line && offset==2) joinedLabel else line
                        if(!Regex("""^[+\-±]?(?:\d|\.\d)""").containsMatchIn(following.trim())) {
                            if(joinedLabel!=line && offset==1) continue else break
                        }
                        if(!parser.isValueContinuation(following)) break
                        parsed=parser.parse(prefix+" "+following,previous,CriterionDictionary.dimension(query.criterion))
                        if(parsed!=null) { evidence=prefix+" "+following;break }
                    }
                }
                if(parsed!=null && !TablePartScope.allows(doc.requestedPart,parsed.partScope,verifiedGrades)) continue
                if(parsed==null && '|' !in line && (1..2).any { offset ->
                    Regex("""(?i)^\s*(?:rejection ratio|bandwidth|drift|temperature coefficient)\s*$""")
                        .matches(lines.getOrNull(index+offset).orEmpty()) &&
                        !CriterionLabelMatcher.matches(joinedLabel+" "+lines[index+offset],query)
                }) continue
                val localTechnical=technical && doc.sections.any { it.pageNumber==page.pageNumber &&
                    it.section in setOf(DatasheetSection.ELECTRICAL,DatasheetSection.RECOMMENDED_OPERATING,DatasheetSection.ABSOLUTE_MAXIMUM) }
                if(parsed==null && !textCriterion && query.criterion!=null && (!localTechnical || !exact)) continue
                val priority=priority(query,section)
                val score=((if(exact) 35 else 20)+priority+(if(parsed!=null) 20 else if(textCriterion) 20 else 0)+
                    (if(parsed?.numbers?.structured==true) 15 else 0)+(if(parsed!=null) 5 else 0)).coerceAtMost(100)
                val warnings=mutableListOf<String>()
                if(packageProof!=null && parsed?.partScope==packageProof.grade) warnings+="${doc.requestedPart} → ${packageProof.grade}: ${packageProof.packageCode} paketi, kaynak s.${packageProof.page}."
                if(parsed==null && !textCriterion) warnings+="Parametre adı bulundu; değer/birim/sütunlar güvenilir biçimde çözülemedi. Kaynak satırını incele."
                if(section==DatasheetSection.ABSOLUTE_MAXIMUM) warnings+="Mutlak maksimum sınır; normal çalışma değeri olarak kullanılmaz."
                if(parsed?.numbers?.typical!=null) warnings+="Tipik değer, garanti edilen minimum veya maksimum sınır değildir."
                if(parsed?.numbers?.nominal==true && parsed.numbers.value!=null) warnings+="Nominal değer, tipik değer olarak yorumlanmaz."
                if(parsed?.requiresReview==true) warnings+="Koşul satırı veya bazı semboller tam çözülemedi; kaynak sayfasını kontrol et."
                if(doc.extraction.truncated) warnings+="Belge kısmen okundu; eşleşme yalnızca incelenen sayfaları kapsar."
                if(page.truncated) warnings+="Bu sayfanın metni kesildi; değeri orijinal sayfada doğrula."
                if(page.origin==TextOrigin.OCR) warnings+="OCR sayılar/sütunlar konusunda hata içerebilir; PDF sayfasını kontrol et."
                if(page.origin==TextOrigin.WEB_TEXT) warnings+="Arşiv sayfa metni; PDF aslındaki değer ve sütunları kaynak sayfasından doğrula."
                if(fuzzy) warnings+="Yakın kelime eşleşmesi; kesin kriter eşleşmesi yok."
                if(query.criterion==Criterion.PINOUT) warnings+="Yalnızca belgedeki metin gösterilir; çizimdeki pin sırası tahmin edilmez."
                val body=if(textCriterion) sectionText(doc,page,index,section,context) else context
                val hit=ExtractedParameter(query.requested,query.criterion?.label ?: query.requested,parsed?.numbers,
                    section,page.pageNumber,if(parsed==null && !textCriterion) minOf(score,59) else if(page.origin==TextOrigin.OCR || page.truncated || parsed?.requiresReview==true) minOf(score,74) else score,
                    body,evidence.take(350),parsed?.conditions.orEmpty(),page.origin,warnings,
                    parsed?.partScope?.ifBlank { captionScope } ?: captionScope)
                val guarded=VariantResultGuard.apply(doc.requestedPart,ParameterResult(query,ParameterStatus.POSSIBLE,listOf(hit)),verifiedGrades).matches.single()
                hits+=guarded
            }
        }
        val structured=hits.filter { it.numbers?.structured==true && '|' in it.evidenceLine }
        val unique=hits.filter { hit -> '|' in hit.evidenceLine ||
            structured.none { it.page==hit.page && it.section==hit.section } }
            .distinctBy { Triple(it.page,it.evidenceLine,it.numbers) }
        val absolute=unique.filter { it.section==DatasheetSection.ABSOLUTE_MAXIMUM }.sortedByDescending { it.confidence }
        val main=if(query.mode==QueryMode.ABSOLUTE_MAXIMUM) absolute.ifEmpty { unique }
            else if(query.mode==QueryMode.RECOMMENDED_OPERATING) unique.filter { it.section==DatasheetSection.RECOMMENDED_OPERATING }
                .ifEmpty { unique.filter { it.section!=DatasheetSection.ABSOLUTE_MAXIMUM } }.ifEmpty { absolute }
            else unique.filter { it.section!=DatasheetSection.ABSOLUTE_MAXIMUM }.ifEmpty { absolute }
        val sorted=main.sortedWith(compareByDescending<ExtractedParameter> { priority(query,it.section) }
            .thenByDescending { it.numbers!=null || textCriterion }.thenByDescending { it.confidence })
        if(sorted.isEmpty()) return ParameterResult(query,ParameterStatus.NOT_FOUND,
            explanation="Bu parametre datasheet içerisinde güvenilir şekilde tespit edilemedi.")
        val selected=sorted.take(config.maxMatches)
        val ambiguous=selected.size>1 && selected.filter { it.section==selected.first().section && it.conditions==selected.first().conditions }
            .mapNotNull { it.numbers }.distinctBy(::signature).size>1
        val primary=selected.first()
        val possible=primary.confidence<config.minimumConfidence || ambiguous ||
            (query.mode!=QueryMode.ABSOLUTE_MAXIMUM && primary.section==DatasheetSection.ABSOLUTE_MAXIMUM) ||
            (query.mode==QueryMode.RECOMMENDED_OPERATING && primary.section!=DatasheetSection.RECOMMENDED_OPERATING) ||
            (query.mode==QueryMode.ABSOLUTE_MAXIMUM && primary.section!=DatasheetSection.ABSOLUTE_MAXIMUM)
        return ParameterResult(query,if(possible) ParameterStatus.POSSIBLE else ParameterStatus.DETECTED,selected,
            if(query.mode!=QueryMode.ABSOLUTE_MAXIMUM && primary.section!=DatasheetSection.ABSOLUTE_MAXIMUM) absolute.take(2) else emptyList(),
            when {
                primary.numbers==null && !textCriterion -> "Parametre adı kaynakta bulundu; sayısal değer güvenilir biçimde ayrıştırılamadı. Aşağıdaki satırı kaynak sayfasında incele."
                ambiguous -> "Aynı bağlamda birden fazla farklı değer bulundu; kaynak ve koşulları incele."
                query.mode!=QueryMode.ABSOLUTE_MAXIMUM && primary.section==DatasheetSection.ABSOLUTE_MAXIMUM -> "Önerilen çalışma değeri bulunamadı; yalnızca mutlak sınır tespit edildi."
                query.mode==QueryMode.ABSOLUTE_MAXIMUM && primary.section!=DatasheetSection.ABSOLUTE_MAXIMUM -> "Mutlak maksimum bölümünde sonuç bulunamadı; başka bölümde olası eşleşme var."
                query.mode==QueryMode.RECOMMENDED_OPERATING && primary.section!=DatasheetSection.RECOMMENDED_OPERATING -> "Önerilen çalışma koşulları bölümünde eşleşme bulunamadı; başka bölümdeki kaynak alıntısını incele."
                possible -> "Olası sonuç; değer ve bağlamı PDF sayfasından doğrula."
                else -> "Belgedeki eşleşme ve kaynak sayfası aşağıda."
            })
    }
    private fun signature(n: NumericValues)=Triple(listOf(n.minimum,n.typical,n.maximum,n.value).map { it?.times(n.unit.scale) },n.unit.dimension,n.nominal)
    private fun section(hits: List<SectionHit>,page: Int,line: Int): DatasheetSection? = hits
        .filter { it.pageNumber<page || (it.pageNumber==page && it.lineIndex<=line) }
        .maxWithOrNull(compareBy<SectionHit> { it.pageNumber }.thenBy { it.lineIndex })?.section
    private fun priority(query: ParameterQuery,section: DatasheetSection?): Int {
        if(query.mode==QueryMode.RECOMMENDED_OPERATING) return if(section==DatasheetSection.RECOMMENDED_OPERATING) 25 else 5
        val wanted=when(query.criterion) {
            Criterion.PINOUT -> DatasheetSection.PINOUT;Criterion.PACKAGE -> DatasheetSection.PACKAGE
            Criterion.FEATURES -> DatasheetSection.FEATURES;Criterion.APPLICATIONS -> DatasheetSection.APPLICATIONS
            Criterion.ELECTRICAL_CHARACTERISTICS -> DatasheetSection.ELECTRICAL
            Criterion.ABSOLUTE_MAXIMUMS -> DatasheetSection.ABSOLUTE_MAXIMUM
            Criterion.RECOMMENDED_OPERATING_CONDITIONS -> DatasheetSection.RECOMMENDED_OPERATING
            else -> null
        }
        if(wanted!=null) return if(section==wanted) 25 else 5
        if(query.mode==QueryMode.ABSOLUTE_MAXIMUM) return if(section==DatasheetSection.ABSOLUTE_MAXIMUM) 25 else 5
        return when(section) {
            DatasheetSection.RECOMMENDED_OPERATING -> 25;DatasheetSection.ELECTRICAL -> 20
            DatasheetSection.DESCRIPTION -> 15;DatasheetSection.ABSOLUTE_MAXIMUM -> 0;else -> 10
        }
    }
    private fun sectionText(doc: AnalyzedDocument,page: PageText,index: Int,section: DatasheetSection?,fallback: String): String {
        if(section==null) return fallback
        val heading=doc.sections.firstOrNull { it.pageNumber==page.pageNumber && it.lineIndex==index && it.section==section } ?: return fallback
        val lines=page.text.lines()
        val next=doc.sections.filter { it.pageNumber==page.pageNumber && it.lineIndex>heading.lineIndex }
            .minOfOrNull { it.lineIndex } ?: lines.size
        return lines.subList(index,minOf(next,index+35,lines.size)).joinToString("\n").take(3500)
    }
    private fun overlaps(line: String,phrase: String): Boolean {
        val wanted=SearchText.normalize(phrase).split(' ').filter { it.length>=3 }
        val words=SearchText.normalize(line).split(' ')
        if(wanted.isEmpty()) return false
        val matched=wanted.count { word -> words.any { it==word || (word.length>=5 && abs(it.length-word.length)<=1 && editDistanceOne(it,word)) } }
        return matched >= maxOf(1,kotlin.math.ceil(wanted.size*0.75).toInt())
    }
    private fun editDistanceOne(a: String,b: String): Boolean {
        var i=0;var j=0;var changes=0
        while(i<a.length && j<b.length) {
            if(a[i]==b[j]) { i++;j++;continue }
            if(++changes>1) return false
            when { a.length>b.length -> i++;b.length>a.length -> j++;else -> { i++;j++ } }
        }
        return changes+(a.length-i)+(b.length-j)<=1
    }
}
