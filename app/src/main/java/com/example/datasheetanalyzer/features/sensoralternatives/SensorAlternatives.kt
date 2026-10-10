package com.example.datasheetanalyzer.features.sensoralternatives

import java.net.URI
import java.util.Locale

enum class SensorMeasurement(val label: String) {
    TEMPERATURE("Sıcaklık"), HUMIDITY("Nem"), PRESSURE("Basınç"), ACCELERATION("İvme"), DISTANCE("Mesafe"), GAS_RESISTANCE("Gaz direnci"),
    ANGULAR_RATE("Açısal hız"), MAGNETIC_FIELD("Manyetik alan"), MAGNETIC_SWITCH("Manyetik anahtar"),
    ILLUMINANCE("Aydınlık düzeyi"), OPTICAL_IRRADIANCE("Optik ışınım"), COLOR("Renk")
}
enum class SensorInterface(val label: String) {
    ANALOG("Analog"), I2C("I²C"), SPI("SPI"), I3C("MIPI I3C"), SMAART_UART("SMAART Wire / UART"),
    ONE_WIRE("1-Wire"), PWM("PWM"), DIGITAL_SWITCH("Sayısal anahtar")
}
enum class SensorField(val label: String) {
    SCOPE("Parça kapsamı"), MEASUREMENT("Ölçüm türü"), SUPPLY("Besleme"), INTERFACE("Çıkış / iletişim"),
    RANGE("Ölçüm aralığı"), ACCURACY("Doğruluk / koşullar"), PACKAGE("Paket"), PINOUT("Pin bağlantıları")
}
data class SensorFact(val text: String, val pages: List<Int>)
data class OperatingVoltage(val minimum: Double, val maximum: Double) {
    init { require(minimum.isFinite() && maximum.isFinite() && minimum>0 && minimum<=maximum) }
    fun covers(other: OperatingVoltage) = minimum<=other.minimum && maximum>=other.maximum
    fun overlap(other: OperatingVoltage): OperatingVoltage? {
        val low=maxOf(minimum,other.minimum);val high=minOf(maximum,other.maximum)
        return if(low<=high) OperatingVoltage(low,high) else null
    }
}
data class SensorSource(val url: String, val revision: String, val pageCount: Int, val sha256: String) {
    val checkedOn = "2026-10-09"
    fun isUsable() = SensorLinks.isManufacturerUrl(url) && URI(url).path.endsWith(".pdf",true) &&
        revision.isNotBlank() && pageCount>0 && sha256.matches(Regex("[a-f0-9]{64}"))
}
data class SensorProfile(
    val code: String, val manufacturer: String, val aliases: Set<String>,
    val measurements: Set<SensorMeasurement>, val interfaces: Set<SensorInterface>,
    val voltage: OperatingVoltage?, val source: SensorSource, val facts: Map<SensorField,SensorFact>,
    val additionalVoltageRanges: List<OperatingVoltage> = emptyList(),
    val axisCounts: Map<SensorMeasurement,Int> = emptyMap()
) {
    val voltageRanges get()=listOfNotNull(voltage)+additionalVoltageRanges
    val measurementLabel get()=measurements.sortedBy { it.ordinal }.joinToString(" · ") { it.label }
    fun isUsable() = code.isNotBlank() && manufacturer.isNotBlank() && measurements.isNotEmpty() && interfaces.isNotEmpty() &&
        source.isUsable() && voltage!=null && axisCounts.all { (key,count) -> key in measurements && count in 1..3 } && SensorField.entries.all { key ->
            facts[key]?.let { it.text.isNotBlank() && it.pages.isNotEmpty() && it.pages.all { p -> p in 1..source.pageCount } }==true
        }
}
object SensorLinks {
    private val hosts=setOf("www.ti.com","www.analog.com","sensirion.com","admin.sensirion.com","www.bosch-sensortec.com","www.st.com")
    fun isManufacturerUrl(url: String): Boolean = runCatching {
        val u=URI(url)
        u.scheme=="https" && u.host in hosts && u.userInfo==null && u.port in setOf(-1,443) && u.fragment==null &&
            u.rawPath!=null && !u.rawPath.contains("..") && !u.rawPath.contains('%')
    }.getOrDefault(false)
    fun documentPage(source: SensorSource,page: Int?): String? = if(source.isUsable() && (page==null || page in 1..source.pageCount))
        source.url+(page?.let { "#page=$it" } ?: "") else null
}
enum class SensorCompatibility(val label: String) {
    DIRECT("Doğrudan muadil · koşullu"), FUNCTIONAL("İşlevsel alternatif"), REVIEW("İnceleme gerekiyor")
}
data class SensorReplacementProof(val original: String,val alternative: String,val url: String,val condition: String)
data class SensorAlternative(val sensor: SensorProfile,val compatibility: SensorCompatibility,val reasons: List<String>,val proof: SensorReplacementProof?)
data class SensorAlternativeSearch(val original: SensorProfile?,val alternatives: List<SensorAlternative>)

