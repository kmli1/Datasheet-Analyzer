package com.example.datasheetanalyzer.domain.security

import com.example.datasheetanalyzer.domain.model.TrustLevel
import com.example.datasheetanalyzer.domain.resolver.CandidateUrl
import com.example.datasheetanalyzer.domain.resolver.CandidateUrlPolicy
import com.example.datasheetanalyzer.domain.resolver.TrustedDomainRegistry
import java.net.InetAddress

object PdfPolicy {
    private val registry = TrustedDomainRegistry()
    fun source(raw: String): CandidateUrl {
        val url = CandidateUrlPolicy.parse(raw) ?: throw SecurityFailure("URL", "Yalnızca geçerli HTTPS kaynakları kabul edilir.")
        if (registry.classify(url).level !in setOf(TrustLevel.MANUFACTURER, TrustLevel.ARCHIVE, TrustLevel.DISTRIBUTOR))
            throw SecurityFailure("SOURCE", "Bu alan adı indirme için kayıtlı değil.")
        return url
    }
    fun pdfMime(value: String?) {
        val mime = value?.substringBefore(';')?.trim()?.lowercase()
        if (mime !in setOf("application/pdf", "application/octet-stream"))
            throw SecurityFailure("MIME", "Kaynak PDF dosyası döndürmedi.")
    }
    fun length(value: String?, limit: Long): Long? {
        if (value == null) return null
        val size = value.trim().toLongOrNull() ?: throw SecurityFailure("LENGTH", "Dosya boyutu başlığı geçersiz.")
        if (size <= 0 || size > limit) throw SecurityFailure("SIZE", "PDF boş veya boyut sınırını aşıyor.")
        return size
    }
    fun magic(bytes: ByteArray) {
        if (bytes.size < 8 || !bytes.copyOfRange(0,5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII)) ||
            bytes[5].toInt().toChar() !in '1'..'2' || bytes[6].toInt().toChar() != '.' ||
            bytes[7].toInt().toChar() !in '0'..'9')
            throw SecurityFailure("MAGIC", "Dosya geçerli bir PDF başlığıyla başlamıyor.")
    }
    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        if (b.size == 4) {
            val a=b[0]; val c=b[1]; val d=b[2]
            return a !in setOf(0,10,127) && a < 224 &&
                !(a==100 && c in 64..127) && !(a==169 && c==254) && !(a==172 && c in 16..31) &&
                !(a==192 && (c==168 || c==0 || (c==88 && d==99))) &&
                !(a==198 && (c in 18..19 || (c==51 && d==100))) && !(a==203 && c==0 && d==113)
        }
        // Permit only global-unicast IPv6, excluding documentation and transition ranges.
        return b.size==16 && (b[0] and 0xe0)==0x20 &&
            !(b[0]==0x20 && b[1]==0x01 && (b[2]<2 || (b[2]==0x0d && b[3]==0xb8))) &&
            !(b[0]==0x20 && b[1]==0x02)
    }
}
