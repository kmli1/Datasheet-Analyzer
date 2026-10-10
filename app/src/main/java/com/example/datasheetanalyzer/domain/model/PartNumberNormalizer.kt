package com.example.datasheetanalyzer.domain.model

import java.util.Locale

object PartNumberNormalizer {
    const val MAX_LENGTH = 64
    private val spaces = Regex("\\s+")
    private val syntax = Regex("[A-Z0-9][A-Z0-9/-]{0,63}")

    fun normalize(raw: String): String =
        raw.trim().uppercase(Locale.ROOT).replace(spaces, "")

    fun isValid(normalized: String): Boolean =
        normalized.length in 1..MAX_LENGTH && syntax.matches(normalized)
}
