package com.example.datasheetanalyzer.ocr

import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer

data class PartCandidate(val code: String, val rank: Int)

class PartCandidateExtractor {
    private val split = Regex("[\\s,;:()\\[\\]]+")
    private val shortPrefix = Regex("[A-Z]{1,5}")
    private val numericSuffix = Regex("[0-9][A-Z0-9/-]{1,23}")

    fun extract(lines: List<String>): List<PartCandidate> {
        val candidates = linkedSetOf<String>()
        lines.take(24).forEach { line ->
            val tokens = line.trim().take(256).split(split)
                .map { PartNumberNormalizer.normalize(it.trim('.', '!', '?', '"')) }
                .filter { it.isNotEmpty() }
            candidates.addAll(tokens)
            // Join split prefixes such as "LM 383", not entire manufacturer lines.
            tokens.zipWithNext().forEach { (first, second) ->
                if (shortPrefix.matches(first) && numericSuffix.matches(second)) {
                    candidates.add(first + second)
                }
            }
        }
        return candidates.asSequence()
            .filter { it.length in 3..32 && PartNumberNormalizer.isValid(it) }
            .filter { code -> code.any { it in 'A'..'Z' } && code.any { it in '0'..'9' } }
            .map { code ->
                // A ranking heuristic, never a probability or identity verification.
                val rank = (if (code.length >= 4) 20 else 0) +
                    (if (code.count { it in '0'..'9' } >= 2) 10 else 0) +
                    code.length.coerceAtMost(12)
                PartCandidate(code, rank)
            }
            .sortedWith(compareByDescending<PartCandidate> { it.rank }.thenBy { it.code })
            .take(12)
            .toList()
    }
}
