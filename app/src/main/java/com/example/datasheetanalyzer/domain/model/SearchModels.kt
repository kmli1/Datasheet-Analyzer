package com.example.datasheetanalyzer.domain.model

data class SearchCandidate(val title: String, val url: String, val snippet: String = "")
enum class PartMatch { EXACT, FAMILY_VARIANT, NONE }
enum class TrustLevel { MANUFACTURER, ARCHIVE, DISTRIBUTOR, UNKNOWN, BLOCKED, LOCAL }
data class SourceTrust(val level: TrustLevel, val label: String)
data class RankedCandidate(
    val candidate: SearchCandidate,
    val canonicalUrl: String,
    val host: String,
    val source: SourceTrust,
    val match: PartMatch,
    val score: Int,
    val selectable: Boolean,
    val reasons: List<String>
)
data class CandidateSelection(
    val candidates: List<RankedCandidate>,
    val recommendedUrl: String?,
    val discardedCount: Int
)
enum class SearchFailureKind { CONNECTION, TIMEOUT, BLOCKED, RATE_LIMITED, FORMAT_CHANGED, RESPONSE_TOO_LARGE, HTTP }
class SearchFailure(val kind: SearchFailureKind, message: String) : Exception(message)
