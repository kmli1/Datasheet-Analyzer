package com.example.datasheetanalyzer.data.workspace

import android.util.AtomicFile
import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.extraction.*
import com.example.datasheetanalyzer.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** App-owned structured records only; source PDFs and temporary paths are never persisted here. */
class WorkspaceStore(directory: File) {
    private val atomic=AtomicFile(File(directory.apply { mkdirs() },"workspace.json"))
    private val flow=MutableStateFlow(read())
    val state=flow.asStateFlow()
    private val comparisonFlow=MutableStateFlow<List<String>>(emptyList())
    val comparisonSelection=comparisonFlow.asStateFlow()
    @Synchronized fun selectForComparison(id: String) {
        require(flow.value.analyses.any { it.id==id })
        if(id in comparisonFlow.value) return
        require(comparisonFlow.value.size<3) { "Karşılaştırmada en fazla 3 kayıt seçilebilir. Önce bir seçimi kaldır." }
        comparisonFlow.value=comparisonFlow.value+id
    }
    @Synchronized fun toggleComparison(id: String) {
        if(id in comparisonFlow.value) comparisonFlow.value=comparisonFlow.value-id else selectForComparison(id)
    }
    @Synchronized fun createFolder(name: String): ProjectFolder {
        val clean=name.trim();require(clean.length in 1..60) { "Proje adı 1–60 karakter olmalı." }
        require(flow.value.folders.none { it.name.equals(clean,true) }) { "Bu adlı bir proje zaten var." }
        require(flow.value.folders.size<40) { "En fazla 40 proje kaydedebilirsin." }
        val folder=ProjectFolder(UUID.randomUUID().toString(),clean)
        store(flow.value.copy(folders=flow.value.folders+folder));return folder
    }
    @Synchronized fun renameFolder(id: String,name: String) {
        val clean=name.trim();require(clean.length in 1..60) { "Proje adı 1–60 karakter olmalı." }
        require(flow.value.folders.any { it.id==id })
        require(flow.value.folders.none { it.id!=id && it.name.equals(clean,true) }) { "Bu adlı bir proje zaten var." }
        store(flow.value.copy(folders=flow.value.folders.map { if(it.id==id) it.copy(name=clean) else it }))
    }
    @Synchronized fun removeFolder(id: String) {
        store(flow.value.copy(folders=flow.value.folders.filter { it.id!=id },analyses=flow.value.analyses.map {
            if(it.folderId==id) it.copy(folderId=null) else it
        }))
    }
    @Synchronized fun save(analysis: SavedAnalysis): SavedAnalysis {
        require(PartNumberNormalizer.isValid(analysis.part));require(analysis.report.results.size<=24)
        require(analysis.folderId==null || flow.value.folders.any { it.id==analysis.folderId }) { "Seçilen proje bulunamadı." }
        require(analysis.note.length<=2000) { "Not en fazla 2000 karakter olabilir." }
        require(analysis.tags.size<=8 && analysis.tags.all { it.length in 1..30 }) { "En fazla 8 etiket; her biri 30 karakter olabilir." }
        require(flow.value.analyses.any { it.id==analysis.id } || flow.value.analyses.size<60) { "60 analiz sınırına ulaştın. Kullanmadığın bir kaydı silebilirsin." }
        val clean=analysis.copy(note=analysis.note.trim(),tags=analysis.tags.distinct())
        store(flow.value.copy(analyses=(listOf(clean)+flow.value.analyses.filter { it.id!=clean.id }).sortedByDescending { it.createdAt }));return clean
    }
    @Synchronized fun removeAnalysis(id: String) { store(flow.value.copy(analyses=flow.value.analyses.filter { it.id!=id })) }
    @Synchronized fun saveTemplate(name: String,criteria: Set<Criterion>,custom: String,graphs: String) {
        val clean=name.trim();require(clean.length in 1..60) { "Şablon adı 1–60 karakter olmalı." }
        require(criteria.isNotEmpty() || custom.isNotBlank() || graphs.isNotBlank()) { "Önce kriter veya grafik başlığı gir." }
        require(custom.length<=500 && graphs.length<=500)
        require(flow.value.templates.size<30) { "En fazla 30 özel şablon kaydedebilirsin." }
        require((AnalysisTemplates.builtIns+flow.value.templates).none { it.name.equals(clean,true) }) { "Bu adlı bir şablon zaten var." }
        store(flow.value.copy(templates=flow.value.templates+AnalysisTemplate(UUID.randomUUID().toString(),clean,criteria,custom,graphs)))
    }
    @Synchronized fun removeTemplate(id: String) { store(flow.value.copy(templates=flow.value.templates.filter { it.id!=id })) }
    @Synchronized fun clear() { write(WorkspaceState()) }
    private fun store(next: WorkspaceState) {
        check(flow.value.loadError==null) { "Proje kayıtları okunamadı. Kayıtları temizlemeden önce yeniden açmayı dene." }
        write(next)
    }
    private fun write(next: WorkspaceState) {
        val bytes=encode(next).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=4*1024*1024) { "Proje kayıtlarının boyut sınırına ulaştın." }
        var output: java.io.FileOutputStream?=null
        try { output=atomic.startWrite();output.write(bytes);atomic.finishWrite(output);flow.value=next
            comparisonFlow.value=comparisonFlow.value.filter { id -> next.analyses.any { it.id==id } }
        }
        catch(e: Exception) { atomic.failWrite(output);throw e }
    }
    private fun read(): WorkspaceState {
        if(!atomic.baseFile.exists() && !File(atomic.baseFile.path+".bak").exists()) return WorkspaceState()
        return try {
            val bytes=atomic.openRead().use { input ->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(output.size()<=4*1024*1024) { val n=input.read(buffer);if(n<0) break;output.write(buffer,0,n) }
                output.toByteArray()
            }
            require(bytes.size<=4*1024*1024)
            decode(JSONObject(String(bytes,Charsets.UTF_8)))
        } catch(_: Exception) { WorkspaceState(loadError="Proje kayıtları okunamadı. Kayıtlar korunuyor; uygulamayı yeniden açmayı dene.") }
    }
    private fun array(items: Iterable<Any>)=JSONArray().apply { items.forEach { put(it) } }
    private fun query(q: ParameterQuery)=JSONObject().put("requested",q.requested).put("criterion",q.criterion?.name)
        .put("keywords",array(q.keywords)).put("mode",q.mode.name)
    private fun hit(h: ExtractedParameter)=JSONObject().put("name",h.parameterName).put("requested",h.requestedCriterion)
        .put("section",h.section?.name).put("page",h.page).put("confidence",h.confidence).put("evidence",h.evidenceLine)
        .put("conditions",h.conditions).put("partScope",h.sourcePartScope).put("origin",h.origin.name).put("warnings",array(h.warnings)).put("raw",h.rawContext)
        .put("numbers",h.numbers?.let { n -> JSONObject().put("min",n.minimum).put("typ",n.typical).put("max",n.maximum).put("value",n.value)
            .put("unit",n.unit.symbol).put("dimension",n.unit.dimension.name).put("scale",n.unit.scale).put("structured",n.structured).put("symmetric",n.symmetric).put("nominal",n.nominal) })
    private fun result(r: ParameterResult)=JSONObject().put("query",query(r.query)).put("status",r.status.name)
        .put("matches",array(r.matches.map(::hit))).put("absolute",array(r.absoluteLimits.map(::hit))).put("explanation",r.explanation)
    private fun encode(s: WorkspaceState)=JSONObject().put("version",1)
        .put("folders",array(s.folders.map { JSONObject().put("id",it.id).put("name",it.name) }))
        .put("templates",array(s.templates.map { JSONObject().put("id",it.id).put("name",it.name).put("criteria",array(it.criteria.map(Criterion::name)))
            .put("custom",it.custom).put("graphs",it.graphTitles) }))
        .put("analyses",array(s.analyses.map { a -> JSONObject().put("id",a.id).put("part",a.part).put("created",a.createdAt)
            .put("source",a.sourceLabel).put("url",a.sourceUrl).put("sha",a.sha256).put("folder",a.folderId).put("note",a.note)
            .put("tags",array(a.tags)).put("eligible",a.report.documentEligible).put("results",array(a.report.results.map(::result)))
            .put("graphs",array(a.report.graphs.map { g -> JSONObject().put("title",g.requested).put("explanation",g.explanation)
                .put("matches",array(g.matches.map { m -> JSONObject().put("title",m.title).put("page",m.page) })) })) }))
    private fun JSONObject.text(key: String,max: Int): String=optString(key,"").also { require(it.length<=max) }
    private fun objects(a: JSONArray,max: Int): List<JSONObject> { require(a.length()<=max);return (0 until a.length()).map(a::getJSONObject) }
    private fun strings(a: JSONArray,max: Int,length: Int): List<String> { require(a.length()<=max);return (0 until a.length()).map { a.getString(it).also { t -> require(t.length<=length) } } }
    private fun decodeQuery(o: JSONObject)=ParameterQuery(o.text("requested",500),o.optString("criterion").takeIf(String::isNotBlank)?.let(Criterion::valueOf),
        strings(o.getJSONArray("keywords"),40,500),QueryMode.valueOf(o.getString("mode")))
    private fun decodeHit(o: JSONObject): ExtractedParameter {
        val n=o.optJSONObject("numbers")?.let { v ->
            fun number(key: String): Double?=if(!v.has(key) || v.isNull(key)) null else v.getDouble(key).also { require(it.isFinite()) }
            val scale=v.getDouble("scale");require(scale.isFinite() && scale>0)
            NumericValues(number("min"),number("typ"),number("max"),number("value"),
                EngineeringUnit(v.text("unit",20),UnitDimension.valueOf(v.getString("dimension")),scale),v.getBoolean("structured"),v.getBoolean("symmetric"),v.optBoolean("nominal",false))
        }
        val page=o.getInt("page");val score=o.getInt("confidence");require(page in 1..1000 && score in 0..100)
        return ExtractedParameter(o.text("requested",500),o.text("name",500),n,o.optString("section").takeIf(String::isNotBlank)?.let(DatasheetSection::valueOf),
            page,score,o.text("raw",4000),o.text("evidence",500),o.text("conditions",500),TextOrigin.valueOf(o.getString("origin")),strings(o.getJSONArray("warnings"),4,300),o.text("partScope",256))
    }
    private fun decodeResult(o: JSONObject)=ParameterResult(decodeQuery(o.getJSONObject("query")),ParameterStatus.valueOf(o.getString("status")),
        objects(o.getJSONArray("matches"),3).map(::decodeHit),objects(o.getJSONArray("absolute"),3).map(::decodeHit),o.text("explanation",500))
    private fun decode(o: JSONObject): WorkspaceState {
        require(o.getInt("version")==1)
        val folders=objects(o.getJSONArray("folders"),40).map { ProjectFolder(it.text("id",64),it.text("name",60)) }
        val templates=objects(o.getJSONArray("templates"),30).map {
            AnalysisTemplate(it.text("id",64),it.text("name",60),strings(it.getJSONArray("criteria"),Criterion.entries.size,60).map(Criterion::valueOf).toSet(),it.text("custom",500),it.text("graphs",500))
        }
        val analyses=objects(o.getJSONArray("analyses"),60).map { a ->
            val part=a.text("part",64);require(PartNumberNormalizer.isValid(part))
            SavedAnalysis(a.text("id",64),part,a.getLong("created"),a.text("source",100),a.text("url",3000),a.text("sha",100),
                ParameterReport(objects(a.getJSONArray("results"),24).map(::decodeResult),a.getBoolean("eligible"),
                    objects(a.optJSONArray("graphs") ?: JSONArray(),3).map { g -> FigureResult(g.text("title",180),
                        objects(g.getJSONArray("matches"),2).map { m -> val page=m.getInt("page");require(page in 1..80);FigureMatch(m.text("title",180),page) },g.text("explanation",500)) }),
                a.optString("folder").takeIf { id -> folders.any { it.id==id } },a.text("note",2000),strings(a.getJSONArray("tags"),8,30))
        }
        require(folders.map { it.id }.distinct().size==folders.size && analyses.map { it.id }.distinct().size==analyses.size && templates.map { it.id }.distinct().size==templates.size)
        return WorkspaceState(folders,analyses.sortedByDescending { it.createdAt },templates)
    }
}
