package com.example.datasheetanalyzer.data.cache

import com.example.datasheetanalyzer.domain.document.*
import com.example.datasheetanalyzer.domain.model.*
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest

/** Private, bounded, versioned cache. A digest checks integrity, never certifies malware safety. */
class DocumentCache(private val directory: File,private val quarantine: File,private val now: ()->Long=System::currentTimeMillis) {
    private val mutex=Mutex()
    private val ttl=7L*24*60*60*1000
    suspend fun save(part: String,document: AnalyzedDocument) = mutex.withLock {
        if(!document.verification.automaticEligible || document.checked.security!=SecurityStatus.PASSED ||
            document.sourceKind==DocumentSource.ARCHIVE_HTML) return@withLock
        if(document.sourceKind!=DocumentSource.LOCAL_PDF &&
            runCatching { PdfPolicy.source(document.candidate.canonicalUrl) }.isFailure) return@withLock
        directory.mkdirs();quarantine.mkdirs()
        val sha=digest(document.checked.pdf.file)
        if(sha!=document.checked.pdf.sha256) return@withLock
        val snapshot=File(directory,"$sha.payload")
        if(!snapshot.exists()) atomicCopy(document.checked.pdf.file,snapshot)
        val entry=File(directory,"${id(part,document.candidate.canonicalUrl)}.entry")
        val temp=File.createTempFile("entry-",".tmp",directory)
        try {
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { out ->
                out.writeInt(POLICY);out.writeLong(now());out.writeUTF(part);out.writeUTF(document.sourceKind.name)
                out.writeUTF(document.candidate.canonicalUrl);out.writeUTF(sha);out.writeLong(snapshot.length())
                out.writeUTF(document.extraction.metadata.title.take(512));out.writeUTF(document.extraction.metadata.author.take(512))
                out.writeInt(document.extraction.totalPages);out.writeBoolean(document.extraction.truncated)
                out.writeInt(document.extraction.pages.size)
                for(p in document.extraction.pages) {
                    out.writeInt(p.pageNumber);writeText(out,p.rawText);writeText(out,p.text)
                    out.writeUTF(p.origin.name);out.writeBoolean(p.truncated);writeText(out,p.nativeRawText.orEmpty())
                }
                out.writeInt(document.sourcePages.size)
                for((n,url) in document.sourcePages) { out.writeInt(n);out.writeUTF(url) }
            }
            check(temp.length()<=2_000_000)
            check(temp.renameTo(entry));entry.setLastModified(now());prune()
        } finally { temp.delete() }
    }
    suspend fun load(part: String,url: String?=null): AnalyzedDocument? = mutex.withLock {
        val files=directory.listFiles().orEmpty().filter { it.extension=="entry" && it.length() in 1..2_000_000 }
            .sortedByDescending { it.lastModified() }
        for(entry in files) {
            currentCoroutineContext().ensureActive()
            var lease: File?=null
            try {
                val value=DataInputStream(BufferedInputStream(entry.inputStream())).use { input ->
                    require(input.readInt()==POLICY)
                    val timestamp=input.readLong();require(timestamp<=now()+60_000 && now()-timestamp<=ttl)
                    val savedPart=input.readUTF()
                    val source=DocumentSource.valueOf(input.readUTF());val candidateUrl=input.readUTF()
                    require(source!=DocumentSource.ARCHIVE_HTML)
                    if(source!=DocumentSource.LOCAL_PDF) PdfPolicy.source(candidateUrl)
                    if(savedPart!=part) return@use null
                    if(url!=null && candidateUrl!=url) return@use null
                    val sha=input.readUTF();require(Regex("[a-f0-9]{64}").matches(sha))
                    val bytes=input.readLong();require(bytes in 8..20L*1024*1024)
                    val payload=File(directory,"$sha.payload")
                    require(payload.canonicalFile.parentFile==directory.canonicalFile && payload.length()==bytes && digest(payload)==sha)
                    val candidate=if(source==DocumentSource.LOCAL_PDF) {
                        require(candidateUrl=="local://document/$sha")
                        localCandidate(part,candidateUrl)
                    } else {
                        PdfPolicy.source(candidateUrl)
                        CandidateSelector().select(part,listOf(SearchCandidate("$part Datasheet",candidateUrl))).candidates.single().also { require(it.selectable) }
                    }
                    val metadata=PdfMetadata(input.readUTF().also { require(it.length<=512) },input.readUTF().also { require(it.length<=512) })
                    val total=input.readInt();require(total in 1..1000)
                    val truncated=input.readBoolean();val count=input.readInt();require(count in 1..80)
                    var chars=0
                    val pages=(1..count).map {
                        val number=input.readInt();require(number in 1..minOf(total,80))
                        val raw=readText(input);val clean=readText(input);chars+=raw.length;require(chars<=300_000)
                        val origin=TextOrigin.valueOf(input.readUTF());val clipped=input.readBoolean();val native=readText(input)
                        PageText(number,raw,clean,origin,clipped,native.ifEmpty { null })
                    }
                    require(pages.map { it.pageNumber }.distinct().size==count)
                    val pageCount=input.readInt();require(pageCount==0)
                    val sourcePages=emptyMap<Int,String>()
                    require(input.read()==-1)
                    val extraction=PdfExtraction(metadata,total,pages,truncated)
                    val sections=DatasheetSectionDetector().detect(pages)
                    val verification=DocumentVerifier().verify(part,candidate,extraction,sections)
                    require(verification.automaticEligible)
                    if(source!=DocumentSource.ARCHIVE_HTML) {
                        payload.inputStream().use { stream ->
                            val buffer=ByteArray(8192);val scan=RawPdfScanner();val header=ByteArray(8);DataInputStream(stream).readFully(header)
                            PdfPolicy.magic(header);scan.accept(header)
                            while(true) { val n=stream.read(buffer);if(n<0) break;scan.accept(buffer,n) };scan.finish()
                        }
                    }
                    quarantine.mkdirs();lease=File.createTempFile("cache-",if(source==DocumentSource.ARCHIVE_HTML) ".txt" else ".pdf",quarantine)
                    payload.copyTo(lease!!,overwrite=true)
                    AnalyzedDocument(CheckedDocument(QuarantinedPdf(lease!!,candidateUrl,bytes,sha),total,
                        security=if(source==DocumentSource.ARCHIVE_HTML) SecurityStatus.WEB_TEXT_VALIDATED else SecurityStatus.PASSED,
                        identity=verification.status,downloadMs=0,checkMs=0,extraction=extraction),candidate,extraction,sections,verification,
                        sourceKind=source,sourcePages=sourcePages,fromCache=true,requestedPart=part)
                }
                if(value!=null) { currentCoroutineContext().ensureActive();return@withLock value }
            } catch(c: CancellationException) { lease?.delete();throw c }
              catch(_: Exception) { lease?.delete();entry.delete() }
        }
        prune();null
    }
    suspend fun clear() = mutex.withLock { directory.listFiles().orEmpty().forEach { it.delete() };Unit }
    suspend fun bytes(): Long = mutex.withLock { prune();directory.listFiles().orEmpty().sumOf { it.length() } }
    private fun prune() {
        // Drop removed sources even when another part is being searched; original imported files are outside this directory.
        directory.listFiles().orEmpty().filter { it.extension=="entry" }.forEach { entry ->
            val allowed=runCatching {
                DataInputStream(BufferedInputStream(entry.inputStream())).use { input ->
                    require(input.readInt()==POLICY);input.readLong();input.readUTF()
                    val source=DocumentSource.valueOf(input.readUTF());val url=input.readUTF()
                    require(source!=DocumentSource.ARCHIVE_HTML)
                    if(source!=DocumentSource.LOCAL_PDF) PdfPolicy.source(url)
                };true
            }.getOrDefault(false)
            if(!allowed) entry.delete()
        }
        val entries=directory.listFiles().orEmpty().filter { it.extension=="entry" }.sortedByDescending { it.lastModified() }
        entries.forEachIndexed { index,e -> if(index>=12 || now()-e.lastModified()>ttl) e.delete() }
        val used=directory.listFiles().orEmpty().filter { it.extension=="entry" }.mapNotNull { entry ->
            runCatching { DataInputStream(entry.inputStream()).use { it.readInt();it.readLong();it.readUTF();it.readUTF();it.readUTF();it.readUTF() } }.getOrNull()
        }.toSet()
        directory.listFiles().orEmpty().filter { it.extension=="payload" && it.nameWithoutExtension !in used }.forEach { it.delete() }
        var bytes=directory.listFiles().orEmpty().sumOf { it.length() }
        for(e in entries.reversed()) { if(bytes<=100L*1024*1024) break;bytes-=e.length();e.delete() }
        if(bytes>100L*1024*1024) directory.listFiles().orEmpty().filter { it.extension=="payload" }.sortedBy { it.lastModified() }.forEach { f ->
            if(bytes>100L*1024*1024) { bytes-=f.length();f.delete() }
        }
    }
    private fun atomicCopy(source: File,target: File) {
        val tmp=File.createTempFile("payload-",".tmp",directory)
        try { source.copyTo(tmp,true);check(tmp.renameTo(target)) } finally { tmp.delete() }
    }
    companion object {
        const val POLICY=704
        fun digest(file: File): String {
            val d=MessageDigest.getInstance("SHA-256");file.inputStream().use { stream ->
                val buffer=ByteArray(8192);while(true) { val n=stream.read(buffer);if(n<0) break;d.update(buffer,0,n) }
            };return d.digest().joinToString("") { "%02x".format(it) }
        }
        private fun id(part: String,url: String)=MessageDigest.getInstance("SHA-256").digest((part+"\n"+url).toByteArray()).joinToString("") { "%02x".format(it) }
        private fun writeText(out: DataOutputStream,text: String) { require(text.length<=20_000);val b=text.toByteArray(Charsets.UTF_8);out.writeInt(b.size);out.write(b) }
        private fun readText(input: DataInputStream): String { val size=input.readInt();require(size in 0..80_000);val bytes=ByteArray(size);input.readFully(bytes);return bytes.toString(Charsets.UTF_8).also { require(it.length<=20_000) } }
        fun localCandidate(part: String,url: String)=RankedCandidate(SearchCandidate("$part · seçilen PDF",url),url,"Yerel PDF",SourceTrust(TrustLevel.LOCAL,"Kullanıcı tarafından seçildi"),PartMatch.EXACT,0,true,emptyList())
    }
}
