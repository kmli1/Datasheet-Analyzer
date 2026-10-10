package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.domain.model.SourceTrust
import com.example.datasheetanalyzer.domain.model.TrustLevel
import java.util.Locale

class TrustedDomainRegistry {
    private val manufacturers = linkedMapOf(
        "ti.com" to "Texas Instruments", "st.com" to "STMicroelectronics",
        "analog.com" to "Analog Devices", "onsemi.com" to "onsemi",
        "nxp.com" to "NXP", "infineon.com" to "Infineon",
        "microchip.com" to "Microchip", "renesas.com" to "Renesas",
        "vishay.com" to "Vishay", "rohm.com" to "ROHM",
        "toshiba-semicon-storage.com" to "Toshiba"
    )
    private val distributors = linkedMapOf("digikey.com" to "DigiKey", "mouser.com" to "Mouser")
    private val archives = linkedMapOf(
        "datasheetarchive.com" to "Datasheet Archive"
    )
    private val shorteners = setOf("bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly")
    private val discussionLabels = setOf("forum", "forums", "blog", "blogs", "community", "e2e")

    // Retained only to reject stale cache entries and redirects from removed sources.
    fun isExcluded(url: CandidateUrl): Boolean = url.host.split('.').any { it.startsWith("alldatasheet") }

    fun classify(url: CandidateUrl): SourceTrust {
        if (isExcluded(url)) return SourceTrust(TrustLevel.BLOCKED, "Devre dışı kaynak")
        val pathSegments = url.path.lowercase(Locale.ROOT).split('/').filter { it.isNotBlank() }
        if (shorteners.any { matchesDomain(url.host, it) }) {
            return SourceTrust(TrustLevel.BLOCKED, "URL kısaltıcı")
        }
        if (url.host.split('.').any { it in discussionLabels } ||
            pathSegments.any { it in discussionLabels }) {
            return SourceTrust(TrustLevel.BLOCKED, "Forum / blog / topluluk")
        }
        manufacturers.entries.firstOrNull { matchesDomain(url.host, it.key) }?.let {
            return SourceTrust(TrustLevel.MANUFACTURER, it.value)
        }
        archives.entries.firstOrNull { matchesDomain(url.host, it.key) }?.let {
            return SourceTrust(TrustLevel.ARCHIVE, it.value)
        }
        distributors.entries.firstOrNull { matchesDomain(url.host, it.key) }?.let {
            return SourceTrust(TrustLevel.DISTRIBUTOR, it.value)
        }
        return SourceTrust(TrustLevel.UNKNOWN, "Tanımlanmamış kaynak")
    }

    private fun matchesDomain(host: String, domain: String) = host == domain || host.endsWith(".$domain")
}
