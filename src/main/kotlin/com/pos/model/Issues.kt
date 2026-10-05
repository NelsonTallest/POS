package com.pos.model

import kotlinx.serialization.Serializable

@Serializable
enum class Severity { LOW, MEDIUM, HIGH }

@Serializable
data class DataQualityIssue(
    val columnName: String? = null,
    val severity: Severity,
    val category: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)
