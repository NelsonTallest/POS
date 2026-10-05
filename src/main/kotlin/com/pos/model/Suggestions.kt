package com.pos.model

import kotlinx.serialization.Serializable

@Serializable
enum class SuggestionConfidence { LOW, MEDIUM, HIGH }

@Serializable
data class AnalysisSuggestion(
    val type: String,
    val rationale: String,
    val confidence: SuggestionConfidence,
    val caveats: List<String> = emptyList(),
)