/** Independent curated catalogue. It never reads or writes an analysis, a PDF cache or extraction results. */
class SensorAlternatives(
    private val profiles: List<SensorProfile> = SensorCatalog.profiles,
    private val proofs: List<SensorReplacementProof> = SensorCatalog.proofs
) {
    companion object {
        fun normalize(code: String): String? {
            if(code.length>64 || code.isBlank() || !code.matches(Regex("[A-Za-z0-9 _-]+"))) return null
            return code.uppercase(Locale.ROOT).replace(Regex("[ _-]"),"")
        }
    }
    fun profile(input: String): SensorProfile? {
        val normalized=normalize(input) ?: return null
        val matches=profiles.filter { p -> (p.aliases+p.code).any { normalize(it)==normalized } }
        return matches.singleOrNull() // Ambiguous aliases never pick the first or infer a suffix.
    }
    fun find(input: String,includePartial: Boolean=false,sameInterface: Boolean=false): SensorAlternativeSearch {
        val original=profile(input) ?: return SensorAlternativeSearch(null,emptyList())
        val results=profiles.filter { it.code!=original.code }.mapNotNull { other ->
            if(original.measurements.intersect(other.measurements).isEmpty()) return@mapNotNull null
            val missing=original.measurements-other.measurements
            val missingAxes=original.axisCounts.filter { (key,count) -> key in other.measurements && (other.axisCounts[key] ?: 0)<count }
            if((missing.isNotEmpty() || missingAxes.isNotEmpty()) && !includePartial) return@mapNotNull null
            val common=original.interfaces.intersect(other.interfaces)
            if(sameInterface && common.isEmpty()) return@mapNotNull null
            val proof=proofs.singleOrNull { it.original==original.code && it.alternative==other.code &&
                SensorLinks.isManufacturerUrl(it.url) && it.condition.isNotBlank() }
            val complete=original.isUsable() && other.isUsable()
            val coversPower=original.voltageRanges.all { wanted -> other.voltageRanges.any { it.covers(wanted) } }
            val direct=complete && missing.isEmpty() && missingAxes.isEmpty() && common.isNotEmpty() && proof!=null && coversPower
            val kind=when { !complete || missing.isNotEmpty() || missingAxes.isNotEmpty() -> SensorCompatibility.REVIEW
                direct -> SensorCompatibility.DIRECT
                else -> SensorCompatibility.FUNCTIONAL }
            val reasons=buildList {
                if(missing.isNotEmpty()) add("Eksik ölçüm: "+missing.sortedBy { it.ordinal }.joinToString { it.label })
                else add("Seçilen sensörün ölçüm türlerini karşılayan aday.")
                missingAxes.forEach { (key,count) -> add("Eksik / doğrulanmamış eksen: ${key.label}; seçilen sensör $count eksen, aday ${other.axisCounts[key]?.toString() ?: "bilinmiyor"}.") }
                if(common.isEmpty()) add("Çıkış / iletişim yapısı değişiyor; devre ve yazılım uyarlaması gerekir.")
                else add("Ortak arabirim: "+common.sortedBy { it.ordinal }.joinToString { it.label }+". Adres, komutlar ve veri biçimi ayrıca kontrol edilmeli.")
                val overlaps=original.voltageRanges.any { first -> other.voltageRanges.any { it.overlap(first)!=null } }
                when {
                    original.voltage==null || other.voltage==null -> add("Besleme bilgisi eksik.")
                    !overlaps -> add("Besleme aralıkları örtüşmüyor; yeni besleme tasarımı gerekir.")
                    !coversPower -> add("Aday, eski sensörün tüm besleme aralığını karşılamıyor.")
                }
                if(!complete) add("Katalog kanıtı eksik; uyumluluk onayı verilmedi.")
                if(direct) add(proof!!.condition)
                else add("Doğrudan takılabilirlik doğrulanmadı; paket, pinler, ölçüm aralığı ve test koşullarını karşılaştır.")
            }
            SensorAlternative(other,kind,reasons,proof.takeIf { direct })
        }.sortedWith(compareBy<SensorAlternative> { it.compatibility.ordinal }
            .thenByDescending { it.sensor.interfaces.intersect(original.interfaces).isNotEmpty() }.thenBy { it.sensor.code })
        return SensorAlternativeSearch(original,results)
    }
}
