package com.example.datasheetanalyzer.ui.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import com.example.datasheetanalyzer.ocr.PartCandidate
import com.example.datasheetanalyzer.ocr.PartCandidateExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScannerUiState(
    val scanning: Boolean = true,
    val candidates: List<PartCandidate> = emptyList(),
    val detectedLines: List<String> = emptyList(),
    val draft: String = "",
    val error: String? = null,
    val hasProcessedFrame: Boolean = false
)

class ScannerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val extractor = PartCandidateExtractor()
    private val _state = MutableStateFlow(
        ScannerUiState(
            scanning = savedStateHandle["scanning"] ?: true,
            draft = savedStateHandle["draft"] ?: ""
        )
    )
    val state = _state.asStateFlow()

    private fun update(next: ScannerUiState) {
        savedStateHandle["draft"] = next.draft
        savedStateHandle["scanning"] = next.scanning
        _state.value = next
    }

    fun onLines(lines: List<String>) {
        val current = _state.value
        if (!current.scanning) return
        update(current.copy(
            candidates = extractor.extract(lines),
            detectedLines = lines,
            hasProcessedFrame = true,
            error = null
        ))
    }

    fun selectCandidate(code: String) {
        update(_state.value.copy(scanning = false, draft = code, error = null))
    }

    fun editDraft(value: String) {
        // Freeze results when the user starts editing; OCR must never overwrite edits.
        update(_state.value.copy(scanning = false, draft = value.take(64), error = null))
    }

    fun onFailure(message: String) {
        update(_state.value.copy(scanning = false, error = message))
    }

    fun rescan() {
        // Preserve the user's draft while collecting new candidates.
        update(_state.value.copy(scanning = true, candidates = emptyList(),
            detectedLines = emptyList(), error = null, hasProcessedFrame = false))
    }

    fun confirmedCode(): String? {
        val normalized = PartNumberNormalizer.normalize(_state.value.draft)
        if (!PartNumberNormalizer.isValid(normalized)) {
            update(_state.value.copy(error = "Bir kod seç veya A–Z, 0–9, - ve / ile kodu düzelt."))
            return null
        }
        return normalized
    }
}
